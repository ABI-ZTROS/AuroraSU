/* SPDX-License-Identifier: GPL-2.0 */
/*
 * VFS Debug Hook Integration
 * Hooks into file operations for monitoring and access control
 */

#include "vfs_debug.h"
#include "klog.h"
#include <linux/fs.h>
#include <linux/fdtable.h>
#include <linux/dcache.h>
#include <linux/path.h>
#include <linux/uaccess.h>
#include <linux/version.h>

/* External context from vfs_debug.c */
extern struct vfs_debug_ctx *vfs_debug_get_ctx(void);

/* Get absolute path from file pointer */
static char *get_file_path(struct file *file, char *buf, size_t size)
{
	char *path = NULL;
	
	if (!file || !file->f_path.dentry)
		return NULL;
	
	path = d_path(&file->f_path, buf, size);
	if (IS_ERR(path))
		return NULL;
	
	return path;
}

/* Hook: file open - called from do_filp_open or similar */
void vfs_debug_hook_file_open(struct file *file)
{
	char buf[PATH_MAX];
	char *path;
	int ret;
	
	if (!file)
		return;
	
	/* Count the operation */
	vfs_debug_count_op(VFS_OP_OPEN);
	
	/* Check access control */
	path = get_file_path(file, buf, sizeof(buf));
	if (path) {
		ret = vfs_debug_check_access(path, file->f_flags);
		if (ret) {
			/* Access denied - mark file for denial */
			pr_info("VFS debug: open denied for %s\n", path);
		}
	}
}

/* Hook: file read - called from vfs_read */
void vfs_debug_hook_file_read(struct file *file, size_t count)
{
	(void)count; /* unused for now */
	
	if (!file)
		return;
	
	vfs_debug_count_op(VFS_OP_READ);
}

/* Hook: file write - called from vfs_write */
void vfs_debug_hook_file_write(struct file *file, size_t count)
{
	(void)count; /* unused for now */
	
	if (!file)
		return;
	
	vfs_debug_count_op(VFS_OP_WRITE);
}

/* Hook: file close - called from filp_close */
void vfs_debug_hook_file_close(struct file *file)
{
	if (!file)
		return;
	
	vfs_debug_count_op(VFS_OP_CLOSE);
}

/*
 * Handler for ksu_handle_execveat_sucompat - hook execve for VFS monitoring
 * This is called from the patched kernel code
 */
int ksu_handle_execve_vfs_debug(int *fd, const char __user **filename_user,
                                 void *argv, void *envp, int *flags)
{
	struct vfs_debug_ctx *ctx;
	
	/* Just count exec operations if VFS debug is enabled */
	ctx = vfs_debug_get_ctx();
	if (ctx && ctx->initialized && ctx->policy.enabled) {
		vfs_debug_count_op(VFS_OP_OPEN);
	}
	
	return 0; /* Always allow, just monitoring */
}

/*
 * Handler for ksu_handle_faccessat - hook faccessat for VFS monitoring
 */
int ksu_handle_faccessat_vfs_debug(int *dfd, const char __user **filename_user,
                                    int *mode, int *flags)
{
	struct vfs_debug_ctx *ctx;
	
	ctx = vfs_debug_get_ctx();
	if (ctx && ctx->initialized && ctx->policy.enabled) {
		/* Count access check as read operation */
		vfs_debug_count_op(VFS_OP_READ);
	}
	
	return 0;
}

/*
 * Handler for ksu_handle_stat - hook stat for VFS monitoring
 */
int ksu_handle_stat_vfs_debug(int *dfd, const char __user **filename_user,
                               int *flags)
{
	struct vfs_debug_ctx *ctx;
	
	ctx = vfs_debug_get_ctx();
	if (ctx && ctx->initialized && ctx->policy.enabled) {
		vfs_debug_count_op(VFS_OP_READ);
	}
	
	return 0;
}
