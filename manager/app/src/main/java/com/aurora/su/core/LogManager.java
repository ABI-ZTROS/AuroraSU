/*
 * AuroraSU - Log Manager
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
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.aurora.su.util.FileUtils;

/**
 * LogManager - 日志管理类
 * 负责 root 操作日志的记录、查询、过滤、导出和统计
 */
public class LogManager {

    private static final String TAG = "LogManager";
    private static final String AURORA_BASE_DIR = "/data/adb/aurora";
    private static final String LOG_FILE = AURORA_BASE_DIR + "/root_log.json";
    private static final String LOG_DIR = AURORA_BASE_DIR + "/logs";
    private static final int MAX_LOG_ENTRIES = 5000;
    private static final long MAX_LOG_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private Context context;

    public LogManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 获取所有日志列表
     * @return 日志条目列表，按时间倒序
     */
    public List<LogEntry> getLogs() {
        return getLogs(0, MAX_LOG_ENTRIES);
    }

    /**
     * 获取分页日志列表
     * @param offset 偏移量
     * @param limit 数量限制
     * @return 日志条目列表
     */
    public List<LogEntry> getLogs(int offset, int limit) {
        List<LogEntry> allLogs = loadLogsFromFile();
        List<LogEntry> result = new ArrayList<>();

        // 按时间倒序
        Collections.sort(allLogs, new Comparator<LogEntry>() {
            @Override
            public int compare(LogEntry a, LogEntry b) {
                return Long.compare(b.timestamp, a.timestamp);
            }
        });

        int end = Math.min(offset + limit, allLogs.size());
        for (int i = offset; i < end; i++) {
            result.add(allLogs.get(i));
        }

        return result;
    }

    /**
     * 清除所有日志
     * @return true 表示清除成功
     */
    public boolean clearLogs() {
        try {
            File logFile = new File(LOG_FILE);
            if (logFile.exists()) {
                // 备份旧日志
                File backupFile = new File(LOG_DIR, "backup_" + System.currentTimeMillis() + ".json");
                backupFile.getParentFile().mkdirs();
                if (logFile.renameTo(backupFile)) {
                    Log.d(TAG, "Old logs backed up to: " + backupFile.getAbsolutePath());
                } else {
                    logFile.delete();
                }
            }

            // 创建空日志文件
            logFile.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(logFile);
            writer.write("[]");
            writer.close();

            Log.d(TAG, "All logs cleared");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error clearing logs", e);
            return false;
        }
    }

    /**
     * 清除指定时间之前的日志
     * @param beforeTimestamp 时间戳
     * @return 清除的日志数量
     */
    public int clearLogsBefore(long beforeTimestamp) {
        try {
            List<LogEntry> logs = loadLogsFromFile();
            int originalSize = logs.size();

            List<LogEntry> retained = new ArrayList<>();
            for (LogEntry entry : logs) {
                if (entry.timestamp >= beforeTimestamp) {
                    retained.add(entry);
                }
            }

            if (retained.size() != originalSize) {
                saveLogsToFile(retained);
                Log.d(TAG, "Cleared " + (originalSize - retained.size()) + " log entries");
            }

            return originalSize - retained.size();
        } catch (Exception e) {
            Log.e(TAG, "Error clearing old logs", e);
            return 0;
        }
    }

