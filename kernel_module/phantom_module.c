/*
 * phantom_module.c - OnePlus ACE5 (SM8650) Phantom Kernel Module
 *
 * External kernel module for Android GKI 6.1 (SM8650 / ARM64)
 * Designed to be compiled against GKI prebuilt kernel headers.
 *
 * Target device: OnePlus ACE5 (codename: phantom)
 * SoC: Qualcomm SM8650 (Snapdragon 8 Gen 3)
 * Kernel: 6.1.x (Android 14 GKI)
 */

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/proc_fs.h>
#include <linux/seq_file.h>
#include <linux/uaccess.h>
#include <linux/version.h>
#include <linux/slab.h>

#define MODULE_NAME "phantom_module"
#define PROC_DIR_NAME "phantom"
#define PROC_INFO_NAME "info"
#define PROC_STATUS_NAME "status"

MODULE_LICENSE("GPL");
MODULE_AUTHOR("AuroraSU Team");
MODULE_DESCRIPTION("OnePlus ACE5 (SM8650) Phantom Kernel Module");
MODULE_VERSION("1.0.0");

static struct proc_dir_entry *proc_dir = NULL;
static struct proc_dir_entry *proc_info = NULL;
static struct proc_dir_entry *proc_status = NULL;

/* Module state */
static bool module_active = true;
static int phantom_debug_level = 0;
module_param_named(debug_level, phantom_debug_level, int, 0644);
MODULE_PARM_DESC(debug_level, "Debug output level (0=none, 1=basic, 2=verbose)");

/* Device information */
static const char *device_name = "OnePlus ACE5";
static const char *soc_name = "SM8650";
static const char *kernel_compat = "6.1.x GKI";

/*
 * proc_info_show - Display module information via /proc/phantom/info
 */
static int proc_info_show(struct seq_file *m, void *v)
{
    seq_printf(m, "=== Phantom Module Info ===\n");
    seq_printf(m, "Device:    %s\n", device_name);
    seq_printf(m, "SoC:       %s\n", soc_name);
    seq_printf(m, "Kernel:    %s\n", kernel_compat);
    seq_printf(m, "Version:   %s\n", MODULE_VERSION);
    seq_printf(m, "Status:    %s\n", module_active ? "Active" : "Inactive");
    seq_printf(m, "Debug:     Level %d\n", phantom_debug_level);
    seq_printf(m, "===========================\n");
    return 0;
}

static int proc_info_open(struct inode *inode, struct file *file)
{
    return single_open(file, proc_info_show, NULL);
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
static const struct proc_ops proc_info_ops = {
    .proc_open  = proc_info_open,
    .proc_read  = seq_read,
    .proc_lseek = seq_lseek,
    .proc_release = single_release,
};
#else
static const struct file_operations proc_info_ops = {
    .owner   = THIS_MODULE,
    .open    = proc_info_open,
    .read    = seq_read,
    .llseek  = seq_lseek,
    .release = single_release,
};
#endif

/*
 * proc_status_show - Display module status via /proc/phantom/status
 */
static int proc_status_show(struct seq_file *m, void *v)
{
    seq_printf(m, "%s\n", module_active ? "1" : "0");
    return 0;
}

static int proc_status_open(struct inode *inode, struct file *file)
{
    return single_open(file, proc_status_show, NULL);
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
static const struct proc_ops proc_status_ops = {
    .proc_open  = proc_status_open,
    .proc_read  = seq_read,
    .proc_lseek = seq_lseek,
    .proc_release = single_release,
};
#else
static const struct file_operations proc_status_ops = {
    .owner   = THIS_MODULE,
    .open    = proc_status_open,
    .read    = seq_read,
    .llseek  = seq_lseek,
    .release = single_release,
};
#endif

/*
 * phantom_module_init - Module initialization
 */
static int __init phantom_module_init(void)
{
    pr_info("[%s] Initializing for %s (%s)\n", MODULE_NAME, device_name, soc_name);
    pr_info("[%s] Kernel compatibility: %s\n", MODULE_NAME, kernel_compat);

    /* Create /proc/phantom directory */
    proc_dir = proc_mkdir(PROC_DIR_NAME, NULL);
    if (!proc_dir) {
        pr_err("[%s] Failed to create /proc/%s\n", MODULE_NAME, PROC_DIR_NAME);
        return -ENOMEM;
    }

    /* Create /proc/phantom/info */
    proc_info = proc_create(PROC_INFO_NAME, 0444, proc_dir, &proc_info_ops);
    if (!proc_info) {
        pr_err("[%s] Failed to create /proc/%s/%s\n",
               MODULE_NAME, PROC_DIR_NAME, PROC_INFO_NAME);
        remove_proc_entry(PROC_DIR_NAME, NULL);
        return -ENOMEM;
    }

    /* Create /proc/phantom/status */
    proc_status = proc_create(PROC_STATUS_NAME, 0444, proc_dir, &proc_status_ops);
    if (!proc_status) {
        pr_err("[%s] Failed to create /proc/%s/%s\n",
               MODULE_NAME, PROC_DIR_NAME, PROC_STATUS_NAME);
        remove_proc_entry(PROC_INFO_NAME, proc_dir);
        remove_proc_entry(PROC_DIR_NAME, NULL);
        return -ENOMEM;
    }

    module_active = true;
    pr_info("[%s] Module loaded successfully\n", MODULE_NAME);
    pr_info("[%s] Interface: /proc/%s/%s, /proc/%s/%s\n",
            MODULE_NAME, PROC_DIR_NAME, PROC_INFO_NAME, PROC_DIR_NAME, PROC_STATUS_NAME);

    return 0;
}

/*
 * phantom_module_exit - Module cleanup
 */
static void __exit phantom_module_exit(void)
{
    pr_info("[%s] Unloading module...\n", MODULE_NAME);

    if (proc_status)
        remove_proc_entry(PROC_STATUS_NAME, proc_dir);
    if (proc_info)
        remove_proc_entry(PROC_INFO_NAME, proc_dir);
    if (proc_dir)
        remove_proc_entry(PROC_DIR_NAME, NULL);

    module_active = false;
    pr_info("[%s] Module unloaded\n", MODULE_NAME);
}

module_init(phantom_module_init);
module_exit(phantom_module_exit);
