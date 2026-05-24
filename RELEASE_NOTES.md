# AuroraSU v1.0.0 Release Notes

## 🎉 Initial Release

我们很高兴发布 AuroraSU 的第一个正式版本！这是一个融合多种先进技术的 Android 内核级 Root 解决方案。

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

## 📦 发布内容

| 文件 | 说明 | 大小 |
|------|------|------|
| `aurorad` | AuroraSU 用户空间守护进程 (x86_64) | ~1.6 MB |
| `aurora.c` | 内核模块源码 | ~1.2 KB |
| `uapi/aurora.h` | 用户空间 API 头文件 | ~3.5 KB |
| `docs/ARCHITECTURE.md` | 架构设计文档 | ~8.5 KB |
| `INSTALL.md` | 安装指南 | ~2.3 KB |
| `README.md` | 项目说明 | ~5.8 KB |

## 🔧 系统要求

- Android 10+ (API 29+)
- 内核版本 4.14+
- 架构: arm64-v8a (推荐) 或 x86_64
- 已解锁 Bootloader

## 📖 安装方法

### 方式一: 使用 Kernel Flasher (推荐)
1. 下载 `AuroraSU-v1.0.0-release.zip`
2. 打开 Kernel Flasher 应用
3. 选择下载的 zip 文件
4. 点击 Flash 并等待完成
5. 重启系统
6. 安装 AuroraSU Manager APK

### 方式二: 手动修补 Boot
```bash
# 提取 boot 镜像
adb shell su -c "dd if=/dev/block/by-name/boot of=/sdcard/boot.img"
adb pull /sdcard/boot.img

# 使用 aurorad 修补
./aurorad patch-boot --input boot.img --output aurora-boot.img

# 刷入修补后的镜像
adb shell su -c "dd if=/sdcard/aurora-boot.img of=/dev/block/by-name/boot"
```

## 🙏 致谢

AuroraSU 的诞生离不开以下项目的启发和支持：

- [KernelSU](https://github.com/tiann/KernelSU) - 原版 KernelSU，提供核心架构
- [SukiSU Ultra](https://github.com/ShirkNeko/SukiSU-Ultra) - KPM 功能参考
- [KernelSU Next](https://github.com/KernelSU-Next/KernelSU-Next) - WebUI API 设计
- [Wild KSU](https://github.com/WildKernels/Wild_KSU) - Manual Hook 技术

## 🐛 已知问题

- Android Manager APK 需要手动构建（尚未预编译）
- 内核模块需要针对具体设备内核版本编译
- 某些设备可能需要额外的 SELinux 策略调整

## 📞 支持

- **GitHub Issues**: https://github.com/ABI-ZTROS/AuroraSU/issues
- **Telegram**: @AuroraSU

## 📜 许可证

GPL-2.0-or-later

---

**Full Changelog**: https://github.com/ABI-ZTROS/AuroraSU/commits/v1.0.0
