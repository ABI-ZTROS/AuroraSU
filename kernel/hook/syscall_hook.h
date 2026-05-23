/*
 * AuroraSU - Syscall Hook Interface
 * 
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

#ifndef __AURORA_SYSCALL_HOOK_H__
#define __AURORA_SYSCALL_HOOK_H__

#include <linux/types.h>
#include <asm/syscall.h>

/* Syscall Hook Handler Type */
typedef long (*aurora_syscall_hook_fn)(int orig_nr, const struct pt_regs *regs);

/* Hook Registration */
int aurora_register_syscall_hook(int nr, aurora_syscall_hook_fn fn);
void aurora_unregister_syscall_hook(int nr);
bool aurora_has_syscall_hook(int nr);

/* Direct Syscall Table Patching (for manual hook mode) */
void aurora_syscall_table_hook(int nr, void *fn, void **old);
void aurora_syscall_table_unhook(int nr);

/* Hook Manager */
int aurora_syscall_hook_manager_init(void);
void aurora_syscall_hook_manager_exit(void);

/* Kprobe Hook Functions */
int aurora_kprobe_hook_init(void);
void aurora_kprobe_hook_exit(void);

/* Specific Hook Handlers */
long aurora_hook_execve(int orig_nr, const struct pt_regs *regs);
long aurora_hook_setresuid(int orig_nr, const struct pt_regs *regs);
long aurora_hook_newfstatat(int orig_nr, const struct pt_regs *regs);
long aurora_hook_faccessat(int orig_nr, const struct pt_regs *regs);

/* Dispatcher Number (for tracepoint redirection) */
extern int aurora_dispatcher_nr;

/* Syscall Table Pointer */
extern void **aurora_syscall_table;

#endif /* __AURORA_SYSCALL_HOOK_H__ */
