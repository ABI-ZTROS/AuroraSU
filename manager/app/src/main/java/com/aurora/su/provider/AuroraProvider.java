/*
 * AuroraSU - Content Provider
 *
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.provider;

import java.io.File;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import com.aurora.su.core.LogManager;
import com.aurora.su.core.ModuleManager;
import com.aurora.su.core.RootManager;
import com.aurora.su.core.SecurityManager;

/**
 * AuroraProvider - ContentProvider
 * 提供 root 状态查询接口和模块信息查询接口
 * 允许其他应用通过 ContentResolver 查询 AuroraSU 状态
 */
public class AuroraProvider extends ContentProvider {

    private static final String TAG = "AuroraProvider";
    private static final String AUTHORITY = "com.aurora.su.provider";

    // URI 路径
    private static final String PATH_ROOT_STATUS = "root_status";
    private static final String PATH_GRANTED_APPS = "granted_apps";
    private static final String PATH_MODULES = "modules";
    private static final String PATH_MODULE_INFO = "module_info";
    private static final String PATH_SECURITY = "security";
    private static final String PATH_LOGS = "logs";
    private static final String PATH_VERSION = "version";

    // URI 编码
    private static final int CODE_ROOT_STATUS = 1;
    private static final int CODE_GRANTED_APPS = 2;
    private static final int CODE_GRANTED_APP_ITEM = 3;
    private static final int CODE_MODULES = 4;
    private static final int CODE_MODULE_ITEM = 5;
    private static final int CODE_SECURITY = 6;
    private static final int CODE_LOGS = 7;
    private static final int CODE_VERSION = 8;

    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        uriMatcher.addURI(AUTHORITY, PATH_ROOT_STATUS, CODE_ROOT_STATUS);
        uriMatcher.addURI(AUTHORITY, PATH_GRANTED_APPS, CODE_GRANTED_APPS);
        uriMatcher.addURI(AUTHORITY, PATH_GRANTED_APPS + "/*", CODE_GRANTED_APP_ITEM);
        uriMatcher.addURI(AUTHORITY, PATH_MODULES, CODE_MODULES);
        uriMatcher.addURI(AUTHORITY, PATH_MODULES + "/*", CODE_MODULE_ITEM);
        uriMatcher.addURI(AUTHORITY, PATH_SECURITY, CODE_SECURITY);
        uriMatcher.addURI(AUTHORITY, PATH_LOGS, CODE_LOGS);
        uriMatcher.addURI(AUTHORITY, PATH_VERSION, CODE_VERSION);
    }

    private Context appContext;
    private RootManager rootManager;
    private ModuleManager moduleManager;
    private SecurityManager securityManager;
    private LogManager logManager;

    @Override
    public boolean onCreate() {
        Log.d(TAG, "AuroraProvider onCreate");
        appContext = getContext();
        if (appContext != null) {
            rootManager = new RootManager(appContext);
            moduleManager = new ModuleManager(appContext);
            securityManager = new SecurityManager(appContext);
            logManager = new LogManager(appContext);
        }
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Log.d(TAG, "Query: " + uri);

        if (appContext == null) {
            return null;
        }

        int match = uriMatcher.match(uri);
        switch (match) {
            case CODE_ROOT_STATUS:
                return queryRootStatus();
            case CODE_GRANTED_APPS:
                return queryGrantedApps(selection, selectionArgs);
            case CODE_GRANTED_APP_ITEM:
                String packageName = uri.getLastPathSegment();
                return queryGrantedApp(packageName);
            case CODE_MODULES:
                return queryModules();
            case CODE_MODULE_ITEM:
                String moduleId = uri.getLastPathSegment();
                return queryModuleInfo(moduleId);
            case CODE_SECURITY:
                return querySecurityInfo();
            case CODE_LOGS:
                return queryLogs(selection, selectionArgs);
            case CODE_VERSION:
                return queryVersion();
            default:
                Log.w(TAG, "Unknown URI: " + uri);
                return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        int match = uriMatcher.match(uri);
        switch (match) {
            case CODE_ROOT_STATUS:
            case CODE_SECURITY:
            case CODE_VERSION:
                return "vnd.android.cursor.item/aurora_status";
            case CODE_GRANTED_APPS:
            case CODE_MODULES:
            case CODE_LOGS:
                return "vnd.android.cursor.dir/aurora_list";
            case CODE_GRANTED_APP_ITEM:
            case CODE_MODULE_ITEM:
                return "vnd.android.cursor.item/aurora_item";
            default:
                return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // AuroraProvider 是只读的
        Log.w(TAG, "Insert not supported: " + uri);
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // AuroraProvider 是只读的
        Log.w(TAG, "Delete not supported: " + uri);
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // AuroraProvider 是只读的
        Log.w(TAG, "Update not supported: " + uri);
        return 0;
    }

    // ---- 查询实现 ----

    /**
     * 查询 root 状态
     */
    private Cursor queryRootStatus() {
        MatrixCursor cursor = new MatrixCursor(new String[]{
            "root_available", "selinux_status", "kernel_version",
            "security_patch", "granted_apps_count", "module_count"
        });

        boolean rootAvailable = rootManager.checkRootAccess();
        String selinux = securityManager.getSelinuxStatus();
        String kernel = securityManager.getKernelVersion();
        String patch = securityManager.getSecurityPatchLevel();
        int grantedCount = rootManager.getAllGrantedApps().size();
        int moduleCount = moduleManager.getInstalledModules().size();

        cursor.addRow(new Object[]{
            rootAvailable ? 1 : 0,
            selinux,
            kernel,
            patch,
            grantedCount,
            moduleCount
        });

        return cursor;
    }

    /**
     * 查询所有已授权应用
     */
    private Cursor queryGrantedApps(String selection, String[] selectionArgs) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
            "package_name", "app_name", "granted_at", "last_used_at", "use_count", "policy"
        });

        List<RootManager.GrantedApp> apps = rootManager.getAllGrantedApps();
        for (RootManager.GrantedApp app : apps) {
            cursor.addRow(new Object[]{
                app.packageName,
                app.appName,
                app.grantedAt,
                app.lastUsedAt,
                app.useCount,
                app.policy.name()
            });
        }

        return cursor;
    }

    /**
     * 查询单个已授权应用
     */
    private Cursor queryGrantedApp(String packageName) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
            "package_name", "app_name", "granted_at", "last_used_at", "use_count", "policy", "granted"
        });

        boolean granted = rootManager.isAppGranted(packageName);
        List<RootManager.GrantedApp> apps = rootManager.getAllGrantedApps();
        for (RootManager.GrantedApp app : apps) {
            if (app.packageName.equals(packageName)) {
                cursor.addRow(new Object[]{
                    app.packageName,
                    app.appName,
                    app.grantedAt,
                    app.lastUsedAt,
                    app.useCount,
                    app.policy.name(),
                    1
                });
                return cursor;
            }
        }

        // 未找到，返回空结果
        cursor.addRow(new Object[]{packageName, "", 0, 0, 0, "DENY", 0});
        return cursor;
    }

    /**
     * 查询模块列表
     */
    private Cursor queryModules() {
        MatrixCursor cursor = new MatrixCursor(new String[]{
            "id", "name", "version", "version_code", "author", "description", "enabled"
        });

        List<ModuleManager.ModuleInfo> modules = moduleManager.getInstalledModules();
        for (ModuleManager.ModuleInfo module : modules) {
            cursor.addRow(new Object[]{
                module.id,
                module.name,
                module.version,
                module.versionCode,
                module.author,
                module.description,
                module.enabled ? 1 : 0
            });
        }

        return cursor;
    }

    /**
     * 查询单个模块详情
     */
    private Cursor queryModuleInfo(String moduleId) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
            "id", "name", "version", "version_code", "author",
            "description", "enabled", "path", "total_size", "file_count"
        });

        ModuleManager.ModuleInfo info = moduleManager.getModuleInfo(moduleId);
        if (info != null) {
            cursor.addRow(new Object[]{
                info.id,
                info.name,
                info.version,
                info.versionCode,
                info.author,
                info.description,
                info.enabled ? 1 : 0,
                info.path,
                info.totalSize,
                info.fileCount
            });
        }

        return cursor;
    }

    /**
     * 查询安全信息
     */
    private Cursor querySecurityInfo() {
        MatrixCursor cursor = new MatrixCursor(new String[]{
            "selinux_status", "kernel_version", "security_patch",
            "root_hidden", "security_score", "safetynet_passed"
        });

        String selinux = securityManager.getSelinuxStatus();
        String kernel = securityManager.getKernelVersion();
        String patch = securityManager.getSecurityPatchLevel();
        boolean rootHidden = securityManager.isRootHidden();
        int score = securityManager.getSecurityScore();
        boolean safetynet = securityManager.checkSafetyNet().passed;

        cursor.addRow(new Object[]{
            selinux,
            kernel,
            patch,
            rootHidden ? 1 : 0,
            score,
            safetynet ? 1 : 0
        });

        return cursor;
    }

    /**
     * 查询日志
     */
    private Cursor queryLogs(String selection, String[] selectionArgs) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
            "timestamp", "package_name", "action", "message", "uid", "pid"
        });

        List<LogManager.LogEntry> logs;
        if (selection != null && !selection.isEmpty() && selectionArgs != null && selectionArgs.length > 0) {
            logs = logManager.filterLogs(selectionArgs[0], null);
        } else {
            logs = logManager.getLogs(0, 100);
        }

        for (LogManager.LogEntry entry : logs) {
            cursor.addRow(new Object[]{
                entry.timestamp,
                entry.packageName,
                entry.action,
                entry.message,
                entry.uid,
                entry.pid
            });
        }

        return cursor;
    }

    /**
     * 查询版本信息
     */
    private Cursor queryVersion() {
        MatrixCursor cursor = new MatrixCursor(new String[]{
            "version_name", "version_code", "build_date", "api_version"
        });

        cursor.addRow(new Object[]{
            "1.0.0",
            100,
            "2026-01-01",
            1
        });

        return cursor;
    }
}
