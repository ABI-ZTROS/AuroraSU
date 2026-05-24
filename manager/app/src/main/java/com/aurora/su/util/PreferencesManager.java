/*
 * AuroraSU - Preferences Manager
 *
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

/**
 * PreferencesManager - 偏好设置管理
 * 统一管理应用的各种设置项，提供类型安全的读写接口
 */
public class PreferencesManager {

    private static final String TAG = "PreferencesManager";
    private static final String PREFS_NAME = "aurora_su_prefs";

    // 设置键名常量
    public static final String KEY_ROOT_HIDE_ENABLED = "root_hide_enabled";
    public static final String KEY_AUTO_GRANT_ENABLED = "auto_grant_enabled";
    public static final String KEY_NOTIFICATION_ENABLED = "notification_enabled";
    public static final String KEY_BOOT_ENABLED = "boot_enabled";
    public static final String KEY_MULTI_USER_ENABLED = "multi_user_enabled";
    public static final String KEY_DEBUG_LOG_ENABLED = "debug_log_enabled";
    public static final String KEY_LOG_RETENTION_DAYS = "log_retention_days";
    public static final String KEY_SERVICE_RUNNING = "service_running";
    public static final String KEY_LAST_BOOT_TIME = "last_boot_time";
    public static final String KEY_LAST_SELINUX_STATUS = "last_selinux_status";
    public static final String KEY_SAFETYNET_LAST_CHECK = "safetynet_last_check";
    public static final String KEY_SAFETYNET_PASSED = "safetnet_passed";
    public static final String KEY_THEME_MODE = "theme_mode";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_LAST_GRANTED_APP = "last_granted_app";
    public static final String KEY_GLOBAL_NOTIFICATION_ID = "global_notification_id";

    private SharedPreferences prefs;

