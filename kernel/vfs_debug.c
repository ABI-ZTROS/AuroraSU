/* SPDX-License-Identifier: GPL-2.0 */
/*
 * VFS Debug Module for AuroraSU
 * Provides VFS operation monitoring and access control
 */

#include "vfs_debug.h"
#include "klog.h"
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/fs.h>
#include <linux/slab.h>
#include <linux/string.h>
#include <linux/uaccess.h>
#include <linux/timekeeping.h>
#include <linux/glob.h>
#include <linux/fdtable.h>
#include <linux/dcache.h>
#include <linux/path.h>

#define pr_fmt(fmt) KBUILD_MODNAME ": " fmt

/* Global context */
static struct vfs_debug_ctx g_vfs_ctx;

/* Get global context */
struct vfs_debug_ctx *vfs_debug_get_ctx(void)
{
	return &g_vfs_ctx;
}

/* Initialize VFS debug context */
static void vfs_debug_ctx_init(void)
{
	memset(&g_vfs_ctx, 0, sizeof(g_vfs_ctx));
	
	/* Initialize statistics */
	atomic64_set(&g_vfs_ctx.stats.open_count, 0);
	atomic64_set(&g_vfs_ctx.stats.read_count, 0);
	atomic64_set(&g_vfs_ctx.stats.write_count, 0);
	atomic64_set(&g_vfs_ctx.stats.close_count, 0);
	atomic64_set(&g_vfs_ctx.stats.denied_count, 0);
	g_vfs_ctx.stats.last_updated = ktime_get_real_seconds();
	
	/* Initialize policy defaults */
	g_vfs_ctx.policy.enabled = false;
	g_vfs_ctx.policy.log_level = 0;
	g_vfs_ctx.policy.default_action = VFS_ACTION_ALLOW;
	g_vfs_ctx.policy.rules_count = 0;
	INIT_LIST_HEAD(&g_vfs_ctx.policy.rules);
	
	/* Initialize lock */
	spin_lock_init(&g_vfs_ctx.lock);
	
	g_vfs_ctx.initialized = true;
}

/* Cleanup VFS debug context */
static void vfs_debug_ctx_exit(void)
{
	struct vfs_rule *rule, *tmp;
	
	if (!g_vfs_ctx.initialized)
		return;
	
	spin_lock(&g_vfs_ctx.lock);
	
	/* Free all rules */
	list_for_each_entry_safe(rule, tmp, &g_vfs_ctx.policy.rules, list) {
		list_del(&rule->list);
		kfree(rule);
	}
	g_vfs_ctx.policy.rules_count = 0;
	
	spin_unlock(&g_vfs_ctx.lock);
	
	g_vfs_ctx.initialized = false;
}

/* Count VFS operation */
void vfs_debug_count_op(enum vfs_op_type op)
{
	if (!g_vfs_ctx.initialized || !g_vfs_ctx.policy.enabled)
		return;
	
	switch (op) {
	case VFS_OP_OPEN:
		atomic64_inc(&g_vfs_ctx.stats.open_count);
		break;
	case VFS_OP_READ:
		atomic64_inc(&g_vfs_ctx.stats.read_count);
		break;
	case VFS_OP_WRITE:
		atomic64_inc(&g_vfs_ctx.stats.write_count);
		break;
	case VFS_OP_CLOSE:
		atomic64_inc(&g_vfs_ctx.stats.close_count);
		break;
	default:
		return;
	}
	
	g_vfs_ctx.stats.last_updated = ktime_get_real_seconds();
}

/* Count denied access */
void vfs_debug_count_denied(void)
{
	if (!g_vfs_ctx.initialized)
		return;
	
	atomic64_inc(&g_vfs_ctx.stats.denied_count);
	g_vfs_ctx.stats.last_updated = ktime_get_real_seconds();
}

/* Reset statistics */
void vfs_debug_reset_stats(void)
{
	if (!g_vfs_ctx.initialized)
		return;
	
	spin_lock(&g_vfs_ctx.lock);
	
	atomic64_set(&g_vfs_ctx.stats.open_count, 0);
	atomic64_set(&g_vfs_ctx.stats.read_count, 0);
	atomic64_set(&g_vfs_ctx.stats.write_count, 0);
	atomic64_set(&g_vfs_ctx.stats.close_count, 0);
	atomic64_set(&g_vfs_ctx.stats.denied_count, 0);
	g_vfs_ctx.stats.last_updated = ktime_get_real_seconds();
	
	spin_unlock(&g_vfs_ctx.lock);
	
	pr_info("VFS debug stats reset\n");
}

/* Set enabled state */
int vfs_debug_set_enabled(bool enabled)
{
	if (!g_vfs_ctx.initialized)
		return -ENODEV;
	
	spin_lock(&g_vfs_ctx.lock);
	g_vfs_ctx.policy.enabled = enabled;
	spin_unlock(&g_vfs_ctx.lock);
	
	pr_info("VFS debug %s\n", enabled ? "enabled" : "disabled");
	return 0;
}

