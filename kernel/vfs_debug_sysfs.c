/* SPDX-License-Identifier: GPL-2.0 */
/*
 * VFS Debug sysfs interface
 * Creates /sys/kernel/ztrosu/vfs/* interface
 */

#include "vfs_debug.h"
#include <linux/kobject.h>
#include <linux/sysfs.h>
#include <linux/slab.h>

#define VFS_SYSFS_PATH "ztrosu/vfs"

/* Global sysfs entries */
static struct kobject *vfs_kobj = NULL;

/* External function from vfs_debug.c */
extern struct vfs_debug_ctx *vfs_debug_get_ctx(void);
extern ssize_t vfs_debug_get_stats_str(char *buf, size_t size);
extern ssize_t vfs_debug_get_policy_str(char *buf, size_t size);

/* ==================== stats ==================== */
static ssize_t stats_show(struct kobject *kobj, struct kobj_attribute *attr,
                          char *buf)
{
	return vfs_debug_get_stats_str(buf, PAGE_SIZE);
}

static struct kobj_attribute stats_attr = __ATTR_RO(stats);

/* ==================== stats_reset ==================== */
static ssize_t stats_reset_store(struct kobject *kobj,
                                  struct kobj_attribute *attr,
                                  const char *buf, size_t count)
{
	vfs_debug_reset_stats();
	return count;
}

static struct kobj_attribute stats_reset_attr = __ATTR_WO(stats_reset);

/* ==================== enabled ==================== */
static ssize_t enabled_show(struct kobject *kobj, struct kobj_attribute *attr,
                            char *buf)
{
	struct vfs_debug_ctx *ctx = vfs_debug_get_ctx();
	if (!ctx || !ctx->initialized)
		return -ENODEV;
	
	return sprintf(buf, "%d\n", ctx->policy.enabled ? 1 : 0);
}

static ssize_t enabled_store(struct kobject *kobj, struct kobj_attribute *attr,
                             const char *buf, size_t count)
{
	bool enabled;
	int ret;
	
	if (kstrtobool(buf, &enabled))
		return -EINVAL;
	
	ret = vfs_debug_set_enabled(enabled);
	if (ret)
		return ret;
	
	return count;
}

static struct kobj_attribute enabled_attr = __ATTR(enabled, 0644,
                                                    enabled_show, enabled_store);

/* ==================== log_level ==================== */
static ssize_t log_level_show(struct kobject *kobj, struct kobj_attribute *attr,
                              char *buf)
{
	struct vfs_debug_ctx *ctx = vfs_debug_get_ctx();
	if (!ctx || !ctx->initialized)
		return -ENODEV;
	
	return sprintf(buf, "%u\n", ctx->policy.log_level);
}

static ssize_t log_level_store(struct kobject *kobj, struct kobj_attribute *attr,
                               const char *buf, size_t count)
{
	unsigned int level;
	int ret;
	
	if (kstrtouint(buf, 10, &level))
		return -EINVAL;
	
	ret = vfs_debug_set_log_level(level);
	if (ret)
		return ret;
	
	return count;
}

static struct kobj_attribute log_level_attr = __ATTR(log_level, 0644,
                                                      log_level_show,
                                                      log_level_store);

/* ==================== default_action ==================== */
static ssize_t default_action_show(struct kobject *kobj,
                                   struct kobj_attribute *attr,
                                   char *buf)
{
	struct vfs_debug_ctx *ctx = vfs_debug_get_ctx();
	if (!ctx || !ctx->initialized)
		return -ENODEV;
	
	return sprintf(buf, "%s\n",
	               ctx->policy.default_action == VFS_ACTION_ALLOW ?
	               "allow" : "deny");
}

static ssize_t default_action_store(struct kobject *kobj,
                                    struct kobj_attribute *attr,
                                    const char *buf, size_t count)
{
	enum vfs_action action;
	int ret;
	
	if (strncmp(buf, "allow", 5) == 0)
		action = VFS_ACTION_ALLOW;
	else if (strncmp(buf, "deny", 4) == 0)
		action = VFS_ACTION_DENY;
	else
		return -EINVAL;
	
	ret = vfs_debug_set_default_action(action);
	if (ret)
		return ret;
	
	return count;
}

