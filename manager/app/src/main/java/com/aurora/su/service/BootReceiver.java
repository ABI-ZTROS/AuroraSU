/*
 * AuroraSU - Boot Receiver
 *
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.aurora.su.core.LogManager;
import com.aurora.su.core.RootManager;
import com.aurora.su.core.SecurityManager;
import com.aurora.su.util.PreferencesManager;

/**
 * BootReceiver - 开机启动接收器
 * 在设备启动完成后自动启动 AuroraService，并执行初始化检查
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            Log.w(TAG, "Received null intent or action");
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "Received broadcast: " + action);

        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
                onBootCompleted(context);
                break;
            case "android.intent.action.LOCKED_BOOT_COMPLETED":
                onLockedBootCompleted(context);
                break;
            case Intent.ACTION_USER_PRESENT:
                onUserPresent(context);
                break;
            default:
                Log.d(TAG, "Unhandled action: " + action);
                break;
        }
    }

    /**
     * 设备启动完成
     */
    private void onBootCompleted(Context context) {
        Log.d(TAG, "Boot completed, initializing AuroraSU service");

        try {
            // 1. 检查 root 权限
            RootManager rootManager = new RootManager(context);
            boolean rootAvailable = rootManager.checkRootAccess();
            Log.d(TAG, "Root available: " + rootAvailable);

            if (!rootAvailable) {
                Log.w(TAG, "Root not available, skipping service start");
                return;
            }

            // 2. 执行安全初始化
            performSecurityInit(context);

            // 3. 启动后台服务
            startAuroraService(context);

            // 4. 记录启动日志
            LogManager logManager = new LogManager(context);
            logManager.addLog("system", "BOOT", "AuroraSU service started after boot");

            Log.d(TAG, "AuroraSU boot initialization complete");

        } catch (Exception e) {
            Log.e(TAG, "Error during boot initialization", e);
        }
    }

    /**
     * 锁屏启动完成（Android 7.0+ 直接启动）
     */
    private void onLockedBootCompleted(Context context) {
        Log.d(TAG, "Locked boot completed");
        // 在直接启动阶段只执行最小化初始化
        // 完整初始化等待 ACTION_BOOT_COMPLETED
    }

    /**
     * 用户解锁设备
     */
    private void onUserPresent(Context context) {
        Log.d(TAG, "User present, performing post-unlock tasks");
        try {
            // 检查是否需要重新启动服务
            PreferencesManager prefs = new PreferencesManager(context);
            boolean serviceRunning = prefs.getBoolean("service_running", false);

            if (!serviceRunning) {
                startAuroraService(context);
            }

            // 检查 root 隐藏状态
            SecurityManager securityManager = new SecurityManager(context);
            boolean rootHidden = securityManager.isRootHidden();
            if (rootHidden) {
                securityManager.toggleRootHide(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in user present handler", e);
        }
    }

    /**
     * 执行安全初始化
     */
    private void performSecurityInit(Context context) {
        try {
            SecurityManager securityManager = new SecurityManager(context);
            PreferencesManager prefs = new PreferencesManager(context);

            // 检查 SELinux 状态
            String selinuxStatus = securityManager.getSelinuxStatus();
            Log.d(TAG, "SELinux status: " + selinuxStatus);

            // 如果启用了 root 隐藏，确保隐藏机制生效
            if (prefs.getBoolean("root_hide_enabled", false)) {
                Log.d(TAG, "Root hiding is enabled, applying hide rules");
                securityManager.toggleRootHide(true);
            }

            // 检查模块状态
            com.aurora.su.core.ModuleManager moduleManager =
                new com.aurora.su.core.ModuleManager(context);
            java.util.List modules = moduleManager.getInstalledModules();
            Log.d(TAG, "Found " + modules.size() + " installed modules");

            // 验证已授权应用列表完整性
            RootManager rootManager = new RootManager(context);
            java.util.List grantedApps = rootManager.getAllGrantedApps();
            Log.d(TAG, "Found " + grantedApps.size() + " granted apps");

            // 记录安全状态
            prefs.putLong("last_boot_time", System.currentTimeMillis());
            prefs.putString("last_selinux_status", selinuxStatus);

        } catch (Exception e) {
            Log.e(TAG, "Error during security initialization", e);
        }
    }

    /**
     * 启动 AuroraService
     */
    private void startAuroraService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, AuroraService.class);
            serviceIntent.setAction("START_MONITOR");

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            Log.d(TAG, "AuroraService start requested");

            // 标记服务为运行中
            PreferencesManager prefs = new PreferencesManager(context);
            prefs.putBoolean("service_running", true);

        } catch (Exception e) {
            Log.e(TAG, "Error starting AuroraService", e);
        }
    }
}
