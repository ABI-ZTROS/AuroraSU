/*
 * AuroraSU - KPM (Kernel Patch Module) Support
 * 
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

#ifndef __AURORA_KPM_H__
#define __AURORA_KPM_H__

#include <linux/types.h>
#include <linux/list.h>
#include <linux/spinlock.h>

/* KPM Magic and Version */
#define AURORA_KPM_MAGIC 0x4155524F5241ULL  /* "AURORA" */
#define AURORA_KPM_VERSION 2

/* KPM Control Codes */
#define AURORA_KPM_LOAD     1
#define AURORA_KPM_UNLOAD   2
#define AURORA_KPM_NUM      3
#define AURORA_KPM_LIST     4
#define AURORA_KPM_INFO     5
#define AURORA_KPM_CONTROL  6
#define AURORA_KPM_VERSION  7

/* KPM Flags */
#define AURORA_KPM_FLAG_PERSISTENT  (1U << 0)   /* Survive reboot */
#define AURORA_KPM_FLAG_HOTPATCH    (1U << 1)   /* Hot patch mode */
#define AURORA_KPM_FLAG_SIGNED      (1U << 2)   /* Require signature */

/* KPM Header */
struct aurora_kpm_header {
    u64 magic;              /* AURORA_KPM_MAGIC */
    u32 version;            /* KPM version */
    u32 flags;              /* KPM flags */
    u32 header_size;        /* Size of this header */
    u32 payload_size;       /* Size of payload */
    char name[32];          /* Module name */
    char version_str[16];   /* Version string */
    u8 signature[64];       /* Ed25519 signature */
};

/* KPM Info Structure */
struct aurora_kpm_info {
    char name[32];
    char version[16];
    u32 flags;
    u32 load_count;
    u64 load_time;
    bool active;
};

/* KPM Module Structure */
struct aurora_kpm_module {
    struct list_head list;
    struct aurora_kpm_header header;
    void *payload;
    void *mapped_mem;
    size_t mapped_size;
    struct module *mod;
    bool active;
    atomic_t refcnt;
};

/* KPM Operations */
struct aurora_kpm_ops {
    int (*load)(const char *path, const char *args, u32 flags);
    int (*unload)(const char *name);
    int (*control)(const char *name, const char *cmd, void *arg);
    int (*get_info)(const char *name, struct aurora_kpm_info *info);
    int (*list)(struct aurora_kpm_info *buf, int max_count);
};

/* External Interface */
int aurora_kpm_init(void);
void aurora_kpm_exit(void);

int aurora_kpm_load_module(const char *path, const char *args, u32 flags);
int aurora_kpm_unload_module(const char *name);
int aurora_kpm_control_module(const char *name, const char *cmd, void *arg);
int aurora_kpm_get_module_info(const char *name, struct aurora_kpm_info *info);
int aurora_kpm_list_modules(struct aurora_kpm_info *buf, int max_count);
int aurora_kpm_get_module_count(void);

/* IOCTL Handler */
int aurora_handle_kpm_ioctl(unsigned long control_code, unsigned long arg1,
                            unsigned long arg2, unsigned long result_code);

/* Signature Verification */
bool aurora_kpm_verify_signature(const struct aurora_kpm_header *header,
                                 const void *payload, size_t payload_size);

/* Symbol Resolution for KPM */
void *aurora_kpm_resolve_symbol(const char *name);

#endif /* __AURORA_KPM_H__ */
