/*
 * AuroraSU - Security Manager
 *
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.aurora.su.util.ShellUtils;
import com.aurora.su.util.DeviceUtils;
import com.aurora.su.util.PreferencesManager;

/**
 * SecurityManager - 安全管理类
 * 负责 SELinux 状态检测、内核版本获取、安全补丁检查、root 隐藏、安全评分计算
 */
public class SecurityManager {

    private static final String TAG = "SecurityManager";
    private static final String AURORA_BASE_DIR = "/data/adb/aurora";
    private static final String SECURITY_CONFIG_FILE = AURORA_BASE_DIR + "/security_config.json";

    private Context context;
    private ShellUtils shellUtils;
    private PreferencesManager prefs;

    public SecurityManager(Context context) {
        this.context = context.getApplicationContext();
        this.shellUtils = new ShellUtils();
        this.prefs = new PreferencesManager(context);
    }

    /**
     * 获取 SELinux 状态
     * @return SELinux 状态字符串 (Enforcing/Permissive/Disabled)
     */
    public String getSelinuxStatus() {
        try {
            // 方法1: 读取 /sys/fs/selinux/enforce
            File enforceFile = new File("/sys/fs/selinux/enforce");
            if (enforceFile.exists()) {
                String content = readFileContent(enforceFile).trim();
                if ("1".equals(content)) {
                    return "Enforcing";
                } else if ("0".equals(content)) {
                    return "Permissive";
                }
            }

            // 方法2: 通过 getenforce 命令
            ShellUtils.CommandResult result = shellUtils.runCommand("getenforce");
            if (result.success && result.output != null && !result.output.isEmpty()) {
                return result.output.trim();
            }

            // 方法3: 通过 root 命令
            result = shellUtils.runRootCommand("getenforce");
            if (result.success && result.output != null && !result.output.isEmpty()) {
                return result.output.trim();
            }

            return "Unknown";
        } catch (Exception e) {
            Log.e(TAG, "Error getting SELinux status", e);
            return "Unknown";
        }
    }

    /**
     * 判断 SELinux 是否为 Enforcing 模式
     */
    public boolean isSelinuxEnforcing() {
        return "Enforcing".equals(getSelinuxStatus());
    }

    /**
     * 获取内核版本
     * @return 内核版本字符串
     */
    public String getKernelVersion() {
        try {
            // 方法1: 读取 /proc/version
            File versionFile = new File("/proc/version");
            if (versionFile.exists()) {
                String content = readFileContent(versionFile).trim();
                // 解析内核版本号，格式: Linux version x.x.x ...
                Pattern pattern = Pattern.compile("Linux version (\\S+)");
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
                return content;
            }

            // 方法2: 通过 uname 命令
            ShellUtils.CommandResult result = shellUtils.runCommand("uname -r");
            if (result.success && result.output != null && !result.output.isEmpty()) {
                return result.output.trim();
            }

            return "Unknown";
        } catch (Exception e) {
            Log.e(TAG, "Error getting kernel version", e);
            return "Unknown";
        }
    }

