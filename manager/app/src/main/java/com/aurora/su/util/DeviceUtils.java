/*
 * AuroraSU - Device Information Utilities
 *
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.os.Build;
import android.util.Log;

/**
 * DeviceUtils - 设备信息工具类
 * 提供设备型号、Android 版本、API 级别、CPU 架构等设备信息查询
 */
public class DeviceUtils {

    private static final String TAG = "DeviceUtils";

    /**
     * 获取设备型号
     * @return 设备型号字符串
     */
    public static String getDeviceModel() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;

        if (manufacturer != null && model != null) {
            // 如果型号已包含厂商名，避免重复
            if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
                return capitalize(model);
            }
            return capitalize(manufacturer) + " " + model;
        }

        return Build.PRODUCT != null ? Build.PRODUCT : "Unknown";
    }

    /**
     * 获取 Android 版本
     * @return Android 版本字符串
     */
    public static String getAndroidVersion() {
        String version = Build.VERSION.RELEASE;
        if (version != null && !version.isEmpty()) {
            return "Android " + version;
        }
        return "Unknown";
    }

    /**
     * 获取 API 级别
     * @return API 级别数字
     */
    public static int getApiLevel() {
        return Build.VERSION.SDK_INT;
    }

    /**
     * 获取 API 级别名称
     * @return API 级别名称字符串
     */
    public static String getApiLevelName() {
        switch (Build.VERSION.SDK_INT) {
            case Build.VERSION_CODES.UPSIDE_DOWN_CAKE: return "API 34 (Upside Down Cake)";
            case Build.VERSION_CODES.TIRAMISU: return "API 33 (Tiramisu)";
            case Build.VERSION_CODES.S_V2: return "API 33 (S v2)";
            case Build.VERSION_CODES.S: return "API 31 (S)";
            case Build.VERSION_CODES.R: return "API 30 (R)";
            case Build.VERSION_CODES.Q: return "API 29 (Q)";
            case Build.VERSION_CODES.P: return "API 28 (P)";
            case Build.VERSION_CODES.O_MR1: return "API 27 (Oreo MR1)";
            case Build.VERSION_CODES.O: return "API 26 (Oreo)";
            case Build.VERSION_CODES.N_MR1: return "API 25 (Nougat MR1)";
            case Build.VERSION_CODES.N: return "API 24 (Nougat)";
            case Build.VERSION_CODES.M: return "API 23 (Marshmallow)";
            case Build.VERSION_CODES.LOLLIPOP_MR1: return "API 22 (Lollipop MR1)";
            case Build.VERSION_CODES.LOLLIPOP: return "API 21 (Lollipop)";
            default:
                if (Build.VERSION.SDK_INT >= 35) return "API " + Build.VERSION.SDK_INT + " (Future)";
                return "API " + Build.VERSION.SDK_INT;
        }
    }

    /**
     * 获取 CPU 架构
     * @return CPU 架构字符串
     */
    public static String getArchitecture() {
        // 方法1: 通过 Build.SUPPORTED_ABIS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String[] abis = Build.SUPPORTED_ABIS;
            if (abis != null && abis.length > 0) {
                return formatArch(abis[0]);
            }
        }

        // 方法2: 通过 Build.CPU_ABI
        String cpuAbi = Build.CPU_ABI;
        if (cpuAbi != null && !cpuAbi.isEmpty()) {
            return formatArch(cpuAbi);
        }

        // 方法3: 通过读取 /proc/cpuinfo
        String cpuInfo = readCpuInfo();
        if (cpuInfo != null) {
            if (cpuInfo.contains("aarch64") || cpuInfo.contains("ARMv8")) {
                return "arm64-v8a";
            }
            if (cpuInfo.contains("ARMv7")) {
                return "armeabi-v7a";
            }
            if (cpuInfo.contains("x86_64")) {
                return "x86_64";
            }
            if (cpuInfo.contains("x86")) {
                return "x86";
            }
        }

        return "Unknown";
    }

    /**
     * 获取所有支持的 ABI
     */
    public static String[] getSupportedAbis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Build.SUPPORTED_ABIS;
        }
        return new String[]{Build.CPU_ABI};
    }

    /**
     * 获取内核版本
     * @return 内核版本字符串
     */
    public static String getKernelVersion() {
        return getKernelVersion(null);
    }

    /**
     * 获取内核版本（通过 Context 获取）
     * @param context 上下文（可为 null）
     * @return 内核版本字符串
     */
    public static String getKernelVersion(Context context) {
        try {
            File versionFile = new File("/proc/version");
            if (versionFile.exists()) {
                String content = readFileContent(versionFile).trim();
                // 解析内核版本号
                Pattern pattern = Pattern.compile("Linux version (\\S+)");
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
                return content;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting kernel version", e);
        }

        // 回退方案
        try {
            Process process = Runtime.getRuntime().exec("uname -r");
            BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
            );
            String line = reader.readLine();
            reader.close();
            if (line != null && !line.isEmpty()) {
                return line.trim();
            }
        } catch (Exception e) {
            // 忽略
        }

        return "Unknown";
    }

    /**
     * 获取完整内核信息
     */
    public static String getFullKernelInfo() {
        try {
            File versionFile = new File("/proc/version");
            if (versionFile.exists()) {
                return readFileContent(versionFile).trim();
            }
        } catch (Exception e) {
            // 忽略
        }
        return "Unknown";
    }

    /**
     * 获取安全补丁级别
     * @return 安全补丁日期字符串
     */
    public static String getSecurityPatchLevel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String patch = Build.VERSION.SECURITY_PATCH;
            if (patch != null && !patch.isEmpty()) {
                return patch;
            }
        }

        // 回退方案：通过系统属性
        try {
            Process process = Runtime.getRuntime().exec("getprop ro.build.version.security_patch");
            BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
            );
            String line = reader.readLine();
            reader.close();
            if (line != null && !line.trim().isEmpty()) {
                return line.trim();
            }
        } catch (Exception e) {
            // 忽略
        }

        return "Unknown";
    }

    /**
     * 获取构建指纹
     */
    public static String getBuildFingerprint() {
        return Build.FINGERPRINT;
    }

    /**
     * 获取构建类型
     */
    public static String getBuildType() {
        return Build.TYPE;
    }

    /**
     * 获取主板信息
     */
    public static String getBoard() {
        return Build.BOARD;
    }

    /**
     * 获取硬件信息
     */
    public static String getHardware() {
        return Build.HARDWARE;
    }

    /**
     * 获取设备显示名称
     */
    public static String getDeviceName(Context context) {
        if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            return android.provider.Settings.Global.getString(
                context.getContentResolver(),
                "device_name"
            );
        }
        return Build.MODEL;
    }

    /**
     * 获取 CPU 信息
     */
    public static String getCpuInfo() {
        return readCpuInfo();
    }

    /**
     * 获取 CPU 核心数
     */
    public static int getCpuCoreCount() {
        try {
            File cpuDir = new File("/sys/devices/system/cpu");
            if (cpuDir.exists() && cpuDir.isDirectory()) {
                File[] cores = cpuDir.listFiles(new java.io.FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return file.getName().startsWith("cpu")
                            && Character.isDigit(file.getName().charAt(3));
                    }
                });
                return cores != null ? cores.length : 1;
            }
        } catch (Exception e) {
            // 忽略
        }

        // 回退方案
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * 获取总内存大小
     */
    public static long getTotalMemory() {
        try {
            File memInfo = new File("/proc/meminfo");
            if (memInfo.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(memInfo));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("MemTotal:")) {
                        reader.close();
                        // 格式: MemTotal:        XXXX kB
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 2) {
                            return Long.parseLong(parts[1]) * 1024; // 转换为字节
                        }
                    }
                }
                reader.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting total memory", e);
        }

        // 回退方案
        return Runtime.getRuntime().maxMemory();
    }

    /**
     * 格式化内存大小
     */
    public static String formatMemory(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ---- 内部辅助方法 ----

    private static String formatArch(String abi) {
        switch (abi) {
            case "arm64-v8a": return "ARM64 (arm64-v8a)";
            case "armeabi-v7a": return "ARM32 (armeabi-v7a)";
            case "armeabi": return "ARM (armeabi)";
            case "x86_64": return "x86_64";
            case "x86": return "x86";
            case "mips64": return "MIPS64";
            case "mips": return "MIPS";
            default: return abi;
        }
    }

    private static String readCpuInfo() {
        try {
            File cpuInfo = new File("/proc/cpuinfo");
            if (cpuInfo.exists()) {
                return readFileContent(cpuInfo);
            }
        } catch (Exception e) {
            // 忽略
        }
        return null;
    }

    private static String readFileContent(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
