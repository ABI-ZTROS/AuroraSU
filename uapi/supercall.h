/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Copyright (C) 2025 AuroraSU. All Rights Reserved.
 *
 * UAPI SuperCall definitions for AuroraSU
 * Based on SukiSU-Ultra implementation
 */

#ifndef __KSU_UAPI_SUPERCALL_H
#define __KSU_UAPI_SUPERCALL_H

#include <linux/types.h>
#include <linux/ioctl.h>

/*
 * KPM (Kernel Patch Module) Control Codes
 * Based on KernelPatch/SukiSU-Ultra specification
 */
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

/*
 * KPM IOCTL Commands
 * NOTE: Command numbers 100/101 are used by KSU_IOCTL_GET_FULL_VERSION and
 * KSU_IOCTL_HOOK_TYPE in the manager. KPM uses 102 and 200 to avoid conflicts,
 * matching the SukiSU-Ultra/KernelPatch specification.
 */
#define KSU_IOCTL_ENABLE_KPM    _IOW('K', 102, bool)
#define KSU_IOCTL_KPM           _IOWR('K', 200, struct ksu_kpm_cmd)

/*
 * KPM command structure for ioctl
 */
struct ksu_kpm_cmd {
    __u32 control_code;     /* Input: SUKISU_KPM_* command */
    __u64 arg1;             /* Input: first argument (usually pointer) */
    __u64 arg2;             /* Input: second argument (usually pointer/size) */
    __u64 result_code;      /* Output: result pointer */
};

/*
 * KPM module information structure
 */
struct ksu_kpm_info {
    char name[KPM_NAME_LEN];
    char version[32];
    char license[32];
    char author[64];
    char description[128];
    __u32 size;
    __u32 flags;
};

/*
 * Version Spoofing IOCTL
 * NOTE: Uses command number 42 to match SukiSU-Ultra specification.
 * Command number 102 is used by KSU_IOCTL_ENABLE_KPM.
 */
#define KSU_IOCTL_SET_SPOOF_VERSION _IOW('K', 42, struct ksu_spoof_version_cmd)

/*
 * Version spoofing command structure
 */
struct ksu_spoof_version_cmd {
    char release[65];       /* Input: kernel release string (e.g., "5.10.0") */
    char version[65];       /* Input: kernel version string */
};

/*
 * Sulog IOCTL Commands
 * NOTE: Uses command number 20 to match SukiSU-Ultra specification.
 */
#define KSU_IOCTL_GET_SULOG_FD  _IOR('K', 20, int)

/*
 * Sulog event types
 */
enum ksu_sulog_event_type {
    KSU_SULOG_EVENT_NONE = 0,
    KSU_SULOG_EVENT_SU_GRANT = 1,
    KSU_SULOG_EVENT_SU_DENY = 2,
    KSU_SULOG_EVENT_EXECVE = 3,
    KSU_SULOG_EVENT_SUCOMPAT = 4,
    KSU_SULOG_EVENT_MAX
};

/*
 * Sulog event record header
 */
struct ksu_sulog_event_hdr {
    __u16 type;             /* Event type (enum ksu_sulog_event_type) */
    __u16 flags;            /* Event flags */
    __u32 len;              /* Total length of event record */
    __u64 seq;              /* Sequence number */
    __u64 ts_ns;            /* Timestamp in nanoseconds */
};

/*
 * Sulog SU grant event
 */
struct ksu_sulog_event_su_grant {
    struct ksu_sulog_event_hdr hdr;
    __u32 uid;              /* Requesting UID */
    __u32 euid;             /* Effective UID after grant */
    __s32 pid;              /* Process ID */
    __s32 ppid;             /* Parent Process ID */
    char scontext[64];      /* SELinux context */
    char command[128];      /* Command being executed */
};

/*
 * Sulog execve event
 */
struct ksu_sulog_event_execve {
    struct ksu_sulog_event_hdr hdr;
    __u32 uid;
    __s32 pid;
    __s32 ppid;
    int retval;             /* Return value from execve */
    char filename[128];
};

/*
 * SuperCall magic numbers for reboot hook
 */
#define KSU_INSTALL_MAGIC1 0xDEADBEEF
#define KSU_INSTALL_MAGIC2 0xCAFEBABE

/*
 * Toolkit extensions
 */
#define CHANGE_MANAGER_UID 10006
#define KSU_UMOUNT_GETSIZE 107
#define KSU_UMOUNT_GETLIST 108
#define GET_SULOG_DUMP_V2 10010
#define CHANGE_KSUVER 10011
#define CHANGE_SPOOF_UNAME 10012

/*
 * ZTR_OS SU: SuperKey management
 */
#define ZTRSU_SUPERKEY_SET 10020
#define ZTRSU_SUPERKEY_VERIFY 10021
#define ZTRSU_SUPERKEY_GET_STATUS 10022

/*
 * ZTR_OS SU: KPM SuperCall extensions
 */
#define ZTRSU_KPM_LOAD 10030
#define ZTRSU_KPM_UNLOAD 10031
#define ZTRSU_KPM_NUM 10032
#define ZTRSU_KPM_LIST 10033
#define ZTRSU_KPM_INFO 10034
#define ZTRSU_KPM_CONTROL 10035
#define ZTRSU_KPM_VERSION 10036

#endif /* __KSU_UAPI_SUPERCALL_H */
