/*
 * AuroraSU - Module Manager
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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;

import com.aurora.su.util.ShellUtils;
import com.aurora.su.util.FileUtils;

/**
 * ModuleManager - 模块管理类
 * 负责模块的安装、卸载、启用、禁用和更新检查
 */
public class ModuleManager {

    private static final String TAG = "ModuleManager";
    private static final String AURORA_BASE_DIR = "/data/adb/aurora";
    private static final String MODULES_DIR = AURORA_BASE_DIR + "/modules";
    private static final String MODULE_UPDATE_CACHE = AURORA_BASE_DIR + "/module_updates.json";
    private static final String MODULE_PROP_FILE = "module.prop";

    private Context context;
    private ShellUtils shellUtils;

    public ModuleManager(Context context) {
        this.context = context.getApplicationContext();
        this.shellUtils = new ShellUtils();
    }

    /**
     * 获取已安装模块列表
     * @return 模块信息列表
     */
    public List<ModuleInfo> getInstalledModules() {
        List<ModuleInfo> modules = new ArrayList<>();
        try {
            File modulesDir = new File(MODULES_DIR);
            if (!modulesDir.exists() || !modulesDir.isDirectory()) {
                Log.w(TAG, "Modules directory does not exist");
                return modules;
            }

            File[] moduleDirs = modulesDir.listFiles(new java.io.FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.isDirectory();
                }
            });
            if (moduleDirs == null) {
                return modules;
            }

            for (File moduleDir : moduleDirs) {
                ModuleInfo info = parseModuleInfo(moduleDir);
                if (info != null) {
                    modules.add(info);
                }
            }