    public PreferencesManager(Context context) {
        if (context != null) {
            prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    // ---- Boolean ----

    /**
     * 保存布尔值
     */
    public void putBoolean(String key, boolean value) {
        if (prefs == null) return;
        prefs.edit().putBoolean(key, value).apply();
    }

    /**
     * 读取布尔值
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        if (prefs == null) return defaultValue;
        return prefs.getBoolean(key, defaultValue);
    }

    /**
     * 读取布尔值（默认 false）
     */
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    // ---- Int ----

    /**
     * 保存整数值
     */
    public void putInt(String key, int value) {
        if (prefs == null) return;
        prefs.edit().putInt(key, value).apply();
    }

    /**
     * 读取整数值
     */
    public int getInt(String key, int defaultValue) {
        if (prefs == null) return defaultValue;
        return prefs.getInt(key, defaultValue);
    }

    /**
     * 读取整数值（默认 0）
     */
    public int getInt(String key) {
        return getInt(key, 0);
    }

    // ---- Long ----

    /**
     * 保存长整数值
     */
    public void putLong(String key, long value) {
        if (prefs == null) return;
        prefs.edit().putLong(key, value).apply();
    }

    /**
     * 读取长整数值
     */
    public long getLong(String key, long defaultValue) {
        if (prefs == null) return defaultValue;
        return prefs.getLong(key, defaultValue);
    }

    /**
     * 读取长整数值（默认 0）
     */
    public long getLong(String key) {
        return getLong(key, 0L);
    }

    // ---- Float ----

    /**
     * 保存浮点数值
     */
    public void putFloat(String key, float value) {
        if (prefs == null) return;
        prefs.edit().putFloat(key, value).apply();
    }

    /**
     * 读取浮点数值
     */
    public float getFloat(String key, float defaultValue) {
        if (prefs == null) return defaultValue;
        return prefs.getFloat(key, defaultValue);
    }

    // ---- String ----

    /**
     * 保存字符串
     */
    public void putString(String key, String value) {
        if (prefs == null) return;
        prefs.edit().putString(key, value).apply();
    }

    /**
     * 读取字符串
     */
    public String getString(String key, String defaultValue) {
        if (prefs == null) return defaultValue;
        return prefs.getString(key, defaultValue);
    }

    /**
     * 读取字符串（默认空字符串）
     */
    public String getString(String key) {
        return getString(key, "");
    }

    // ---- String Set ----

    /**
     * 保存字符串集合
     */
    public void putStringSet(String key, java.util.Set<String> value) {
        if (prefs == null) return;
        prefs.edit().putStringSet(key, value).apply();
    }

    /**
     * 读取字符串集合
     */
    public java.util.Set<String> getStringSet(String key, java.util.Set<String> defaultValue) {
        if (prefs == null) return defaultValue;
        return prefs.getStringSet(key, defaultValue);
    }

    // ---- 通用操作 ----

    /**
     * 检查键是否存在
     */
    public boolean contains(String key) {
        if (prefs == null) return false;
        return prefs.contains(key);
    }

    /**
     * 删除指定键
     */
    public void remove(String key) {
        if (prefs == null) return;
        prefs.edit().remove(key).apply();
    }

    /**
     * 清除所有设置
     */
    public void clearAll() {
        if (prefs == null) return;
        prefs.edit().clear().apply();
        Log.d(TAG, "All preferences cleared");
    }

    /**
     * 获取所有键值对
     */
    public java.util.Map<String, ?> getAll() {
        if (prefs == null) return new java.util.HashMap<>();
        return prefs.getAll();
    }

    // ---- 便捷方法：特定设置项 ----

    /**
     * 获取 Root 隐藏设置
     */
    public boolean isRootHideEnabled() {
        return getBoolean(KEY_ROOT_HIDE_ENABLED, false);
    }

    /**
     * 设置 Root 隐藏
     */
    public void setRootHideEnabled(boolean enabled) {
        putBoolean(KEY_ROOT_HIDE_ENABLED, enabled);
    }

    /**
     * 获取自动授权设置
     */
    public boolean isAutoGrantEnabled() {
        return getBoolean(KEY_AUTO_GRANT_ENABLED, false);
    }

    /**
     * 获取通知设置
     */
    public boolean isNotificationEnabled() {
        return getBoolean(KEY_NOTIFICATION_ENABLED, true);
    }

    /**
     * 获取开机自启设置
     */
    public boolean isBootEnabled() {
        return getBoolean(KEY_BOOT_ENABLED, true);
    }

    /**
     * 获取多用户模式设置
     */
    public boolean isMultiUserEnabled() {
        return getBoolean(KEY_MULTI_USER_ENABLED, false);
    }

    /**
     * 获取调试日志设置
     */
    public boolean isDebugLogEnabled() {
        return getBoolean(KEY_DEBUG_LOG_ENABLED, false);
    }

    /**
     * 获取日志保留天数
     */
    public int getLogRetentionDays() {
        return getInt(KEY_LOG_RETENTION_DAYS, 30);
    }

    /**
     * 获取主题模式
     */
    public String getThemeMode() {
        return getString(KEY_THEME_MODE, "system");
    }

    /**
     * 设置主题模式
     */
    public void setThemeMode(String mode) {
        putString(KEY_THEME_MODE, mode);
    }

    /**
     * 获取语言设置
     */
    public String getLanguage() {
        return getString(KEY_LANGUAGE, "system");
    }

    /**
     * 设置语言
     */
    public void setLanguage(String lang) {
        putString(KEY_LANGUAGE, lang);
    }

    /**
     * 记录最后一次授权的应用
     */
    public void setLastGrantedApp(String packageName) {
        putString(KEY_LAST_GRANTED_APP, packageName);
        putLong(KEY_LAST_GRANTED_TIME, System.currentTimeMillis());
    }

    /**
     * 获取最后一次授权的应用
     */
    public String getLastGrantedApp() {
        return getString(KEY_LAST_GRANTED_APP);
    }

    /**
     * 获取通知 ID 计数器
     */
    public int getNextNotificationId() {
        int id = getInt(KEY_GLOBAL_NOTIFICATION_ID, 3000);
        putInt(KEY_GLOBAL_NOTIFICATION_ID, id + 1);
        return id;
    }

    // ---- 批量操作 ----

    /**
     * 批量保存设置
     */
    @SuppressWarnings("unchecked")
    public void putMultiple(java.util.Map<String, Object> values) {
        if (prefs == null || values == null) return;
        Editor editor = prefs.edit();
        for (java.util.Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof java.util.Set) {
                editor.putStringSet(key, (java.util.Set<String>) value);
            }
        }
        editor.apply();
    }

    /**
     * 导出所有设置为 JSON 字符串
     */
    public String exportToJson() {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            java.util.Map<String, ?> all = getAll();
            for (java.util.Map.Entry<String, ?> entry : all.entrySet()) {
                json.put(entry.getKey(), org.json.JSONObject.wrap(entry.getValue()));
            }
            return json.toString(2);
        } catch (Exception e) {
            Log.e(TAG, "Error exporting preferences to JSON", e);
            return "{}";
        }
    }

    /**
     * 从 JSON 字符串导入设置
     */
    public boolean importFromJson(String jsonStr) {
        try {
            org.json.JSONObject json = new org.json.JSONObject(jsonStr);
            Editor editor = prefs.edit();
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = json.get(key);
                if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else if (value instanceof Double) {
                    editor.putFloat(key, ((Double) value).floatValue());
                } else if (value instanceof String) {
                    editor.putString(key, (String) value);
                }
            }
            editor.apply();
            Log.d(TAG, "Preferences imported from JSON");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error importing preferences from JSON", e);
            return false;
        }
    }

    // 内部常量
    private static final String KEY_LAST_GRANTED_TIME = "last_granted_time";
}
