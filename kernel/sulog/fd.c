/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Copyright (C) 2025 AuroraSU. All Rights Reserved.
 *
 * Sulog file descriptor implementation for AuroraSU
 * Based on SukiSU-Ultra implementation
 *
 * Provides an anonymous file descriptor for reading sulog events
 * via the KSU_IOCTL_GET_SULOG_FD ioctl.
 */

#include <linux/compiler.h>
#include <linux/anon_inodes.h>
#include <linux/err.h>
#include <linux/fdtable.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/ktime.h>
#include <linux/mutex.h>
#include <linux/poll.h>
#include <linux/sched.h>
#include <linux/slab.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <linux/uaccess.h>
#include <linux/version.h>
#include "event.h"
#include "../klog.h"

/*
 * Event record header stored in the queue
 */
struct ksu_event_record_hdr {
    __u16 type;
    __u16 flags;
    __u32 len;
    __u64 seq;
    __u64 ts_ns;
};

/*
 * Event queue node (linked list entry)
 */
struct ksu_event_queue_node {
    struct list_head list;
    struct ksu_event_record_hdr hdr;
    __u8 payload[];
};

/*
 * Event queue interface
 */
struct ksu_event_queue {
    spinlock_t lock;
    struct mutex read_lock;
    struct list_head pending;
    wait_queue_head_t read_wait;
    __u32 queued;
    __u32 max_queued;
    __u32 max_payload_len;
    __u64 next_seq;
    __u64 dropped_total;
    __u64 dropped_pending;
    __u64 dropped_first_seq;
    __u64 dropped_last_seq;
    __u64 dropped_inflight;
    __u64 dropped_inflight_first_seq;
    __u64 dropped_inflight_last_seq;
    bool closed;
};

/*
 * Check if queue has data (must be called with lock held)
 */
static bool ksu_event_queue_has_data_locked(const struct ksu_event_queue *queue)
{
    return queue->dropped_pending || queue->dropped_inflight ||
           !list_empty(&queue->pending);
}

/*
 * Check if queue has data (can be called without lock)
 */
static bool ksu_event_queue_has_data(struct ksu_event_queue *queue)
{
    unsigned long irq_flags;
    bool has_data;

    spin_lock_irqsave(&queue->lock, irq_flags);
    has_data = ksu_event_queue_has_data_locked(queue);
    spin_unlock_irqrestore(&queue->lock, irq_flags);

    return has_data;
}

/*
 * Wait until data is available or queue is closed
 */
static int ksu_event_queue_wait_ready(struct ksu_event_queue *queue, int file_flags)
{
    int ret;

    for (;;) {
        if (ksu_event_queue_has_data(queue))
            return 0;

        if (READ_ONCE(queue->closed))
            return 0;

        if (file_flags & O_NONBLOCK)
            return -EAGAIN;

        ret = wait_event_interruptible(queue->read_wait,
                                        queue->closed || ksu_event_queue_has_data(queue));
        if (ret)
            return ret;
    }
}

/*
 * Read a dropped-event record from the queue
 */
