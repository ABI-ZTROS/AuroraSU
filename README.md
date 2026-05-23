# AuroraSU 🌌

**Advanced Universal Root Overlay for Android**

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/AuroraSU/AuroraSU/releases)
[![License](https://img.shields.io/badge/license-GPL--2.0--or--later-green.svg)](LICENSE)
[![Android](https://img.shields.io/badge/android-10%2B-brightgreen.svg)](https://android.com)

AuroraSU 是一个融合多种先进技术的 Android 内核级 Root 解决方案，结合 KernelSU 的稳定性、SukiSU Ultra 的 KPM 功能、KernelSU Next 的现代化 API 和 Wild KSU 的 Manual Hook 技术，打造**稳定优先、功能丰富、UI 精美**的 Root 体验。

## ✨ 核心特性

### 🛡️ 极致稳定性
- **双模式 Hook 系统**: 自动选择 kprobe 或 manual hook，支持 hybrid 冗余模式
- **冗余保护系统**: 多重健康监控，自动故障恢复
- **安全模式**: 检测到问题时自动进入安全模式，保护系统

### 🚀 强大功能
- **KPM 2.0**: 改进的内核补丁模块系统，支持热补丁和签名验证
- **SUSFS 深度集成**: 一键隐藏 Root 痕迹，智能反检测
- **Material You 3.0**: 动态主题，自适应色彩

### 🎨 精美界面
- **现代化 UI**: 基于 Material Design 3
- **流畅动画**: 精心设计的过渡效果
- **自适应布局**: 完美支持手机和平板

## 📋 系统要求

| 项目 | 要求 |
|------|------|
| Android 版本 | 10+ (API 29+) |
| 内核版本 | 4.14+ |
| 架构 | arm64-v8a (推荐), x86_64 |
| GKI | 支持 GKI 2.0 |

## 🔧 安装指南

### 前提条件
1. 已解锁 Bootloader
2. 已安装自定义 Recovery (如 TWRP)
3. 已备份原始 Boot 镜像

### 安装步骤

#### 方法 1: 使用 Kernel Flasher
1. 下载 AuroraSU 内核镜像
2. 打开 Kernel Flasher 应用
3. 选择下载的内核镜像
4. 点击 Flash 并等待完成
5. 重启系统
6. 安装 AuroraSU Manager

#### 方法 2: 手动修补 Boot
```bash
# 提取 boot 镜像
adb shell su -c "dd if=/dev/block/by-name/boot of=/sdcard/boot.img"
adb pull /sdcard/boot.img

# 使用 aurorad 修补
aurorad patch-boot --input boot.img --output aurora-boot.img

# 刷入修补后的镜像
adb shell su -c "dd if=/sdcard/aurora-boot.img of=/dev/block/by-name/boot"
```

## 📖 使用说明

### 授予 Root 权限
1. 打开 AuroraSU Manager
2. 在主页查看 Root 状态
3. 应用请求 Root 时，在弹窗中选择"允许"

### 安装模块
1. 进入"模块"页面
2. 点击右下角"+"按钮
3. 选择模块 ZIP 文件
4. 等待安装完成并重启

### 配置应用 Profile
1. 进入"配置"页面
2. 选择要配置的应用
3. 设置 UID、GID、Capabilities
4. 保存配置

## 🏗️ 项目结构

```
AuroraSU/
├── kernel/                 # 内核模块源码
│   ├── core/              # 核心初始化
│   ├── hook/              # 钩子系统
│   ├── kpm/               # KPM 支持
│   ├── redundancy/        # 冗余保护
│   ├── selinux/           # SELinux 管理
│   └── supercall/         # 超级调用接口
├── userspace/             # 用户空间
│   └── aurorad/           # 守护进程 (Rust)
├── manager/               # Android 管理应用
│   └── app/               # Kotlin + Compose
├── uapi/                  # 用户空间 API 头文件
└── docs/                  # 文档
```

## 🔬 技术亮点

### 双模式 Hook 系统
```c
// 自动检测并选择最佳 Hook 模式
if (gki_device && kprobe_available) {
    use_kprobe_mode();      // GKI 设备
} else if (manual_hook_available) {
    use_manual_hook_mode(); // 非 GKI 设备
} else {
    use_hybrid_mode();      // 冗余模式
}
```

### KPM 2.0 签名验证
```c
struct aurora_kpm_header {
    u64 magic;              // AURORA_KPM_MAGIC
    u32 version;            // 版本 2
    u8 signature[64];       // Ed25519 签名
    // ...
};
```

### 冗余保护状态机
```
Healthy → Degraded → Critical → Failed
   ↑________↓__________↓__________↓
         (自动恢复尝试)
```

## 🤝 贡献指南

我们欢迎所有形式的贡献！

### 提交 PR
1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

### 代码规范
- 内核代码: 遵循 Linux Kernel Coding Style
- Rust 代码: 使用 `cargo fmt` 和 `cargo clippy`
- Kotlin 代码: 遵循 Kotlin Coding Conventions

## 📜 开源协议

本项目采用 **GPL-2.0-or-later** 协议开源。

```
Copyright (C) 2026 AuroraSU Team

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.
```

## 🙏 致谢

AuroraSU 的诞生离不开以下项目的启发和支持：

- [KernelSU](https://github.com/tiann/KernelSU) - 原版 KernelSU，提供核心架构
- [SukiSU Ultra](https://github.com/ShirkNeko/SukiSU-Ultra) - KPM 功能参考
- [KernelSU Next](https://github.com/KernelSU-Next/KernelSU-Next) - WebUI API 设计
- [Wild KSU](https://github.com/WildKernels/Wild_KSU) - Manual Hook 技术

## 📞 联系我们

- **Telegram**: [@AuroraSU](https://t.me/AuroraSU)
- **GitHub Issues**: [AuroraSU Issues](https://github.com/AuroraSU/AuroraSU/issues)
- **Email**: dev@aurorasu.org

## ⭐ 星标历史

[![Star History Chart](https://api.star-history.com/svg?repos=AuroraSU/AuroraSU&type=Date)](https://star-history.com/#AuroraSU/AuroraSU&Date)

---

<p align="center">
  <b>Made with ❤️ by AuroraSU Team</b>
</p>

<p align="center">
  <img src="docs/assets/aurora_logo.png" alt="AuroraSU Logo" width="120">
</p>
