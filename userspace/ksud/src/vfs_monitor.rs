/* SPDX-License-Identifier: GPL-2.0 */
/*
 * User-space VFS Monitor for AuroraSU
 * Provides VFS debugging without kernel module
 * Uses ptrace + /proc/pid/fd monitoring
 */

use std::collections::HashMap;
use std::fs::{self, File, OpenOptions};
use std::io::{self, BufRead, BufReader, Write};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::defs::KSU_WORK_DIR;

/// VFS operation types
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum VFSOpType {
    Open = 0,
    Read,
    Write,
    Close,
}

impl VFSOpType {
    pub fn from_u8(v: u8) -> Option<Self> {
        match v {
            0 => Some(VFSOpType::Open),
            1 => Some(VFSOpType::Read),
            2 => Some(VFSOpType::Write),
            3 => Some(VFSOpType::Close),
            _ => None,
        }
    }
}

/// VFS Statistics
#[derive(Debug, Clone, Default)]
pub struct VFSStats {
    pub open_count: u64,
    pub read_count: u64,
    pub write_count: u64,
    pub close_count: u64,
    pub denied_count: u64,
    pub last_updated: u64,
}

/// VFS Rule
#[derive(Debug, Clone)]
pub struct VFSRule {
    pub action: VFSAction,
    pub path_pattern: String,
    pub mode_mask: u32, // 1=read, 2=write, 3=rw
    pub enabled: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum VFSAction {
    Allow = 0,
    Deny = 1,
}

/// VFS Policy
#[derive(Debug, Clone)]
pub struct VFSPolicy {
    pub enabled: bool,
    pub log_level: u32,
    pub default_action: VFSAction,
    pub rules: Vec<VFSRule>,
}

impl Default for VFSPolicy {
    fn default() -> Self {
        VFSPolicy {
            enabled: false,
            log_level: 0,
            default_action: VFSAction::Allow,
            rules: Vec::new(),
        }
    }
}

/// VFS Monitor State
pub struct VFSMonitor {
    stats: Arc<Mutex<VFSStats>>,
    policy: Arc<Mutex<VFSPolicy>>,
    running: Arc<std::sync::atomic::AtomicBool>,
    work_dir: PathBuf,
}

impl VFSMonitor {
    pub fn new() -> Self {
        let work_dir = PathBuf::from(KSU_WORK_DIR).join("vfs_monitor");
        
        // Create work directory
        let _ = fs::create_dir_all(&work_dir);
        
        VFSMonitor {
            stats: Arc::new(Mutex::new(VFSStats::default())),
            policy: Arc::new(Mutex::new(VFSPolicy::default())),
            running: Arc::new(std::sync::atomic::AtomicBool::new(false)),
            work_dir,
        }
    }

    /// Initialize VFS monitor
    pub fn init(&self) -> io::Result<()> {
        // Create state files
        self.save_stats()?;
        self.save_policy()?;
        Ok(())
    }

    /// Start monitoring
    pub fn start(&self) -> io::Result<()> {
        if self.running.load(Ordering::SeqCst) {
            return Ok(());
        }

        self.running.store(true, Ordering::SeqCst);
        
        // Start monitoring threads
        let stats = self.stats.clone();
        let policy = self.policy.clone();
        let running = self.running.clone();
        let work_dir = self.work_dir.clone();
        
        // Thread 1: Monitor /proc for file operations
        thread::spawn(move || {
            Self::monitor_proc_loop(stats.clone(), policy.clone(), running.clone());
        });
        
        // Thread 2: Periodically save stats
        let stats2 = self.stats.clone();
        let running2 = self.running.clone();
        let work_dir2 = self.work_dir.clone();
        thread::spawn(move || {
            Self::save_stats_loop(stats2, running2, work_dir2);
        });
        
        log::info!("VFS Monitor started");
        Ok(())
    }

    /// Stop monitoring
    pub fn stop(&self) {
        self.running.store(false, Ordering::SeqCst);
        log::info!("VFS Monitor stopped");
    }

    /// Get statistics
    pub fn get_stats(&self) -> VFSStats {
        self.stats.lock().unwrap().clone()
    }

    /// Reset statistics
    pub fn reset_stats(&self) {
        let mut stats = self.stats.lock().unwrap();
        *stats = VFSStats::default();
        stats.last_updated = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs();
        drop(stats);
        let _ = self.save_stats();
    }

    /// Get policy
    pub fn get_policy(&self) -> VFSPolicy {
        self.policy.lock().unwrap().clone()
    }

    /// Set enabled
    pub fn set_enabled(&self, enabled: bool) -> io::Result<()> {
        let mut policy = self.policy.lock().unwrap();
        policy.enabled = enabled;
        drop(policy);
        self.save_policy()
    }

