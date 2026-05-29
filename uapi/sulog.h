/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Copyright (C) 2025 AuroraSU. All Rights Reserved.
 *
 * Sulog (Superuser Log) UAPI definitions for AuroraSU
 * Based on SukiSU-Ultra implementation
 */

#ifndef __KSU_UAPI_SULOG_H
#define __KSU_UAPI_SULOG_H

#include <linux/types.h>

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
 * Sulog event flags
 */
#define KSU_SULOG_FLAG_INTERNAL     (1U << 0)
#define KSU_SULOG_FLAG_GRANTED      (1U << 1)
#define KSU_SULOG_FLAG_DENIED       (1U << 2)

/*
 * Sulog event record header
 * This is the common header for all event types
 */
struct ksu_sulog_event_hdr {
    __u16 type;             /* Event type (enum ksu_sulog_event_type) */
    __u16 flags;            /* Event flags */
    __u32 len;              /* Total length of event record including header */
    __u64 seq;              /* Sequence number (monotonically increasing) */
    __u64 ts_ns;            /* Timestamp in nanoseconds (boottime) */
};

/*
 * Maximum sizes for string fields
 */
#define KSU_SULOG_SCONTEXT_MAX      64
#define KSU_SULOG_COMMAND_MAX       128
#define KSU_SULOG_FILENAME_MAX      128

/*
 * Sulog SU grant/deny event
 * Sent when a process requests root access
 */
struct ksu_sulog_event_su {
    struct ksu_sulog_event_hdr hdr;
    __u32 uid;                              /* Requesting UID */
    __u32 euid;                             /* Effective UID after grant */
    __s32 pid;                              /* Process ID */
    __s32 ppid;                             /* Parent Process ID */
    char scontext[KSU_SULOG_SCONTEXT_MAX];  /* SELinux context */
    char command[KSU_SULOG_COMMAND_MAX];    /* Command being executed */
};

/*
 * Sulog execve event
 * Sent when a process executes a file
 */
struct ksu_sulog_event_execve {
    struct ksu_sulog_event_hdr hdr;
    __u32 uid;                              /* UID of the process */
    __s32 pid;                              /* Process ID */
    __s32 ppid;                             /* Parent Process ID */
    __s32 retval;                           /* Return value from execve */
    char filename[KSU_SULOG_FILENAME_MAX];  /* Filename being executed */
};

/*
 * Sulog sucompat event
 * Sent for su compatibility layer events
 */
struct ksu_sulog_event_sucompat {
    struct ksu_sulog_event_hdr hdr;
    __u32 uid;                              /* UID of the process */
    __s32 pid;                              /* Process ID */
    __s32 ppid;                             /* Parent Process ID */
    __s32 retval;                           /* Return value */
    char filename[KSU_SULOG_FILENAME_MAX];  /* Filename */
};

/*
 * Union of all event types for buffer sizing
 */
union ksu_sulog_event {
    struct ksu_sulog_event_hdr hdr;
    struct ksu_sulog_event_su su;
    struct ksu_sulog_event_execve execve;
    struct ksu_sulog_event_sucompat sucompat;
};

/*
 * Maximum size of any event record
 */
#define KSU_SULOG_MAX_EVENT_SIZE    sizeof(union ksu_sulog_event)

/*
 * IOCTL command to get sulog file descriptor
 */
#define KSU_IOCTL_GET_SULOG_FD      _IOR('K', 103, int)

/*
 * Sulog queue configuration
 */
#define KSU_SULOG_QUEUE_MAX_QUEUED  1024    /* Maximum queued events */
#define KSU_SULOG_MAX_PAYLOAD_LEN   2048    /* Maximum payload length */

#endif /* __KSU_UAPI_SULOG_H */
