/* SPDX-License-Identifier: GPL-2.0 */
#ifndef __VFS_DEBUG_HOOK_H
#define __VFS_DEBUG_HOOK_H

#include <linux/fs.h>

/* VFS operation hooks - called from kernel syscall handlers */
void vfs_debug_hook_file_open(struct file *file);
void vfs_debug_hook_file_read(struct file *file, size_t count);
void vfs_debug_hook_file_write(struct file *file, size_t count);
void vfs_debug_hook_file_close(struct file *file);

/* Syscall hook handlers - integrate with KernelSU's syscall hooks */
int ksu_handle_execve_vfs_debug(int *fd, const char __user **filename_user,
                                 void *argv, void *envp, int *flags);
int ksu_handle_faccessat_vfs_debug(int *dfd, const char __user **filename_user,
                                    int *mode, int *flags);
int ksu_handle_stat_vfs_debug(int *dfd, const char __user **filename_user,
                               int *flags);

#endif /* __VFS_DEBUG_HOOK_H */
