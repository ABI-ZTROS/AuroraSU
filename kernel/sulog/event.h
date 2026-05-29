/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Copyright (C) 2025 AuroraSU. All Rights Reserved.
 *
 * Sulog event handling for AuroraSU
 * Based on SukiSU-Ultra implementation
 */

#ifndef __KSU_H_SULOG_EVENT
#define __KSU_H_SULOG_EVENT

#include <linux/compiler_types.h>
#include <linux/gfp.h>
#include <linux/types.h>
#include "../../uapi/sulog.h"

/* Forward declarations */
struct ksu_event_queue;
struct ksu_sulog_pending_event;

/*
 * Initialize sulog events subsystem
 * Returns: 0 on success, negative error code on failure
 */
int ksu_sulog_events_init(void);

/*
 * Cleanup sulog events subsystem
 */
void ksu_sulog_events_exit(void);

/*
 * Capture root execve event (pending)
 * Called before execve completes
 * Returns: Pending event pointer or NULL on failure
 */
struct ksu_sulog_pending_event *ksu_sulog_capture_root_execve(
    const char __user *filename_user,
    const char __user *const __user *argv_user,
    gfp_t gfp);

/*
 * Capture sucompat event (pending)
 * Called for su compatibility layer events
 * Returns: Pending event pointer or NULL on failure
 */
struct ksu_sulog_pending_event *ksu_sulog_capture_sucompat(
    const char __user *filename_user,
    const char __user *const __user *argv_user,
    gfp_t gfp);

/*
 * Emit a pending event with the final result
 * Called after the operation completes
 */
void ksu_sulog_emit_pending(struct ksu_sulog_pending_event *pending,
                            int retval,
                            gfp_t gfp);

/*
 * Emit a grant root event
 * @retval: Return value of the grant operation
 * @uid: UID being granted root
 * @euid: Effective UID after grant
 * @gfp: Memory allocation flags
 * Returns: 0 on success, negative error code on failure
 */
int ksu_sulog_emit_grant_root(int retval,
                              __u32 uid,
                              __u32 euid,
                              gfp_t gfp);

/*
 * Get the sulog event queue
 * Returns: Pointer to the event queue
 */
struct ksu_event_queue *ksu_sulog_get_queue(void);

#endif /* __KSU_H_SULOG_EVENT */