            // 按名称排序
            Collections.sort(modules, new Comparator<ModuleInfo>() {
                @Override
                public int compare(ModuleInfo a, ModuleInfo b) {
                    return a.name.compareToIgnoreCase(b.name);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error getting installed modules", e);
        }
        return modules;
    }

    /**
     * 启用模块
     * @param id 模块 ID
     * @return true 表示启用成功
     */
    public boolean enableModule(String id) {
        if (id == null || id.isEmpty()) {
            Log.e(TAG, "Invalid module id for enable");
            return false;
        }

        try {
            File moduleDir = new File(MODULES_DIR, id);
            if (!moduleDir.exists()) {
                Log.e(TAG, "Module not found: " + id);
                return false;
            }

            File disableFile = new File(moduleDir, "disable");
            File removeFile = new File(moduleDir, "remove");

            // 删除 disable 标记文件
            if (disableFile.exists()) {
                boolean deleted = disableFile.delete();
                if (!deleted) {
                    // 尝试通过 root 删除
                    ShellUtils.CommandResult result = shellUtils.runRootCommand("rm -f " + disableFile.getAbsolutePath());
                    if (!result.success) {
                        Log.e(TAG, "Failed to delete disable file for: " + id);
                        return false;
                    }
                }
            }

            // 删除 remove 标记文件（防止下次重启被删除）
            if (removeFile.exists()) {
                removeFile.delete();
            }

            Log.d(TAG, "Module enabled: " + id);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error enabling module: " + id, e);
            return false;
        }
    }

    /**
     * 禁用模块
     * @param id 模块 ID
     * @return true 表示禁用成功
     */
    public boolean disableModule(String id) {
        if (id == null || id.isEmpty()) {
            Log.e(TAG, "Invalid module id for disable");
            return false;
        }

        try {
            File moduleDir = new File(MODULES_DIR, id);
            if (!moduleDir.exists()) {
                Log.e(TAG, "Module not found: " + id);
                return false;
            }

            File disableFile = new File(moduleDir, "disable");
            if (!disableFile.exists()) {
                boolean created = disableFile.createNewFile();
                if (!created) {
                    // 尝试通过 root 创建
                    ShellUtils.CommandResult result = shellUtils.runRootCommand("touch " + disableFile.getAbsolutePath());
                    if (!result.success) {
                        Log.e(TAG, "Failed to create disable file for: " + id);
                        return false;
                    }
                }
            }

            Log.d(TAG, "Module disabled: " + id);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error disabling module: " + id, e);
            return false;
        }
    }

    /**
     * 删除模块
     * @param id 模块 ID
     * @return true 表示删除成功
     */
    public boolean removeModule(String id) {
        if (id == null || id.isEmpty()) {
            Log.e(TAG, "Invalid module id for remove");
            return false;
        }

        try {
            File moduleDir = new File(MODULES_DIR, id);
            if (!moduleDir.exists()) {
                Log.e(TAG, "Module not found: " + id);
                return false;
            }

            // 创建 remove 标记文件，下次重启时自动删除
            File removeFile = new File(moduleDir, "remove");
            boolean created = removeFile.createNewFile();
            if (!created) {
                ShellUtils.CommandResult result = shellUtils.runRootCommand("touch " + removeFile.getAbsolutePath());
                if (!result.success) {
                    // 直接尝试删除
                    return deleteRecursive(moduleDir);
                }
            }

            // 同时尝试立即删除
            ShellUtils.CommandResult result = shellUtils.runRootCommand("rm -rf " + moduleDir.getAbsolutePath());
            if (result.success) {
                Log.d(TAG, "Module removed immediately: " + id);
                return true;
            }

            Log.d(TAG, "Module marked for removal on reboot: " + id);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error removing module: " + id, e);
            return false;
        }
    }

    /**
     * 安装模块
     * @param path 模块 zip 文件路径
     * @return 安装结果
     */
    public InstallResult installModule(String path) {
        InstallResult result = new InstallResult();

        if (path == null || path.isEmpty()) {
            result.success = false;
            result.message = "无效的文件路径";
            return result;
        }

        File zipFile = new File(path);
        if (!zipFile.exists()) {
            result.success = false;
            result.message = "文件不存在: " + path;
            return result;
        }

        try {
            // 解析模块信息
            ModuleInfo info = parseModuleFromZip(zipFile);
            if (info == null) {
                result.success = false;
                result.message = "无法解析模块信息，缺少 module.prop";
                return result;
            }

            // 检查模块是否已安装
            File targetDir = new File(MODULES_DIR, info.id);
            if (targetDir.exists()) {
                result.success = false;
                result.message = "模块已安装: " + info.name;
                result.moduleInfo = info;
                return result;
            }

            // 创建目标目录
            targetDir.mkdirs();

            // 解压模块文件
            boolean extracted = extractModuleZip(zipFile, targetDir);
            if (!extracted) {
                // 清理失败的安装
                deleteRecursive(targetDir);
                result.success = false;
                result.message = "解压模块文件失败";
                return result;
            }

            // 验证安装
            File propFile = new File(targetDir, MODULE_PROP_FILE);
            if (!propFile.exists()) {
                deleteRecursive(targetDir);
                result.success = false;
                result.message = "安装验证失败，module.prop 不存在";
                return result;
            }

            // 设置正确的权限
            shellUtils.runRootCommand("chmod -R 755 " + targetDir.getAbsolutePath());
            shellUtils.runRootCommand("chown -R root:root " + targetDir.getAbsolutePath());

            result.success = true;
            result.message = "模块安装成功: " + info.name;
            result.moduleInfo = info;
            Log.d(TAG, "Module installed: " + info.id + " (" + info.name + ")");

        } catch (Exception e) {
            Log.e(TAG, "Error installing module from: " + path, e);
            result.success = false;
            result.message = "安装失败: " + e.getMessage();
        }

        return result;
    }

    /**
     * 获取模块详情
     * @param id 模块 ID
     * @return 模块详细信息
     */
    public ModuleInfo getModuleInfo(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }

        File moduleDir = new File(MODULES_DIR, id);
        if (!moduleDir.exists()) {
            return null;
        }

        ModuleInfo info = parseModuleInfo(moduleDir);
        if (info != null) {
            // 补充详细信息
            info.totalSize = calculateModuleSize(moduleDir);
            info.fileCount = countModuleFiles(moduleDir);
        }
        return info;
    }

