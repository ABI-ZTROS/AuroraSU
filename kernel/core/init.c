/*
 * AuroraSU - Kernel Module Initialization
 * 
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

#include <linux/export.h>
#include <linux/fs.h>
#include <linux/kobject.h>
#include <linux/module.h>
#include <linux/rcupdate.h>
#include <linux/sched.h>
#include <linux/workqueue.h>
#include <linux/moduleparam.h>
#include <linux/kernel.h>

#include "../include/aurora.h"
#include "../policy/allowlist.h"
#include "../policy/app_profile.h"
#include "../policy/feature.h"
#include "../hook/syscall_hook.h"
#include "../hook/manual_hook.h"
#include "../hook/lsm_hook.h"
#include "../supercall/supercall.h"
#include "../runtime/aurorad.h"
#include "../selinux/selinux.h"
#include "../kpm/kpm.h"
#include "../redundancy/redundancy.h"

/* Module Parameters */
static int hook_mode = AURORA_HOOK_AUTO;
module_param(hook_mode, int, 0);
MODULE_PARM_DESC(hook_mode, "Hook mode: 0=auto, 1=kprobe, 2=manual, 3=hybrid");

static bool enable_kpm = true;
module_param(enable_kpm, bool, 0);
MODULE_PARM_DESC(enable_kpm, "Enable KPM support");

static bool enable_redundancy = true;
module_param(enable_redundancy, bool, 0);
MODULE_PARM_DESC(enable_redundancy, "Enable redundancy protection");

#ifdef CONFIG_AURORA_DEBUG
static bool allow_shell = true;
#else
static bool allow_shell = false;
#endif
module_param(allow_shell, bool, 0);
MODULE_PARM_DESC(allow_shell, "Allow shell access (debug only)");

/* Global Variables */
struct cred *aurora_cred;
bool aurora_late_loaded;
bool aurora_allow_shell;
u32 aurora_features = 0;
enum aurora_hook_mode aurora_hook_mode_current = AURORA_HOOK_AUTO;
struct selinux_policy *aurora_backup_sepolicy = NULL;
bool aurora_in_safe_mode = false;
bool aurora_redundancy_active = false;

/* Internal Functions */
static void aurora_detect_features(void)
{
    /* Detect KPM support */
    if (enable_kpm) {
        aurora_features |= AURORA_FEATURE_KPM;
        aurora_info("KPM support enabled\n");
    }
    
    /* Detect Manual Hook support */
    if (aurora_manual_hook_available()) {
        aurora_features |= AURORA_FEATURE_MANUAL_HOOK;
        aurora_info("Manual hook support available\n");
    }
    
    /* Always enable dual mode detection */
    aurora_features |= AURORA_FEATURE_DUAL_MODE;
    
    /* WebUI Next is always supported */
    aurora_features |= AURORA_FEATURE_WEBUI_NEXT;
    
    /* Redundancy protection */
    if (enable_redundancy) {
        aurora_features |= AURORA_FEATURE_REDUNDANCY;
        aurora_info("Redundancy protection enabled\n");
    }
    
    aurora_info("Feature flags: 0x%x\n", aurora_features);
}

static int aurora_select_hook_mode(void)
{
    if (hook_mode != AURORA_HOOK_AUTO) {
        aurora_hook_mode_current = hook_mode;
        aurora_info("Using user-specified hook mode: %d\n", hook_mode);
        return 0;
    }
    
    /* Auto detection logic */
#if defined(CONFIG_KPROBES) && defined(CONFIG_HAVE_SYSCALL_TRACEPOINTS)
    /* GKI device with kprobe support */
    aurora_hook_mode_current = AURORA_HOOK_KPROBE;
    aurora_info("Auto-selected kprobe mode for GKI device\n");
#else
    /* Non-GKI or limited kprobe support */
    if (aurora_has_feature(AURORA_FEATURE_MANUAL_HOOK)) {
        aurora_hook_mode_current = AURORA_HOOK_MANUAL;
        aurora_info("Auto-selected manual hook mode for non-GKI device\n");
    } else {
        aurora_err("No suitable hook mode available!\n");
        return -EINVAL;
    }
#endif
    
    return 0;
}

static int __init aurora_init_early(void)
{
    aurora_info("Early initialization started\n");
    
    /* Check if late loaded (LKM mode) */
#ifdef MODULE
    aurora_late_loaded = (current->pid != 1);
#else
    aurora_late_loaded = false;
#endif
    
    aurora_allow_shell = allow_shell;
    
    if (aurora_allow_shell) {
        aurora_warn("SHELL ACCESS ENABLED - DEBUG MODE\n");
    }
    
    return 0;
}