static ssize_t ksu_event_queue_read_drop(struct ksu_event_queue *queue,
                                         char __user *buf, size_t count)
{
    struct ksu_event_record_hdr hdr;
    /* Dropped info: 3 x __u64 */
    struct {
        __u64 dropped;
        __u64 first_seq;
        __u64 last_seq;
    } info;
    size_t record_size = sizeof(hdr) + sizeof(info);
    unsigned long irq_flags;

    spin_lock_irqsave(&queue->lock, irq_flags);
    if (!queue->dropped_pending) {
        spin_unlock_irqrestore(&queue->lock, irq_flags);
        return 0;
    }
    if (count < record_size) {
        spin_unlock_irqrestore(&queue->lock, irq_flags);
        return -EMSGSIZE;
    }

    hdr.type = 0xFFFF; /* KSU_EVENT_QUEUE_TYPE_DROPPED */
    hdr.flags = 1;     /* KSU_EVENT_RECORD_FLAG_INTERNAL */
    hdr.len = sizeof(info);
    hdr.seq = queue->dropped_first_seq;
    hdr.ts_ns = ktime_get_ns();

    info.dropped = queue->dropped_pending;
    info.first_seq = queue->dropped_first_seq;
    info.last_seq = queue->dropped_last_seq;

    queue->dropped_inflight = queue->dropped_pending;
    queue->dropped_inflight_first_seq = queue->dropped_first_seq;
    queue->dropped_inflight_last_seq = queue->dropped_last_seq;
    queue->dropped_pending = 0;
    queue->dropped_first_seq = 0;
    queue->dropped_last_seq = 0;
    spin_unlock_irqrestore(&queue->lock, irq_flags);

    if (copy_to_user(buf, &hdr, sizeof(hdr)))
        goto out_restore;

    if (copy_to_user(buf + sizeof(hdr), &info, sizeof(info)))
        goto out_restore;

    spin_lock_irqsave(&queue->lock, irq_flags);
    queue->dropped_inflight = 0;
    queue->dropped_inflight_first_seq = 0;
    queue->dropped_inflight_last_seq = 0;
    spin_unlock_irqrestore(&queue->lock, irq_flags);

    return record_size;

out_restore:
    spin_lock_irqsave(&queue->lock, irq_flags);
    if (!queue->dropped_pending) {
        queue->dropped_pending = queue->dropped_inflight;
        queue->dropped_first_seq = queue->dropped_inflight_first_seq;
        queue->dropped_last_seq = queue->dropped_inflight_last_seq;
    } else {
        queue->dropped_pending += queue->dropped_inflight;
        queue->dropped_first_seq = queue->dropped_inflight_first_seq;
    }
    queue->dropped_inflight = 0;
    queue->dropped_inflight_first_seq = 0;
    queue->dropped_inflight_last_seq = 0;
    spin_unlock_irqrestore(&queue->lock, irq_flags);

    return -EFAULT;
}

/*
 * Read a pending event node from the queue
 */
static ssize_t ksu_event_queue_read_node(struct ksu_event_queue *queue,
                                          char __user *buf, size_t count)
{
    struct ksu_event_queue_node *node;
    struct list_head *first;
    size_t record_size;
    unsigned long irq_flags;

    spin_lock_irqsave(&queue->lock, irq_flags);
    if (list_empty(&queue->pending)) {
        spin_unlock_irqrestore(&queue->lock, irq_flags);
        return 0;
    }

    first = queue->pending.next;
    node = list_entry(first, struct ksu_event_queue_node, list);
    record_size = sizeof(node->hdr) + node->hdr.len;
    if (count < record_size) {
        spin_unlock_irqrestore(&queue->lock, irq_flags);
        return -EMSGSIZE;
    }
    spin_unlock_irqrestore(&queue->lock, irq_flags);

    if (copy_to_user(buf, &node->hdr, sizeof(node->hdr)))
        return -EFAULT;

    if (node->hdr.len && copy_to_user(buf + sizeof(node->hdr), node->payload, node->hdr.len))
        return -EFAULT;

    spin_lock_irqsave(&queue->lock, irq_flags);
    list_del(first);
    queue->queued--;
    spin_unlock_irqrestore(&queue->lock, irq_flags);

    kfree(node);
    return record_size;
}

/*
 * Read events from the queue into userspace buffer
 */
static ssize_t ksu_event_queue_read(struct ksu_event_queue *queue, char __user *buf,
                                    size_t count, int file_flags)
{
    ssize_t ret;
    ssize_t copied = 0;

    if (!count)
        return 0;

    ret = mutex_lock_interruptible(&queue->read_lock);
    if (ret)
        return ret;

    ret = ksu_event_queue_wait_ready(queue, file_flags);
    if (ret) {
        copied = ret;
        goto out_unlock;
    }

    while (count > 0) {
        ret = ksu_event_queue_read_drop(queue, buf, count);
        if (ret < 0) {
            if (!copied)
                copied = ret;
            break;
        }
        if (ret > 0) {
            copied += ret;
            buf += ret;
            count -= ret;
            continue;
        }

        ret = ksu_event_queue_read_node(queue, buf, count);
        if (ret < 0) {
            if (!copied)
                copied = ret;
            break;
        }
        if (ret == 0)
            break;

        copied += ret;
        buf += ret;
        count -= ret;
    }

    if (!copied && READ_ONCE(queue->closed))
        copied = 0;

out_unlock:
    mutex_unlock(&queue->read_lock);
    return copied;
}

/*
 * Poll the event queue for readable data
 */