static struct kobj_attribute default_action_attr = __ATTR(default_action, 0644,
                                                          default_action_show,
                                                          default_action_store);

/* ==================== rules ==================== */
static ssize_t rules_show(struct kobject *kobj, struct kobj_attribute *attr,
                          char *buf)
{
	struct vfs_debug_ctx *ctx = vfs_debug_get_ctx();
	struct vfs_rule *rule;
	size_t len = 0;
	
	if (!ctx || !ctx->initialized)
		return -ENODEV;
	
	spin_lock(&ctx->lock);
	
	list_for_each_entry(rule, &ctx->policy.rules, list) {
		if (len >= PAGE_SIZE - 128)
			break;
		
		len += snprintf(buf + len, PAGE_SIZE - len, "%s:%s:%s%s\n",
		                rule->action == VFS_ACTION_ALLOW ? "allow" : "deny",
		                rule->path_pattern,
		                (rule->mode_mask & 1) ? "r" : "",
		                (rule->mode_mask & 2) ? "w" : "");
	}
	
	spin_unlock(&ctx->lock);
	
	return len;
}

static ssize_t rules_store(struct kobject *kobj, struct kobj_attribute *attr,
                           const char *buf, size_t count)
{
	char *rule_str;
	int ret;
	
	/* Trim newline */
	rule_str = kstrndup(buf, count, GFP_KERNEL);
	if (!rule_str)
		return -ENOMEM;
	
	/* Remove trailing newline */
	if (rule_str[count - 1] == '\n')
		rule_str[count - 1] = '\0';
	
	ret = vfs_debug_add_rule(rule_str);
	kfree(rule_str);
	
	if (ret)
		return ret;
	
	return count;
}

static struct kobj_attribute rules_attr = __ATTR(rules, 0644,
                                                  rules_show, rules_store);

/* ==================== rules_clear ==================== */
static ssize_t rules_clear_store(struct kobject *kobj,
                                  struct kobj_attribute *attr,
                                  const char *buf, size_t count)
{
	vfs_debug_clear_rules();
	return count;
}

static struct kobj_attribute rules_clear_attr = __ATTR_WO(rules_clear);

/* ==================== Attributes table ==================== */
static struct attribute *vfs_attrs[] = {
	&stats_attr.attr,
	&stats_reset_attr.attr,
	&enabled_attr.attr,
	&log_level_attr.attr,
	&default_action_attr.attr,
	&rules_attr.attr,
	&rules_clear_attr.attr,
	NULL,
};

static struct attribute_group vfs_attr_group = {
	.attrs = vfs_attrs,
};

/* Initialize sysfs interface */
int vfs_debug_sysfs_init(void)
{
	int ret;
	
	/* Create /sys/kernel/ztrosu/vfs */
	vfs_kobj = kobject_create_and_add(VFS_SYSFS_PATH, kernel_kobj);
	if (!vfs_kobj) {
		pr_err("Failed to create VFS sysfs directory\n");
		return -ENOMEM;
	}
	
	ret = sysfs_create_group(vfs_kobj, &vfs_attr_group);
	if (ret) {
		pr_err("Failed to create VFS sysfs attributes: %d\n", ret);
		kobject_put(vfs_kobj);
		vfs_kobj = NULL;
		return ret;
	}
	
	pr_info("VFS debug sysfs interface created at /sys/kernel/%s\n",
	        VFS_SYSFS_PATH);
	return 0;
}

/* Cleanup sysfs interface */
void vfs_debug_sysfs_exit(void)
{
	if (vfs_kobj) {
		sysfs_remove_group(vfs_kobj, &vfs_attr_group);
		kobject_put(vfs_kobj);
		vfs_kobj = NULL;
		pr_info("VFS debug sysfs interface removed\n");
	}
}