static int __init aurora_init(void)
{
    int ret;
    
    aurora_info("========================================\n");
    aurora_info("AuroraSU %s initializing...\n", AURORA_VERSION_STRING);
    aurora_info("========================================\n");
    
    ret = aurora_init_early();
    if (ret) {
        aurora_err("Early init failed: %d\n", ret);
        return ret;
    }
    
    /* Detect available features */
    aurora_detect_features();
    
    /* Select hook mode */
    ret = aurora_select_hook_mode();
    if (ret) {
        aurora_err("Hook mode selection failed: %d\n", ret);
        return ret;
    }
    
    /* Initialize credential structure */
    aurora_cred = prepare_creds();
    if (!aurora_cred) {
        aurora_err("Failed to prepare credentials\n");
        return -ENOMEM;
    }
    
    /* Initialize symbol resolver */
    aurora_init_symbol_resolver();
    
    /* Initialize hook system based on selected mode */
    if (aurora_using_kprobe_hook()) {
        ret = aurora_kprobe_hook_init();
        if (ret) {
            aurora_err("Kprobe hook init failed: %d\n", ret);
            if (aurora_hook_mode_current == AURORA_HOOK_KPROBE) {
                goto err_cred;
            }
            /* Fallback to manual if hybrid */
            aurora_warn("Falling back to manual hook\n");
        }
    }
    
    if (aurora_using_manual_hook()) {
        ret = aurora_manual_hook_init();
        if (ret) {
            aurora_err("Manual hook init failed: %d\n", ret);
            if (!aurora_using_kprobe_hook()) {
                goto err_cred;
            }
        }
    }
    
    /* Initialize feature management */
    aurora_feature_init();
    
    /* Initialize LSM hooks */
    aurora_lsm_hook_init();
    
    /* Initialize SELinux management */
    aurora_selinux_init();
    
    /* Initialize Supercall interface */
    aurora_supercall_init();
    
    /* Initialize KPM if enabled */
    if (aurora_has_feature(AURORA_FEATURE_KPM)) {
        ret = aurora_kpm_init();
        if (ret) {
            aurora_warn("KPM init failed: %d (continuing without KPM)\n", ret);
            aurora_features &= ~AURORA_FEATURE_KPM;
        }
    }
    
    /* Initialize redundancy protection */
    if (aurora_has_feature(AURORA_FEATURE_REDUNDANCY)) {
        ret = aurora_redundancy_init();
        if (ret) {
            aurora_warn("Redundancy init failed: %d\n", ret);
        } else {
            aurora_redundancy_active = true;
        }
    }
    
    /* Late load specific initialization */
    if (aurora_late_loaded) {
        aurora_info("Late load mode detected\n");
        
        /* Apply SELinux rules immediately */
        aurora_apply_selinux_rules();
        aurora_cache_sid();
        aurora_setup_cred();
        
        /* Grant root to current process (aurorad) */
        aurora_escape_to_root_for_init();
        
        /* Initialize allowlist */
        aurora_allowlist_init();
        aurora_load_allow_list();
        
        /* Initialize syscall hook manager */
        aurora_syscall_hook_manager_init();
        
        /* Initialize aurorad integration */
        aurora_aurorad_init_late();
        
        /* Enforce SELinux if permissive */
        if (!aurora_getenforce()) {
            aurora_info("Enforcing SELinux\n");
            aurora_setenforce(true);
        }
    } else {
        /* Normal boot path */
        aurora_syscall_hook_manager_init();
        aurora_allowlist_init();
        aurora_aurorad_init();
    }
    
    aurora_info("========================================\n");
    aurora_info("AuroraSU initialized successfully!\n");
    aurora_info("Hook mode: %s\n", 
        aurora_hook_mode_current == AURORA_HOOK_KPROBE ? "kprobe" :
        aurora_hook_mode_current == AURORA_HOOK_MANUAL ? "manual" :
        aurora_hook_mode_current == AURORA_HOOK_HYBRID ? "hybrid" : "auto");
    aurora_info("Features: 0x%x\n", aurora_features);
    aurora_info("========================================\n");
    
    return 0;

err_cred:
    if (aurora_cred) {
        put_cred(aurora_cred);
        aurora_cred = NULL;
    }
    return ret;
}

static void __exit aurora_exit(void)
{
    aurora_info("Shutting down AuroraSU...\n");
    
    /* Stop all hooks first */
    aurora_syscall_hook_manager_exit();
    
    /* Cleanup supercall */
    aurora_supercall_exit();
    
    /* Cleanup aurorad */
    if (!aurora_late_loaded) {
        aurora_aurorad_exit();
    }
    
    /* Wait for RCU readers */
    synchronize_rcu();
    
    /* Cleanup remaining components */
    aurora_allowlist_exit();
    aurora_selinux_exit();
    aurora_lsm_hook_exit();
    
    if (aurora_has_feature(AURORA_FEATURE_KPM)) {
        aurora_kpm_exit();
    }
    
    if (aurora_redundancy_active) {
        aurora_redundancy_exit();
    }
    
    /* Cleanup hooks */
    if (aurora_using_manual_hook()) {
        aurora_manual_hook_exit();
    }
    if (aurora_using_kprobe_hook()) {
        aurora_kprobe_hook_exit();
    }
    
    /* Cleanup credentials */
    if (aurora_cred) {
        put_cred(aurora_cred);
        aurora_cred = NULL;
    }
    
    aurora_info("AuroraSU shutdown complete\n");
}

module_init(aurora_init);
module_exit(aurora_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("AuroraSU Team");
MODULE_DESCRIPTION("AuroraSU - Advanced Universal Root Overlay for Android");
MODULE_VERSION(AURORA_VERSION_STRING);
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 13, 0)
MODULE_IMPORT_NS("VFS_internal_I_am_really_a_fs_and_am_NOT_a_driver");
#else
MODULE_IMPORT_NS(VFS_internal_I_am_really_a_fs_and_am_NOT_a_driver);
#endif
