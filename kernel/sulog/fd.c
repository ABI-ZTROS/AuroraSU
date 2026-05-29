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

#include <linux/anon_inodes.h>
#include <linux/err.h>
#include <linux/fdtable.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/mutex.h>
#include <linux/poll.h>
#include <linux/sched.h>
#include <linux/uaccess.h>
#include <linux/version.h>
#include "event.h"
#include "../klog.h"

/*
 * Event queue interface (stub implementation)
 * In a full implementation, these would be defined in event_queue.c
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

/* Stub event queue functions */
static ssize_t ksu_event_queue_read(struct ksu_event_queue *queue, char __user *buf,
                                    size_t count, int file_flags)
{
    /* Stub implementation - would read from event queue */
    return -EAGAIN;
}

static __poll_t ksu_event_queue_poll(struct ksu_event_queue *queue,
                                     struct file *file,
                                     poll_table *wait)
{
    /* Stub implementation - would check if data available */
    return 0;
}

static void ksu_event_queue_close(struct ksu_event_queue *queue)
{
    if (queue) {
        queue->closed = true;
        wake_up_all(&queue->read_wait);
    }
}

static bool READ_ONCE_func(bool *ptr)
{
    return *ptr;
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
    if (sulog_queue_initialized && sulog_queue.closed) {
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
