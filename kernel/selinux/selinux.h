#ifndef __KSU_H_SELINUX
#define __KSU_H_SELINUX

#include "linux/types.h"
#include "linux/version.h"
#include "linux/cred.h"

// NOTE: ZTRSU_DOMAIN is intentionally set to "su" for compatibility with existing
// SELinux policies. Changing to "ksu" would require updating all sepolicy rules
// in rules.c, ksud.c exec contexts, and allowlist.c default domain, and would
// break compatibility with existing installations. Do not change without a migration plan.
#define ZTRSU_DOMAIN "su"
#define ZTRSU_FILE "ksu_file"
#define ZTRSU_CONTEXT "u:r:" ZTRSU_DOMAIN ":s0"

#define KERNEL_SU_DOMAIN ZTRSU_DOMAIN
#define KERNEL_SU_FILE ZTRSU_FILE
#define KERNEL_SU_CONTEXT ZTRSU_CONTEXT

#define KSU_FILE_CONTEXT "u:object_r:" ZTRSU_FILE ":s0"
#define ZYGOTE_CONTEXT "u:r:zygote:s0"
#define INIT_CONTEXT "u:r:init:s0"

void setup_selinux(const char *, struct cred *);

void setenforce(bool);

bool getenforce();

void cache_sid(void);

bool is_task_ksu_domain(const struct cred *cred);

bool is_ksu_domain();

bool is_zygote(const struct cred *cred);

bool is_init(const struct cred *cred);

void apply_kernelsu_rules();

int handle_sepolicy(void __user *user_data, u64 data_len);

void setup_ksu_cred();

#endif
