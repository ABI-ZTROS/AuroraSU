# AuroraSU v1.0.0 安装指南

## 系统要求

- Android 10+ (API 29+)
- 内核版本 4.14+
- 架构: arm64-v8a (推荐) 或 x86_64
- 已解锁 Bootloader
- 已安装自定义 Recovery (推荐 TWRP)

## 安装方式

### 方式一: 使用 Kernel Flasher (推荐)

1. 下载 `aurora-boot.zip`
2. 打开 Kernel Flasher 应用
3. 选择下载的 zip 文件
4. 点击 Flash 并等待完成
5. 重启系统
6. 安装 AuroraSU Manager APK

### 方式二: 手动修补 Boot

```bash
# 1. 提取 boot 镜像
adb shell su -c "dd if=/dev/block/by-name/boot of=/sdcard/boot.img"
adb pull /sdcard/boot.img

# 2. 使用 aurorad 修补
./aurorad patch-boot --input boot.img --output aurora-boot.img

# 3. 刷入修补后的镜像
adb shell su -c "dd if=/sdcard/aurora-boot.img of=/dev/block/by-name/boot"
```

### 方式三: 直接刷入内核模块 (高级用户)

```bash
# 需要 root 权限
adb shell
su
insmod /path/to/aurora.ko
```

## 文件说明

| 文件 | 说明 |
|------|------|
| `aurorad` | AuroraSU 用户空间守护进程 (x86_64) |
| `aurora.c` | 内核模块源码 |
| `uapi/aurora.h` | 用户空间 API 头文件 |
| `docs/ARCHITECTURE.md` | 架构设计文档 |

## 验证安装

安装完成后，打开 AuroraSU Manager 应用，查看主页状态卡片：

- ✅ **绿色**: 工作正常
- ⚠️ **橙色**: 需要更新
- ❌ **红色**: 安装失败

## 卸载

```bash
# 恢复原始 boot 镜像
adb shell su -c "dd if=/sdcard/boot-backup.img of=/dev/block/by-name/boot"
```

## 故障排除

### 无法获取 Root
1. 检查内核模块是否加载: `lsmod | grep aurora`
2. 检查 aurorad 是否运行: `ps | grep aurorad`
3. 查看日志: `dmesg | grep AuroraSU`

### 进入安全模式
如果系统不稳定，AuroraSU 会自动进入安全模式：
1. 打开 Manager 应用
2. 点击"恢复"按钮
3. 或运行: `aurorad recovery`

## 支持

- GitHub Issues: https://github.com/ABI-ZTROS/AuroraSU/issues
- Telegram: @AuroraSU

## 许可证

GPL-2.0-or-later
