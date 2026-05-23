/*
 * AuroraSU - Advanced Universal Root Overlay for Android
 * Kernel Module Header
 * 
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

#ifndef __AURORA_H__
#define __AURORA_H__

#include <linux/types.h>
#include <linux/cred.h>
#include <linux/workqueue.h>
#include <linux/version.h>

/* Version Information */
#define AURORA_VERSION_MAJOR 1
#define AURORA_VERSION_MINOR 0
#define AURORA_VERSION_PATCH 0
#define AURORA_VERSION_CODE ((AURORA_VERSION_MAJOR << 16) | (AURORA_VERSION_MINOR << 8) | AURORA_VERSION_PATCH)
#define AURORA_VERSION_STRING "v1.0.0"

/* Feature Flags */
#define AURORA_FEATURE_KPM          (1U << 0)   /* KPM support */
#define AURORA_FEATURE_MANUAL_HOOK  (1U << 1)   /* Manual hook support */
#define AURORA_FEATURE_SUSFS        (1U << 2)   /* SUSFS integration */
#define AURORA_FEATURE_DUAL_MODE    (1U << 3)   /* Dual hook mode */
#define AURORA_FEATURE_WEBUI_NEXT   (1U << 4)   /* WebUI Next API */
#define AURORA_FEATURE_REDUNDANCY   (1U << 5)   /* Redundancy protection */

/* Hook Modes */
enum aurora_hook_mode {
    AURORA_HOOK_AUTO = 0,       /* Auto detect best mode */
    AURORA_HOOK_KPROBE,         /* Use kprobe/tracepoint */
    AURORA_HOOK_MANUAL,         /* Use manual syscall table patching */
    AURORA_HOOK_HYBRID,         /* Use both for redundancy */
};

/* Global State */
extern struct cred *aurora_cred;
extern bool aurora_late_loaded;
extern bool aurora_allow_shell;
extern u32 aurora_features;
extern enum aurora_hook_mode aurora_hook_mode_current;

/* SELinux Policy Backup */
extern struct selinux_policy *aurora_backup_sepolicy;

/* Safety States */
extern bool aurora_in_safe_mode;
extern bool aurora_redundancy_active;

/* Utility Macros */
#define AURORA_LOG_TAG "AuroraSU"
#define aurora_info(fmt, ...) pr_info("[" AURORA_LOG_TAG "] " fmt, ##__VA_ARGS__)
#define aurora_err(fmt, ...) pr_err("[" AURORA_LOG_TAG "] " fmt, ##__VA_ARGS__)
#define aurora_warn(fmt, ...) pr_warn("[" AURORA_LOG_TAG "] " fmt, ##__VA_ARGS__)
#define aurora_debug(fmt, ...) pr_debug("[" AURORA_LOG_TAG "] " fmt, ##__VA_ARGS__)

/* Inline Helpers */
static inline int aurora_startswith(const char *s, const char *prefix)
{
    return strncmp(s, prefix, strlen(prefix));
}

static inline int aurora_endswith(const char *s, const char *t)
{
    size_t slen = strlen(s);
    size_t tlen = strlen(t);
    if (tlen > slen)
        return 1;
    return strcmp(s + slen - tlen, t);
}

/* Feature Check */
static inline bool aurora_has_feature(u32 feature)
{
    return (aurora_features & feature) != 0;
}

/* Hook Mode Check */
static inline bool aurora_using_manual_hook(void)
{
    return aurora_hook_mode_current == AURORA_HOOK_MANUAL ||
           aurora_hook_mode_current == AURORA_HOOK_HYBRID;
}

static inline bool aurora_using_kprobe_hook(void)
{
    return aurora_hook_mode_current == AURORA_HOOK_KPROBE ||
           aurora_hook_mode_current == AURORA_HOOK_HYBRID ||
           aurora_hook_mode_current == AURORA_HOOK_AUTO;
}

/* Redundancy Check */
static inline bool aurora_is_redundancy_active(void)
{
    return aurora_redundancy_active;
}

#endif /* __AURORA_H__ */
