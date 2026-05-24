/*
 * AuroraSU - Root Permission Manager
 *
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.aurora.su.util.ShellUtils;

/**
 * RootManager - Root 权限管理核心类
 * 负责检测 root 状态、授权/撤销应用 root 权限、查询已授权应用列表
 */
public class RootManager {

    private static final String TAG = "RootManager";
    private static final String AURORA_BASE_DIR = "/data/adb/aurora";
    private static final String GRANTED_APPS_FILE = AURORA_BASE_DIR + "/granted_apps.json";
    private static final String DENIED_APPS_FILE = AURORA_BASE_DIR + "/denied_apps.json";
    private static final String ROOT_LOG_FILE = AURORA_BASE_DIR + "/root_log.json";

    private Context context;
    private ShellUtils shellUtils;

    public RootManager(Context context) {
        this.context = context.getApplicationContext();
        this.shellUtils = new ShellUtils();
    }

    /**
     * 检测 root 状态
     * @return true 表示设备已 root 且 AuroraSU 正常工作
     */
    public boolean checkRootAccess() {
        // 方法1: 检查 su 二进制文件
        boolean hasSuBinary = shellUtils.isRootAvailable();
        if (!hasSuBinary) {
            Log.w(TAG, "su binary not found");
            return false;
        }

        // 方法2: 尝试执行 root 命令
        ShellUtils.CommandResult result = shellUtils.runRootCommand("id");
        if (result.success && result.output != null && result.output.contains("uid=0")) {
            Log.d(TAG, "Root access confirmed via 'id' command");
            return true;
        }

        // 方法3: 检查 AuroraSU daemon socket
        File socketFile = new File("/dev/aurora_socket");
        if (socketFile.exists()) {
            Log.d(TAG, "AuroraSU daemon socket found");
            return true;
        }

        // 方法4: 检查 AuroraSU 基础目录
        File baseDir = new File(AURORA_BASE_DIR);
        if (baseDir.exists() && baseDir.isDirectory()) {
            Log.d(TAG, "AuroraSU base directory found");
            return true;
        }

        Log.w(TAG, "Root access check failed");
        return false;
    }

    /**
     * 授权应用 root 权限
     * @param packageName 应用包名
     * @return true 表示授权成功
     */
    public boolean grantRoot(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            Log.e(TAG, "Invalid package name for grantRoot");
            return false;
        }

