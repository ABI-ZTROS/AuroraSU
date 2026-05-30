/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Copyright (C) 2025 AuroraSU. All Rights Reserved.
 *
 * KPM (Kernel Patch Module) support for AuroraSU
 * Based on SukiSU-Ultra implementation
 */

#ifndef __AURORASU_KPM_H
#define __AURORASU_KPM_H

#include <linux/types.h>
#include <linux/ioctl.h>

/* KPM Control Codes - matching SukiSU-Ultra/KernelPatch specification */
#define SUKISU_KPM_LOAD     1
#define SUKISU_KPM_UNLOAD   2
#define SUKISU_KPM_NUM      3
#define SUKISU_KPM_LIST     4
#define SUKISU_KPM_INFO     5
#define SUKISU_KPM_CONTROL  6
#define SUKISU_KPM_VERSION  7

#define SUKISU_KPM_CONTROL_MIN  1
#define SUKISU_KPM_CONTROL_MAX  10

/* KPM name and args length limits */
#define KPM_NAME_LEN    32
#define KPM_ARGS_LEN    1024

/* IOCTL command for KPM operations
 * NOTE: Command numbers 100/101 are used by KSU_IOCTL_GET_FULL_VERSION and
 * KSU_IOCTL_HOOK_TYPE in the manager. KPM uses 102 and 200 to avoid conflicts,
 * matching the SukiSU-Ultra/KernelPatch specification.
 */
#define KSU_IOCTL_ENABLE_KPM    _IOW('K', 102, bool)
#define KSU_IOCTL_KPM           _IOWR('K', 200, struct ksu_kpm_cmd)

/* KPM command structure for ioctl */
struct ksu_kpm_cmd {
    __u32 control_code;     /* Input: SUKISU_KPM_* command */
    __u64 arg1;             /* Input: first argument (usually pointer) */
    __u64 arg2;             /* Input: second argument (usually pointer/size) */
    __u64 result_code;      /* Output: result pointer */
};

/* KPM module information structure */
struct ksu_kpm_info {
    char name[KPM_NAME_LEN];
    char version[32];
    char license[32];
    char author[64];
    char description[128];
    __u32 size;
    __u32 flags;
};

/* Function prototypes */
int sukisu_handle_kpm(unsigned long control_code, unsigned long arg1,
                      unsigned long arg2, unsigned long result_code);
int sukisu_is_kpm_control_code(unsigned long control_code);
int do_kpm(void __user *arg);

/* KPM function prototypes */
void sukisu_kpm_load_module_path(const char *path, const char *args,
                                 void *ptr, int *result);
void sukisu_kpm_unload_module(const char *name, void *ptr, int *result);
void sukisu_kpm_num(int *result);
void sukisu_kpm_info(const char *name, char *buf, int bufferSize, int *size);
void sukisu_kpm_list(void *out, int bufferSize, int *result);
void sukisu_kpm_control(const char *name, const char *args,
                        long arg_len, int *result);
void sukisu_kpm_version(char *buf, int bufferSize);

#endif /* __AURORASU_KPM_H */
