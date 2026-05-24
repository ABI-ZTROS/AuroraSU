/*
 * AuroraSU - Shell Command Utilities
 *
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.util;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import android.util.Log;

/**
 * ShellUtils - Shell 命令工具类
 * 提供普通命令和 root 命令的执行能力，支持超时控制和输出捕获
 */
public class ShellUtils {

    private static final String TAG = "ShellUtils";
    private static final String SU_PATH = "su";
    private static final String SH_PATH = "/system/bin/sh";
    private static final int DEFAULT_TIMEOUT = 30000; // 30 秒超时

    /**
     * 命令执行结果
     */
    public static class CommandResult {
        public boolean success;
        public int exitCode;
        public String output;
        public String error;
        public long duration; // 执行时间（毫秒）

        public CommandResult() {
            this.success = false;
            this.exitCode = -1;
            this.output = "";
            this.error = "";
            this.duration = 0;
        }

        public boolean isSuccessful() {
            return success && exitCode == 0;
        }

        public List<String> getOutputLines() {
            List<String> lines = new ArrayList<>();
            if (output != null && !output.isEmpty()) {
                String[] split = output.split("\n");
                for (String line : split) {
                    if (!line.trim().isEmpty()) {
                        lines.add(line.trim());
                    }
                }
            }
            return lines;
        }
    }

    /**
     * 以普通用户执行 shell 命令
     * @param command 要执行的命令
     * @return 命令执行结果
     */
    public CommandResult runCommand(String command) {
        return runCommand(command, DEFAULT_TIMEOUT);
    }

    /**
     * 以普通用户执行 shell 命令（带超时）
     * @param command 要执行的命令
     * @param timeoutMs 超时时间（毫秒）
     * @return 命令执行结果
     */
    public CommandResult runCommand(String command, int timeoutMs) {
        if (command == null || command.trim().isEmpty()) {
            CommandResult result = new CommandResult();
            result.error = "Empty command";
            return result;
        }

        long startTime = System.currentTimeMillis();
        CommandResult result = new CommandResult();

        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{SH_PATH, "-c", command});

            // 读取标准输出
            StreamGobbler outputGobbler = new StreamGobbler(
                process.getInputStream(), false);
            // 读取错误输出
            StreamGobbler errorGobbler = new StreamGobbler(
                process.getErrorStream(), true);

            outputGobbler.start();
            errorGobbler.start();

            // 等待命令完成
            boolean finished = process.waitFor(timeoutMs,
                java.util.concurrent.TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                result.error = "Command timed out after " + timeoutMs + "ms";
                result.exitCode = -1;
                Log.w(TAG, "Command timed out: " + command);
                return result;
            }

            outputGobbler.join(1000);
            errorGobbler.join(1000);