        try {
            List<GrantedApp> grantedApps = loadGrantedApps();

            // 检查是否已授权
            for (GrantedApp app : grantedApps) {
                if (app.packageName.equals(packageName)) {
                    Log.d(TAG, "App already granted: " + packageName);
                    return true;
                }
            }

            // 从拒绝列表中移除（如果存在）
            removeFromDeniedList(packageName);

            // 添加新授权
            GrantedApp newApp = new GrantedApp();
            newApp.packageName = packageName;
            newApp.appName = getAppName(packageName);
            newApp.grantedAt = System.currentTimeMillis();
            newApp.lastUsedAt = 0;
            newApp.useCount = 0;
            newApp.policy = Policy.ALLOW;
            newApp.uid = getAppUid(packageName);
            grantedApps.add(newApp);

            // 保存到文件
            boolean saved = saveGrantedApps(grantedApps);
            if (saved) {
                logRootAction(packageName, "GRANT", "Root access granted");
                // 通过 root 命令更新白名单
                shellUtils.runRootCommand("echo '" + packageName + "' >> " + AURORA_BASE_DIR + "/whitelist");
            }
            return saved;
        } catch (Exception e) {
            Log.e(TAG, "Error granting root to " + packageName, e);
            return false;
        }
    }

    /**
     * 撤销应用 root 权限
     * @param packageName 应用包名
     * @return true 表示撤销成功
     */
    public boolean revokeRoot(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            Log.e(TAG, "Invalid package name for revokeRoot");
            return false;
        }

        try {
            List<GrantedApp> grantedApps = loadGrantedApps();
            boolean removed = false;

            for (int i = grantedApps.size() - 1; i >= 0; i--) {
                if (grantedApps.get(i).packageName.equals(packageName)) {
                    grantedApps.remove(i);
                    removed = true;
                    break;
                }
            }

            if (removed) {
                boolean saved = saveGrantedApps(grantedApps);
                if (saved) {
                    logRootAction(packageName, "REVOKE", "Root access revoked");
                    // 从白名单中移除
                    shellUtils.runRootCommand("sed -i '/" + packageName + "/d' " + AURORA_BASE_DIR + "/whitelist");
                }
                return saved;
            }
            return true; // 本来就不在列表中
        } catch (Exception e) {
            Log.e(TAG, "Error revoking root from " + packageName, e);
            return false;
        }
    }

    /**
     * 查询应用是否已被授权 root
     * @param packageName 应用包名
     * @return true 表示已授权
     */
    public boolean isAppGranted(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }

        try {
            List<GrantedApp> grantedApps = loadGrantedApps();
            for (GrantedApp app : grantedApps) {
                if (app.packageName.equals(packageName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking grant status for " + packageName, e);
        }
        return false;
    }

    /**
     * 获取所有已授权应用列表
     * @return 已授权应用列表
     */
    public List<GrantedApp> getAllGrantedApps() {
        try {
            List<GrantedApp> apps = loadGrantedApps();
            // 按最后使用时间排序
            Collections.sort(apps, new java.util.Comparator<GrantedApp>() {
                @Override
                public int compare(GrantedApp a, GrantedApp b) {
                    return Long.compare(b.lastUsedAt, a.lastUsedAt);
                }
            });
            return apps;
        } catch (Exception e) {
            Log.e(TAG, "Error getting all granted apps", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取 root 使用统计
     * @return 统计信息 Map
     */
    public Map<String, Object> getRootStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            List<GrantedApp> grantedApps = loadGrantedApps();

            stats.put("total_granted", grantedApps.size());
            stats.put("total_denied", loadDeniedApps().size());

            long totalUsage = 0;
            long lastActivity = 0;
            for (GrantedApp app : grantedApps) {
                totalUsage += app.useCount;
                if (app.lastUsedAt > lastActivity) {
                    lastActivity = app.lastUsedAt;
                }
            }

            stats.put("total_root_requests", totalUsage);
            stats.put("last_activity_time", lastActivity);
            stats.put("root_available", checkRootAccess());

            // 计算今日使用次数
            long todayStart = getTodayStartMillis();
            int todayCount = 0;
            for (GrantedApp app : grantedApps) {
                if (app.lastUsedAt >= todayStart) {
                    todayCount += app.useCount;
                }
            }
            stats.put("today_requests", todayCount);

            // 最常用应用
            GrantedApp mostUsed = null;
            for (GrantedApp app : grantedApps) {
                if (mostUsed == null || app.useCount > mostUsed.useCount) {
                    mostUsed = app;
                }
            }
            stats.put("most_used_app", mostUsed != null ? mostUsed.packageName : "none");

        } catch (Exception e) {
            Log.e(TAG, "Error getting root stats", e);
        }
        return stats;
    }

    /**
     * 更新应用使用记录
     */
    public void updateAppUsage(String packageName) {
        try {
            List<GrantedApp> grantedApps = loadGrantedApps();
            for (GrantedApp app : grantedApps) {
                if (app.packageName.equals(packageName)) {
                    app.lastUsedAt = System.currentTimeMillis();
                    app.useCount++;
                    break;
                }
            }
            saveGrantedApps(grantedApps);
            logRootAction(packageName, "USE", "Root access used");
        } catch (Exception e) {
            Log.e(TAG, "Error updating app usage", e);
        }
    }

    /**
     * 设置应用授权策略
     */
    public boolean setAppPolicy(String packageName, Policy policy) {
        try {
            List<GrantedApp> grantedApps = loadGrantedApps();
            for (GrantedApp app : grantedApps) {
                if (app.packageName.equals(packageName)) {
                    app.policy = policy;
                    break;
                }
            }
            return saveGrantedApps(grantedApps);
        } catch (Exception e) {
            Log.e(TAG, "Error setting app policy", e);
            return false;
        }
    }

    // ---- 内部辅助方法 ----

    private List<GrantedApp> loadGrantedApps() {
        List<GrantedApp> apps = new ArrayList<>();
        try {
            File file = new File(GRANTED_APPS_FILE);
            if (!file.exists()) {
                return apps;
            }
            String content = readFileContent(file);
            JSONArray array = new JSONArray(content);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                GrantedApp app = new GrantedApp();
                app.packageName = obj.optString("package", "");
                app.appName = obj.optString("app_name", "");
                app.grantedAt = obj.optLong("granted_at", 0);
                app.lastUsedAt = obj.optLong("last_used_at", 0);
                app.useCount = obj.optInt("use_count", 0);
                app.uid = obj.optInt("uid", -1);
                String policyStr = obj.optString("policy", "ALLOW");
                app.policy = Policy.valueOf(policyStr);
                apps.add(app);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading granted apps", e);
        }
        return apps;
    }

    private boolean saveGrantedApps(List<GrantedApp> apps) {
        try {
            JSONArray array = new JSONArray();
            for (GrantedApp app : apps) {
                JSONObject obj = new JSONObject();
                obj.put("package", app.packageName);
                obj.put("app_name", app.appName);
                obj.put("granted_at", app.grantedAt);
                obj.put("last_used_at", app.lastUsedAt);
                obj.put("use_count", app.useCount);
                obj.put("uid", app.uid);
                obj.put("policy", app.policy.name());
                array.put(obj);
            }
            File file = new File(GRANTED_APPS_FILE);
            file.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(file);
            writer.write(array.toString(2));
            writer.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving granted apps", e);
            return false;
        }
    }

    private List<String> loadDeniedApps() {
        List<String> apps = new ArrayList<>();
        try {
            File file = new File(DENIED_APPS_FILE);
            if (!file.exists()) return apps;
            String content = readFileContent(file);
            JSONArray array = new JSONArray(content);
            for (int i = 0; i < array.length(); i++) {
                apps.add(array.getString(i));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading denied apps", e);
        }
        return apps;
    }

    private boolean removeFromDeniedList(String packageName) {
        try {
            List<String> denied = loadDeniedApps();
            denied.remove(packageName);
            JSONArray array = new JSONArray();
            for (String pkg : denied) {
                array.put(pkg);
            }
            File file = new File(DENIED_APPS_FILE);
            file.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(file);
            writer.write(array.toString());
            writer.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error removing from denied list", e);
            return false;
        }
    }

    private void logRootAction(String packageName, String action, String message) {
        try {
            File logFile = new File(ROOT_LOG_FILE);
            logFile.getParentFile().mkdirs();

            JSONArray logArray = new JSONArray();
            if (logFile.exists()) {
                String content = readFileContent(logFile);
                logArray = new JSONArray(content);
            }

            JSONObject entry = new JSONObject();
            entry.put("package", packageName);
            entry.put("action", action);
            entry.put("message", message);
            entry.put("timestamp", System.currentTimeMillis());

            logArray.put(entry);

            // 只保留最近 1000 条日志
            while (logArray.length() > 1000) {
                logArray.remove(0);
            }

            FileWriter writer = new FileWriter(logFile);
            writer.write(logArray.toString());
            writer.close();
        } catch (Exception e) {
            Log.e(TAG, "Error logging root action", e);
        }
    }

    private String getAppName(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence name = pm.getApplicationLabel(info);
            return name != null ? name.toString() : packageName;
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private int getAppUid(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return info.uid;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    private long getTodayStartMillis() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private String readFileContent(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    // ---- 授权策略枚举 ----

    public enum Policy {
        ALLOW,    // 始终允许
        DENY,     // 始终拒绝
        PROMPT,   // 每次询问
        ONCE      // 仅本次允许
    }

    // ---- 授权应用数据类 ----

    public static class GrantedApp {
        public String packageName;
        public String appName;
        public long grantedAt;
        public long lastUsedAt;
        public int useCount;
        public int uid;
        public Policy policy;

        public Drawable getAppIcon(Context ctx) {
            try {
                PackageManager pm = ctx.getPackageManager();
                return pm.getApplicationIcon(packageName);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }
    }
}