    /**
     * 导出日志到外部存储
     * @return 导出文件路径，失败返回 null
     */
    public String exportLogs() {
        try {
            List<LogEntry> logs = loadLogsFromFile();
            if (logs.isEmpty()) {
                Log.w(TAG, "No logs to export");
                return null;
            }

            // 检查外部存储是否可用
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                Log.e(TAG, "External storage not mounted");
                return null;
            }

            // 创建导出目录
            File exportDir = new File(
                Environment.getExternalStorageDirectory(),
                "AuroraSU/logs"
            );
            exportDir.mkdirs();

            // 生成文件名（含时间戳）
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String fileName = "aurora_log_" + sdf.format(new Date()) + ".json";
            File exportFile = new File(exportDir, fileName);

            // 写入日志
            JSONArray array = new JSONArray();
            for (LogEntry entry : logs) {
                JSONObject obj = new JSONObject();
                obj.put("timestamp", entry.timestamp);
                obj.put("time", formatTimestamp(entry.timestamp));
                obj.put("package", entry.packageName);
                obj.put("action", entry.action);
                obj.put("message", entry.message);
                obj.put("uid", entry.uid);
                obj.put("pid", entry.pid);
                array.put(obj);
            }

            FileWriter writer = new FileWriter(exportFile);
            writer.write(array.toString(2));
            writer.close();

            // 同时导出可读的文本格式
            File textFile = new File(exportDir, "aurora_log_" + sdf.format(new Date()) + ".txt");
            PrintWriter pw = new PrintWriter(textFile);
            pw.println("AuroraSU Root Log Export");
            pw.println("Exported at: " + sdf.format(new Date()));
            pw.println("Total entries: " + logs.size());
            pw.println("========================================");
            pw.println();

            for (LogEntry entry : logs) {
                pw.printf("[%s] [%s] %s (uid=%d, pid=%d): %s%n",
                    formatTimestamp(entry.timestamp),
                    entry.action,
                    entry.packageName,
                    entry.uid,
                    entry.pid,
                    entry.message
                );
            }
            pw.close();

            Log.d(TAG, "Logs exported to: " + exportFile.getAbsolutePath());
            return exportFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error exporting logs", e);
            return null;
        }
    }

    /**
     * 获取日志统计信息
     * @return 统计信息 Map
     */
    public Map<String, Object> getLogStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            List<LogEntry> logs = loadLogsFromFile();

            stats.put("total_entries", logs.size());

            // 按操作类型统计
            Map<String, Integer> actionCounts = new HashMap<>();
            Map<String, Integer> packageCounts = new HashMap<>();
            long oldestTimestamp = Long.MAX_VALUE;
            long newestTimestamp = 0;
            int todayCount = 0;
            long todayStart = getTodayStartMillis();

            for (LogEntry entry : logs) {
                // 操作类型统计
                String action = entry.action;
                actionCounts.put(action, actionCounts.getOrDefault(action, 0) + 1);

                // 应用统计
                String pkg = entry.packageName;
                packageCounts.put(pkg, packageCounts.getOrDefault(pkg, 0) + 1);

                // 时间范围
                if (entry.timestamp < oldestTimestamp) oldestTimestamp = entry.timestamp;
                if (entry.timestamp > newestTimestamp) newestTimestamp = entry.timestamp;

                // 今日统计
                if (entry.timestamp >= todayStart) todayCount++;
            }

            stats.put("action_counts", actionCounts);
            stats.put("package_counts", packageCounts);
            stats.put("oldest_entry", oldestTimestamp == Long.MAX_VALUE ? 0 : oldestTimestamp);
            stats.put("newest_entry", newestTimestamp);
            stats.put("today_entries", todayCount);

            // 日志文件大小
            File logFile = new File(LOG_FILE);
            stats.put("file_size", logFile.exists() ? logFile.length() : 0);

            // 最活跃应用
            String mostActivePkg = "";
            int mostActiveCount = 0;
            for (Map.Entry<String, Integer> entry : packageCounts.entrySet()) {
                if (entry.getValue() > mostActiveCount) {
                    mostActiveCount = entry.getValue();
                    mostActivePkg = entry.getKey();
                }
            }
            stats.put("most_active_package", mostActivePkg);
            stats.put("most_active_count", mostActiveCount);

        } catch (Exception e) {
            Log.e(TAG, "Error getting log stats", e);
        }
        return stats;
    }

    /**
     * 过滤日志
     * @param type 操作类型过滤 (null 表示不过滤)
     * @param query 关键词搜索 (null 表示不过滤)
     * @return 过滤后的日志列表
     */
    public List<LogEntry> filterLogs(String type, String query) {
        List<LogEntry> allLogs = loadLogsFromFile();
        List<LogEntry> filtered = new ArrayList<>();

        String lowerQuery = (query != null) ? query.toLowerCase(Locale.US) : null;

        for (LogEntry entry : allLogs) {
            // 类型过滤
            if (type != null && !type.isEmpty() && !type.equals("ALL")) {
                if (!type.equalsIgnoreCase(entry.action)) {
                    continue;
                }
            }

            // 关键词过滤
            if (lowerQuery != null && !lowerQuery.isEmpty()) {
                boolean match = false;
                if (entry.packageName != null && entry.packageName.toLowerCase(Locale.US).contains(lowerQuery)) {
                    match = true;
                }
                if (entry.message != null && entry.message.toLowerCase(Locale.US).contains(lowerQuery)) {
                    match = true;
                }
                if (entry.action != null && entry.action.toLowerCase(Locale.US).contains(lowerQuery)) {
                    match = true;
                }
                if (!match) {
                    continue;
                }
            }

            filtered.add(entry);
        }

        // 按时间倒序
        Collections.sort(filtered, new Comparator<LogEntry>() {
            @Override
            public int compare(LogEntry a, LogEntry b) {
                return Long.compare(b.timestamp, a.timestamp);
            }
        });

        return filtered;
    }

    /**
     * 添加日志条目
     */
    public void addLog(String packageName, String action, String message) {
        addLog(packageName, action, message, -1, -1);
    }

    /**
     * 添加日志条目（含 UID 和 PID）
     */
    public void addLog(String packageName, String action, String message, int uid, int pid) {
        try {
            List<LogEntry> logs = loadLogsFromFile();

            LogEntry entry = new LogEntry();
            entry.timestamp = System.currentTimeMillis();
            entry.packageName = packageName;
            entry.action = action;
            entry.message = message;
            entry.uid = uid;
            entry.pid = pid;

            logs.add(entry);

            // 限制日志数量
            while (logs.size() > MAX_LOG_ENTRIES) {
                logs.remove(0);
            }

            saveLogsToFile(logs);
        } catch (Exception e) {
            Log.e(TAG, "Error adding log entry", e);
        }
    }

    /**
     * 获取日志总数
     */
    public int getLogCount() {
        return loadLogsFromFile().size();
    }

    // ---- 内部辅助方法 ----

    private List<LogEntry> loadLogsFromFile() {
        List<LogEntry> logs = new ArrayList<>();
        try {
            File logFile = new File(LOG_FILE);
            if (!logFile.exists()) {
                return logs;
            }

            String content = FileUtils.readFile(logFile.getAbsolutePath());
            if (content == null || content.trim().isEmpty()) {
                return logs;
            }

            JSONArray array = new JSONArray(content.trim());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                LogEntry entry = new LogEntry();
                entry.timestamp = obj.optLong("timestamp", 0);
                entry.packageName = obj.optString("package", "");
                entry.action = obj.optString("action", "");
                entry.message = obj.optString("message", "");
                entry.uid = obj.optInt("uid", -1);
                entry.pid = obj.optInt("pid", -1);
                logs.add(entry);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading logs from file", e);
        }
        return logs;
    }

    private boolean saveLogsToFile(List<LogEntry> logs) {
        try {
            JSONArray array = new JSONArray();
            for (LogEntry entry : logs) {
                JSONObject obj = new JSONObject();
                obj.put("timestamp", entry.timestamp);
                obj.put("package", entry.packageName);
                obj.put("action", entry.action);
                obj.put("message", entry.message);
                obj.put("uid", entry.uid);
                obj.put("pid", entry.pid);
                array.put(obj);
            }

            File logFile = new File(LOG_FILE);
            logFile.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(logFile);
            writer.write(array.toString());
            writer.close();

            // 检查文件大小，如果超过限制则清理旧日志
            if (logFile.length() > MAX_LOG_FILE_SIZE) {
                trimOldLogs(logs);
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving logs to file", e);
            return false;
        }
    }

    private void trimOldLogs(List<LogEntry> logs) {
        // 保留最近的一半日志
        int halfSize = logs.size() / 2;
        if (halfSize > 0 && logs.size() > halfSize) {
            List<LogEntry> trimmed = new ArrayList<>(logs.subList(halfSize, logs.size()));
            saveLogsToFile(trimmed);
            Log.d(TAG, "Trimmed old logs, kept " + trimmed.size() + " entries");
        }
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) return "Unknown";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        return sdf.format(new Date(timestamp));
    }

    private long getTodayStartMillis() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    // ---- 数据类 ----

    public static class LogEntry {
        public long timestamp;
        public String packageName;
        public String action;    // GRANT, REVOKE, USE, DENY, ERROR
        public String message;
        public int uid;
        public int pid;

        public String getFormattedTime() {
            if (timestamp <= 0) return "Unknown";
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);
            return sdf.format(new Date(timestamp));
        }

        public String getActionDisplay() {
            switch (action) {
                case "GRANT": return "授权";
                case "REVOKE": return "撤销";
                case "USE": return "使用";
                case "DENY": return "拒绝";
                case "ERROR": return "错误";
                default: return action;
            }
        }
    }
}
