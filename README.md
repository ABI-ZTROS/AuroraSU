<div align="center">

<img src="logo.jpg" alt="ZTR_OS SU" width="120">

# ZTR_OS SU

**Kernel-level Root Solution for Android**

基于 SuperKey 密钥认证的 Android 内核级 Root 方案

[![Build Manager](https://github.com/ABI-ZTROS/AuroraSU/actions/workflows/build-manager.yml/badge.svg)](https://github.com/ABI-ZTROS/AuroraSU/actions/workflows/build-manager.yml)

</div>

## 这是什么

ZTR_OS SU 是一个 Android 内核级 SU 管理器，提供 Root 权限管理、模块化扩展、SELinux 策略控制等功能。

## 核心特性

- **SuperKey 密钥认证** — 通过密钥而非证书哈希验证 Manager 身份，无需为每次更新重新编译内核
- **模块系统** — 支持 Magisk/KernelSU 格式模块的热插拔挂载
- **SELinux 管理** — 快速切换 Enforcing/Permissive 模式
- **多主题 UI** — 支持 ZTR_OS SU / Material You / MIUI X 三种界面风格
- **内核功能增强** — SU 兼容模式、内核 Umount、AVC Spoof
- **仪表盘** — 系统信息概览、快捷操作、模块统计

## 工作原理

ZTR_OS SU 由两部分组成：

1. **内核模块** — 以 LKM 形式加载到 Android 内核，提供 Root 权限授予、模块挂载、SELinux 策略修改等核心能力
2. **Manager 应用** — 用户界面，通过 SuperCall 系统调用与内核模块通信

认证流程：Manager 通过设置 SuperKey → 内核验证密钥 → 授权通信

## 构建产物

从 [Actions](https://github.com/ABI-ZTROS/AuroraSU/actions) 页面下载最新的 Manager APK。

## 许可证

GPL-3.0