/* Set log level */
int vfs_debug_set_log_level(unsigned int level)
{
	if (!g_vfs_ctx.initialized)
		return -ENODEV;
	
	if (level > 5)
		return -EINVAL;
	
	spin_lock(&g_vfs_ctx.lock);
	g_vfs_ctx.policy.log_level = level;
	spin_unlock(&g_vfs_ctx.lock);
	
	pr_info("VFS debug log level set to %u\n", level);
	return 0;
}

/* Set default action */
int vfs_debug_set_default_action(enum vfs_action action)
{
	if (!g_vfs_ctx.initialized)
		return -ENODEV;
	
	if (action != VFS_ACTION_ALLOW && action != VFS_ACTION_DENY)
		return -EINVAL;
	
	spin_lock(&g_vfs_ctx.lock);
	g_vfs_ctx.policy.default_action = action;
	spin_unlock(&g_vfs_ctx.lock);
	
	pr_info("VFS debug default action set to %s\n",
		action == VFS_ACTION_ALLOW ? "allow" : "deny");
	return 0;
}

/* Parse and add rule from string */
int vfs_debug_add_rule(const char *rule_str)
{
	struct vfs_rule *rule;
	char *buf, *p;
	char *action_str, *path_str, *mode_str;
	enum vfs_action action;
	unsigned int mode_mask = 0;
	
	if (!g_vfs_ctx.initialized)
		return -ENODEV;
	
	if (!rule_str || strlen(rule_str) == 0)
		return -EINVAL;
	
	if (g_vfs_ctx.policy.rules_count >= VFS_MAX_RULES)
		return -ENOSPC;
	
	/* Duplicate string for parsing */
	buf = kstrdup(rule_str, GFP_KERNEL);
	if (!buf)
		return -ENOMEM;
	
	p = buf;
	
	/* Parse: action:path:mode */
	action_str = strsep(&p, ":");
	path_str = strsep(&p, ":");
	mode_str = strsep(&p, ":");
	
	if (!action_str || !path_str || !mode_str) {
		kfree(buf);
		return -EINVAL;
	}
	
	/* Parse action */
	if (strcmp(action_str, "allow") == 0)
		action = VFS_ACTION_ALLOW;
	else if (strcmp(action_str, "deny") == 0)
		action = VFS_ACTION_DENY;
	else {
		kfree(buf);
		return -EINVAL;
	}
	
	/* Parse mode */
	if (strchr(mode_str, 'r'))
		mode_mask |= 1;
	if (strchr(mode_str, 'w'))
		mode_mask |= 2;
	
	if (mode_mask == 0) {
		kfree(buf);
		return -EINVAL;
	}
	
	/* Create rule */
	rule = kzalloc(sizeof(*rule), GFP_KERNEL);
	if (!rule) {
		kfree(buf);
		return -ENOMEM;
	}
	
	INIT_LIST_HEAD(&rule->list);
	rule->action = action;
	rule->mode_mask = mode_mask;
	rule->enabled = true;
	
	if (strlen(path_str) >= VFS_MAX_PATH_LEN) {
		kfree(rule);
		kfree(buf);
		return -ENAMETOOLONG;
	}
	strcpy(rule->path_pattern, path_str);
	
	/* Add to list */
	spin_lock(&g_vfs_ctx.lock);
	list_add_tail(&rule->list, &g_vfs_ctx.policy.rules);
	g_vfs_ctx.policy.rules_count++;
	spin_unlock(&g_vfs_ctx.lock);
	
	pr_info("VFS debug rule added: %s %s %s\n",
		action_str, path_str, mode_str);
	
	kfree(buf);
	return 0;
}

/* Clear all rules */
void vfs_debug_clear_rules(void)
{
	struct vfs_rule *rule, *tmp;
	
	if (!g_vfs_ctx.initialized)
		return;
	
	spin_lock(&g_vfs_ctx.lock);
	
	list_for_each_entry_safe(rule, tmp, &g_vfs_ctx.policy.rules, list) {
		list_del(&rule->list);
		kfree(rule);
	}
	g_vfs_ctx.policy.rules_count = 0;
	
	spin_unlock(&g_vfs_ctx.lock);
	
	pr_info("VFS debug rules cleared\n");
}

/* Check if path matches pattern (simple glob matching) */
static bool path_matches(const char *path, const char *pattern)
{
	/* Simple implementation - use kernel's glob_match if available */
	const char *p = pattern;
	const char *s = path;
	
	while (*p && *s) {
		if (*p == '*') {
			/* Skip consecutive stars */
			while (*p == '*')
				p++;
			if (!*p)
				return true;
			/* Try to match rest */
			while (*s) {
				if (path_matches(s, p))
					return true;
				s++;
			}
			return false;
		} else if (*p == '?') {
			p++;
			s++;
		} else {
			if (*p != *s)
				return false;
			p++;
			s++;
		}
	}
	
	/* Handle trailing stars */
	while (*p == '*')
		p++;
	
	return *p == '\0' && *s == '\0';
}