static __poll_t ksu_event_queue_poll(struct ksu_event_queue *queue,
                                     struct file *file,
                                     poll_table *wait)
{
    __poll_t mask = 0;
    unsigned long irq_flags;

    poll_wait(file, &queue->read_wait, wait);

    spin_lock_irqsave(&queue->lock, irq_flags);
    if (ksu_event_queue_has_data_locked(queue))
        mask |= POLLIN | POLLRDNORM;
    if (queue->closed)
        mask |= POLLHUP;
    spin_unlock_irqrestore(&queue->lock, irq_flags);

    return mask;
}

/*
 * Push an event onto the queue
 */
int ksu_event_queue_write(struct ksu_event_queue *queue, __u16 type, __u16 flags,
                          const void *payload, __u32 len, gfp_t gfp)
{
    struct ksu_event_queue_node *node = NULL;
    unsigned long irq_flags;
    __u64 seq;
    bool wake = false;
    int ret = 0;

    if (len > queue->max_payload_len)
        return -EMSGSIZE;

    if (len && !payload)
        return -EINVAL;

    node = kmalloc(sizeof(*node) + len, gfp);
    if (node) {
        INIT_LIST_HEAD(&node->list);
        node->hdr.type = type;
        node->hdr.flags = flags;
        node->hdr.len = len;
        node->hdr.ts_ns = 0;
        node->hdr.seq = 0;
        if (len)
            memcpy(node->payload, payload, len);
    }

    spin_lock_irqsave(&queue->lock, irq_flags);
    if (queue->closed) {
        ret = -EPIPE;
        goto out_unlock;
    }

    seq = queue->next_seq++;
    if (!node || (queue->max_queued && queue->queued >= queue->max_queued)) {
        /* Drop event */
        queue->dropped_total++;
        if (!queue->dropped_pending)
            queue->dropped_first_seq = seq;
        queue->dropped_pending++;
        queue->dropped_last_seq = seq;
        wake = true;
        ret = node ? -ENOSPC : -ENOMEM;
        goto out_unlock;
    }

    node->hdr.seq = seq;
    node->hdr.ts_ns = ktime_get_ns();
    list_add_tail(&node->list, &queue->pending);
    queue->queued++;
    wake = true;

out_unlock:
    spin_unlock_irqrestore(&queue->lock, irq_flags);

    if (ret && node)
        kfree(node);

    if (wake)
        wake_up_interruptible_poll(&queue->read_wait, EPOLLIN | EPOLLRDNORM);

    return ret;
}
EXPORT_SYMBOL(ksu_event_queue_write);

static void ksu_event_queue_close(struct ksu_event_queue *queue)
{
    unsigned long irq_flags;

    spin_lock_irqsave(&queue->lock, irq_flags);
    queue->closed = true;
    spin_unlock_irqrestore(&queue->lock, irq_flags);
    wake_up_all(&queue->read_wait);
}

/* Static sulog event queue instance */
static struct ksu_event_queue sulog_queue;
static bool sulog_queue_initialized = false;

/* Lock for protecting sulog fd state */
static DEFINE_MUTEX(ksu_sulog_fd_lock);
static bool ksu_sulog_fd_active;

/*
 * File operations for sulog fd
 */

/*
 * Read from sulog fd
 * Reads events from the event queue into userspace buffer
 */
static ssize_t ksu_sulog_read(struct file *file, char __user *buf,
                              size_t count, loff_t *ppos)
{
    /* Delegate to event queue read function */
    return ksu_event_queue_read(&sulog_queue, buf, count, file->f_flags);
}

/*
 * Poll for sulog fd
 * Allows userspace to use select/poll/epoll for event notification
 */
static __poll_t ksu_sulog_poll(struct file *file, poll_table *wait)
{
    return ksu_event_queue_poll(&sulog_queue, file, wait);
}

/*
 * Release sulog fd
 * Called when the file descriptor is closed
 */
static int ksu_sulog_release(struct inode *inode, struct file *file)
{
    mutex_lock(&ksu_sulog_fd_lock);
    ksu_sulog_fd_active = false;
    mutex_unlock(&ksu_sulog_fd_lock);

    pr_info("sulog: fd released\n");
    return 0;
}

/*
 * File operations table for sulog anonymous file
 */
