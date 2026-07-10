# ZTR_OS SU — Code Wiki

> **项目全称**：ZTR_OS SU（仓库 `ZTR-OS/ZTR_OS_SU`，又称 AuroraSU）
> **定位**：基于 SuperKey 密钥认证的 Android 内核级 Root 方案，衍生自 KernelSU，融合 SukiSU-Ultra 的部分能力。
> **许可证**：GPL-3.0（内核模块为 GPL-2.0-or-later）
> **语言构成**：C（内核模块）、Kotlin/Java（Android Manager App）、Rust（userspace daemon `ksud`）、Shell/Python（脚本与 CI）

---

## 目录

1. [项目总览](#1-项目总览)
2. [整体架构](#2-整体架构)
3. [核心工作原理与通信机制](#3-核心工作原理与通信机制)
4. [目录结构总览](#4-目录结构总览)
5. [内核模块详解（kernel/）](#5-内核模块详解kernel)
6. [Userspace Daemon 详解（userspace/ksud）](#6-userspace-daemon-详解userspaceksud)
7. [Android Manager App 详解（manager/）](#7-android-manager-app-详解manager)
8. [辅助子系统（susfsd / scripts / uapi / website）](#8-辅助子系统susfsd--scripts--uapi--website)
9. [依赖关系](#9-依赖关系)
10. [构建与运行方式](#10-构建与运行方式)
11. [关键流程时序](#11-关键流程时序)
12. [配置项与特性开关](#12-配置项与特性开关)

---

## 1. 项目总览

ZTR_OS SU 是一个 Android 内核级 SU 管理器，提供 Root 权限管理、模块化扩展、SELinux 策略控制等功能。

**核心特性：**

- **SuperKey 密钥认证** — 通过密钥而非证书哈希验证 Manager 身份，无需为每次 Manager 更新重新编译内核
- **模块系统** — 支持 Magisk/KernelSU 格式模块，含 Metamodule 热插拔挂载机制
- **SELinux 管理** — 快速切换 Enforcing/Permissive，支持运行时策略热补丁
- **内核功能增强** — SU 兼容模式、内核 Umount、AVC Spoof、UTS 伪造、Sulog 审计
- **多主题 UI** — ZTR_OS SU / Material You / MIUI X 三种界面风格
- **KPM 接口** — 保留 SukiSU-Ultra 兼容的 Kernel Patch Module ioctl 接口（当前为桩实现）

**与上游 KernelSU 的主要差异：**

| 能力 | KernelSU 上游 | ZTR_OS SU |
|---|---|---|
| Manager 身份认证 | APK 证书 SHA-256 哈希 | SuperKey 密钥 + 证书双路 |
| Hook 方式 | kprobes / 手动补丁 | 手动（Manual）LSM hook |
| 版本伪造 | 无 | `CHANGE_SPOOF_UNAME` + ioctl |
| 审计日志 | dmesg | Sulog ring buffer + fd |
| KPM | 无 | ioctl 接口（桩） |
| Metamodule | 无 | 支持单一 Metamodule 改写挂载/安装 |

---

## 2. 整体架构

ZTR_OS SU 由三大运行时组件 + 辅助子系统构成：

```
┌─────────────────────────────────────────────────────────────────┐
│                     Android 用户空间                             │
│  ┌───────────────────────┐        ┌───────────────────────────┐  │
│  │   Manager App (Kotlin) │        │  ksud daemon (Rust)       │  │
│  │   - Jetpack Compose UI │  JNI   │  - 多功能 multicall 二进制 │  │
│  │   - libsu root shell  │◄──────►│    (ksud / su / resetprop)│  │
│  │   - JNI ↔ 内核 ioctl  │        │  - 模块/启动/策略编排       │  │
│  └──────────┬────────────┘        └────────────┬──────────────┘  │
│             │ ioctl (anon inode [ksu_driver])   │ execve 重写     │
│             │ reboot 系统调用 (SuperKey/magic)  │ /system/bin/su  │
└─────────────┼───────────────────────────────────┼───────────────┘
              │                                   │
┌─────────────▼───────────────────────────────────▼───────────────┐
│                      Android Linux 内核                          │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │            ZTR_OS SU 内核模块 (kernelsu.ko)                │  │
│  │  ┌────────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │  │
│  │  │ supercalls │  │ LSM hook │  │ sucompat │  │ selinux  │  │  │
│  │  │ (ioctl 分发)│  │task_fix_ │  │(execve 拦│  │ (策略热补│  │  │
│  │  │            │  │ setuid)  │  │  截 su)   │  │  丁)     │  │  │
│  │  └────────────┘  └──────────┘  └──────────┘  └──────────┘  │  │
│  │  ┌────────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │  │
│  │  │ allowlist  │  │app_profile│ │ ksud     │  │ throne_  │  │  │
│  │  │(UID 授权表)│  │(root 提权)│  │(init 事件│  │ tracker  │  │  │
│  │  │            │  │           │  │  驱动)    │  │(Manager  │  │  │
│  │  │            │  │           │  │           │  │ 发现)    │  │  │
│  │  └────────────┘  └──────────┘  └──────────┘  └──────────┘  │  │
│  │  ┌────────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │  │
│  │  │ kernel_    │  │ kpm (桩) │  │ sulog    │  │ feature  │  │  │
│  │  │ umount     │  │          │  │(审计 ring │  │(特性开关)│  │  │
│  │  └────────────┘  └──────────┘  └──────────┘  └──────────┘  │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

**三种语言、三个层次：**

- **C / 内核层**：以可加载内核模块（LKM）或内建（built-in）形式存在于 Android 内核，提供 Root 提权、模块挂载、SELinux 修改等"内核态能力"。
- **Rust / userspace 层**：`ksud` 守护进程作为 init.rc 启动的 root 服务，编排模块安装/挂载/卸载、SELinux 应用、启动事件、SU shell。
- **Kotlin / Android 层**：Manager App 通过 JNI 直接与内核 ioctl 通信，通过 libsu 调用 `ksud` 完成特权操作，并提供 Compose UI。

---

## 3. 核心工作原理与通信机制

### 3.1 内核 ↔ 用户空间通信：匿名 inode fd + ioctl

ZTR_OS SU **不创建 `/dev/*` 设备节点**，而是使用一个匿名 inode 文件描述符：

1. 内核侧 `ksu_install_fd()`（[kernel/supercalls.c](file:///workspace/kernel/supercalls.c)）调用 `anon_inode_getfile("[ksu_driver]", &anon_ksu_fops, ...)` 创建一个名为 `[ksu_driver]` 的匿名文件并安装为 fd。
2. 该 fd 的 `unlocked_ioctl = anon_ksu_ioctl`，根据 `KSU_IOCTL_*` 命令码分发到 `ksu_ioctl_handlers[]` 表中的处理函数（每个命令带一个权限检查回调）。
3. 用户空间获取该 fd 的方式有两种：
   - **被动**：当进程通过 `task_fix_setuid` hook 切换到 Manager UID 时，内核自动 `ksu_install_fd()`。
   - **主动**：通过 `syscall(SYS_reboot, 0xDEADBEEF, 0xCAFEBABE, 0, &fd)`，内核 `ksu_handle_sys_reboot()` 识别魔法数后用 `task_work` 回调安装 fd。

**ioctl 命令表（节选，完整见 [uapi/supercall.h](file:///workspace/uapi/supercall.h) 与 [kernel/supercalls.h](file:///workspace/kernel/supercalls.h)）：**

| 命令码 | 名称 | 权限 | 说明 |
|---|---|---|---|
| `_IO('K',1)` | GRANT_ROOT | allowed_for_su | 授予当前进程 root |
| `_IOR('K',2)` | GET_INFO | always_allow | 版本/模式/特性位图 |
| `_IOW('K',3)` | REPORT_EVENT | only_root | 上报 post-fs-data/boot-completed |
| `_IOWR('K',4)` | SET_SEPOLICY | only_root | 热补丁 SELinux 策略 |
| `_IOWR('K',6/7)` | (NEW_)GET_ALLOW/DENY_LIST | manager_or_root | 读取授权/拒绝列表 |
| `_IOWR('K',11/12)` | GET/SET_APP_PROFILE | only_manager | 应用 profile 读写 |
| `_IOWR('K',13/14)` | GET/SET_FEATURE | manager_or_root | 特性开关 |
| `_IOW('K',42)` | SET_SPOOF_VERSION | manager_or_root | 伪造 uname 版本 |
| `_IOR('K',20)` | GET_SULOG_FD | manager_or_root | 获取 sulog fd |
| `_IOW('K',102)` | ENABLE_KPM | manager_or_root | KPM 开关（桩） |
| `_IOWR('K',200)` | KPM | manager_or_root | KPM 操作（桩） |

权限模型（定义于 [kernel/supercalls.c](file:///workspace/kernel/supercalls.c)）：
- `only_manager` — 仅 Manager UID
- `only_root` — 仅 UID 0
- `manager_or_root` — 二者之一
- `allowed_for_su` — Manager 或在 allowlist 中的 UID
- `always_allow` — 无限制

### 3.2 SuperKey 认证（ZTR_OS 核心特性）

绕过 ioctl fd，使用 `__NR_reboot` 系统调用 + 自定义魔法数（[uapi/supercall.h](file:///workspace/uapi/supercall.h)）：

| 魔法数 magic2 | 命令 | 说明 |
|---|---|---|
| `ZTRSU_SUPERKEY_SET = 10020` | 设置密钥 | 要求 root 或当前 Manager，长度 8–64 |
| `ZTRSU_SUPERKEY_VERIFY = 10021` | 验证密钥 | 任意进程 |
| `ZTRSU_SUPERKEY_GET_STATUS = 10022` | 查询状态 | 返回 `is_set` |

**认证流程**：Manager 启动 → `setSuperKey(key)` → 内核存储 → 后续特权操作前 `verifySuperKey` → 授权。这样 Manager 升级后无需重编内核即可继续被内核识别。

### 3.3 SU 提权流程

```
应用 execve("/system/bin/su")
        │
        ▼
内核 sucompat hook (ksu_handle_execveat_sucompat)
        │  检查 ksu_su_compat_enabled + is_su_allowed
        ▼
escape_with_root_profile()  [app_profile.c]
        │  prepare_creds → 设置 uid/gid/caps/groups
        │  setup_selinux → u:r:su:s0
        │  commit_creds → disable_seccomp
        │  setup_mount_ns → 全局/独立/继承
        ▼
重写 execve filename → "/data/adb/ksud"，argv[0]="su"
        │
        ▼
ksud 多调用检测 (cli::run: argv[0]=="su")
        │
        ▼
su::root_shell() → exec /system/bin/sh
```

### 3.4 启动事件驱动

内核 hook `init` 第二阶段与 `zygote` 启动（[kernel/ksud.c](file:///workspace/kernel/ksud.c)），通过在 init 读取 `init.rc` 时**追加 RC 片段**（`KERNEL_SU_RC`）注入三个触发点：

```rc
on post-fs-data          → exec u:r:su:s0 root /data/adb/ksud post-fs-data
on nonencrypted           → exec u:r:su:s0 root /data/adb/ksud services
on property:sys.boot_completed=1 → exec u:r:su:s0 root /data/adb/ksud boot-completed
```

ksud 收到这些子命令后调用 `init_event::on_post_data_fs()` / `on_services()` / `on_boot_completed()`。

---

## 4. 目录结构总览

```
/workspace
├── kernel/                  # C — 内核模块（LKM / built-in）
│   ├── ksu.c/.h             #   模块入口、unity include
│   ├── supercalls.c/.h      #   ioctl 分发、reboot hook
│   ├── allowlist.c/.h        #   UID 授权表（RCU + bitmap）
│   ├── app_profile.c/.h     #   root 提权 cred 变换
│   ├── sucompat.c/.h        #   /system/bin/su 拦截
│   ├── ksud.c/.h            #   init 事件 + RC 注入
│   ├── kernel_umount.c/.h   #   app 进程模块卸载
│   ├── su_mount_ns.c/.h     #   mount namespace 切换
│   ├── feature.c/.h         #   特性注册表
│   ├── tp_marker.c/.h       #   tracepoint 标记
│   ├── pkg_observer.c       #   packages.list fsnotify
│   ├── seccomp_cache.c/.h   #   seccomp 缓存操作
│   ├── file_wrapper.c/.h    #   fd 包装器（SELinux 伪装）
│   ├── extras.c             #   AVC Spoof / SELinux Hide
│   ├── tiny_sulog.c         #   紧凑 sulog（250×8B）
│   ├── arch.h               #   架构相关宏
│   ├── klog.h               #   内核日志宏
│   ├── hook/
│   │   └── lsm_hook.c       #   task_fix_setuid LSM hook
│   ├── manager/
│   │   ├── apk_sign.c/.h    #   APK v2 签名校验
│   │   ├── throne_tracker.c/.h  # Manager UID 发现（"加冕"）
│   │   └── manager_identity.h
│   ├── selinux/
│   │   ├── selinux.c/.h     #   SID 缓存 + 域切换
│   │   ├── sepolicy.c/.h    #   policydb 底层操作 + dup/swap
│   │   └── rules.c          #   基线规则 + 用户策略批处理
│   ├── kpm/
│   │   └── kpm.c/.h         #   Kernel Patch Module（桩）
│   ├── feature/
│   │   └── uts_spoof.c/.h   #   uname 伪造
│   ├── sulog/
│   │   ├── fd.c             #   sulog 事件队列 fd
│   │   └── event.h          #   事件捕获 API
│   ├── patches/gki/         #   GKI 手动 hook 补丁
│   ├── tools/
│   │   ├── auto_patch.sh    #   补丁应用脚本
│   │   └── check_symbol.c
│   ├── Kbuild / Kconfig / Makefile
│   └── setup.sh             #   集成到内核源码树
│
├── userspace/
│   ├── ksud/                # Rust — userspace daemon
│   │   └── src/
│   │       ├── main.rs / cli.rs / cli_non_android.rs
│   │       ├── ksucalls.rs  #   内核 ioctl 桥（最关键）
│   │       ├── module.rs / metamodule.rs / module_config.rs
│   │       ├── init_event.rs #  启动事件编排
│   │       ├── sepolicy.rs / profile.rs
│   │       ├── su.rs / resetprop.rs / debug.rs
│   │       ├── feature.rs / boot_info.rs / utils.rs
│   │       ├── defs.rs / assets.rs / restorecon.rs / apk_sign.rs
│   │       ├── installer.sh  #  嵌入式模块安装脚本
│   │       └── vfs_monitor.rs #  （孤儿/实验代码）
│   │   └── bin/             #  预编译 busybox / bootctl
│   └── susfsd/              # C — SusFS 守护 JNI（libsusfsd.so）
│
├── manager/                 # Kotlin — Android Manager App
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── aidl/.../IKsuInterface.aidl
│   │   │   ├── cpp/ (jni.cc, ksu.cc, ksu.h)   # JNI ↔ ioctl
│   │   │   ├── java/com/ztros/ztrosu/
│   │   │   │   ├── KernelSUApplication.kt
│   │   │   │   ├── Kernels.kt / KsuService.kt / Natives.kt
│   │   │   │   ├── profile/ (Capabilities, Groups)
│   │   │   │   └── ui/  (~90 Compose 文件)
│   │   │   ├── jniLibs/      # libksud.so / libmagiskboot.so / libsusfsd.so
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   ├── gradle/libs.versions.toml
│   └── build.gradle.kts      # 根构建脚本（cmaker、版本号）
│
├── uapi/                    # 内核 ↔ 用户空间 UAPI 头
│   ├── supercall.h          #   ioctl / KPM / SuperKey 定义
│   └── sulog.h              #   sulog 事件结构
│
├── scripts/                 # Python 自动化
│   ├── wksubot.py / add_device_handler.py
│   └── allowlist.bt
│
├── website/docs/            # 文档站点（多语言）
├── docs/                    # README 多语言版 + 专题文档
├── .github/workflows/       # CI（build-manager / ksud / susfsd / release …）
├── build.sh / justfile      # 本地构建入口
└── README.md
```

---

## 5. 内核模块详解（kernel/）

入口 [ksu.c](file:///workspace/kernel/ksu.c) 通过 `#include` 将各 `.c` 文件 unity-include 为单一翻译单元（`kernelsu-objs` 见 [Kbuild](file:///workspace/kernel/Kbuild)）。

### 5.1 supercalls.c / .h — IOCTL 分发与 reboot hook

- [kernel/supercalls.c](file:///workspace/kernel/supercalls.c)
- 维护 `ksu_ioctl_handlers[]` 命令表（cmd → handler + perm_check），由 `anon_ksu_ioctl()` 线性查找分发。
- `ksu_install_fd()`：创建 `[ksu_driver]` 匿名 inode fd。
- `ksu_handle_sys_reboot(magic1, magic2, cmd, arg)`：处理 `__NR_reboot` 系统调用，识别多种魔法数：
  - `KSU_INSTALL_MAGIC1/2 (0xDEADBEEF/0xCAFEBABE)` — 安装 driver fd（通过 task_work）
  - `CHANGE_MANAGER_UID (10006)` — 设置 Manager UID
  - `GET_SULOG_DUMP_V2 (10010)` — 导出紧凑 sulog
  - `CHANGE_KSUVER (10011)` — 伪造版本号
  - `CHANGE_SPOOF_UNAME (10012)` — 伪造 uname（三重指针解引用）
  - `ZTRSU_SUPERKEY_SET/VERIFY/GET_STATUS (10020–22)` — SuperKey 管理
- `ksu_supercalls_init()`：打印所有 ioctl 命令、初始化 sulog 堆。
- 权限检查函数：`only_manager` / `only_root` / `manager_or_root` / `allowed_for_su` / `always_allow`。

### 5.2 hook/lsm_hook.c — 根提权触发点

- [kernel/hook/lsm_hook.c](file:///workspace/kernel/hook/lsm_hook.c)
- 注册 LSM hook `task_fix_setuid → ksu_task_fix_setuid`：每次 setuid 系列调用时检查新 UID：
  - 是 Manager UID → `disable_seccomp()` + `ksu_install_fd()`
  - 在 allowlist → `disable_seccomp()`
  - 是 app/isolated → `ksu_handle_umount()`（卸载 KSU 模块挂载）
- `key_permission → ksu_key_permission`（条件编译，旧内核/华为/兼容模式）— 捕获 init 的 session keyring。
- `ksu_lsm_hook_init/exit()` — 通过 `security_add_hooks()` 注册（LSM 名为 `"ksu"`）。

### 5.3 selinux/ — SELinux 子系统

#### selinux.c/.h — SID 缓存与域切换
- [kernel/selinux/selinux.c](file:///workspace/kernel/selinux/selinux.c)
- 缓存 `su`/`zygote`/`init`/`ksu_file` 上下文的 SID，避免热路径字符串比较。
- `setup_selinux(domain, cred)` / `setup_ksu_cred()` — 将 cred 切换到 `u:r:su:s0`。
- `setenforce(bool)` / `getenforce()` — 切换/读取 enforcing 状态。
- `is_task_ksu_domain()` / `is_zygote()` / `is_init()` — 上下文谓词。
- **注意**：SELinux 域刻意设为 `"su"`（非 `"ksu"`）以保持策略兼容性。

#### sepolicy.c/.h — policydb 底层操作引擎
- [kernel/selinux/sepolicy.c](file:///workspace/kernel/selinux/sepolicy.c)
- 直接操作 `struct policydb`/`struct avtab`，提供：
  - 规则原语：`ksu_allow`/`ksu_deny`/`ksu_auditallow`/`ksu_dontaudit`
  - Xperm：`ksu_allowxperm`/`ksu_auditallowxperm`/`ksu_dontauditxperm`
  - 类型：`ksu_type`/`ksu_attribute`/`ksu_permissive`/`ksu_enforce`/`ksu_typeattribute`/`ksu_exists`
  - 类型规则：`ksu_type_transition`/`ksu_type_change`/`ksu_type_member`/`ksu_genfscon`
- `ksu_dup_sepolicy(old_pol)` — 深拷贝整个策略（class/avtab/role/type/permissive/filename_trans），用于原子 RCU 替换。
- `ksu_destroy_sepolicy(pol)` — 配套销毁。
- 跨版本兼容宏：`ksu_kvrealloc`（5.15/6.12 签名差异）、`ksu_hashtab_for_each`、`symtab_search`（pre-5.9）。

#### rules.c — 基线规则 + 用户策略批处理
- [kernel/selinux/rules.c](file:///workspace/kernel/selinux/rules.c)
- `apply_kernelsu_rules()` — 应用 KernelSU 基线策略（su 域 permissive + mlstrustedsubject + netdomain，创建 `ksu_file` 类型，允许 binder/logd/system_server 交互，镜像自 Magisk suRights）。采用 dup-swap-RCU-reset_avc 模式。
- `handle_sepolicy(user_data, data_len)` — 解析用户空间发来的二进制命令流（最多 8 MiB），每条 `{cmd, subcmd, args[]}` 通过 `apply_one_sepolicy_cmd` 应用，最后在 `selinux_state.policy_mutex` 下 swap 并 `reset_avc_cache`。
- 命令常量：`CMD_NORMAL_PERM(1)`、`CMD_XPERM(2)`、`CMD_TYPE_STATE(3)`、`CMD_TYPE(4)`、`CMD_TYPE_ATTR(5)`、`CMD_ATTR(6)`、`CMD_TYPE_TRANSITION(7)`、`CMD_TYPE_CHANGE(8)`、`CMD_GENFSCON(9)`。

### 5.4 manager/ — Manager 识别子系统

#### apk_sign.c/.h — APK 签名校验
- [kernel/manager/apk_sign.c](file:///workspace/manager/apk_sign.c)
- `is_manager_apk(path)` — 解析 APK Signing Block v2，SHA-256 校验签名者证书，与编译期 `EXPECTED_HASH`/`EXPECTED_SIZE`（可配置双签名 `EXPECTED_SIZE2/HASH2`）比对。拒绝 v1（JAR）/v3/v3.1。
- `get_pkg_from_apk_path()` — 从 `/data/app/<pkg>-<hash>/base.apk` 解析包名。
- 编译期哈希在 [Kbuild](file:///workspace/kernel/Kbuild) 中定义，可通过环境变量 `KSU_EXPECTED_HASH` 等覆盖。

#### throne_tracker.c/.h — Manager UID 发现（"加冕"）
- [kernel/manager/throne_tracker.c](file:///workspace/kernel/manager/throne_tracker.c)
- 由于 Manager UID 由用户安装时确定，无法硬编码。
- `track_throne(prune_only)` — 解析 `/data/system/packages.list` 得到 UID 列表；若无 Manager UID 则 BFS 遍历 `/data/app` 调用 `is_manager_apk` 匹配签名，找到后 `crown_manager()` 调用 `ksu_set_manager_appid(uid)`；否则裁剪 allowlist 中失效 UID。
- `search_manager(path, depth, uid_data)` — 限制深度 BFS，用 `s_magic` 一致性检测 bind-mount 欺骗，按哈希缓存 APK 路径。

### 5.5 allowlist.c — UID 授权表
- [kernel/allowlist.c](file:///workspace/kernel/allowlist.c)
- RCU 链表 `perm_data` + 页对齐 bitmap `allow_list_bitmap`（O(1) 快速路径）+ 高 UID fallback 数组。
- `ksu_set_app_profile(profile)` — 插入/更新，更新 bitmap，缓存默认 profile（key `"#"`=root 默认、`"$"`=非 root 默认）。
- `__ksu_is_allow_uid(uid)` / `__ksu_is_allow_uid_for_current(uid)` — bitmap 快查，Manager UID 恒允许，uid 0 仅 KSU 域允许。
- `ksu_uid_should_umount(uid)` — 决定是否卸载模块。
- `ksu_get_root_profile(uid, *profile)` — 取 root profile（caps/groups/SELinux 域/namespaces）。
- 持久化到 `/data/adb/ksu/.allowlist`（magic `0x7f4b5355`，version 3），通过 `task_work_add` 在 PID 1 下写入。

### 5.6 app_profile.c — Root 提权 cred 变换
- [kernel/app_profile.c](file:///workspace/kernel/app_profile.c)
- `escape_with_root_profile()` — **核心提权函数**：`prepare_creds` → 设 uid/gid/fsuid/sgid/euid/egid/fsgid → `alloc_uid`+`set_cred_ucounts`（5.14+）→ 设 capabilities（追加 `CAP_DAC_READ_SEARCH` 以便访问 ksud）→ `setup_groups` → `setup_selinux` → `commit_creds` → `disable_seccomp` → `ksu_set_task_tracepoint_flag`（逐线程）→ `setup_mount_ns`。
- `escape_to_root_for_init()` — PID 1 极简 cred 切换到 `KERNEL_SU_CONTEXT`。
- `disable_seccomp()` — 在 `sighand->siglock` 下清 `TIF_SECCOMP`/`SECCOMP` work flag；5.9+ 用伪造 task_struct 调 `seccomp_filter_release`。

### 5.7 sucompat.c / .h — /system/bin/su 拦截
- [kernel/sucompat.c](file:///workspace/kernel/sucompat.c)
- `ksu_handle_execveat_sucompat(fd, filename_ptr, argv, envp, flags)` — execve su 时：捕获 sulog 待处理事件 → `escape_with_root_profile()` → 重写 filename 到 `KSUD_PATH`（或回退 `/system/bin/sh`），argv[0]="su"。
- `ksu_handle_faccessat()` / `ksu_handle_stat()` — 将对 `/system/bin/su` 的 faccessat/stat 重定向到 `sh`（无提权）。
- `ksu_sucompat_user_common(filename_user, syscall_name, escalate)` — 共享逻辑：检测 su 路径，可选提权，选择 ksud-or-sh 目标。
- `is_su_allowed(ptr)` 宏 — 闸门：`ksu_su_compat_enabled` + seccomp 未设 + `ksu_is_allow_uid_for_current`。
- 用户栈缓冲技巧：`userspace_stack_buffer()` / `sh_user_path()` / `ksud_user_path()` — 在用户栈指针下方写字符串避免 mmap。
- `ksu_sucompat_init/exit` — 注册 `KSU_FEATURE_SU_COMPAT` 特性处理器。

### 5.8 ksud.c / .h — 内核侧启动事件驱动
- [kernel/ksud.c](file:///workspace/kernel/ksud.c)
- `on_post_fs_data()` — zygote 首次 exec 时触发：加载 allowlist、初始化 pkg observer、停止 input hook。幂等。
- `on_boot_completed()` — 置 `ksu_boot_completed`、`track_throne(true)`（仅裁剪）、`ksu_avc_spoof_late_init()`。
- `ksu_handle_execveat_ksud_path(path, argv)` — 检测 `/system/bin/init second_stage`（触发 `apply_kernelsu_rules`/`cache_sid`/`setup_ksu_cred`）与 `/system/bin/app_process -Xzygote`（触发 `on_post_fs_data`）。
- **RC 注入**：`read_proxy`/`read_iter_proxy` 是 file_ops 代理，在 init 读完 `init.rc` 返回 EOF 后追加 `KERNEL_SU_RC`（含 post-fs-data/services/boot-completed 调用 `KSUD_PATH`）。`ksu_install_rc_hook(file)` 替换 `file->f_op`。配套 `ksu_handle_sys_read`/`ksu_sys_fstat` 调整 `st_size` 让 init 读到追加内容。
- **Safe mode**：`vol_detector_*` 监听音量键，`ksu_is_safe_mode()` — 3+ 次音量键按下 → safe mode（禁用模块）。
- `nuke_ext4_sysfs(mnt)` — `ext4_unregister_sysfs`（反检测）。
- 常量：`KSUD_PATH "/data/adb/ksud"`、`VOLUME_PRESS_THRESHOLD_COUNT 3`。

### 5.9 kernel_umount.c — App 进程模块卸载
- [kernel/kernel_umount.c](file:///workspace/kernel/kernel_umount.c)
- `ksu_handle_umount(old_uid, new_uid)` — 由 LSM `task_fix_setuid` 触发：校验特性启用 + `ksu_cred` 存在 + 新 UID 是 app/isolated + 应卸载 + 调用方是 zygote 子进程 → `override_creds(ksu_cred)` → 遍历 `mount_list` 调 `try_umount` → `revert_creds`。
- 处理 5 种 zygote fork 场景（普通 app、isolated、app-zygote 等）。
- `ksu_kernel_umount_init/exit` — 注册 `KSU_FEATURE_KERNEL_UMOUNT`。

### 5.10 su_mount_ns.c — Mount Namespace 切换
- [kernel/su_mount_ns.c](file:///workspace/kernel/su_mount_ns.c)
- `setup_mount_ns(ns_mode)` — 按 `KSU_NS_INHERITED`（空操作）/`KSU_NS_GLOBAL`/`KSU_NS_INDIVIDUAL` 分发。
- `ksu_mnt_ns_global()` — 保存 pwd → 找 PID 1 → `ns_get_path`+`dentry_open`+`fd_install`+`ksu_sys_setns(CLONE_NEWNS)` → 恢复 pwd。
- `ksu_mnt_ns_individual()` — `ksys_unshare(CLONE_NEWNS)` + 设 root `MS_PRIVATE|MS_REC`。
- `ksu_sys_setns(fd, flags)` — 架构相关包装，通过合成 `pt_regs` 调 `__arm64_sys_setns`/`__x64_sys_setns`。

### 5.11 feature.c / .h — 特性注册表
- [kernel/feature.c](file:///workspace/kernel/feature.c)
- `ksu_register_feature_handler(handler)` — 在 `feature_mutex` 下存入 `feature_handlers[id]`。
- `ksu_get_feature(id, *value, *supported)` / `ksu_set_feature(id, value)` — 查询/设置特性值。
- `enum ksu_feature_id`：`SU_COMPAT(0)`、`KERNEL_UMOUNT(1)`、`SELINUX_HIDE(4)`、`AVC_SPOOF(10003)`、`MAX`。

### 5.12 tp_marker.c / .h — Tracepoint 标记
- [kernel/tp_marker.c](file:///workspace/kernel/tp_marker.c)
- 切换 per-task `TIF_SYSCALL_TRACEPOINT` flag，使内核 syscall tracepoint 对目标 task 生效（root/zygote/shell/allowlisted UID）。
- `ksu_mark_all_process()`/`ksu_unmark_all_process()` — 全任务设/清。
- `ksu_mark_running_process()` — 选择性标记（root/zygote/shell/init/allowlisted），清其他；跳过内核线程（PID 1 除外）。
- `ksu_get_task_mark(pid)`/`ksu_set_task_mark(pid, mark)` — 单任务 get/set，返回 1/0/-ESRCH。
- 注册计数 API 避免与 ftrace 等其他消费者冲突。

### 5.13 pkg_observer.c — 包变更监听
- [kernel/pkg_observer.c](file:///workspace/kernel/pkg_observer.c)
- 用 `fsnotify` 监视 `/data/system`，当 `packages.list` 变化（app 安装/卸载/更新）时触发 `track_throne(false)` 重新评估 Manager UID 并裁剪 allowlist。
- `ksu_observer_init()` — `fsnotify_alloc_group(&ksu_ops)` + `watch_one_dir`（mask `FS_CREATE|FS_MOVE|FS_EVENT_ON_CHILD`）。

### 5.14 seccomp_cache.c / .h — Seccomp 缓存操作
- [kernel/seccomp_cache.c](file:///workspace/kernel/seccomp_cache.c)
- 操纵内部 `struct seccomp_filter` 的 action cache bitmap，让被 hook 的 syscall（如 execve su）不被 seccomp 阻挡。
- `ksu_seccomp_allow_cache(filter, nr)` — `set_bit(nr, filter->cache.allow_native)`（及 `allow_compat`）。
- `ksu_seccomp_clear_cache(filter, nr)` — `clear_bit` 对应。
- 局部重声明 `struct seccomp_filter`/`action_cache` 以访问私有字段。

### 5.15 file_wrapper.c / .h — FD 包装器
- [kernel/file_wrapper.c](file:///workspace/kernel/file_wrapper.c)
- `ksu_install_file_wrapper(fd)` — 取已存在 fd，`fget` 后分配新 fd + `ksu_file_wrapper`，创建 `[ksu_fdwrapper]` 匿名 inode 文件，设 `wrapper_inode->i_mode` 与 `wrapper_sec->sid = ksu_file_sid`，安装 `ksu_file_wrapper_inode_fops` 与 d_ops。
- `ksu_create_file_wrapper(fp)` — 构建 `struct file_operations` 代理，包装全部 op（llseek/read/write/ioctl/poll/mmap/splice 等）。
- 用途：root 进程向 app 传递 fd（如 tty）时绕过 SELinux 拒绝并伪装路径。

### 5.16 extras.c — AVC Spoof / SELinux Hide
- [kernel/extras.c](file:///workspace/kernel/extras.c)
- **avc_spoof** — 拦截慢路径 AVC 审计，将目标 SID 从 `u:r:su:s0` 改写为 `u:r:priv_app:s0:c512,c768`，使 SELinux 拒绝日志不暴露 su 域（反检测）。
- `ksu_handle_slow_avc_audit(tsid)` — 若 `disable_spoof` 清且 `*tsid == su_sid`，替换为 `priv_app_sid`。
- `ksu_avc_spoof_enable/disable()` — 切换原子 `disable_spoof`。
- `ksu_avc_spoof_late_init()` — `on_boot_completed` 调用，按特性 flag 启用。
- `ksu_avc_spoof_init/exit` — 注册 `KSU_FEATURE_AVC_SPOOF` 与 `KSU_FEATURE_SELINUX_HIDE`。

### 5.17 sulog/ — 审计日志

#### sulog/fd.c — 事件队列 fd
- [kernel/sulog/fd.c](file:///workspace/kernel/sulog/fd.c)
- `ksu_install_sulog_fd()` — 分配 fd + `[ksu_sulog]` 匿名 inode，用户空间可 `read`/`poll`/`epoll` 接收结构化事件流（root grant/execve/sucompat）。
- `ksu_event_queue_write(queue, type, flags, payload, len, gfp)` — 入队带 seq/时间戳；满时丢弃并记账 `dropped_pending`/`dropped_inflight`。
- `ksu_event_queue_read/poll` — `wait_event_interruptible` 出队，丢弃汇总记录（`type=0xFFFF`）与正常记录交错。
- 单活动 fd 限制 `ksu_sulog_fd_active`。

#### tiny_sulog.c — 紧凑环形缓冲
- [kernel/tiny_sulog.c](file:///workspace/kernel/tiny_sulog.c)
- 250 条 × 8 字节（uptime + 打包 uid/symbol）的半环形，`write_sulog(sym)` 写入，`send_sulog_dump(uptr)` 拷贝到用户空间。仅小端。

#### sulog/event.h — 事件捕获 API
- [kernel/sulog/event.h](file:///workspace/kernel/sulog/event.h)
- 声明 `ksu_sulog_capture_root_execve`/`ksu_sulog_capture_sucompat`/`ksu_sulog_emit_pending`/`ksu_sulog_emit_grant_root`，供 sucompat 与 execve hook 调用记录事件。

### 5.18 kpm/ — Kernel Patch Module（桩）
- [kernel/kpm/kpm.c](file:///workspace/kernel/kpm/kpm.c)
- 兼容 SukiSU-Ultra/KernelPatch 的 KPM 加载器接口，**所有 load/unload/control 操作返回 `-ENOSYS`**，但分发层与 ioctl 完整实现以便 Manager UI 优雅查询。
- `sukisu_handle_kpm(control_code, arg1, arg2, result_code)` — 分发器，处理 `SUKISU_KPM_LOAD/UNLOAD/NUM/LIST/INFO/CONTROL/VERSION`。
- `do_kpm(arg)` — ioctl 入口。
- 命令码：`KSU_IOCTL_ENABLE_KPM _IOW('K',102,bool)`、`KSU_IOCTL_KPM _IOWR('K',200,struct ksu_kpm_cmd)`（102/200 避免与 KSU 100/101 冲突）。
- `sukisu_kpm_version` 返回 `"AuroraSU (KPM not supported)"`。

### 5.19 feature/uts_spoof.c — UTS 伪造
- [kernel/feature/uts_spoof.c](file:///workspace/kernel/feature/uts_spoof.c)
- 伪造 `init_uts_ns` 的 release/version 字符串，绕过 `uname -r` 检测。
- `ksu_spoof_version(release, version)` — 通过 `kallsyms_lookup_name` 解析 `uts_sem`/`init_uts_ns`（未导出）。
- `ksu_handle_spoof_version(arg)` — ioctl 处理器，从用户空间拷贝 `ksu_spoof_version_cmd` 并应用。

### 5.20 vfs_debug/ — VFS 调试子系统（可选，CONFIG_KSU_VFS_DEBUG）
- [kernel/vfs_debug.c](file:///workspace/kernel/vfs_debug.c) — VFS 操作监控 + 访问控制（glob 规则匹配 `action:path_pattern:mode`，默认 allow/deny）。
- [kernel/vfs_debug_sysfs.c](file:///workspace/kernel/vfs_debug_sysfs.c) — 暴露 `/sys/kernel/ztrosu/vfs/`（stats/enabled/log_level/default_action/rules）。
- [kernel/vfs_debug_hook.c](file:///workspace/kernel/vfs_debug_hook.c) — 从 patched syscall 路径调用 `vfs_debug_*`（open/read/write/close 计数）。
- 常量：`VFS_MAX_RULES 64`、`VFS_MAX_PATH_LEN 512`。

### 5.21 tools/ 与 patches/ — 内核集成工具
- [kernel/setup.sh](file:///workspace/kernel/setup.sh) — 将模块集成进 GKI 内核源码树：克隆仓库 → 软链 `kernel/` 为 `drivers/kernelsu` → 修改 `Makefile`/`Kconfig`（幂等）。
- [kernel/tools/auto_patch.sh](file:///workspace/kernel/tools/auto_patch.sh) — 应用 `patches/gki/` 下的手动 hook 补丁（exec/open/stat/stat_ret/reboot），dry-run 后 apply，支持 32 位变体。
- [kernel/patches/gki/](file:///workspace/kernel/patches/gki) — `exec.patch`、`open.patch`、`stat.patch`、`stat_ret.patch`、`reboot.patch`（及 `_32` 变体）。

### 5.22 Kconfig / Kbuild
- [kernel/Kconfig](file:///workspace/kernel/Kconfig) — 三个选项：
  - `CONFIG_KSU`（依赖 `EXT4_FS`，默认 y）— 主功能
  - `CONFIG_KSU_DEBUG`（默认 n）— 调试日志 + `ksu_debug_manager_appid` 模块参数
  - `CONFIG_KSU_VFS_DEBUG`（默认 n）— VFS 调试子系统 + sysfs
- [kernel/Kbuild](file:///workspace/kernel/Kbuild) — 列出 `kernelsu-objs`；从 git 计算版本 `KSU_VERSION = 30000 + git_commit_count`；定义 `EXPECTED_HASH`/`EXPECTED_SIZE`（默认官方 KernelSU cert `947ae9...`，可 `KSU_EXPECTED_HASH` 覆盖）与可选第二签名。

---

## 6. Userspace Daemon 详解（userspace/ksud）

源码 [userspace/ksud/src/](file:///workspace/userspace/ksud/src/)。构建为 `aarch64-linux-android`/`x86_64-linux-android`，产物 `ksud` 复制为 `libksud.so` 打入 Manager APK。**多调用二进制**：`argv[0]=="su"` → SU shell；`argv[0]` 含 `resetprop` → resetprop 工具；否则走 clap CLI。

几乎所有子模块用 `#[cfg(target_os = "android")]` 门控；仅 `apk_sign`/`assets`/`defs`/`cli_non_android` 在 host 编译。

### 6.1 main.rs / cli.rs — 入口与 CLI
- [userspace/ksud/src/main.rs](file:///workspace/userspace/ksud/src/main.rs) — `fn main()` 分发到 `cli::run()`（Android）或 `cli_non_android::run()`（host）。强制 clippy pedantic/nursery。
- [userspace/ksud/src/cli.rs](file:///workspace/userspace/ksud/src/cli.rs) — clap `Args`/`Commands` 枚举：`Module`、`PostFsData`、`Services`、`BootCompleted`、`Install`、`Sepolicy`、`Profile`、`Feature`、`BootInfo`、`Debug`、`Kernel`、`Resetprop`、`SoftReboot`。`run()` 初始化 `android_logger`（tag "ZTR_OS SU"），实现多调用检测，路由到各模块。

### 6.2 ksucalls.rs — 内核 ioctl 桥（最关键）
- [userspace/ksud/src/ksucalls.rs](file:///workspace/userspace/ksud/src/ksucalls.rs)
- `#[repr(C)]` 命令结构体 + `libc::ioctl` 调用，magic `'K'`。
- **Driver fd 获取 `init_driver_fd()`**：
  1. `scan_driver_fd()` 读 `/proc/self/fd/*` 的 symlink，找含 `[ksu_driver]` 的。
  2. 否则 `libc::syscall(SYS_reboot, 0xDEADBEEF, 0xCAFEBABE, 0, &mut fd)` — 内核 reboot hook 识别后 task_work 安装 fd。
  3. fd 缓存于 `static DRIVER_FD: OnceLock<RawFd>`，`INFO_CACHE` 缓存 `GetInfoCmd`。
- 公共 API：`get_version`、`grant_root`、`report_post_fs_data`/`report_boot_complete`、`check_kernel_safemode`、`set_sepolicy`、`get_feature`/`set_feature`、`get_wrapped_fd`、`mark_get/set/unset/refresh`、`nuke_ext4_sysfs`、`umount_list_wipe/add/del`、`set_init_pgrp`。
- 仅依赖 `libc`。

### 6.3 defs.rs — 路径常量
- [userspace/ksud/src/defs.rs](file:///workspace/userspace/ksud/src/defs.rs)
- `ADB_DIR=/data/adb/`、`WORKING_DIR=/data/adb/ksu/`、`DAEMON_PATH=/data/adb/ksud`、`MODULE_DIR=/data/adb/modules/`、`MODULE_UPDATE_DIR=/data/adb/modules_update/`、`METAMODULE_DIR=/data/adb/metamodule/`、`MODULE_CONFIG_DIR`、`PERSIST_CONFIG_NAME="persist.config"`、`TEMP_CONFIG_NAME="tmp.config"`、metamodule 脚本名（`metamount.sh`/`metainstall.sh`/`metauninstall.sh`）、模块标志文件名（`disable`/`update`/`remove`）。
- `VERSION_CODE`/`VERSION_NAME` 用 `include_str!` 从 `OUT_DIR` 读取；`MOUNT_SYSTEM="Meta"`。

### 6.4 utils.rs — 通用工具 + 自安装
- [userspace/ksud/src/utils.rs](file:///workspace/userspace/ksud/src/utils.rs)
- FS/进程助手：`ensure_clean_dir`、`ensure_binary`（写+chmod 0755）、`getprop`、`is_safe_mode`、`get_zip_uncompressed_size`、`switch_mnt_ns(pid)`（rustix `move_into_link_name_space`）、`switch_cgroups`（写 `/acct`/`/dev/cg2_bpf`/`/sys/fs/cgroup`/`/dev/memcg/apps`）、`daemonize`（双 fork+setpgid+cgroups+dup null stdio）。
- `install(magiskboot)` — 拷贝 `current_exe` 到 `DAEMON_PATH` → `restorecon::lsetfilecon(ADB_CON)` → `assets::ensure_binaries(false)` → `link_ksud_to_bin()` → 可选拷贝 magiskboot。
- 宏 `debug_select!($debug, $release)` — debug 取第一参、release 取第二参。

### 6.5 module.rs — 模块生命周期
- [userspace/ksud/src/module.rs](file:///workspace/userspace/ksud/src/module.rs)
- `ModuleType{All,Active,Updated}`、`validate_module_id`（正则 `^[a-zA-Z][a-zA-Z0-9._-]+$`）。
- `install_module`/`install_module_to_system`、`restore_module`、`uninstall_module`、`enable_module`、`disable_module`、`disable_all_modules`、`run_action`、`mount_system`（仅打印 `"Meta"`，实际挂载委托给 metamodule）、`list_modules`。
- `foreach_module`/`foreach_active_module`、`load_sepolicy_rule`、`exec_script`/`exec_stage_script`/`exec_common_scripts`（经 `assets::BUSYBOX_PATH sh`，`pre_exec` 调 `ksucalls::set_init_pgrp`+`switch_cgroups`，`MODULE_DIR` 下设 `KSU_MODULE` 环境变量）、`load_system_prop`、`prune_modules`、`handle_updated_modules`、`read_module_prop`、`get_managed_features`。
- 嵌入资产 `INSTALLER_CONTENT = include_str!("./installer.sh")`。

### 6.6 metamodule.rs — Metamodule 支持
- [userspace/ksud/src/metamodule.rs](file:///workspace/userspace/ksud/src/metamodule.rs)
- "Metamodule" 是单一特殊模块，改写常规模块的安装/挂载/卸载并提供 stage hook。
- `is_metamodule(props)`（`module.prop` `metamodule=1|true`）、`get_metamodule_path()`、`has_metamodule`、`check_install_safety()`、`ensure_symlink`/`remove_symlink`、`get_install_script()`（返回活跃 metamodule 的 `metainstall.sh` 或内置 `INSTALL_MODULE_SCRIPT`）、`exec_metauninstall_script`、`exec_mount_script`、`exec_stage_script(stage, block)`。

### 6.7 init_event.rs — 启动事件编排
- [userspace/ksud/src/init_event.rs](file:///workspace/userspace/ksud/src/init_event.rs)
- `on_post_data_fs()` — **主启动指挥**（见 §11.2 时序）。
- `run_stage(stage, block)` — 执行 `<stage>.d` 公共脚本 → metamodule stage → 常规模块 stage。
- `on_services()`、`on_boot_completed()`、`soft_reboot()`（daemonize → `sys.boot_completed=0` → `emulated-soft-reboot` stage → stop → 重跑 post-fs-data/services/boot-completed → start）、`catch_bootlog`（`logcat -b all` + `dmesg -w -r` 到 `LOG_DIR`）。

### 6.8 sepolicy.rs / profile.rs — 策略解析与 profile
- [userspace/ksud/src/sepolicy.rs](file:///workspace/userspace/ksud/src/sepolicy.rs) — KernelSU/Magisk sepolicy-statement DSL 解析器+序列化器。`live_patch`/`apply_file`/`check_rule`。语句展开为原子语句（笛卡尔积），序列化为 `cmd|subcmd|<len-prefixed NUL-terminated strings>`，通过 `ksucalls::set_sepolicy` 批量送内核。
- [userspace/ksud/src/profile.rs](file:///workspace/userspace/ksud/src/profile.rs) — 每包 app-profile 存储（SELinux 策略 + root profile 模板）。`set_sepolicy`/`get_sepolicy`/`set_template`/`get_template`/`delete_template`/`list_templates`/`apply_sepolies`。

### 6.9 su.rs — Root Shell
- [userspace/ksud/src/su.rs](file:///workspace/userspace/ksud/src/su.rs)
- `grant_root(global_mnt)` — `ksucalls::grant_root()` 后 exec sh。
- `root_shell()` — getopts SU CLI（`-c/-h/-l/-p/-s/-v/-V/-M/-g/-G/-W`），解析 uid，设 HOME/USER/LOGNAME/SHELL，PATH 追加 `BINARY_DIR`，pre_exec 中 `umask(0o22)`+`switch_cgroups`+可选全局 mount ns+可选 tty fd 包装（`wrap_tty` 调 `ksucalls::get_wrapped_fd`）+`set_identity` 降权。

### 6.10 其他模块

| 文件 | 职责 |
|---|---|
| [assets.rs](file:///workspace/userspace/ksud/src/assets.rs) | `rust_embed` 嵌入 busybox/resetprop/magiskboot；`ensure_binaries` 解压 + 建 `resetprop → /data/adb/ksud` 软链 |
| [debug.rs](file:///workspace/userspace/ksud/src/debug.rs) | `set_manager`（写 `/sys/module/kernelsu/parameters/ksu_debug_manager_uid`，需 CONFIG_KSU_DEBUG）、`mark_get/set/unset/refresh` |
| [feature.rs](file:///workspace/userspace/ksud/src/feature.rs) | 特性管理 + 二进制配置（`.feature_config`，magic `0x7f4b5355`）。`FeatureId{SuCompat,KernelUmount,EnhancedSecurity,AvcSpoof}`。模块可声明 `manage.<feature>` 接管特性 |
| [boot_info.rs](file:///workspace/userspace/ksud/src/boot_info.rs) | 启动分区探测（A/B、slot_suffix、`init_boot` vs `boot`） |
| [resetprop.rs](file:///workspace/userspace/ksud/src/resetprop.rs) | Magisk 兼容 resetprop multicall（`-n/-p/-P/-d/-v/-w/-f/-c/-Z`） |
| [restorecon.rs](file:///workspace/userspace/ksud/src/restorecon.rs) | SELinux xattr 标签管理（`SYSTEM_CON`/`ADB_CON`/`UNLABEL_CON`，递归 `restore_syscon`） |
| [apk_sign.rs](file:///workspace/userspace/ksud/src/apk_sign.rs) | 提取 APK v2 证书 size+SHA-256（host 兼容） |
| [module_config.rs](file:///workspace/userspace/ksud/src/module_config.rs) | 每模块 KV 配置（二进制，persist+temp 层，magic `"KSUM"`，max 32 条） |
| [vfs_monitor.rs](file:///workspace/userspace/ksud/src/vfs_monitor.rs) | **孤儿代码**：未在 main.rs 声明，引用不存在的 `KSU_WORK_DIR`，不会编译 |
| [cli_non_android.rs](file:///workspace/userspace/ksud/src/cli_non_android.rs) | host 最小 CLI（仅 `GetSign { apk }`） |
| [installer.sh](file:///workspace/userspace/ksud/src/installer.sh) | 嵌入式 Magisk 风格模块安装脚本 |

---

## 7. Android Manager App 详解（manager/）

源码 [manager/app/src/main/](file:///workspace/manager/app/src/main/)，包名 `com.ztros.ztrosu`，minSdk 26 / targetSdk 36 / compileSdk 36，ABI `arm64-v8a`+`x86_64`。单 Activity + Jetpack Compose + Material 3。

### 7.1 AIDL 接口
- [manager/app/src/main/aidl/com/ztros/ztrosu/IKsuInterface.aidl](file:///workspace/manager/app/src/main/aidl/com/ztros/ztrosu/IKsuInterface.aidl)
- 单方法 `ParcelableListSlice<PackageInfo> getPackages(int flags)` — 跨用户枚举已安装包（Manager 自身无权限，需 root 服务代劳）。

### 7.2 JNI / C++ 层（cpp/）
- [manager/app/src/main/cpp/](file:///workspace/manager/app/src/main/cpp/) — CMake 构建 `libkernelsu.so`（`jni.cc` + `ksu.cc`）。
- `ksu.h` 声明内核命令结构与 `KSU_IOCTL_*` 码；`ksu.cc` 的 `ksuctl()` 模板执行 `ioctl()`。
- **Driver fd 发现**：`scan_driver_fd()` 遍历 `/proc/self/fd` 找含 `[ksu_driver]` 的 symlink（由 ksud/init 预开并继承）。
- 暴露给 Java 的 `external` 函数（`Java_com_ztros_ztrosu_Natives_*`）：

| Kotlin 函数 | 内核 ioctl |
|---|---|
| `getVersion()` | GET_INFO（legacy `prctl(0xDEADBEEF,2)` 回退） |
| `getManagerAppid()` | GET_MANAGER_APPID |
| `getHookMode()`/`getHookType()` | GET_HOOK_MODE / HOOK_TYPE |
| `getVersionTag()`/`getFullVersion()` | GET_VERSION_TAG / GET_FULL_VERSION |
| `getSuperuserCount()` | NEW_GET_ALLOW_LIST（total_count） |
| `isSafeMode()` | CHECK_SAFEMODE |
| `isLkmMode()`/`isLateLoadMode()`/`isManager()`/`isPrBuild()` | GET_INFO flags |
| `getAppProfile`/`setAppProfile` | GET/SET_APP_PROFILE |
| `uidShouldUmount(uid)` | UID_SHOULD_UMOUNT |
| `isSuEnabled`/`setSuEnabled` | GET/SET_FEATURE (SU_COMPAT) |
| `isKernelUmountEnabled`/`setKernelUmountEnabled` | (KERNEL_UMOUNT) |
| `isAvcSpoofEnabled`/`setAvcSpoofEnabled` | (AVC_SPOOF) |
| `isSelinuxHideEnabled`/`setSelinuxHideEnabled` | (SELINUX_HIDE) |
| `getUserName(uid)` | `getpwuid()` |
| `setSuperKey`/`verifySuperKey`/`isSuperKeyActive` | `syscall(__NR_reboot, 0xfee1dead, 10020/21/22, …)` |

### 7.3 顶层 Kotlin 文件

| 文件 | 职责 |
|---|---|
| [KernelSUApplication.kt](file:///workspace/manager/app/src/main/java/com/ztros/ztrosu/KernelSUApplication.kt) | `Application` 子类；全局 init（root shell、Coil、OkHttpClient、webroot 目录）；启动时探测内核 + 记录 SuperKey 状态 |
| [Natives.kt](file:///workspace/manager/app/src/main/java/com/ztros/ztrosu/Natives.kt) | JNI 桥对象；声明所有 `external`；`data class Profile`（Parcelable，含 `enum Namespace`）；`requireNewKernel()`（最小内核 33075 / v4.0.0） |
| [Kernels.kt](file:///workspace/manager/app/src/main/java/com/ztros/ztrosu/Kernels.kt) | `KernelVersion` 解析与分类（GKI/U-Legacy/Legacy/GKI1/510） |
| [KsuService.kt](file:///workspace/manager/app/src/main/java/com/ztros/ztrosu/KsuService.kt) | libsu `RootService`；`Stub` 实现跨用户包枚举 |
| [profile/Capabilities.kt](file:///workspace/manager/app/src/main/java/com/ztros/ztrosu/profile/Capabilities.kt) | Linux CAP_* 枚举（0–40） |
| [profile/Groups.kt](file:///workspace/manager/app/src/main/java/com/ztros/ztrosu/profile/Groups.kt) | Android GID 枚举 |

### 7.4 UI 层结构（ui/）

- **单 Activity**：[MainActivity.kt](file:///workspace/manager/app/src/main/java/com/ztros/ztrosu/ui/MainActivity.kt) — Compose `ComponentActivity`，`DestinationsNavHost(navGraph = NavGraphs.root)` + 浮动玻璃底栏 + 主题/密度/滚动状态 + Intent 路由（flash/shortcut）。
- **导航**：`com.ramcosta.composedestinations`（KSP 生成 `NavGraphs.root`），每个 screen 是 `@Destination<RootGraph>`（Home `start=true`）；`BottomBarDestination` 枚举底栏项。
- **屏幕**（`ui/screen/`）：Home、SuperUser、Module、ModuleRepo、MetaModule、Settings、Customization、Install、Flash、ExecuteModuleAction、AppProfile、Template、TemplateEditor、ProfileTemplate、SELinux、Audit、IdentitySpoof、Monitor、VFSDebug、DeveloperEnhanced、Workspace、BackupRestore、UpdateEngine、HotUpdate。
- **组件**（`ui/component/`）：Dialog、GlassEffect（haze 玻璃态）、SearchBar、SettingsItem、ShortcutDialog、AboutCard、KeyEventBlocker、profile/RootProfileConfig、profile/AppProfileConfig、profile/TemplateConfig。
- **ViewModel**（`ui/viewmodel/`）：SuperUserViewModel（经 KsuService 加载 app 列表）、ModuleViewModel、TemplateViewModel。
- **工具**（`ui/util/`）：KsuCli（libsu root shell + ksud CLI 包装，`execKsud`/`install`/`listModules`/`flashModule`/`setSepolicy`/`isAbDevice` 等）、KernelDetect、LocaleHelper、ResponsiveLayout、Downloader、SELinuxChecker、VibrationHelper、AntiBrickService、SecurityAuditInterface、IdentitySpoofInterface、VFS* 系列（VFSKernelInterface/VFSCommEngine/VFSRuleEngine/VFSPersistenceManager 等）、HanziToPinyin（Java）。

### 7.5 WebUI（ui/webui/）
- [WebUIActivity.kt](file:///workspace/manager/app/src/main/java/com/ztros/ztrosu/ui/webui/WebUIActivity.kt) — WebView 宿主，加载 `https://mui.kernelsu.org/index.html`，经 `WebViewAssetLoader` + `SuFilePathHandler` 从 `/data/adb/modules/$moduleId/webroot` 读文件（root shell）。注入 `eruda.min.js` 调试控制台。
- [WebViewInterface.kt](file:///workspace/manager/app/src/main/java/com/ztros/ztrosu/ui/webui/WebViewInterface.kt) — JS 接口名 `ksu`：`exec`/`spawn`（root shell）、`toast`/`fullScreen`/`moduleInfo`/`createShortcut`/`listPackages`/`getPackagesInfo`/`cacheAllPackageIcons`/文件 IO（read/write/move/copy/remove + FileOutputStream 流式）。
- [SuFilePathHandler.java](file:///workspace/manager/app/src/main/java/com/ztros/ztrosu/ui/webui/SuFilePathHandler.java) — `PathHandler` 用 `SuFile`+`SuFileInputStream` 读 root 文件；禁止 `/data/data`/`/data/system`；服务动态 `insets.css`/`colors.css`。
- [AppIconUtil.java](file:///workspace/manager/app/src/main/java/com/ztros/ztrosu/ui/webui/AppIconUtil.java) — LruCache(200) 图标缓存，服务 `ksu://icon/<pkg>`。
- [MonetColorsProvider.kt](file:///workspace/manager/app/src/main/java/com/ztros/ztrosu/ui/webui/MonetColorsProvider.kt) — Material You CSS 变量（AMOLED 感知）。

### 7.6 主题（ui/theme/）
- [Color.kt](file:///workspace/manager/app/src/main/java/com/ztros/ztrosu/ui/theme/Color.kt) — Catppuccin 调色 + 7 套预设（ice_abyss/blood_moon/heavenly_palace/azure_sky/fresh_lemon/dragon_fruit/divine_yellow）。
- [Theme.kt](file:///workspace/manager/app/src/main/java/com/ztros/ztrosu/ui/theme/Theme.kt) — 动态色、AMOLED、预设、强调色、字体/圆角/海拔缩放。

### 7.7 App 整体架构

- **App ↔ 内核**：`Natives.kt`(Java) → `jni.cc`(JNI) → `ksu.cc`(`ksuctl`→`ioctl`)。driver fd 由 ksud/init 预开继承。SuperKey 走 `__NR_reboot` 原始系统调用。
- **App ↔ root**：libsu（core/service/io）。`KernelSUApplication` 设全局 `Shell.Builder = createRootShellBuilder(true)`，命令为 `<nativeLibraryDir>/libksud.so debug su [-g] || su [--mount-master] || sh`。多数特权操作委托 `ksud`（`KsuCli.kt` 包装）。
- **KsuService 用途**：libsu `RootService`（在 root 进程跑）。Manager 无权枚举其他用户包，故 `SuperUserViewModel` 经 `RootService.bindOrTask` 绑定 `KsuService`，调 `getPackages(0)` 拿全用户 `PackageInfo`，与 `Natives.getAppProfile()` 拼成 SuperUser 列表。
- **Compose 导航**：KSP 生成类型安全 `NavGraphs.root`，无手写 NavHost 路由；`MainActivity` 用自定义 `NavHostAnimatedDestinationStyle` 提供方向性滑动转场。

---

## 8. 辅助子系统（susfsd / scripts / uapi / website）

### 8.1 susfsd（userspace/susfsd/）
- [userspace/susfsd/jni/susfsd.c](file:///workspace/userspace/susfsd/jni/susfsd.c) — C JNI 守护，ndk-build 产物 `libsusfsd.so` 打入 Manager APK 的 `arm64-v8a`/`x86_64` jniLibs。提供 SusFS 相关接口。

### 8.2 scripts/
- [scripts/wksubot.py](file:///workspace/scripts/wksubot.py) — Telegram bot，CI 构建后上传 APK 到频道。
- [scripts/add_device_handler.py](file:///workspace/scripts/add_device_handler.py) — 处理 `.github/ISSUE_TEMPLATE/add_device.yml` 提交的设备添加请求。
- [scripts/allowlist.bt](file:///workspace/scripts/allowlist.bt) — allowlist 相关数据。

### 8.3 uapi/
- [uapi/supercall.h](file:///workspace/uapi/supercall.h) — KPM 控制码、ioctl 命令、SuperKey 常量、sulog 事件结构（内核与用户空间共享）。
- [uapi/sulog.h](file:///workspace/uapi/sulog.h) — sulog 事件类型/标志/结构定义。

### 8.4 website/docs/
文档站点（多语言），含 `guide/metamodule.md`、`guide/x86_64-support.md` 等。

### 8.5 docs/
多语言 README（17 种语言）+ `MANUAL_HOOK_INTEGRATION.md`、`VFS_KERNEL_MODULE_SPEC.md`、`WebUi_Next/API_DOC.md`。

---

## 9. 依赖关系

### 9.1 组件间依赖

```
Manager App ──JNI──► 内核模块（ioctl）
          ──libsu──► ksud daemon（exec）
                        │
                        ▼
                     内核模块（ioctl via ksucalls.rs）

内核模块 ──execve 重写──► ksud（/system/bin/su → /data/adb/ksud）
         ──init.rc 注入──► ksud post-fs-data/services/boot-completed
```

- **Manager → 内核**：直连 ioctl（不经 ksud），用于版本/profile/特性查询与设置。
- **Manager → ksud**：经 libsu root shell 执行 `ksud <subcommand>`，用于模块/策略/profile/boot-info/su shell。
- **内核 → ksud**：通过 execve 重写（sucompat）与 init.rc 注入（ksud.c RC 代理）。
- **ksud → 内核**：经 `[ksu_driver]` fd 的 ioctl（ksucalls.rs）。

### 9.2 内核模块内部依赖

| 模块 | 依赖 |
|---|---|
| supercalls.c | allowlist, app_profile, feature, ksud, kernel_umount, manager, selinux, file_wrapper, tp_marker, tiny_sulog, kpm, uts_spoof, sulog/fd |
| hook/lsm_hook.c | allowlist, app_profile, kernel_umount, manager, sucompat |
| sucompat.c | allowlist, app_profile, ksud, selinux, sulog |
| ksud.c | allowlist, selinux, app_profile, manager/throne_tracker, extras |
| app_profile.c | allowlist, selinux, su_mount_ns, tp_marker |
| selinux/rules.c | selinux/sepolicy.c |
| manager/throne_tracker.c | manager/apk_sign.c, allowlist |
| extras.c | selinux |

### 9.3 ksud 内部依赖

| 模块 | 依赖 |
|---|---|
| cli.rs | 几乎全部（apk_sign/assets/boot_info/debug/defs/init_event/ksucalls/module/module_config/utils/sepolicy/profile/feature/resetprop/su） |
| ksucalls.rs | 仅 libc |
| module.rs | utils, assets, defs, ksucalls, metamodule, restorecon, sepolicy, resetprop, module_config |
| init_event.rs | module, utils, assets, defs, ksucalls, metamodule, restorecon, module_config, feature, profile, resetprop |
| su.rs | ksucalls, defs, utils |
| feature.rs | defs, ksucalls, module |

### 9.4 外部依赖

**Rust（[userspace/ksud/Cargo.toml](file:///workspace/userspace/ksud/Cargo.toml)）：**
`anyhow`、`clap`、`const_format`、`log`、`env_logger`、`rust-embed`、`which`、`sha1`、`sha256`、`tempfile`、`chrono`、`regex-lite`。
Android-only：`rustix`(all-apis)、`android-properties`、`android_logger`、`zip`/`zip-extensions`、`java-properties`(git)、`serde_json`、`encoding_rs`、`humansize`、`libc`、`extattr`、`jwalk`、`is_executable`、`nom`、`derive-new`、`getopts`、`prop-rs-android`(git)。
Release profile：`strip=true`、`opt-level="z"`、`lto=true`、`codegen-units=1`。

**Android（[manager/gradle/libs.versions.toml](file:///workspace/manager/gradle/libs.versions.toml)）：**
AGP 8.13.1、Kotlin 2.2.0、Compose BOM 2025.07.00、Compose Destinations 2.2.0（KSP）、libsu 6.0.0、Coil 2.7.0、Markwon 4.6.2、webkit 1.14.0、haze 1.7.1、vico 2.0.0-beta.5、glance 1.1.1、mmrl-ui、lsposed-cxx 28.1.13356709、apksign 1.4（LSPosed plugin）、cmaker 1.2。
NDK 29.0.14206865。Java/Kotlin 21。

**内核：** 依赖 `EXT4_FS`（`ext4_unregister_sysfs`）、`SECURITY_SELINUX`、`FSNOTIFY`、可选 `CONFIG_KSU_DEBUG`/`CONFIG_KSU_VFS_DEBUG`。

---

## 10. 构建与运行方式

### 10.1 本地构建

**入口脚本 [build.sh](file:///workspace/build.sh)：**
```bash
# 1. 构建 ksud（aarch64-linux-android release）
cross build --target aarch64-linux-android --release --manifest-path ./userspace/ksud/Cargo.toml
cp userspace/ksud/target/.../release/ksud manager/app/src/main/jniLibs/arm64-v8a/libksud.so

# 2. 构建 susfsd（ndk-build）
cd userspace/susfsd/jni && ndk-build
cp ../libs/arm64-v8a/susfsd ../../../manager/app/src/main/jniLibs/arm64-v8a/libsusfsd.so

# 3. 构建 Manager APK
cd manager && ./setup.sh
# 产物：manager/app/build/outputs/apk/release/ZTR_OS_SU_*.apk
```

**[justfile](file:///workspace/justfile) 别名：**
- `just build_ksud`（`bk`）— 仅构建 ksud
- `just build_manager`（`bm`）— 构建 ksud → 复制 → `gradlew aDebug`
- `just clippy` — fmt + clippy（windows + android target）

**Manager 签名（[manager/sign.example.properties](file:///workspace/manager/sign.example.properties)）：**
复制为 `sign.properties`，填 `KEYSTORE_FILE`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD`。CI 无 secrets 时用确定性 AuroraSU CI keystore（`aurorasu2025`），其哈希需与内核 `EXPECTED_HASH` 匹配。

### 10.2 CI 构建（.github/workflows/）

- [build-manager.yml](file:///workspace/.github/workflows/build-manager.yml) — 总入口：
  1. `build-susfsd`（调用 susfsd.yml）
  2. `build-ksud`（矩阵 `aarch64-linux-android` + `x86_64-linux-android`，调 ksud.yml）
  3. `build-ksud-extra`（矩阵 x86_64-pc-windows-gnu / aarch64-apple-darwin / x86_64-apple-darwin / musl）
  4. `build-manager`（依赖 ksud+susfsd）：下载产物 → 复制到 jniLibs → `./gradlew clean assembleRelease` → 上传 APK + mappings → 可选 Telegram 上传
- [ksud.yml](file:///workspace/.github/workflows/ksud.yml) — `cross build --target $target --release`，缓存 `userspace/ksud`。
- 其他：`build-kernel.yml`、`susfsd.yml`、`release.yml`、`clang-format.yml`、`clippy.yml`、`rustfmt.yml`、`shellcheck.yml`。
- [dependabot.yml](file:///workspace/.github/dependabot.yml) — 依赖自动更新。

### 10.3 内核集成

**方式 A：GKI 内置（推荐）**
1. 在内核源码树根运行 `kernel/setup.sh`（克隆仓库 → 软链 `kernel/` 为 `drivers/kernelsu` → 改 `Makefile`/`Kconfig`）。
2. 内核配置启用 `CONFIG_KSU=y`（`CONFIG_KSU_DEBUG`/`CONFIG_KSU_VFS_DEBUG` 可选）。
3. 编译内核。

**方式 B：手动补丁（非 GKI 或需精细控制）**
1. 运行 `kernel/tools/auto_patch.sh -p <kernel-path>` 应用 `patches/gki/` 下的 exec/open/stat/stat_ret/reboot 补丁。
2. 详见 [GKI_MANUAL_HOOK_README.md](file:///workspace/GKI_MANUAL_HOOK_README.md) 与 [docs/MANUAL_HOOK_INTEGRATION.md](file:///workspace/docs/MANUAL_HOOK_INTEGRATION.md)。

### 10.4 安装与运行

1. 刷入内置 KSU 的内核（或加载 LKM）。
2. 安装 Manager APK（`ZTR_OS_SU_*.apk`）。
3. Manager 启动 → 探测内核 → 设置 SuperKey → 内核"加冕" Manager UID（throne_tracker）。
4. Manager 通过 `ksud install` 将 daemon 安装到 `/data/adb/ksud`（由内核 init.rc 触发，或用户手动）。
5. 重启后 `ksud post-fs-data`/`services`/`boot-completed` 由 init 自动调用，编排模块挂载与策略应用。

### 10.5 版本号规则

- 内核 `KSU_VERSION = 30000 + git_commit_count`（[kernel/Kbuild](file:///workspace/kernel/Kbuild)）。
- Manager `managerVersionCode = 1 * 30000 + git_commit_count`，`managerVersionName = git describe --tags --always`（[manager/build.gradle.kts](file:///workspace/manager/build.gradle.kts)）。

---

## 11. 关键流程时序

### 11.1 Manager 启动与认证

```
Manager App 启动
  │  KernelSUApplication.onCreate()
  │  - createRootShellBuilder(true)  (libsu)
  │  - Coil/OkHttpClient/webroot 初始化
  │  - 后台协程: Natives.getVersionTag() / isSuperKeyActive()
  ▼
Natives.setSuperKey(key)  ──syscall(__NR_reboot,0xfee1dead,10020,...)──► 内核存 key
  ▼
内核 task_fix_setuid hook 检测 Manager UID 切换
  │  - disable_seccomp()
  │  - ksu_install_fd()  (被动安装 [ksu_driver] fd)
  ▼
Manager 持有 [ksu_driver] fd → 后续 ioctl 直连内核
  ▼
Natives.getAppProfile / setAppProfile / getFeature / ...  (ioctl)
```

### 11.2 启动后 ksud 编排（on_post_data_fs）

```
init second_stage execve
  │  ksu_handle_execveat_ksud_path 检测 "/system/bin/init second_stage"
  ▼
apply_kernelsu_rules()  (selinux/rules.c: dup 策略 → 应用基线 → RCU swap → reset_avc)
cache_sid() / setup_ksu_cred()
  │
zygote execve "/system/bin/app_process -Xzygote"
  │  → on_post_fs_data()
  ▼
init 读取 init.rc  → ksu_install_rc_hook 注入 KERNEL_SU_RC
  │
init 执行 "exec u:r:su:s0 root /data/adb/ksud post-fs-data"
  ▼
ksud post-fs-data  → init_event::on_post_data_fs():
  1. ksucalls::report_post_fs_data()
  2. module_config::clear_all_temp_configs()
  3. (safe mode?) module::disable_all_modules() + return
  4. module::handle_updated_modules()  (modules_update → modules)
  5. module::prune_modules()  (remove 标志 → metauninstall + uninstall.sh + rm)
  6. restorecon::restorecon()
  7. module::load_sepolicy_rule()  (apply_file → SET_SEPOLICY ioctl)
  8. profile::apply_sepolies()
  9. feature::init_features()  (跳过模块管理特性, 推其余 → SET_FEATURE)
  10. metamodule::exec_stage_script("post-fs-data") + module::exec_stage_script("post-fs-data")
  11. module::load_system_prop()  (resetprop -n --file system.prop)
  12. metamodule::exec_mount_script()  (metamount.sh 实际挂载)
  13. run_stage("post-mount")
  14. chdir("/")

on nonencrypted / vold.decrypt=trigger_restart_framework → ksud services → on_services()
sys.boot_completed=1 → ksud boot-completed → on_boot_completed()
```

### 11.3 SU 请求处理

```
应用 execve("/system/bin/su")
  ▼
内核 sucompat hook: ksu_handle_execveat_sucompat
  │  is_su_allowed?  (su_compat enabled + seccomp unset + allowlisted uid)
  ├─ 否: 放行原 su（通常不存在）或拒绝
  └─ 是:
      1. ksu_sulog_capture_root_execve(...)  (审计)
      2. escape_with_root_profile()  (cred 变换 + selinux + seccomp + mount_ns)
      3. 重写 filename → "/data/adb/ksud", argv[0]="su"
  ▼
ksud 多调用检测 (cli::run: argv[0]=="su")  → su::root_shell()
  │  - getopts 解析 -c/-l/-p/-s/-v/-V/-M/-g/-G/-W
  │  - 解析 uid (getpwnam 或数字)
  │  - 设 HOME/USER/LOGNAME/SHELL, PATH += BINARY_DIR
  │  - pre_exec: umask(0o22) + switch_cgroups + 可选全局 mount ns + wrap_tty + set_identity
  ▼
exec /system/bin/sh  (root shell)
```

### 11.4 模块安装

```
Manager: KsuCli.install(zip)  ──libsu──► ksud module install <zip>
  ▼
module::install_module_to_system(zip):
  1. 解压 module.prop → 解析 → validate_module_id
  2. 检测 metamodule; 常规模块则 metamodule::check_install_safety()
  3. 创建 MODULE_UPDATE_DIR/<id>, 解压全文, restorecon system/
  4. 执行安装脚本 (metamodule 的 metainstall.sh 或内置 installer.sh + "install_module\nexit 0")
  5. 复制 module.prop → MODULE_DIR/<id>, 建 "update" 标志
  6. metamodule 则 ensure_symlink
  ▼
下次启动 on_post_data_fs → handle_updated_modules() 移动到 MODULE_DIR 生效
```

---

## 12. 配置项与特性开关

### 12.1 内核 Kconfig（[kernel/Kconfig](file:///workspace/kernel/Kconfig)）

| 配置 | 依赖 | 默认 | 说明 |
|---|---|---|---|
| `CONFIG_KSU` | `EXT4_FS` | y | 主功能（手动 LSM hook） |
| `CONFIG_KSU_DEBUG` | `KSU` | n | 调试日志 + `ksu_debug_manager_appid` 模块参数 |
| `CONFIG_KSU_VFS_DEBUG` | `KSU` | n | VFS 调试子系统 + `/sys/kernel/ztrosu/vfs` |

### 12.2 Kbuild 环境变量（[kernel/Kbuild](file:///workspace/kernel/Kbuild)）

| 变量 | 默认 | 说明 |
|---|---|---|
| `KSU_EXPECTED_SIZE` | `0x35c` | Manager 签名证书 size |
| `KSU_EXPECTED_HASH` | `947ae944...` | Manager 签名证书 SHA-256 |
| `KSU_EXPECTED_SIZE2`/`KSU_EXPECTED_HASH2` | — | 第二签名（AuroraSU 自有 Manager） |
| `KSU_MANAGER_PACKAGE` | — | Manager 包名（可选校验） |

### 12.3 运行时特性（feature.c，经 GET/SET_FEATURE ioctl）

| ID | 名称 | 说明 |
|---|---|---|
| 0 | `su_compat` | SU 兼容模式（execve su 重定向） |
| 1 | `kernel_umount` | App 进程模块卸载 |
| 4 | `selinux_hide` | SELinux 隐藏 |
| 10003 | `avc_spoof` | AVC 审计伪造 |

特性可被模块"管理"（`module.prop` 声明 `manage.<feature>`），此时 Manager 不能直接改，由模块控制。

### 12.4 关键运行时路径

| 路径 | 用途 |
|---|---|
| `/data/adb/ksud` | daemon 二进制 |
| `/data/adb/ksu/` | 工作目录（bin/、log/、profile/、.allowlist、.feature_config） |
| `/data/adb/ksu/bin/` | busybox、resetprop→ksud、ksud→/data/adb/ksud |
| `/data/adb/modules/` | 已安装模块 |
| `/data/adb/modules_update/` | 待生效模块 |
| `/data/adb/metamodule/` | 活跃 metamodule 软链 |
| `/data/adb/modules/<id>/{disable,update,remove}` | 模块状态标志文件 |
| `/sys/module/kernelsu/parameters/ksu_debug_manager_uid` | 调试用 Manager UID（CONFIG_KSU_DEBUG） |
| `/sys/fs/selinux/enforce` | SELinux enforcing 状态 |
| `/sys/kernel/ztrosu/vfs/` | VFS 调试 sysfs（CONFIG_KSU_VFS_DEBUG） |
| `/data/system/packages.list` | 包列表（throne_tracker 监视） |

---

## 附录：版本与溯源

- **品牌**：`ZTR_OS SU`（setup.sh、sysfs `ztrosu`、README）、`AuroraSU`（SPDX 头、CI keystore、KPM version 字符串）。
- **上游**：KernelSU（tiann/KernelSU）、SukiSU-Ultra（KPM/uts_spoof/sulog 规格）。
- **SELinux 域**：刻意保留 `"su"`（非 `"ksu"`）以兼容策略。
- **最小支持内核**：Manager 端 `MINIMAL_SUPPORTED_KERNEL = 33075` / `v4.0.0`（[Natives.kt](file:///workspace/manager/app/src/main/java/com/ztros/ztrosu/Natives.kt)）。
- **目标 Android**：minSdk 26（Android 8.0）/ targetSdk 36 / compileSdk 36。
- **支持架构**：`arm64-v8a`（主）、`x86_64`。
- **多语言**：README 17 种语言（Crowdin 协作，[crowdin.yml](file:///workspace/crowdin.yml)）。