/* Check access permission */
int vfs_debug_check_access(const char *path, int flags)
{
	struct vfs_rule *rule;
	enum vfs_action action;
	unsigned int req_mode = 0;
	bool rule_matched = false;
	
	if (!g_vfs_ctx.initialized || !g_vfs_ctx.policy.enabled)
		return 0; /* Allow by default when disabled */
	
	/* Determine required mode */
	if ((flags & O_ACCMODE) == O_RDONLY)
		req_mode = 1;
	else if ((flags & O_ACCMODE) == O_WRONLY)
		req_mode = 2;
	else if ((flags & O_ACCMODE) == O_RDWR)
		req_mode = 3;
	
	spin_lock(&g_vfs_ctx.lock);
	
	/* Check rules in order */
	list_for_each_entry(rule, &g_vfs_ctx.policy.rules, list) {
		if (!rule->enabled)
			continue;
		
		if (path_matches(path, rule->path_pattern)) {
			/* Check mode match */
			if (req_mode & rule->mode_mask) {
				action = rule->action;
				rule_matched = true;
				break;
			}
		}
	}
	
	if (!rule_matched)
		action = g_vfs_ctx.policy.default_action;
	
	spin_unlock(&g_vfs_ctx.lock);
	
	if (action == VFS_ACTION_DENY) {
		vfs_debug_count_denied();
		if (g_vfs_ctx.policy.log_level >= 2)
			pr_info("VFS access denied: %s (flags=%x)\n", path, flags);
		return -EACCES;
	}
	
	return 0;
}

/* Get stats string representation */
ssize_t vfs_debug_get_stats_str(char *buf, size_t size)
{
	struct vfs_stats stats;
	
	if (!g_vfs_ctx.initialized)
		return -ENODEV;
	
	spin_lock(&g_vfs_ctx.lock);
	stats = g_vfs_ctx.stats;
	spin_unlock(&g_vfs_ctx.lock);
	
	return snprintf(buf, size,
		"open: %llu\n"
		"read: %llu\n"
		"write: %llu\n"
		"close: %llu\n"
		"denied: %llu\n"
		"last_updated: %llu\n",
		(u64)atomic64_read(&stats.open_count),
		(u64)atomic64_read(&stats.read_count),
		(u64)atomic64_read(&stats.write_count),
		(u64)atomic64_read(&stats.close_count),
		(u64)atomic64_read(&stats.denied_count),
		stats.last_updated);
}

/* Get policy string representation */
ssize_t vfs_debug_get_policy_str(char *buf, size_t size)
{
	ssize_t len = 0;
	struct vfs_rule *rule;
	
	if (!g_vfs_ctx.initialized)
		return -ENODEV;
	
	spin_lock(&g_vfs_ctx.lock);
	
	len += snprintf(buf + len, size - len,
		"enabled: %d\n"
		"log_level: %u\n"
		"default_action: %s\n"
		"rules_count: %u\n",
		g_vfs_ctx.policy.enabled ? 1 : 0,
		g_vfs_ctx.policy.log_level,
		g_vfs_ctx.policy.default_action == VFS_ACTION_ALLOW ? "allow" : "deny",
		g_vfs_ctx.policy.rules_count);
	
	if (len < size) {
		len += snprintf(buf + len, size - len, "rules:\n");
		
		list_for_each_entry(rule, &g_vfs_ctx.policy.rules, list) {
			if (len >= size)
				break;
			len += snprintf(buf + len, size - len, "%s:%s:%s%s\n",
				rule->action == VFS_ACTION_ALLOW ? "allow" : "deny",
				rule->path_pattern,
				(rule->mode_mask & 1) ? "r" : "",
				(rule->mode_mask & 2) ? "w" : "");
		}
	}
	
	spin_unlock(&g_vfs_ctx.lock);
	
	return len;
}

/* Module init */
int vfs_debug_init(void)
{
	int ret;
	
	pr_info("Initializing VFS debug module\n");
	
	vfs_debug_ctx_init();
	
	ret = vfs_debug_sysfs_init();
	if (ret) {
		pr_err("Failed to initialize VFS debug sysfs: %d\n", ret);
		vfs_debug_ctx_exit();
		return ret;
	}
	
	pr_info("VFS debug module initialized\n");
	return 0;
}

/* Module exit */
void vfs_debug_exit(void)
{
	pr_info("Exiting VFS debug module\n");
	
	vfs_debug_sysfs_exit();
	vfs_debug_ctx_exit();
	
	pr_info("VFS debug module exited\n");
}
