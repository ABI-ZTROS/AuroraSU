/*
 * AuroraSU - Userspace API Header
 * 
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

#ifndef __AURORA_UAPI_H__
#define __AURORA_UAPI_H__

#include <linux/ioctl.h>
#include <linux/types.h>

/* Magic Numbers */
#define AURORA_INSTALL_MAGIC1 0x4155524F  /* "AURO" */
#define AURORA_INSTALL_MAGIC2 0x52415355  /* "RASU" */

/* Version Info */
struct aurora_get_info_cmd {
    __u32 version;
    __u32 flags;
    __u32 features;
    __u32 hook_mode;
    char version_full[64];
};

/* Event Reporting */
#define AURORA_EVENT_POST_FS_DATA   1
#define AURORA_EVENT_BOOT_COMPLETED 2
#define AURORA_EVENT_MODULE_MOUNTED 3
#define AURORA_EVENT_SAFE_MODE      4

struct aurora_report_event_cmd {
    __u32 event;
};

/* SELinux Policy */
struct aurora_set_sepolicy_cmd {
    __u64 data_len;
    __aligned_u64 data;
};

/* App Profile */
#define AURORA_MAX_GROUPS 32
#define AURORA_MAX_CAPABILITIES 128

struct aurora_root_profile {
    __u32 uid;
    __u32 gid;
    __u32 groups_count;
    __u32 groups[AURORA_MAX_GROUPS];
    __u64 capabilities;
    __u32 flags;
};

struct aurora_app_profile {
    __u32 uid;
    __u32 flags;
    struct aurora_root_profile root_profile;
    char namespace[64];
};

struct aurora_get_app_profile_cmd {
    struct aurora_app_profile profile;
};

struct aurora_set_app_profile_cmd {
    struct aurora_app_profile profile;
};

/* Allowlist */
struct aurora_get_allowlist_cmd {
    __u16 count;
    __u16 total_count;
    __u32 uids[0];
};

/* Feature Management */
struct aurora_get_feature_cmd {
    __u32 feature_id;
    __u64 value;
    __u8 supported;
};

struct aurora_set_feature_cmd {
    __u32 feature_id;
    __u64 value;
};

/* Process Marking */
#define AURORA_MARK_GET     1
#define AURORA_MARK_MARK    2
#define AURORA_MARK_UNMARK  3
#define AURORA_MARK_REFRESH 4

struct aurora_manage_mark_cmd {
    __u32 operation;
    __s32 pid;
    __u32 result;
};

/* Umount Management */
#define AURORA_UMOUNT_WIPE 0
#define AURORA_UMOUNT_ADD  1
#define AURORA_UMOUNT_DEL  2

struct aurora_add_try_umount_cmd {
    __aligned_u64 arg;
    __u32 flags;
    __u8 mode;
};

/* KPM Commands */
struct aurora_kpm_cmd {
    __aligned_u64 control_code;
    __aligned_u64 arg1;
    __aligned_u64 arg2;
    __aligned_u64 result_code;
};

/* Redundancy Status */
struct aurora_redundancy_status_cmd {
    __u32 state;
    __u32 health_flags;
    __u8 in_safe_mode;
    __u8 auto_recovery;
};

/* IOCTL Definitions */
#define AURORA_IOCTL_MAGIC 'A'

#define AURORA_IOCTL_GRANT_ROOT         _IO(AURORA_IOCTL_MAGIC, 1)
#define AURORA_IOCTL_GET_INFO           _IOR(AURORA_IOCTL_MAGIC, 2, struct aurora_get_info_cmd)
#define AURORA_IOCTL_REPORT_EVENT       _IOW(AURORA_IOCTL_MAGIC, 3, struct aurora_report_event_cmd)
#define AURORA_IOCTL_SET_SEPOLICY       _IOWR(AURORA_IOCTL_MAGIC, 4, struct aurora_set_sepolicy_cmd)
#define AURORA_IOCTL_CHECK_SAFEMODE     _IOR(AURORA_IOCTL_MAGIC, 5, __u8)
#define AURORA_IOCTL_GET_ALLOWLIST      _IOWR(AURORA_IOCTL_MAGIC, 6, struct aurora_get_allowlist_cmd)
#define AURORA_IOCTL_UID_GRANTED_ROOT   _IOWR(AURORA_IOCTL_MAGIC, 7, __u32)
#define AURORA_IOCTL_UID_SHOULD_UMOUNT  _IOWR(AURORA_IOCTL_MAGIC, 8, __u32)
#define AURORA_IOCTL_GET_MANAGER_APPID  _IOR(AURORA_IOCTL_MAGIC, 9, __u32)
#define AURORA_IOCTL_GET_APP_PROFILE    _IOWR(AURORA_IOCTL_MAGIC, 10, struct aurora_get_app_profile_cmd)
#define AURORA_IOCTL_SET_APP_PROFILE    _IOW(AURORA_IOCTL_MAGIC, 11, struct aurora_set_app_profile_cmd)
#define AURORA_IOCTL_GET_FEATURE        _IOWR(AURORA_IOCTL_MAGIC, 12, struct aurora_get_feature_cmd)
#define AURORA_IOCTL_SET_FEATURE        _IOW(AURORA_IOCTL_MAGIC, 13, struct aurora_set_feature_cmd)
#define AURORA_IOCTL_MANAGE_MARK        _IOWR(AURORA_IOCTL_MAGIC, 14, struct aurora_manage_mark_cmd)
#define AURORA_IOCTL_ADD_TRY_UMOUNT     _IOW(AURORA_IOCTL_MAGIC, 15, struct aurora_add_try_umount_cmd)
#define AURORA_IOCTL_SET_INIT_PGRP      _IO(AURORA_IOCTL_MAGIC, 16)
#define AURORA_IOCTL_GET_SULOG_FD       _IOR(AURORA_IOCTL_MAGIC, 17, __s32)
#define AURORA_IOCTL_KPM                _IOWR(AURORA_IOCTL_MAGIC, 18, struct aurora_kpm_cmd)
#define AURORA_IOCTL_GET_REDUNDANCY     _IOR(AURORA_IOCTL_MAGIC, 19, struct aurora_redundancy_status_cmd)
#define AURORA_IOCTL_TRIGGER_RECOVERY   _IO(AURORA_IOCTL_MAGIC, 20)

/* Feature IDs */
enum aurora_feature_id {
    AURORA_FEATURE_ID_SU_COMPAT = 0,
    AURORA_FEATURE_ID_LOG,
    AURORA_FEATURE_ID_HIDE_ROOT,
    AURORA_FEATURE_ID_MOUNT_MASTER,
    AURORA_FEATURE_ID_OVERLAY_FS,
    AURORA_FEATURE_ID_WEBUI,
    AURORA_FEATURE_ID_COUNT
};

/* Flags for get_info */
#define AURORA_FLAG_LKM         (1U << 0)
#define AURORA_FLAG_MANAGER     (1U << 1)
#define AURORA_FLAG_LATE_LOAD   (1U << 2)
#define AURORA_FLAG_SAFE_MODE   (1U << 3)
#define AURORA_FLAG_REDUNDANCY  (1U << 4)

#endif /* __AURORA_UAPI_H__ */
