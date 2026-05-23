/*
 * AuroraSU - Userspace API Interface
 * 
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

use std::fs;
use std::os::fd::RawFd;
use std::sync::OnceLock;
use anyhow::{Result, Context};
use log::{info, debug, error};

// Include the UAPI header
pub const AURORA_IOCTL_MAGIC: u8 = b'A';
pub const AURORA_INSTALL_MAGIC1: u32 = 0x4155524F;
pub const AURORA_INSTALL_MAGIC2: u32 = 0x52415355;

pub const AURORA_EVENT_POST_FS_DATA: u32 = 1;
pub const AURORA_EVENT_BOOT_COMPLETED: u32 = 2;
pub const AURORA_EVENT_MODULE_MOUNTED: u32 = 3;
pub const AURORA_EVENT_SAFE_MODE: u32 = 4;

pub const AURORA_FEATURE_ID_SU_COMPAT: u32 = 0;
pub const AURORA_FEATURE_ID_LOG: u32 = 1;
pub const AURORA_FEATURE_ID_HIDE_ROOT: u32 = 2;
pub const AURORA_FEATURE_ID_MOUNT_MASTER: u32 = 3;
pub const AURORA_FEATURE_ID_OVERLAY_FS: u32 = 4;
pub const AURORA_FEATURE_ID_WEBUI: u32 = 5;

// Feature flags from kernel
pub const AURORA_FEATURE_KPM: u32 = 1 << 0;
pub const AURORA_FEATURE_MANUAL_HOOK: u32 = 1 << 1;
pub const AURORA_FEATURE_SUSFS: u32 = 1 << 2;
pub const AURORA_FEATURE_DUAL_MODE: u32 = 1 << 3;
pub const AURORA_FEATURE_WEBUI_NEXT: u32 = 1 << 4;
pub const AURORA_FEATURE_REDUNDANCY: u32 = 1 << 5;

// Hook modes
#[derive(Debug, Clone, Copy, PartialEq)]
#[repr(u32)]
pub enum HookMode {
    Auto = 0,
    Kprobe = 1,
    Manual = 2,
    Hybrid = 3,
}

// Redundancy states
#[derive(Debug, Clone, Copy, PartialEq)]
#[repr(u32)]
pub enum RedundancyState {
    Healthy = 0,
    Degraded = 1,
    Critical = 2,
    Failed = 3,
}

#[derive(Debug, Clone)]
pub struct AuroraInfo {
    pub version: u32,
    pub flags: u32,
    pub features: u32,
    pub hook_mode: HookMode,
    pub version_string: String,
}

#[derive(Debug, Clone)]
pub struct RedundancyStatus {
    pub state: RedundancyState,
    pub health_flags: u32,
    pub in_safe_mode: bool,
    pub auto_recovery: bool,
}

#[derive(Debug, Clone)]
pub struct AppProfile {
    pub uid: u32,
    pub flags: u32,
    pub root_uid: u32,
    pub root_gid: u32,
    pub capabilities: u64,
}

// Global driver FD cache
static DRIVER_FD: OnceLock<RawFd> = OnceLock::new();

#[derive(Clone)]
pub struct AuroraDriver {
    fd: RawFd,
}

impl AuroraDriver {
    pub fn new() -> Result<Self> {
        let fd = *DRIVER_FD.get_or_init(|| {
            Self::init_driver_fd().unwrap_or(-1)
        });
        
        if fd < 0 {
            anyhow::bail!("Failed to initialize Aurora driver connection");
        }
        
        Ok(Self { fd })
    }
    
    fn init_driver_fd() -> Option<RawFd> {
        // First, try to find existing fd
        if let Some(fd) = Self::scan_driver_fd() {
            info!("Found existing Aurora driver fd: {}", fd);
            return Some(fd);
        }
        
        // Otherwise, install new fd via reboot syscall
        info!("Installing Aurora driver fd via reboot syscall...");
        let mut fd: i32 = -1;
        let ret = unsafe {
            libc::syscall(
                libc::SYS_reboot,
                AURORA_INSTALL_MAGIC1,
                AURORA_INSTALL_MAGIC2,
                0,
                &mut fd as *mut i32,
            )
        };
        
        if ret == 0 && fd >= 0 {
            info!("Aurora driver fd installed: {}", fd);
            Some(fd)
        } else {
            error!("Failed to install Aurora driver fd, ret={}", ret);
            None
        }
    }
    
    fn scan_driver_fd() -> Option<RawFd> {
        let fd_dir = fs::read_dir("/proc/self/fd").ok()?;
        
        for entry in fd_dir.flatten() {
            if let Ok(fd_num) = entry.file_name().to_string_lossy().parse::<i32>() {
                let link_path = format!("/proc/self/fd/{}", fd_num);
                if let Ok(target) = fs::read_link(&link_path) {
                    let target_str = target.to_string_lossy();
                    if target_str.contains("[aurora_driver]") {
                        return Some(fd_num);
                    }
                }
            }
        }
        
        None
    }
    
    fn ioctl<T>(&self, request: u32, arg: *mut T) -> Result<i32> {
        let ret = unsafe {
            libc::ioctl(self.fd, request as libc::c_ulong, arg)
        };
        
        if ret < 0 {
            Err(std::io::Error::last_os_error().into())
        } else {
            Ok(ret)
        }
    }
    
    pub fn get_info(&self) -> Result<AuroraInfo> {
        #[repr(C)]
        struct InfoCmd {
            version: u32,
            flags: u32,
            features: u32,
            hook_mode: u32,
            version_full: [u8; 64],
        }
        
        let mut cmd = InfoCmd {
            version: 0,
            flags: 0,
            features: 0,
            hook_mode: 0,
            version_full: [0; 64],
        };
        
        let request = Self::make_ioctl(2, true, false); // GET_INFO
        self.ioctl(request, &mut cmd)?;
        
        let version_string = String::from_utf8_lossy(&cmd.version_full)
            .trim_end_matches('\0')
            .to_string();
        
        Ok(AuroraInfo {
            version: cmd.version,
            flags: cmd.flags,
            features: cmd.features,
            hook_mode: match cmd.hook_mode {
                1 => HookMode::Kprobe,
                2 => HookMode::Manual,
                3 => HookMode::Hybrid,
                _ => HookMode::Auto,
            },
            version_string,
        })
    }
    
    pub fn report_event(&self, event: u32) -> Result<()> {
        #[repr(C)]
        struct EventCmd {
            event: u32,
        }
        
        let mut cmd = EventCmd { event };
        let request = Self::make_ioctl(3, false, true); // REPORT_EVENT
        self.ioctl(request, &mut cmd)?;
        
        Ok(())
    }
    
    pub fn check_safemode(&self) -> Result<bool> {
        let mut in_safe_mode: u8 = 0;
        let request = Self::make_ioctl(5, true, false); // CHECK_SAFEMODE
        self.ioctl(request, &mut in_safe_mode)?;
        
        Ok(in_safe_mode != 0)
    }
    
    pub fn grant_root(&self) -> Result<()> {
        let request = Self::make_ioctl(1, false, false); // GRANT_ROOT
        self.ioctl::<u8>(request, std::ptr::null_mut())?;
        Ok(())
    }
    
    pub fn get_app_profile(&self, uid: u32) -> Result<AppProfile> {
        #[repr(C)]
        struct ProfileCmd {
            uid: u32,
            flags: u32,
            root_uid: u32,
            root_gid: u32,
            groups_count: u32,
            groups: [u32; 32],
            capabilities: u64,
            namespace: [u8; 64],
        }
        
        let mut cmd = ProfileCmd {
            uid,
            flags: 0,
            root_uid: 0,
            root_gid: 0,
            groups_count: 0,
            groups: [0; 32],
            capabilities: 0,
            namespace: [0; 64],
        };
        
        let request = Self::make_ioctl(10, true, true); // GET_APP_PROFILE
        self.ioctl(request, &mut cmd)?;
        
        Ok(AppProfile {
            uid: cmd.uid,
            flags: cmd.flags,
            root_uid: cmd.root_uid,
            root_gid: cmd.root_gid,
            capabilities: cmd.capabilities,
        })
    }
    
    pub fn set_app_profile(&self, profile: &AppProfile) -> Result<()> {
        #[repr(C)]
        struct ProfileCmd {
            uid: u32,
            flags: u32,
            root_uid: u32,
            root_gid: u32,
            groups_count: u32,
            groups: [u32; 32],
            capabilities: u64,
            namespace: [u8; 64],
        }
        
        let mut cmd = ProfileCmd {
            uid: profile.uid,
            flags: profile.flags,
            root_uid: profile.root_uid,
            root_gid: profile.root_gid,
            groups_count: 0,
            groups: [0; 32],
            capabilities: profile.capabilities,
            namespace: [0; 64],
        };
        
        let request = Self::make_ioctl(11, false, true); // SET_APP_PROFILE
        self.ioctl(request, &mut cmd)?;
        
        Ok(())
    }
    
    pub fn get_feature(&self, feature_id: u32) -> Result<(u64, bool)> {
        #[repr(C)]
        struct FeatureCmd {
            feature_id: u32,
            value: u64,
            supported: u8,
        }
        
        let mut cmd = FeatureCmd {
            feature_id,
            value: 0,
            supported: 0,
        };
        
        let request = Self::make_ioctl(12, true, true); // GET_FEATURE
        self.ioctl(request, &mut cmd)?;
        
        Ok((cmd.value, cmd.supported != 0))
    }
    
    pub fn set_feature(&self, feature_id: u32, value: u64) -> Result<()> {
        #[repr(C)]
        struct FeatureCmd {
            feature_id: u32,
            value: u64,
        }
        
        let mut cmd = FeatureCmd {
            feature_id,
            value,
        };
        
        let request = Self::make_ioctl(13, false, true); // SET_FEATURE
        self.ioctl(request, &mut cmd)?;
        
        Ok(())
    }
    
    pub fn get_redundancy_status(&self) -> Result<RedundancyStatus> {
        #[repr(C)]
        struct RedundancyCmd {
            state: u32,
            health_flags: u32,
            in_safe_mode: u8,
            auto_recovery: u8,
        }
        
        let mut cmd = RedundancyCmd {
            state: 0,
            health_flags: 0,
            in_safe_mode: 0,
            auto_recovery: 0,
        };
        
        let request = Self::make_ioctl(19, true, false); // GET_REDUNDANCY
        self.ioctl(request, &mut cmd)?;
        
        Ok(RedundancyStatus {
            state: match cmd.state {
                1 => RedundancyState::Degraded,
                2 => RedundancyState::Critical,
                3 => RedundancyState::Failed,
                _ => RedundancyState::Healthy,
            },
            health_flags: cmd.health_flags,
            in_safe_mode: cmd.in_safe_mode != 0,
            auto_recovery: cmd.auto_recovery != 0,
        })
    }
    
    pub fn trigger_recovery(&self) -> Result<()> {
        let request = Self::make_ioctl(20, false, false); // TRIGGER_RECOVERY
        self.ioctl::<u8>(request, std::ptr::null_mut())?;
        Ok(())
    }
    
    fn make_ioctl(nr: u8, read: bool, write: bool) -> u32 {
        let mut request: u32 = 0;
        request |= (AURORA_IOCTL_MAGIC as u32) << 8;
        request |= nr as u32;
        
        if !read && !write {
            request |= 0 << 30; // _IOC_NONE
        } else if read && !write {
            request |= 2 << 30; // _IOC_READ
        } else if !read && write {
            request |= 1 << 30; // _IOC_WRITE
        } else {
            request |= 3 << 30; // _IOC_READ | _IOC_WRITE
        }
        
        request
    }
}

unsafe impl Send for AuroraDriver {}
unsafe impl Sync for AuroraDriver {}
