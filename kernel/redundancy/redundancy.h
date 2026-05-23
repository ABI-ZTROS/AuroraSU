/*
 * AuroraSU - Redundancy Protection System
 * 
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

#ifndef __AURORA_REDUNDANCY_H__
#define __AURORA_REDUNDANCY_H__

#include <linux/types.h>
#include <linux/workqueue.h>

/* Redundancy States */
enum aurora_redundancy_state {
    AURORA_REDUNDANCY_HEALTHY = 0,
    AURORA_REDUNDANCY_DEGRADED,
    AURORA_REDUNDANCY_CRITICAL,
    AURORA_REDUNDANCY_FAILED
};

/* Backup Types */
#define AURORA_BACKUP_BOOT_IMAGE  0x01
#define AURORA_BACKUP_MODULES     0x02
#define AURORA_BACKUP_CONFIG      0x04
#define AURORA_BACKUP_ALLOWLIST   0x08

/* Health Check Types */
enum aurora_health_check {
    AURORA_HEALTH_SYSCALL_HOOK = 0,
    AURORA_HEALTH_SELINUX,
    AURORA_HEALTH_SUPERCALL,
    AURORA_HEALTH_KPM,
    AURORA_HEALTH_AURORAD,
    AURORA_HEALTH_COUNT
};

/* Redundancy Configuration */
struct aurora_redundancy_config {
    bool auto_recovery;
    bool backup_on_boot;
    u32 health_check_interval;  /* seconds */
    u32 max_retry_attempts;
    u32 critical_threshold;
};

/* Health Status */
struct aurora_health_status {
    enum aurora_redundancy_state overall_state;
    bool checks[AURORA_HEALTH_COUNT];
    u32 failure_count[AURORA_HEALTH_COUNT];
    u64 last_check_time;
    char error_message[256];
};

/* External Interface */
int aurora_redundancy_init(void);
void aurora_redundancy_exit(void);

/* Health Monitoring */
int aurora_redundancy_start_monitoring(void);
void aurora_redundancy_stop_monitoring(void);
int aurora_redundancy_check_health(enum aurora_health_check type);
int aurora_redundancy_run_full_check(struct aurora_health_status *status);

/* Backup and Recovery */
int aurora_redundancy_create_backup(u32 backup_types);
int aurora_redundancy_restore_from_backup(u32 backup_types);
int aurora_redundancy_verify_backup(u32 backup_types);

/* Auto Recovery */
int aurora_redundancy_attempt_recovery(enum aurora_health_check failed_check);
void aurora_redundancy_enter_safe_mode(void);
bool aurora_redundancy_is_in_safe_mode(void);

/* Failure Handling */
void aurora_redundancy_report_failure(enum aurora_health_check type, 
                                      const char *reason);
void aurora_redundancy_report_success(enum aurora_health_check type);

/* Boot Protection */
int aurora_redundancy_protect_boot_image(void);
int aurora_redundancy_verify_boot_integrity(void);

#endif /* __AURORA_REDUNDANCY_H__ */