    /**
     * 检查模块更新
     * @return 需要更新的模块列表
     */
    public List<ModuleUpdateInfo> checkModuleUpdates() {
        List<ModuleUpdateInfo> updates = new ArrayList<>();
        try {
            List<ModuleInfo> modules = getInstalledModules();
            File updateCache = new File(MODULE_UPDATE_CACHE);

            JSONObject cache = new JSONObject();
            if (updateCache.exists()) {
                String content = FileUtils.readFile(updateCache.getAbsolutePath());
                if (content != null && !content.isEmpty()) {
                    cache = new JSONObject(content);
                }
            }

            for (ModuleInfo module : modules) {
                // 检查模块是否有 update.json
                File updateJson = new File(MODULES_DIR, module.id + "/update.json");
                if (updateJson.exists()) {
                    String content = FileUtils.readFile(updateJson.getAbsolutePath());
                    if (content != null) {
                        JSONObject updateInfo = new JSONObject(content);
                        String latestVersion = updateInfo.optString("version", "");
                        String downloadUrl = updateInfo.optString("download_url", "");
                        String changelog = updateInfo.optString("changelog", "");

                        if (!latestVersion.isEmpty() && !latestVersion.equals(module.version)) {
                            ModuleUpdateInfo update = new ModuleUpdateInfo();
                            update.moduleId = module.id;
                            update.moduleName = module.name;
                            update.currentVersion = module.version;
                            update.latestVersion = latestVersion;
                            update.downloadUrl = downloadUrl;
                            update.changelog = changelog;
                            updates.add(update);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking module updates", e);
        }
        return updates;
    }

    // ---- 内部辅助方法 ----

    private ModuleInfo parseModuleInfo(File moduleDir) {
        try {
            File propFile = new File(moduleDir, MODULE_PROP_FILE);
            if (!propFile.exists()) {
                return null;
            }

            ModuleInfo info = new ModuleInfo();
            info.id = moduleDir.getName();
            info.path = moduleDir.getAbsolutePath();

            BufferedReader reader = new BufferedReader(new FileReader(propFile));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) {
                    continue; // 跳过注释和空行
                }
                int sep = line.indexOf('=');
                if (sep > 0) {
                    String key = line.substring(0, sep).trim();
                    String value = line.substring(sep + 1).trim();
                    switch (key) {
                        case "id":
                            info.id = value;
                            break;
                        case "name":
                            info.name = value;
                            break;
                        case "version":
                            info.version = value;
                            break;
                        case "versionCode":
                            info.versionCode = parseVersionCode(value);
                            break;
                        case "author":
                            info.author = value;
                            break;
                        case "description":
                            info.description = value;
                            break;
                    }
                }
            }
            reader.close();

            // 检查启用状态
            File disableFile = new File(moduleDir, "disable");
            info.enabled = !disableFile.exists();

            // 检查待删除状态
            File removeFile = new File(moduleDir, "remove");
            info.pendingRemove = removeFile.exists();

            return info;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing module info from: " + moduleDir.getName(), e);
            return null;
        }
    }

    private ModuleInfo parseModuleFromZip(File zipFile) {
        try {
            ZipFile zip = new ZipFile(zipFile);
            ZipEntry propEntry = zip.getEntry("module.prop");
            if (propEntry == null) {
                zip.close();
                return null;
            }

            InputStream is = zip.getInputStream(propEntry);
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(is));
            ModuleInfo info = new ModuleInfo();

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) continue;
                int sep = line.indexOf('=');
                if (sep > 0) {
                    String key = line.substring(0, sep).trim();
                    String value = line.substring(sep + 1).trim();
                    switch (key) {
                        case "id": info.id = value; break;
                        case "name": info.name = value; break;
                        case "version": info.version = value; break;
                        case "versionCode": info.versionCode = parseVersionCode(value); break;
                        case "author": info.author = value; break;
                        case "description": info.description = value; break;
                    }
                }
            }
            reader.close();
            zip.close();

            if (info.id == null || info.id.isEmpty()) {
                return null;
            }
            return info;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing module from zip", e);
            return null;
        }
    }

    private boolean extractModuleZip(File zipFile, File targetDir) {
        try {
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(zipFile)
            );
            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(targetDir, entry.getName());

                // 安全检查：防止路径遍历
                if (!outFile.getCanonicalPath().startsWith(targetDir.getCanonicalPath())) {
                    Log.e(TAG, "Path traversal detected in zip: " + entry.getName());
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    OutputStream os = new java.io.FileOutputStream(outFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        os.write(buffer, 0, len);
                    }
                    os.close();
                }
                zis.closeEntry();
            }
            zis.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting module zip", e);
            return false;
        }
    }

    private int parseVersionCode(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long calculateModuleSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    size += calculateModuleSize(file);
                } else {
                    size += file.length();
                }
            }
        }
        return size;
    }

    private int countModuleFiles(File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    count += countModuleFiles(file);
                } else {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    // ---- 数据类 ----

    public static class ModuleInfo {
        public String id;
        public String name = "";
        public String version = "";
        public int versionCode;
        public String author = "";
        public String description = "";
        public String path = "";
        public boolean enabled;
        public boolean pendingRemove;
        public long totalSize;
        public int fileCount;

        public String getFormattedSize() {
            if (totalSize < 1024) return totalSize + " B";
            if (totalSize < 1024 * 1024) return String.format("%.1f KB", totalSize / 1024.0);
            return String.format("%.1f MB", totalSize / (1024.0 * 1024.0));
        }
    }

    public static class InstallResult {
        public boolean success;
        public String message;
        public ModuleInfo moduleInfo;
    }

    public static class ModuleUpdateInfo {
        public String moduleId;
        public String moduleName;
        public String currentVersion;
        public String latestVersion;
        public String downloadUrl;
        public String changelog;
    }
}