    /**
     * 获取完整内核信息
     */
    public String getFullKernelInfo() {
        try {
            File versionFile = new File("/proc/version");
            if (versionFile.exists()) {
                return readFileContent(versionFile).trim();
            }
            return "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * 获取安全补丁级别
     * @return 安全补丁日期字符串
     */
    public String getSecurityPatchLevel() {
        try {
            // 方法1: 通过 Build.VERSION.SECURITY_PATCH (API 23+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String patch = Build.VERSION.SECURITY_PATCH;
                if (patch != null && !patch.isEmpty()) {
                    return patch;
                }
            }

            // 方法2: 通过 ro.build.version.security_patch 属性
            ShellUtils.CommandResult result = shellUtils.runCommand("getprop ro.build.version.security_patch");
            if (result.success && result.output != null && !result.output.isEmpty()) {
                return result.output.trim();
            }

            return "Unknown";
        } catch (Exception e) {
            Log.e(TAG, "Error getting security patch level", e);
            return "Unknown";
        }
    }

    /**
     * 检查 root 隐藏状态
     * @return true 表示 root 已隐藏
     */
    public boolean isRootHidden() {
        return prefs.getBoolean("root_hide_enabled", false);
    }

    /**
     * 切换 root 隐藏状态
     * @param hide true 表示隐藏 root，false 表示显示
     * @return true 表示操作成功
     */
    public boolean toggleRootHide(boolean hide) {
        try {
            prefs.putBoolean("root_hide_enabled", hide);

            if (hide) {
                // 启用 root 隐藏
                // 1. 设置环境变量
                shellUtils.runRootCommand("setprop persist.aurora.hide_root 1");

                // 2. 配置 MagiskHide/Shamiko 兼容
                shellUtils.runRootCommand("mkdir -p /data/adb/modules/aurora_hide");
                shellUtils.runRootCommand("echo 'id=aurora_hide\nname=AuroraSU Root Hider\nversion=1.0\nauthor=AuroraSU Team\ndescription=Hides root from detection' > /data/adb/modules/aurora_hide/module.prop");

                // 3. 配置 su 隐藏列表
                shellUtils.runRootCommand("mkdir -p " + AURORA_BASE_DIR + "/hide");
                shellUtils.runRootCommand("echo '1' > " + AURORA_BASE_DIR + "/hide/enabled");

                Log.d(TAG, "Root hiding enabled");
            } else {
                // 禁用 root 隐藏
                shellUtils.runRootCommand("setprop persist.aurora.hide_root 0");
                shellUtils.runRootCommand("rm -rf /data/adb/modules/aurora_hide");
                shellUtils.runRootCommand("rm -rf " + AURORA_BASE_DIR + "/hide");

                Log.d(TAG, "Root hiding disabled");
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error toggling root hide", e);
            return false;
        }
    }

    /**
     * 计算安全评分 (0-100)
     * @return 安全评分
     */
    public int getSecurityScore() {
        int score = 100;

        // 1. SELinux 状态检查 (-20 分)
        String selinux = getSelinuxStatus();
        if ("Permissive".equals(selinux)) {
            score -= 20;
            Log.d(TAG, "Security penalty: SELinux Permissive (-20)");
        } else if ("Disabled".equals(selinux)) {
            score -= 30;
            Log.d(TAG, "Security penalty: SELinux Disabled (-30)");
        }

        // 2. Root 隐藏检查 (-15 分)
        if (!isRootHidden()) {
            score -= 15;
            Log.d(TAG, "Security penalty: Root not hidden (-15)");
        }

        // 3. 安全补丁检查 (-20 分)
        String patchLevel = getSecurityPatchLevel();
        if (!"Unknown".equals(patchLevel)) {
            try {
                // 检查补丁是否在 6 个月内
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                java.util.Date patchDate = sdf.parse(patchLevel);
                long sixMonthsAgo = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000;
                if (patchDate.getTime() < sixMonthsAgo) {
                    score -= 20;
                    Log.d(TAG, "Security penalty: Outdated security patch (-20)");
                }
            } catch (Exception e) {
                // 解析失败，不扣分
            }
        } else {
            score -= 10;
            Log.d(TAG, "Security penalty: Unknown security patch (-10)");
        }

        // 4. 已授权应用数量检查 (-10 分)
        RootManager rootManager = new RootManager(context);
        int grantedCount = rootManager.getAllGrantedApps().size();
        if (grantedCount > 10) {
            score -= 10;
            Log.d(TAG, "Security penalty: Too many granted apps (-10)");
        } else if (grantedCount > 5) {
            score -= 5;
            Log.d(TAG, "Security penalty: Many granted apps (-5)");
        }

        // 5. 调试模式检查 (-10 分)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (Build.TYPE.equals("userdebug") || Build.TYPE.equals("eng")) {
                score -= 10;
                Log.d(TAG, "Security penalty: Debug build detected (-10)");
            }
        }

        // 6. USB 调试检查 (-5 分)
        if (android.provider.Settings.Global.getInt(
                context.getContentResolver(),
                android.provider.Settings.Global.ADB_ENABLED, 0) == 1) {
            score -= 5;
            Log.d(TAG, "Security penalty: USB debugging enabled (-5)");
        }

        // 7. 未知来源安装检查 (-5 分)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (android.provider.Settings.Secure.getInt(
                    context.getContentResolver(),
                    android.provider.Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 1) {
                score -= 5;
                Log.d(TAG, "Security penalty: Unknown sources enabled (-5)");
            }
        }

        // 确保分数在 0-100 范围内
        score = Math.max(0, Math.min(100, score));
        Log.d(TAG, "Final security score: " + score);
        return score;
    }

    /**
     * 获取安全评分等级描述
     */
    public String getSecurityScoreLevel(int score) {
        if (score >= 90) return "非常安全";
        if (score >= 75) return "安全";
        if (score >= 60) return "一般";
        if (score >= 40) return "存在风险";
        return "高风险";
    }

    /**
     * 获取安全评分颜色
     */
    public int getSecurityScoreColor(int score) {
        if (score >= 90) return 0xFF4CAF50; // 绿色
        if (score >= 75) return 0xFF8BC34A; // 浅绿
        if (score >= 60) return 0xFFFF9800; // 橙色
        if (score >= 40) return 0xFFFF5722; // 深橙
        return 0xFFF44336; // 红色
    }

    /**
     * SafetyNet 检测
     * @return SafetyNet 检测结果
     */
    public SafetyNetResult checkSafetyNet() {
        SafetyNetResult result = new SafetyNetResult();

        try {
            // 检测基本完整性
            result.basicIntegrity = checkBasicIntegrity();

            // 检测 CTS 配置文件匹配
            result.ctsProfileMatch = checkCtsProfileMatch();

            // 检测 root 检测绕过状态
            result.rootDetectionBypassed = isRootHidden();

            // 综合判断
            result.passed = result.basicIntegrity && result.ctsProfileMatch;

            result.timestamp = System.currentTimeMillis();

            // 缓存结果
            prefs.putLong("safetynet_last_check", result.timestamp);
            prefs.putBoolean("safetnet_passed", result.passed);

        } catch (Exception e) {
            Log.e(TAG, "Error checking SafetyNet", e);
            result.passed = false;
            result.error = e.getMessage();
        }

        return result;
    }

    /**
     * 获取安全审计报告
     */
    public List<SecurityAuditItem> getSecurityAuditReport() {
        List<SecurityAuditItem> report = new ArrayList<>();

        // SELinux 检查
        SecurityAuditItem selinuxItem = new SecurityAuditItem();
        selinuxItem.category = "SELinux";
        selinuxItem.name = "SELinux 状态";
        selinuxItem.value = getSelinuxStatus();
        selinuxItem.status = "Enforcing".equals(selinuxItem.value) ? "PASS" : "WARN";
        selinuxItem.detail = "Enforcing 模式提供最强的安全保护";
        report.add(selinuxItem);

        // 内核版本检查
        SecurityAuditItem kernelItem = new SecurityAuditItem();
        kernelItem.category = "内核";
        kernelItem.name = "内核版本";
        kernelItem.value = getKernelVersion();
        kernelItem.status = "INFO";
        kernelItem.detail = "保持内核更新有助于修复安全漏洞";
        report.add(kernelItem);

        // 安全补丁检查
        SecurityAuditItem patchItem = new SecurityAuditItem();
        patchItem.category = "系统";
        patchItem.name = "安全补丁级别";
        patchItem.value = getSecurityPatchLevel();
        String patch = patchItem.value;
        if ("Unknown".equals(patch)) {
            patchItem.status = "WARN";
            patchItem.detail = "无法获取安全补丁信息";
        } else {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                java.util.Date patchDate = sdf.parse(patch);
                long sixMonthsAgo = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000;
                patchItem.status = patchDate.getTime() >= sixMonthsAgo ? "PASS" : "WARN";
                patchItem.detail = patchDate.getTime() >= sixMonthsAgo
                    ? "安全补丁在有效期内"
                    : "安全补丁已过期，建议更新系统";
            } catch (Exception e) {
                patchItem.status = "INFO";
                patchItem.detail = "安全补丁: " + patch;
            }
        }
        report.add(patchItem);

        // Root 隐藏检查
        SecurityAuditItem hideItem = new SecurityAuditItem();
        hideItem.category = "Root";
        hideItem.name = "Root 隐藏";
        hideItem.value = isRootHidden() ? "已启用" : "未启用";
        hideItem.status = isRootHidden() ? "PASS" : "WARN";
        hideItem.detail = "启用 Root 隐藏可以防止应用检测到 Root 权限";
        report.add(hideItem);

        // USB 调试检查
        SecurityAuditItem adbItem = new SecurityAuditItem();
        adbItem.category = "开发者";
        adbItem.name = "USB 调试";
        boolean adbEnabled = android.provider.Settings.Global.getInt(
            context.getContentResolver(),
            android.provider.Settings.Global.ADB_ENABLED, 0) == 1;
        adbItem.value = adbEnabled ? "已开启" : "已关闭";
        adbItem.status = adbEnabled ? "WARN" : "PASS";
        adbItem.detail = adbEnabled
            ? "USB 调试可能被恶意软件利用，建议在不使用时关闭"
            : "USB 调试已关闭，安全性较好";
        report.add(adbItem);

        // 构建类型检查
        SecurityAuditItem buildItem = new SecurityAuditItem();
        buildItem.category = "系统";
        buildItem.name = "构建类型";
        buildItem.value = Build.TYPE;
        buildItem.status = "user".equals(Build.TYPE) ? "PASS" : "WARN";
        buildItem.detail = "user 构建类型安全性最高";
        report.add(buildItem);

        return report;
    }

    // ---- 内部辅助方法 ----

    private boolean checkBasicIntegrity() {
        // 简化的基本完整性检查
        // 实际实现应使用 Google Play Integrity API 或 SafetyNet Attestation API
        try {
            // 检查设备是否被篡改的基本指标
            boolean bootloaderUnlocked = new File("/system/bin/su").exists()
                || new File("/sbin/su").exists()
                || new File("/su/bin/su").exists();

            // 如果 root 已隐藏且没有明显的 su 路径，认为基本完整性通过
            return isRootHidden() || !bootloaderUnlocked;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkCtsProfileMatch() {
        // 简化的 CTS 配置文件匹配检查
        // 实际实现应使用 Google Play Integrity API
        try {
            // 检查基本系统完整性
            String buildFingerprint = Build.FINGERPRINT;
            return buildFingerprint != null && !buildFingerprint.isEmpty()
                && !buildFingerprint.contains("test-keys");
        } catch (Exception e) {
            return false;
        }
    }

    private String readFileContent(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    // ---- 数据类 ----

    public static class SafetyNetResult {
        public boolean passed;
        public boolean basicIntegrity;
        public boolean ctsProfileMatch;
        public boolean rootDetectionBypassed;
        public long timestamp;
        public String error;

        public String getSummary() {
            if (error != null) return "检测失败: " + error;
            if (passed) return "SafetyNet 检测通过";
            StringBuilder sb = new StringBuilder("SafetyNet 检测未通过: ");
            List<String> failures = new ArrayList<>();
            if (!basicIntegrity) failures.add("基本完整性");
            if (!ctsProfileMatch) failures.add("CTS 配置文件");
            sb.append(String.join(", ", failures));
            return sb.toString();
        }
    }

    public static class SecurityAuditItem {
        public String category;
        public String name;
        public String value;
        public String status; // PASS, WARN, FAIL, INFO
        public String detail;
    }
}