    /// Set log level
    pub fn set_log_level(&self, level: u32) -> io::Result<()> {
        if level > 5 {
            return Err(io::Error::new(io::ErrorKind::InvalidInput, "Invalid log level"));
        }
        let mut policy = self.policy.lock().unwrap();
        policy.log_level = level;
        drop(policy);
        self.save_policy()
    }

    /// Set default action
    pub fn set_default_action(&self, action: VFSAction) -> io::Result<()> {
        let mut policy = self.policy.lock().unwrap();
        policy.default_action = action;
        drop(policy);
        self.save_policy()
    }

    /// Add rule
    pub fn add_rule(&self, rule_str: &str) -> io::Result<()> {
        // Parse: action:path:mode
        let parts: Vec<&str> = rule_str.split(':').collect();
        if parts.len() != 3 {
            return Err(io::Error::new(io::ErrorKind::InvalidInput, "Invalid rule format"));
        }

        let action = match parts[0] {
            "allow" => VFSAction::Allow,
            "deny" => VFSAction::Deny,
            _ => return Err(io::Error::new(io::ErrorKind::InvalidInput, "Invalid action")),
        };

        let mut mode_mask = 0u32;
        if parts[2].contains('r') {
            mode_mask |= 1;
        }
        if parts[2].contains('w') {
            mode_mask |= 2;
        }
        if mode_mask == 0 {
            return Err(io::Error::new(io::ErrorKind::InvalidInput, "Invalid mode"));
        }

        let rule = VFSRule {
            action,
            path_pattern: parts[1].to_string(),
            mode_mask,
            enabled: true,
        };

        let mut policy = self.policy.lock().unwrap();
        if policy.rules.len() >= 64 {
            return Err(io::Error::new(io::ErrorKind::Other, "Too many rules"));
        }
        policy.rules.push(rule);
        drop(policy);
        self.save_policy()
    }

    /// Clear rules
    pub fn clear_rules(&self) -> io::Result<()> {
        let mut policy = self.policy.lock().unwrap();
        policy.rules.clear();
        drop(policy);
        self.save_policy()
    }

    /// Check if path matches pattern
    fn path_matches(path: &str, pattern: &str) -> bool {
        // Simple glob matching
        let mut p = pattern.chars().peekable();
        let mut s = path.chars().peekable();

        while let Some(pc) = p.peek() {
            let pc = *pc;
            match pc {
                '*' => {
                    p.next();
                    if p.peek().is_none() {
                        return true;
                    }
                    while let Some(sc) = s.peek() {
                        if Self::path_matches(&s.clone().collect::<String>(), &p.clone().collect::<String>()) {
                            return true;
                        }
                        s.next();
                    }
                    return false;
                }
                '?' => {
                    p.next();
                    if s.next().is_none() {
                        return false;
                    }
                }
                _ => {
                    if s.next() != Some(pc) {
                        return false;
                    }
                    p.next();
                }
            }
        }

        p.next().is_none() && s.next().is_none()
    }

    /// Check access permission
    pub fn check_access(&self, path: &str, flags: i32) -> bool {
        let policy = self.policy.lock().unwrap();
        
        if !policy.enabled {
            return true;
        }

        // Determine required mode
        let req_mode = if (flags & 0o3) == 0o0 { // O_RDONLY
            1
        } else if (flags & 0o3) == 0o1 { // O_WRONLY
            2
        } else { // O_RDWR
            3
        };

        // Check rules
        for rule in &policy.rules {
            if !rule.enabled {
                continue;
            }
            if Self::path_matches(path, &rule.path_pattern) {
                if req_mode & rule.mode_mask != 0 {
                    return rule.action == VFSAction::Allow;
                }
            }
        }

        // Default action
        policy.default_action == VFSAction::Allow
    }

    /// Count operation
    fn count_op(&self, op: VFSOpType) {
        let mut stats = self.stats.lock().unwrap();
        match op {
            VFSOpType::Open => stats.open_count += 1,
            VFSOpType::Read => stats.read_count += 1,
            VFSOpType::Write => stats.write_count += 1,
            VFSOpType::Close => stats.close_count += 1,
        }
        stats.last_updated = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs();
    }

    /// Count denied
    fn count_denied(&self) {
        let mut stats = self.stats.lock().unwrap();
        stats.denied_count += 1;
    }

    /// Monitor /proc loop
    fn monitor_proc_loop(
        stats: Arc<Mutex<VFSStats>>,
        policy: Arc<Mutex<VFSPolicy>>,
        running: Arc<std::sync::atomic::AtomicBool>,
    ) {
        let monitor = VFSMonitor {
            stats: stats.clone(),
            policy: policy.clone(),
            running: running.clone(),
            work_dir: PathBuf::from(KSU_WORK_DIR).join("vfs_monitor"),
        };

        while running.load(Ordering::SeqCst) {
            // Scan /proc for processes and monitor their fd activity
            if let Ok(entries) = fs::read_dir("/proc") {
                for entry in entries.flatten() {
                    let path = entry.path();
                    if let Some(name) = path.file_name() {
                        if let Some(name_str) = name.to_str() {
                            // Check if it's a PID directory
                            if name_str.chars().all(|c| c.is_ascii_digit()) {
                                // Monitor this process's fd directory
                                let _ = Self::monitor_process_fds(
                                    &monitor,
                                    &path,
                                );
                            }
                        }
                    }
                }
            }

            // Sleep to avoid high CPU usage
            thread::sleep(std::time::Duration::from_millis(100));
        }
    }

