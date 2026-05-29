/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Copyright (C) 2025 AuroraSU. All Rights Reserved.
 *
 * UTS (uname) version spoofing support for AuroraSU
 * Based on SukiSU-Ultra implementation
 */

#ifndef __KSU_H_UTS_SPOOF
#define __KSU_H_UTS_SPOOF

#include <linux/rwsem.h>
#include <linux/utsname.h>

/*
 * Spoof the kernel version in the specified uts_namespace
 * @spoof_release: New release string (e.g., "5.10.0"), NULL to keep current
 * @spoof_version: New version string, NULL to keep current
 */
void ksu_spoof_version(const char *spoof_release, const char *spoof_version);

/*
 * Set the kernel version spoofing (wrapper function)
 * @release: New release string
 * @version: New version string
 * Returns: 0 on success, negative error code on failure
 */
int ksu_set_spoof_version(const char *release, const char *version);

/*
 * IOCTL handler for version spoofing
 * @arg: Pointer to ksu_spoof_version_cmd structure from userspace
 * Returns: 0 on success, negative error code on failure
 */
int ksu_handle_spoof_version(void __user *arg);

#endif /* __KSU_H_UTS_SPOOF */
