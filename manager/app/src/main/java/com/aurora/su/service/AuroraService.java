/*
 * AuroraSU - Background Service
 *
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.aurora.su.core.LogManager;
import com.aurora.su.core.RootManager;
import com.aurora.su.ui.MainActivity;

/**
 * AuroraService - 后台服务
 * 负责后台监控 root 请求、维护 root 会话、处理模块生命周期
 */
public class AuroraService extends Service {

    private static final String TAG = "AuroraService";
    private static final String CHANNEL_ID = "aurora_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    // 监控间隔（毫秒）
    private static final long MONITOR_INTERVAL = 5000;
    // Root 请求检查间隔
    private static final long ROOT_REQUEST_CHECK_INTERVAL = 2000;

    private Handler handler;
    private boolean isRunning = false;
    private RootManager rootManager;
    private LogManager logManager;

    // 监控线程
    private Runnable monitorRunnable;
    private Runnable rootRequestRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AuroraService onCreate");

        handler = new Handler(Looper.getMainLooper());
        rootManager = new RootManager(this);
        logManager = new LogManager(this);

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("AuroraSU 服务运行中"));

        isRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "AuroraService onStartCommand");

        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case "START_MONITOR":
                        startMonitoring();
                        break;
                    case "STOP_MONITOR":
                        stopMonitoring();
                        break;
                    case "CHECK_ROOT_REQUESTS":
                        checkRootRequests();
                        break;
                    case "UPDATE_NOTIFICATION":
                        String text = intent.getStringExtra("text");
                        if (text != null) {
                            updateNotification(text);
                        }
                        break;
                    default:
                        startMonitoring();
                        break;
                }
            } else {
                startMonitoring();
            }
        } else {
            startMonitoring();
        }

        return START_STICKY; // 被系统杀死后自动重启
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "AuroraService onBind");
        return new AuroraBinder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "AuroraService onDestroy");
        stopMonitoring();
        isRunning = false;
    }

    /**
     * 启动后台监控
     */
    private void startMonitoring() {
        if (!isRunning) {
            isRunning = true;
        }

        Log.d(TAG, "Starting background monitoring");

        // 系统状态监控
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                try {
                    monitorSystemStatus();
                } catch (Exception e) {
                    Log.e(TAG, "Error in system monitor", e);
                }
                handler.postDelayed(this, MONITOR_INTERVAL);
            }
        };
        handler.post(monitorRunnable);

        // Root 请求监控
        rootRequestRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                try {
                    checkRootRequests();
                } catch (Exception e) {
                    Log.e(TAG, "Error in root request monitor", e);
                }
                handler.postDelayed(this, ROOT_REQUEST_CHECK_INTERVAL);
            }
        };
        handler.post(rootRequestRunnable);

        updateNotification("AuroraSU 监控已启动");
    }

    /**
     * 停止后台监控
     */
    private void stopMonitoring() {
        Log.d(TAG, "Stopping background monitoring");

        if (monitorRunnable != null) {
            handler.removeCallbacks(monitorRunnable);
            monitorRunnable = null;
        }
        if (rootRequestRunnable != null) {
            handler.removeCallbacks(rootRequestRunnable);
            rootRequestRunnable = null;
        }

        isRunning = false;
    }

    /**
     * 监控系统状态
     */
    private void monitorSystemStatus() {
        // 检查 root 可用性
        boolean rootAvailable = rootManager.checkRootAccess();
        if (!rootAvailable) {
            Log.w(TAG, "Root access lost!");
            logManager.addLog("system", "ERROR", "Root access lost during monitoring");
            updateNotification("警告: Root 访问丢失");
            return;
        }

        // 检查 AuroraSU daemon 状态
        File daemonSocket = new File("/dev/aurora_socket");
        if (!daemonSocket.exists()) {
            Log.w(TAG, "AuroraSU daemon socket not found");
        }

        // 检查模块目录
        File modulesDir = new File("/data/adb/aurora/modules");
        if (modulesDir.exists()) {
            File[] modules = modulesDir.listFiles(new java.io.FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.isDirectory();
                }
            });
            if (modules != null) {
                Log.d(TAG, "Monitoring " + modules.length + " modules");
            }
        }

        // 检查日志文件大小
        File logFile = new File("/data/adb/aurora/root_log.json");
        if (logFile.exists() && logFile.length() > 10 * 1024 * 1024) {
            Log.w(TAG, "Log file exceeds 10MB, consider clearing");
        }
    }

    /**
     * 检查是否有待处理的 root 请求
     */
    private void checkRootRequests() {
        try {
            File pendingDir = new File("/data/adb/aurora/pending_requests");
            if (!pendingDir.exists() || !pendingDir.isDirectory()) {
                return;
            }

            File[] pendingFiles = pendingDir.listFiles();
            if (pendingFiles == null || pendingFiles.length == 0) {
                return;
            }

            Log.d(TAG, "Found " + pendingFiles.length + " pending root requests");

            for (File pendingFile : pendingFiles) {
                if (pendingFile.getName().endsWith(".json")) {
                    processRootRequest(pendingFile);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking root requests", e);
        }
    }

    /**
     * 处理单个 root 请求
     */
    private void processRootRequest(File requestFile) {
        try {
            String content = readFileContent(requestFile);
            if (content == null || content.isEmpty()) {
                requestFile.delete();
                return;
            }

            org.json.JSONObject request = new org.json.JSONObject(content);
            String packageName = request.optString("package", "");
            int uid = request.optInt("uid", -1);
            int pid = request.optInt("pid", -1);
            String caller = request.optString("caller", "");

            if (packageName.isEmpty()) {
                requestFile.delete();
                return;
            }

            // 检查是否已授权
            if (rootManager.isAppGranted(packageName)) {
                // 已授权，自动批准
                logManager.addLog(packageName, "USE", "Auto-approved root request", uid, pid);
                rootManager.updateAppUsage(packageName);
                requestFile.delete();

                // 创建批准标记文件
                File approvedFile = new File("/data/adb/aurora/approved/" + packageName);
                approvedFile.getParentFile().mkdirs();
                approvedFile.createNewFile();
            } else {
                // 未授权，发送广播通知 UI
                Intent broadcastIntent = new Intent("com.aurora.su.action.ROOT_REQUEST");
                broadcastIntent.putExtra("package", packageName);
                broadcastIntent.putExtra("uid", uid);
                broadcastIntent.putExtra("pid", pid);
                broadcastIntent.putExtra("caller", caller);
                broadcastIntent.putExtra("request_file", requestFile.getAbsolutePath());
                sendBroadcast(broadcastIntent);

                logManager.addLog(packageName, "DENY", "Pending root request (awaiting user approval)", uid, pid);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing root request", e);
        }
    }

    // ---- 通知相关 ----

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "AuroraSU 服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("AuroraSU 后台监控服务");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("AuroraSU");
        builder.setContentText(text);
        builder.setSmallIcon(android.R.drawable.ic_menu_manage);
        builder.setContentIntent(pendingIntent);
        builder.setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setPriority(Notification.PRIORITY_LOW);
        }

        return builder.build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    // ---- Binder ----

    public class AuroraBinder extends android.os.Binder {
        public AuroraService getService() {
            return AuroraService.this;
        }

        public boolean isMonitoring() {
            return isRunning;
        }

        public int getPendingRequestCount() {
            File pendingDir = new File("/data/adb/aurora/pending_requests");
            if (!pendingDir.exists()) return 0;
            File[] files = pendingDir.listFiles();
            return files != null ? files.length : 0;
        }
    }

    // ---- 工具方法 ----

    private String readFileContent(File file) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }
}
