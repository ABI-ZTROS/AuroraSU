/* SPDX-License-Identifier: GPL-2.0 */
#ifndef __VFS_DEBUG_H
#define __VFS_DEBUG_H

#include <linux/types.h>
#include <linux/spinlock.h>
#include <linux/list.h>

/* VFS debug feature version */
#define VFS_DEBUG_VERSION 1

/* Max rules count */
#define VFS_MAX_RULES 64
#define VFS_MAX_RULE_LEN 256
#define VFS_MAX_PATH_LEN 512

/* Rule actions */
enum vfs_action {
	VFS_ACTION_ALLOW = 0,
	VFS_ACTION_DENY = 1,
};

/* VFS operation types for statistics */
enum vfs_op_type {
	VFS_OP_OPEN = 0,
	VFS_OP_READ,
	VFS_OP_WRITE,
	VFS_OP_CLOSE,
	VFS_OP_MAX
};

/* VFS rule structure */
struct vfs_rule {
	struct list_head list;
	enum vfs_action action;
	char path_pattern[VFS_MAX_PATH_LEN];
	unsigned int mode_mask; /* 1=read, 2=write, 3=rw */
	bool enabled;
};

/* VFS statistics */
struct vfs_stats {
	atomic64_t open_count;
	atomic64_t read_count;
	atomic64_t write_count;
	atomic64_t close_count;
	atomic64_t denied_count;
	u64 last_updated;
};

/* VFS policy configuration */
struct vfs_policy {
	bool enabled;
	unsigned int log_level; /* 0-5 */
	enum vfs_action default_action;
	struct list_head rules;
	unsigned int rules_count;
};

/* VFS debug context */
struct vfs_debug_ctx {
	struct vfs_stats stats;
	struct vfs_policy policy;
	spinlock_t lock;
	bool initialized;
};

/* External interface functions */
int vfs_debug_init(void);
void vfs_debug_exit(void);

/* Statistics functions */
void vfs_debug_count_op(enum vfs_op_type op);
void vfs_debug_count_denied(void);
void vfs_debug_reset_stats(void);

/* Policy functions */
int vfs_debug_set_enabled(bool enabled);
int vfs_debug_set_log_level(unsigned int level);
int vfs_debug_set_default_action(enum vfs_action action);
int vfs_debug_add_rule(const char *rule_str);
void vfs_debug_clear_rules(void);

/* Check access */
int vfs_debug_check_access(const char *path, int flags);

/* sysfs interface */
int vfs_debug_sysfs_init(void);
void vfs_debug_sysfs_exit(void);

#endif /* __VFS_DEBUG_H */
