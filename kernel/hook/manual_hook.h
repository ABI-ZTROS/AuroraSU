/*
 * AuroraSU - Manual Hook Support
 * 
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

#ifndef __AURORA_MANUAL_HOOK_H__
#define __AURORA_MANUAL_HOOK_H__

#include <linux/types.h>
#include <linux/linkage.h>

/* Manual Hook Handlers - Called from patched kernel functions */
int aurora_handle_execveat(int *fd, struct filename **filename_ptr, 
                           void *argv, void *envp, int *flags);
int aurora_handle_faccessat(int *dfd, const char __user **filename_user, 
                            int *mode, int *__unused_flags);
int aurora_handle_stat(int *dfd, const char __user **filename_user, int *flags);
int aurora_handle_sys_reboot(int magic1, int magic2, unsigned int cmd, 
                             void __user **arg);

/* Compatibility return hooks */
int aurora_handle_newfstatat_ret(unsigned int *fd, struct stat __user **statbuf_ptr);
int aurora_handle_fstat64_ret(unsigned long *fd, struct stat64 __user **statbuf_ptr);

/* Initialization */
int aurora_manual_hook_init(void);
void aurora_manual_hook_exit(void);
bool aurora_manual_hook_available(void);

/* Patch Management */
struct aurora_patch_entry {
    const char *symbol;
    void *handler;
    bool applied;
};

int aurora_apply_manual_patches(void);
void aurora_remove_manual_patches(void);

/* Architecture-specific */
#ifdef CONFIG_ARM64
int aurora_patch_arm64_function(void *target, void *replacement);
void aurora_unpatch_arm64_function(void *target, void *original);
#endif

#ifdef CONFIG_X86_64
int aurora_patch_x86_function(void *target, void *replacement);
void aurora_unpatch_x86_function(void *target, void *original);
#endif

#endif /* __AURORA_MANUAL_HOOK_H__ */