            result.exitCode = process.exitValue();
            result.output = outputGobbler.getOutput();
            result.error = errorGobbler.getOutput();
            result.success = (result.exitCode == 0);

        } catch (IOException e) {
            result.error = "IOException: " + e.getMessage();
            Log.e(TAG, "Error running command: " + command, e);
        } catch (InterruptedException e) {
            result.error = "InterruptedException: " + e.getMessage();
            Thread.currentThread().interrupt();
            Log.e(TAG, "Command interrupted: " + command, e);
        } catch (Exception e) {
            result.error = "Exception: " + e.getMessage();
            Log.e(TAG, "Error running command: " + command, e);
        } finally {
            if (process != null) {
                process.destroy();
            }
            result.duration = System.currentTimeMillis() - startTime;
        }

        return result;
    }

    /**
     * 以 root 权限执行 shell 命令
     * @param command 要执行的命令
     * @return 命令执行结果
     */
    public CommandResult runRootCommand(String command) {
        return runRootCommand(command, DEFAULT_TIMEOUT);
    }

    /**
     * 以 root 权限执行 shell 命令（带超时）
     * @param command 要执行的命令
     * @param timeoutMs 超时时间（毫秒）
     * @return 命令执行结果
     */
    public CommandResult runRootCommand(String command, int timeoutMs) {
        if (command == null || command.trim().isEmpty()) {
            CommandResult result = new CommandResult();
            result.error = "Empty command";
            return result;
        }

        long startTime = System.currentTimeMillis();
        CommandResult result = new CommandResult();

        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec(SU_PATH);
            os = new DataOutputStream(process.getOutputStream());

            // 写入命令
            os.writeBytes(command + "\n");
            os.flush();

            // 退出 su
            os.writeBytes("exit\n");
            os.flush();

            // 读取输出
            StreamGobbler outputGobbler = new StreamGobbler(
                process.getInputStream(), false);
            StreamGobbler errorGobbler = new StreamGobbler(
                process.getErrorStream(), true);

            outputGobbler.start();
            errorGobbler.start();

            boolean finished = process.waitFor(timeoutMs,
                java.util.concurrent.TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                result.error = "Root command timed out after " + timeoutMs + "ms";
                result.exitCode = -1;
                Log.w(TAG, "Root command timed out: " + command);
                return result;
            }

            outputGobbler.join(1000);
            errorGobbler.join(1000);

            result.exitCode = process.exitValue();
            result.output = outputGobbler.getOutput();
            result.error = errorGobbler.getOutput();
            result.success = (result.exitCode == 0);

            if (result.success) {
                Log.d(TAG, "Root command succeeded: " + command);
            } else {
                Log.w(TAG, "Root command failed (exit=" + result.exitCode + "): " + command);
            }

        } catch (IOException e) {
            result.error = "IOException: " + e.getMessage();
            Log.e(TAG, "Error running root command: " + command, e);
        } catch (InterruptedException e) {
            result.error = "InterruptedException: " + e.getMessage();
            Thread.currentThread().interrupt();
            Log.e(TAG, "Root command interrupted: " + command, e);
        } catch (Exception e) {
            result.error = "Exception: " + e.getMessage();
            Log.e(TAG, "Error running root command: " + command, e);
        } finally {
            if (os != null) {
                try { os.close(); } catch (IOException ignored) {}
            }
            if (process != null) {
                process.destroy();
            }
            result.duration = System.currentTimeMillis() - startTime;
        }

        return result;
    }

    /**
     * 执行多条 root 命令
     * @param commands 命令列表
     * @return 最后一条命令的执行结果
     */
    public CommandResult runRootCommands(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            CommandResult result = new CommandResult();
            result.error = "Empty command list";
            return result;
        }

        StringBuilder sb = new StringBuilder();
        for (String cmd : commands) {
            sb.append(cmd).append("\n");
        }

        return runRootCommand(sb.toString());
    }

    /**
     * 检测 root 是否可用
     * @return true 表示 root 可用
     */
    public boolean isRootAvailable() {
        // 方法1: 检查常见 su 路径
        String[] suPaths = {
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/vendor/bin/su",
            "/magisk/.core/bin/su"
        };

        for (String path : suPaths) {
            if (new File(path).exists()) {
                Log.d(TAG, "Found su binary at: " + path);
                return true;
            }
        }

        // 方法2: 尝试执行 which su
        CommandResult result = runCommand("which su");
        if (result.success && result.output != null && !result.output.trim().isEmpty()) {
            Log.d(TAG, "Found su via which: " + result.output.trim());
            return true;
        }

        // 方法3: 尝试执行 su -c id
        result = runRootCommand("id", 5000);
        if (result.success && result.output != null && result.output.contains("uid=0")) {
            Log.d(TAG, "Root confirmed via su -c id");
            return true;
        }

        Log.d(TAG, "Root not available");
        return false;
    }

    /**
     * 获取 su 二进制路径
     * @return su 路径，未找到返回 null
     */
    public String getSuPath() {
        String[] suPaths = {
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/vendor/bin/su",
            "/magisk/.core/bin/su"
        };

        for (String path : suPaths) {
            if (new File(path).exists()) {
                return path;
            }
        }

        // 尝试 which
        CommandResult result = runCommand("which su");
        if (result.success && result.output != null && !result.output.trim().isEmpty()) {
            return result.output.trim();
        }

        return null;
    }

    /**
     * 检查指定命令是否可用
     */
    public boolean isCommandAvailable(String command) {
        CommandResult result = runCommand("which " + command);
        return result.success && result.output != null && !result.output.trim().isEmpty();
    }

    /**
     * 获取进程列表
     */
    public List<String> getProcessList() {
        CommandResult result = runCommand("ps -A");
        if (result.success && result.output != null) {
            return result.getOutputLines();
        }
        return new ArrayList<>();
    }

    /**
     * 获取指定进程的 PID
     */
    public int getProcessPid(String processName) {
        CommandResult result = runCommand("pidof " + processName);
        if (result.success && result.output != null) {
            try {
                return Integer.parseInt(result.output.trim().split("\\s+")[0]);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    // ---- 内部辅助类 ----

    /**
     * 流读取器，用于异步读取进程输出
     */
    private static class StreamGobbler extends Thread {
        private final java.io.InputStream is;
        private final boolean isError;
        private final StringBuilder output = new StringBuilder();

        StreamGobbler(java.io.InputStream is, boolean isError) {
            this.is = is;
            this.isError = isError;
        }

        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                reader.close();
            } catch (IOException e) {
                if (isError) {
                    Log.e(TAG, "Error reading error stream", e);
                }
            }
        }

        String getOutput() {
            return output.toString().trim();
        }
    }
}
