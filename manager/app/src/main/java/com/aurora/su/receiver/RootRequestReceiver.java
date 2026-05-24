/*
 * AuroraSU - Root Request Receiver
 *
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.receiver;

import java.io.File;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.aurora.su.core.LogManager;
import com.aurora.su.core.RootManager;
import com.aurora.su.ui.SuperuserActivity;

/**
 * RootRequestReceiver - Root 请求广播接收器
 * 接收来自应用或系统的 root 请求广播，弹出授权对话框通知用户
 */
public class RootRequestReceiver extends BroadcastReceiver {

    private static final String TAG = "RootRequestReceiver";
    private static final String CHANNEL_ID = "aurora_root_request_channel";
    private static final int NOTIFICATION_ID = 2001;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Log.w(TAG, "Received null intent");
            return;
        }

        String action = intent.getAction();
        if (action == null) {
            Log.w(TAG, "Received intent with null action");
            return;
        }

        Log.d(TAG, "Received broadcast: " + action);

        switch (action) {
            case "com.aurora.su.action.ROOT_REQUEST":
                handleRootRequest(context, intent);
                break;
            case "com.aurora.su.action.GRANT_RESULT":
                handleGrantResult(context, intent);
                break;
            case "com.aurora.su.action.REVOKE_RESULT":
                handleRevokeResult(context, intent);
                break;
            default:
                Log.d(TAG, "Unhandled action: " + action);
                break;
        }
    }

    /**
     * 处理 root 请求
     */
    private void handleRootRequest(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.w(TAG, "Root request intent has no extras");
            return;
        }

        String packageName = extras.getString("package", "");
        int uid = extras.getInt("uid", -1);
        int pid = extras.getInt("pid", -1);
        String caller = extras.getString("caller", "");
        String requestFile = extras.getString("request_file", "");

        if (packageName.isEmpty()) {
            Log.w(TAG, "Root request with empty package name");
            return;
        }

        Log.d(TAG, "Root request from: " + packageName + " (uid=" + uid + ", pid=" + pid + ")");

        // 记录请求日志
        LogManager logManager = new LogManager(context);
        logManager.addLog(packageName, "DENY", "Root request received, awaiting user decision", uid, pid);

        // 检查是否已有授权策略
        RootManager rootManager = new RootManager(context);
        if (rootManager.isAppGranted(packageName)) {
            // 已授权，自动批准
            logManager.addLog(packageName, "USE", "Root request auto-approved (previously granted)", uid, pid);
            rootManager.updateAppUsage(packageName);
            approveRequest(context, requestFile, packageName);
            return;
        }

        // 弹出通知，引导用户到授权页面
        showAuthorizationNotification(context, packageName, uid, pid, caller, requestFile);
    }

    /**
     * 处理授权结果
     */
    private void handleGrantResult(Context context, Intent intent) {
        String packageName = intent.getStringExtra("package");
        boolean success = intent.getBooleanExtra("success", false);

        LogManager logManager = new LogManager(context);
        if (success && packageName != null) {
            logManager.addLog(packageName, "GRANT", "Root access granted by user");
            Log.d(TAG, "Root granted to: " + packageName);
        } else {
            logManager.addLog(packageName != null ? packageName : "unknown", "DENY",
                "Root access denied by user");
            Log.d(TAG, "Root denied for: " + packageName);
        }
    }

    /**
     * 处理撤销结果
     */
    private void handleRevokeResult(Context context, Intent intent) {
        String packageName = intent.getStringExtra("package");
        boolean success = intent.getBooleanExtra("success", false);

        LogManager logManager = new LogManager(context);
        if (success && packageName != null) {
            logManager.addLog(packageName, "REVOKE", "Root access revoked by user");
            Log.d(TAG, "Root revoked for: " + packageName);
        }
    }

    /**
     * 显示授权通知
     */
    private void showAuthorizationNotification(Context context, String packageName,
                                               int uid, int pid, String caller,
                                               String requestFile) {
        createNotificationChannel(context);

        // 获取应用名称
        String appName = packageName;
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.pm.ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            if (label != null) {
                appName = label.toString();
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            // 使用包名
        }

        // 点击通知打开授权页面
        Intent grantIntent = new Intent(context, SuperuserActivity.class);
        grantIntent.putExtra("request_package", packageName);
        grantIntent.putExtra("request_uid", uid);
        grantIntent.putExtra("request_pid", pid);
        grantIntent.putExtra("request_caller", caller);
        grantIntent.putExtra("request_file", requestFile);
        grantIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, (int) System.currentTimeMillis(), grantIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 构建通知
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        builder.setContentTitle("Root 权限请求");
        builder.setContentText(appName + " 请求 Root 权限");
        builder.setSubText("点击查看详情并授权");
        builder.setSmallIcon(android.R.drawable.ic_dialog_alert);
        builder.setContentIntent(pendingIntent);
        builder.setAutoCancel(true);
        builder.setDefaults(Notification.DEFAULT_ALL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setPriority(Notification.PRIORITY_HIGH);
        }

        // 添加操作按钮
        Intent denyIntent = new Intent("com.aurora.su.action.GRANT_RESULT");
        denyIntent.putExtra("package", packageName);
        denyIntent.putExtra("success", false);
        PendingIntent denyPending = PendingIntent.getBroadcast(
            context, (int) System.currentTimeMillis() + 1, denyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            Notification.Action denyAction = new Notification.Action(
                android.R.drawable.ic_menu_close_clear_cancel,
                "拒绝",
                denyPending
            );
            builder.addAction(denyAction);
        }

        Notification notification = builder.build();
        NotificationManager manager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            // 使用包名 hash 作为通知 ID，避免重复
            int notifyId = NOTIFICATION_ID + Math.abs(packageName.hashCode() % 1000);
            manager.notify(notifyId, notification);
            Log.d(TAG, "Authorization notification shown for: " + packageName);
        }
    }

    /**
     * 批准 root 请求
     */
    private void approveRequest(Context context, String requestFile, String packageName) {
        if (requestFile != null && !requestFile.isEmpty()) {
            File file = new File(requestFile);
            if (file.exists()) {
                file.delete();
            }
        }

        // 创建批准标记
        File approvedDir = new File("/data/adb/aurora/approved");
        approvedDir.mkdirs();
        try {
            File approvedFile = new File(approvedDir, packageName);
            approvedFile.createNewFile();
        } catch (Exception e) {
            Log.e(TAG, "Error creating approval marker", e);
        }

        // 取消通知
        NotificationManager manager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            int notifyId = NOTIFICATION_ID + Math.abs(packageName.hashCode() % 1000);
            manager.cancel(notifyId);
        }
    }

    /**
     * 创建通知渠道
     */
    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Root 权限请求",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("当应用请求 Root 权限时显示通知");
            channel.enableVibration(true);
            channel.enableLights(true);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
