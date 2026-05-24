/*
 * AuroraSU - File Utilities
 *
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import android.util.Log;

/**
 * FileUtils - 文件工具类
 * 提供文件读写、复制、删除等常用文件操作
 */
public class FileUtils {

    private static final String TAG = "FileUtils";
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_FILE_SIZE = 50 * 1024 * 1024; // 最大读取 50MB

    /**
     * 读取文件内容为字符串
     * @param filePath 文件路径
     * @return 文件内容，失败返回 null
     */
    public static String readFile(String filePath) {
        return readFile(filePath, StandardCharsets.UTF_8);
    }

    /**
     * 读取文件内容为字符串（指定编码）
     * @param filePath 文件路径
     * @param charset 字符编码
     * @return 文件内容，失败返回 null
     */
    public static String readFile(String filePath, Charset charset) {
        if (filePath == null || filePath.isEmpty()) {
            Log.e(TAG, "readFile: empty file path");
            return null;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            Log.w(TAG, "readFile: file not found: " + filePath);
            return null;
        }

        if (file.length() > MAX_FILE_SIZE) {
            Log.w(TAG, "readFile: file too large: " + filePath + " (" + file.length() + " bytes)");
            return null;
        }

        try {
            StringBuilder sb = new StringBuilder((int) file.length());
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), charset)
            );
            char[] buffer = new char[BUFFER_SIZE];
            int len;
            while ((len = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, len);
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            Log.e(TAG, "Error reading file: " + filePath, e);
            return null;
        }
    }

    /**
     * 读取文件内容为字节数组
     * @param filePath 文件路径
     * @return 字节数组，失败返回 null
     */
    public static byte[] readFileBytes(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }

        try {
            byte[] data = new byte[(int) file.length()];
            FileInputStream fis = new FileInputStream(file);
            fis.read(data);
            fis.close();
            return data;
        } catch (IOException e) {
            Log.e(TAG, "Error reading file bytes: " + filePath, e);
            return null;
        }
    }

    /**
     * 写入字符串到文件
     * @param filePath 文件路径
     * @param content 要写入的内容
     * @return true 表示写入成功
     */
    public static boolean writeFile(String filePath, String content) {
        return writeFile(filePath, content, false, StandardCharsets.UTF_8);
    }

    /**
     * 写入字符串到文件
     * @param filePath 文件路径
     * @param content 要写入的内容
     * @param append 是否追加
     * @return true 表示写入成功
     */
    public static boolean writeFile(String filePath, String content, boolean append) {
        return writeFile(filePath, content, append, StandardCharsets.UTF_8);
    }

    /**
     * 写入字符串到文件（指定编码和追加模式）
     * @param filePath 文件路径
     * @param content 要写入的内容
     * @param append 是否追加
     * @param charset 字符编码
     * @return true 表示写入成功
     */
    public static boolean writeFile(String filePath, String content, boolean append, Charset charset) {
        if (filePath == null || filePath.isEmpty()) {
            Log.e(TAG, "writeFile: empty file path");
            return false;
        }

        try {
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file, append), charset)
            );
            writer.write(content);
            writer.flush();
            writer.close();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error writing file: " + filePath, e);
            return false;
        }
    }

    /**
     * 写入字节数组到文件
     * @param filePath 文件路径
     * @param data 字节数组
     * @return true 表示写入成功
     */
    public static boolean writeFileBytes(String filePath, byte[] data) {
        if (filePath == null || filePath.isEmpty() || data == null) {
            return false;
        }

        try {
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.flush();
            fos.close();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error writing file bytes: " + filePath, e);
            return false;
        }
    }

    /**
     * 复制文件
     * @param srcPath 源文件路径
     * @param destPath 目标文件路径
     * @return true 表示复制成功
     */
    public static boolean copyFile(String srcPath, String destPath) {
        if (srcPath == null || destPath == null) {
            Log.e(TAG, "copyFile: null path");
            return false;
        }

        File srcFile = new File(srcPath);
        if (!srcFile.exists() || !srcFile.isFile()) {
            Log.e(TAG, "copyFile: source not found: " + srcPath);
            return false;
        }

        try {
            File destFile = new File(destPath);
            File parentDir = destFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            FileInputStream fis = new FileInputStream(srcFile);
            FileOutputStream fos = new FileOutputStream(destFile);

            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }

            fos.flush();
            fos.close();
            fis.close();

            // 保持文件权限
            destFile.setExecutable(srcFile.canExecute());
            destFile.setReadable(srcFile.canRead());
            destFile.setWritable(srcFile.canWrite());

            Log.d(TAG, "File copied: " + srcPath + " -> " + destPath);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error copying file: " + srcPath + " -> " + destPath, e);
            return false;
        }
    }

    /**
     * 复制目录
     * @param srcDir 源目录
     * @param destDir 目标目录
     * @return true 表示复制成功
     */
    public static boolean copyDirectory(String srcDir, String destDir) {
        File src = new File(srcDir);
        if (!src.exists() || !src.isDirectory()) {
            return false;
        }

        File dest = new File(destDir);
        dest.mkdirs();

        File[] files = src.listFiles();
        if (files == null) return true;

        boolean success = true;
        for (File file : files) {
            String destPath = destDir + File.separator + file.getName();
            if (file.isDirectory()) {
                success = copyDirectory(file.getAbsolutePath(), destPath) && success;
            } else {
                success = copyFile(file.getAbsolutePath(), destPath) && success;
            }
        }
        return success;
    }

    /**
     * 删除文件
     * @param filePath 文件路径
     * @return true 表示删除成功
     */
    public static boolean deleteFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return true; // 文件不存在视为成功
        }

        if (file.isFile()) {
            boolean deleted = file.delete();
            if (deleted) {
                Log.d(TAG, "File deleted: " + filePath);
            } else {
                Log.w(TAG, "Failed to delete file: " + filePath);
            }
            return deleted;
        }

        return false;
    }

    /**
     * 递归删除文件或目录
     * @param path 文件或目录路径
     * @return true 表示删除成功
     */
    public static boolean deleteRecursive(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        return deleteRecursive(new File(path));
    }

    /**
     * 递归删除文件或目录
     * @param file 文件或目录
     * @return true 表示删除成功
     */
    public static boolean deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return true;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) {
                        Log.w(TAG, "Failed to delete: " + child.getAbsolutePath());
                    }
                }
            }
        }

        boolean deleted = file.delete();
        if (deleted) {
            Log.d(TAG, "Deleted: " + file.getAbsolutePath());
        } else {
            Log.w(TAG, "Failed to delete: " + file.getAbsolutePath());
        }
        return deleted;
    }

    /**
     * 获取文件大小（格式化字符串）
     * @param filePath 文件路径
     * @return 格式化的文件大小
     */
    public static String getFormattedFileSize(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) return "0 B";
        return formatFileSize(file.length());
    }

    /**
     * 格式化文件大小
     * @param size 字节数
     * @return 格式化的字符串
     */
    public static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
        return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * 检查文件是否存在
     */
    public static boolean exists(String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        return new File(filePath).exists();
    }

    /**
     * 创建目录（包括父目录）
     */
    public static boolean mkdirs(String dirPath) {
        if (dirPath == null || dirPath.isEmpty()) return false;
        File dir = new File(dirPath);
        return dir.mkdirs();
    }

    /**
     * 获取文件扩展名
     */
    public static String getFileExtension(String filePath) {
        if (filePath == null) return "";
        int dotIndex = filePath.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filePath.length() - 1) {
            return filePath.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 计算目录大小
     */
    public static long getDirectorySize(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) return 0;
        return calculateDirSize(dir);
    }

    private static long calculateDirSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    size += calculateDirSize(file);
                } else {
                    size += file.length();
                }
            }
        }
        return size;
    }
}