static const struct file_operations ksu_sulog_fops = {
    .owner = THIS_MODULE,
    .read = ksu_sulog_read,
    .poll = ksu_sulog_poll,
    .release = ksu_sulog_release,
    .llseek = noop_llseek,
};

/*
 * Get the sulog event queue
 * Used by other sulog components
 */
struct ksu_event_queue *ksu_sulog_get_queue(void)
{
    return &sulog_queue;
}
EXPORT_SYMBOL(ksu_sulog_get_queue);

/*
 * Install a sulog file descriptor for the current process
 * Returns: fd number on success, negative error code on failure
 */
int ksu_install_sulog_fd(void)
{
    struct file *filp;
    int fd;

    mutex_lock(&ksu_sulog_fd_lock);

    /* Check if already active */
    if (ksu_sulog_fd_active) {
        fd = -EBUSY;
        goto out_unlock;
    }

    /* Check if queue is closed */
    if (sulog_queue_initialized && READ_ONCE(sulog_queue.closed)) {
        fd = -EPIPE;
        goto out_unlock;
    }

    /* Allocate a new file descriptor */
    fd = get_unused_fd_flags(O_CLOEXEC);
    if (fd < 0)
        goto out_unlock;

    /* Create anonymous inode file */
    filp = anon_inode_getfile("[ksu_sulog]", &ksu_sulog_fops, NULL,
                              O_RDONLY | O_CLOEXEC);
    if (IS_ERR(filp)) {
        put_unused_fd(fd);
        fd = PTR_ERR(filp);
        goto out_unlock;
    }

    /* Mark as active and install fd */
    ksu_sulog_fd_active = true;
    fd_install(fd, filp);

    pr_info("sulog: fd installed %d for pid %d\n", fd, current->pid);

out_unlock:
    mutex_unlock(&ksu_sulog_fd_lock);
    return fd;
}
EXPORT_SYMBOL(ksu_install_sulog_fd);

/*
 * IOCTL handler for KSU_IOCTL_GET_SULOG_FD
 * Creates and returns a sulog file descriptor to userspace
 */
int ksu_handle_get_sulog_fd(void __user *arg)
{
    int fd;
    int __user *user_fd = arg;

    /* Install the sulog fd */
    fd = ksu_install_sulog_fd();
    if (fd < 0) {
        return fd;
    }

    /* Copy fd to userspace */
    if (put_user(fd, user_fd)) {
        /* Failed to copy to userspace, close the fd */
        #if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0)
            ksys_close(fd);
        #else
            sys_close(fd);
        #endif
        return -EFAULT;
    }

    return 0;
}
EXPORT_SYMBOL(ksu_handle_get_sulog_fd);

/*
 * Initialize sulog fd subsystem
 */
void __init ksu_sulog_fd_init(void)
{
    mutex_lock(&ksu_sulog_fd_lock);
    ksu_sulog_fd_active = false;

    /* Initialize the event queue */
    if (!sulog_queue_initialized) {
        memset(&sulog_queue, 0, sizeof(sulog_queue));
        spin_lock_init(&sulog_queue.lock);
        mutex_init(&sulog_queue.read_lock);
        INIT_LIST_HEAD(&sulog_queue.pending);
        init_waitqueue_head(&sulog_queue.read_wait);
        sulog_queue.max_queued = KSU_SULOG_QUEUE_MAX_QUEUED;
        sulog_queue.max_payload_len = KSU_SULOG_MAX_PAYLOAD_LEN;
        sulog_queue.closed = false;
        sulog_queue_initialized = true;
    }

    mutex_unlock(&ksu_sulog_fd_lock);

    pr_info("sulog: fd subsystem initialized\n");
}
EXPORT_SYMBOL(ksu_sulog_fd_init);

/*
 * Cleanup sulog fd subsystem
 */
void __exit ksu_sulog_fd_exit(void)
{
    mutex_lock(&ksu_sulog_fd_lock);
    ksu_sulog_fd_active = false;

    /* Close the event queue */
    if (sulog_queue_initialized) {
        ksu_event_queue_close(&sulog_queue);
        sulog_queue_initialized = false;
    }

    mutex_unlock(&ksu_sulog_fd_lock);

    pr_info("sulog: fd subsystem exited\n");
}
EXPORT_SYMBOL(ksu_sulog_fd_exit);
