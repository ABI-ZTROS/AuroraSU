/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Copyright (C) 2025 AuroraSU. All Rights Reserved.
 *
 * UTS (uname) version spoofing implementation for AuroraSU
 * Based on SukiSU-Ultra implementation
 */

#include <linux/string.h>
#include <linux/uaccess.h>
#include <linux/kallsyms.h>
#include <linux/slab.h>
#include "uts_spoof.h"
#include "../klog.h"
#include "../../uapi/supercall.h"

/*
 * Internal function to perform the actual version spoofing
 * @sem: Pointer to uts_sem (can be NULL, will use kallsyms lookup)
 * @ns: Pointer to init_uts_ns (can be NULL, will use kallsyms lookup)
 * @release: New release string, NULL to keep current
 * @version: New version string, NULL to keep current
 */
static void do_spoof_version(struct rw_semaphore *sem, struct uts_namespace *ns,
                             const char *release, const char *version)
{
    /* Use provided semaphore or fall back to global uts_sem */
    if (sem) {
        down_write(sem);
    } else {
        down_write(&uts_sem);
    }

    /* Update the specified namespace or init_uts_ns */
    if (ns) {
        if (release && release[0] != '\0') {
            strscpy(ns->name.release, release, sizeof(ns->name.release));
        }
        if (version && version[0] != '\0') {
            strscpy(ns->name.version, version, sizeof(ns->name.version));
        }
    } else {
        if (release && release[0] != '\0') {
            strscpy(init_uts_ns.name.release, release, sizeof(init_uts_ns.name.release));
        }
        if (version && version[0] != '\0') {
            strscpy(init_uts_ns.name.version, version, sizeof(init_uts_ns.name.version));
        }
    }

    /* Release the semaphore */
    if (sem) {
        up_write(sem);
    } else {
        up_write(&uts_sem);
    }

    /* Log the spoofing operation */
    if (ns) {
        pr_info("ksu: spoofed version: %s, release: %s\n",
                ns->name.version, ns->name.release);
    } else {
        pr_info("ksu: spoofed version: %s, release: %s\n",
                init_uts_ns.name.version, init_uts_ns.name.release);
    }
}

/*
 * Spoof the kernel version using kallsyms to find required symbols
 * This is the main entry point for version spoofing
 */
void ksu_spoof_version(const char *spoof_release, const char *spoof_version)
{
    struct rw_semaphore *sem = NULL;
    struct uts_namespace *ns = NULL;

    /*
     * Try to find symbols via kallsyms
     * On some kernels, these may not be exported, so we use kallsyms_lookup_name
     */
    sem = (struct rw_semaphore *)kallsyms_lookup_name("uts_sem");
    ns = (struct uts_namespace *)kallsyms_lookup_name("init_uts_ns");

    if (!sem) {
        pr_warn("ksu: uts_sem not found via kallsyms, using default\n");
    }
    if (!ns) {
        pr_warn("ksu: init_uts_ns not found via kallsyms, using default\n");
    }

    do_spoof_version(sem, ns, spoof_release, spoof_version);
}
EXPORT_SYMBOL(ksu_spoof_version);

/*
 * Wrapper function for setting spoofed version
 * Returns 0 on success
 */
int ksu_set_spoof_version(const char *release, const char *version)
{
    struct rw_semaphore *sem = NULL;
    struct uts_namespace *ns = NULL;

    /* Try to find symbols via kallsyms */
    sem = (struct rw_semaphore *)kallsyms_lookup_name("uts_sem");
    ns = (struct uts_namespace *)kallsyms_lookup_name("init_uts_ns");

    do_spoof_version(sem, ns, release, version);
    return 0;
}
EXPORT_SYMBOL(ksu_set_spoof_version);

/*
 * IOCTL handler for version spoofing
 * Handles KSU_IOCTL_SET_SPOOF_VERSION
 */
int ksu_handle_spoof_version(void __user *arg)
{
    struct ksu_spoof_version_cmd cmd;
    int ret = 0;

    /* Copy command from userspace */
    if (copy_from_user(&cmd, arg, sizeof(cmd))) {
        pr_err("ksu: spoof_version: copy_from_user failed\n");
        return -EFAULT;
    }

    /* Ensure null termination */
    cmd.release[sizeof(cmd.release) - 1] = '\0';
    cmd.version[sizeof(cmd.version) - 1] = '\0';

    /* Validate input - at least one must be non-empty */
    if (cmd.release[0] == '\0' && cmd.version[0] == '\0') {
        pr_warn("ksu: spoof_version: both release and version are empty\n");
        return -EINVAL;
    }

    /* Apply the spoofing */
    ret = ksu_set_spoof_version(
        cmd.release[0] != '\0' ? cmd.release : NULL,
        cmd.version[0] != '\0' ? cmd.version : NULL
    );

    if (ret == 0) {
        pr_info("ksu: version spoofing applied successfully\n");
    } else {
        pr_err("ksu: version spoofing failed: %d\n", ret);
    }

    return ret;
}
EXPORT_SYMBOL(ksu_handle_spoof_version);