    /// Monitor process file descriptors
    fn monitor_process_fds(monitor: &VFSMonitor, proc_path: &Path) -> io::Result<()> {
        let fd_path = proc_path.join("fd");
        if !fd_path.exists() {
            return Ok(());
        }

        let entries = match fs::read_dir(&fd_path) {
            Ok(e) => e,
            Err(_) => return Ok(()),
        };

        for entry in entries.flatten() {
            let fd_link = entry.path();
            if let Ok(target) = fs::read_link(&fd_link) {
                let target_str = target.to_string_lossy();
                
                // Count as open operation
                let policy = monitor.policy.lock().unwrap();
                if policy.enabled {
                    drop(policy);
                    monitor.count_op(VFSOpType::Open);
                    
                    // Check access
                    if !monitor.check_access(&target_str, 0) {
                        monitor.count_denied();
                    }
                }
            }
        }

        Ok(())
    }

    /// Save stats loop
    fn save_stats_loop(
        stats: Arc<Mutex<VFSStats>>,
        running: Arc<std::sync::atomic::AtomicBool>,
        work_dir: PathBuf,
    ) {
        while running.load(Ordering::SeqCst) {
            thread::sleep(std::time::Duration::from_secs(1));
            
            let stats_data = stats.lock().unwrap().clone();
            let stats_file = work_dir.join("stats");
            
            if let Ok(mut file) = File::create(&stats_file) {
                let _ = writeln!(file, "open: {}", stats_data.open_count);
                let _ = writeln!(file, "read: {}", stats_data.read_count);
                let _ = writeln!(file, "write: {}", stats_data.write_count);
                let _ = writeln!(file, "close: {}", stats_data.close_count);
                let _ = writeln!(file, "denied: {}", stats_data.denied_count);
                let _ = writeln!(file, "last_updated: {}", stats_data.last_updated);
            }
        }
    }

    /// Save stats to file
    fn save_stats(&self) -> io::Result<()> {
        let stats = self.stats.lock().unwrap().clone();
        let stats_file = self.work_dir.join("stats");
        
        let mut file = File::create(&stats_file)?;
        writeln!(file, "open: {}", stats.open_count)?;
        writeln!(file, "read: {}", stats.read_count)?;
        writeln!(file, "write: {}", stats.write_count)?;
        writeln!(file, "close: {}", stats.close_count)?;
        writeln!(file, "denied: {}", stats.denied_count)?;
        writeln!(file, "last_updated: {}", stats.last_updated)?;
        
        Ok(())
    }

    /// Save policy to file
    fn save_policy(&self) -> io::Result<()> {
        let policy = self.policy.lock().unwrap().clone();
        let policy_file = self.work_dir.join("policy");
        
        let mut file = File::create(&policy_file)?;
        writeln!(file, "enabled: {}", if policy.enabled { 1 } else { 0 })?;
        writeln!(file, "log_level: {}", policy.log_level)?;
        writeln!(file, "default_action: {}", 
            if policy.default_action == VFSAction::Allow { "allow" } else { "deny" })?;
        writeln!(file, "rules_count: {}", policy.rules.len())?;
        
        for rule in &policy.rules {
            let mode = format!("{}{}", 
                if rule.mode_mask & 1 != 0 { "r" } else { "" },
                if rule.mode_mask & 2 != 0 { "w" } else { "" });
            writeln!(file, "{}:{}:{}",
                if rule.action == VFSAction::Allow { "allow" } else { "deny" },
                rule.path_pattern,
                mode)?;
        }
        
        Ok(())
    }
}

/// Global VFS monitor instance
static mut VFS_MONITOR: Option<VFSMonitor> = None;
static VFS_MONITOR_INIT: std::sync::Once = std::sync::Once::new();

/// Get or create VFS monitor
pub fn get_vfs_monitor() -> &'static VFSMonitor {
    unsafe {
        VFS_MONITOR_INIT.call_once(|| {
            let monitor = VFSMonitor::new();
            let _ = monitor.init();
            VFS_MONITOR = Some(monitor);
        });
        VFS_MONITOR.as_ref().unwrap()
    }
}

/// Initialize VFS monitor
pub fn init_vfs_monitor() -> io::Result<()> {
    let monitor = get_vfs_monitor();
    monitor.init()?;
    monitor.start()?;
    Ok(())
}

/// Stop VFS monitor
pub fn stop_vfs_monitor() {
    let monitor = get_vfs_monitor();
    monitor.stop();
}
