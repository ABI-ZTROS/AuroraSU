/*
 * AuroraSU - Userspace Daemon (aurorad)
 * 
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

mod aurora_uapi;
mod module;
mod boot_patch;
mod kpm;
mod susfs;
mod redundancy;
mod event;
mod cli;

use std::fs;
use std::os::unix::net::UnixListener;
use std::path::Path;
use std::process::Command;
use std::sync::Arc;
use std::thread;

use anyhow::{Result, Context};
use clap::Parser;
use log::{info, error, warn, debug};
use nix::unistd::{Uid, Gid};

use aurora_uapi::AuroraDriver;
use module::ModuleManager;
use redundancy::RedundancyManager;
use event::EventHandler;

const AURORA_VERSION: &str = env!("CARGO_PKG_VERSION");
const AURORA_SOCKET_PATH: &str = "/dev/aurora_socket";
const AURORA_DIR: &str = "/data/adb/aurora";
const AURORA_MODULES_DIR: &str = "/data/adb/aurora/modules";
const AURORA_BACKUP_DIR: &str = "/data/adb/aurora/backup";

/// AuroraSU Daemon - Userspace companion for AuroraSU kernel module
#[derive(Parser, Debug)]
#[command(name = "aurorad")]
#[command(about = "AuroraSU userspace daemon")]
#[command(version = AURORA_VERSION)]
struct Args {
    /// Run in debug mode
    #[arg(long)]
    debug: bool,
    
    /// Skip module loading
    #[arg(long)]
    skip_modules: bool,
    
    /// Force safe mode
    #[arg(long)]
    safe_mode: bool,
    
    /// Command to execute (if not specified, run as daemon)
    #[arg(value_enum)]
    command: Option<CliCommand>,
}

#[derive(clap::ValueEnum, Clone, Debug)]
enum CliCommand {
    /// Start the daemon
    Daemon,
    /// Install/update AuroraSU
    Install,
    /// Uninstall AuroraSU
    Uninstall,
    /// Patch boot image
    PatchBoot,
    /// List installed modules
    ListModules,
    /// Install a module
    InstallModule,
    /// Remove a module
    RemoveModule,
    /// Check system health
    Health,
    /// Enter recovery mode
    Recovery,
}

struct AuroraDaemon {
    driver: AuroraDriver,
    module_manager: ModuleManager,
    redundancy_manager: RedundancyManager,
    event_handler: EventHandler,
}

impl AuroraDaemon {
    fn new() -> Result<Self> {
        info!("Initializing AuroraSU daemon v{}", AURORA_VERSION);
        
        // Initialize driver communication
        let driver = AuroraDriver::new()
            .context("Failed to initialize Aurora driver")?;
        
        // Get kernel info
        let info = driver.get_info()?;
        info!("Kernel module version: {}", info.version_string);
        info!("Features: 0x{:x}", info.features);
        info!("Hook mode: {:?}", info.hook_mode);
        
        // Initialize managers
        let module_manager = ModuleManager::new(&driver)
            .context("Failed to initialize module manager")?;
        
        let redundancy_manager = RedundancyManager::new(&driver)
            .context("Failed to initialize redundancy manager")?;
        
        let event_handler = EventHandler::new(&driver);
        
        Ok(Self {
            driver,
            module_manager,
            redundancy_manager,
            event_handler,
        })
    }
    
    fn initialize_directories(&self) -> Result<()> {
        // Create necessary directories
        for dir in &[AURORA_DIR, AURORA_MODULES_DIR, AURORA_BACKUP_DIR] {
            if !Path::new(dir).exists() {
                fs::create_dir_all(dir)
                    .with_context(|| format!("Failed to create directory: {}", dir))?;
            }
        }
        Ok(())
    }
    
    fn run_daemon(&mut self, args: &Args) -> Result<()> {
        info!("Starting AuroraSU daemon...");
        
        // Initialize directories
        self.initialize_directories()?;
        
        // Check if in safe mode
        if args.safe_mode || self.driver.check_safemode()? {
            warn!("Safe mode detected! Running in limited functionality.");
            return self.run_safe_mode();
        }
        
        // Run health check
        if let Err(e) = self.redundancy_manager.health_check() {
            warn!("Health check warning: {}", e);
        }
        
        // Load modules (unless skipped)
        if !args.skip_modules {
            info!("Loading modules...");
            if let Err(e) = self.module_manager.load_all_modules() {
                error!("Failed to load some modules: {}", e);
                // Continue anyway - partial load is acceptable
            }
        }
        
        // Report boot completed to kernel
        self.driver.report_event(aurora_uapi::AURORA_EVENT_BOOT_COMPLETED)?;
        
        // Start event handling
        self.event_handler.start()?;
        
        // Start redundancy monitoring
        self.redundancy_manager.start_monitoring()?;
        
        // Create Unix socket for IPC
        self.start_socket_server()?;
        
        info!("AuroraSU daemon fully initialized");
        
        // Keep daemon running
        loop {
            thread::park();
        }
    }
    
    fn run_safe_mode(&mut self) -> Result<()> {
        warn!("Running in SAFE MODE - limited functionality");
        
        // In safe mode, only allow basic operations
        // Don't load any modules
        // Don't start monitoring
        
        info!("Safe mode active. Use 'aurorad recovery' to attempt repair.");
        
        // Create emergency socket
        self.start_socket_server()?;
        
        loop {
            thread::park();
        }
    }
    
    fn start_socket_server(&self) -> Result<()> {
        // Remove old socket if exists
        if Path::new(AURORA_SOCKET_PATH).exists() {
            fs::remove_file(AURORA_SOCKET_PATH)?;
        }
        
        let listener = UnixListener::bind(AURORA_SOCKET_PATH)
            .context("Failed to bind Unix socket")?;
        
        // Set permissions
        std::fs::set_permissions(AURORA_SOCKET_PATH, std::fs::Permissions::from_mode(0o666))?;
        
        info!("Unix socket listening at {}", AURORA_SOCKET_PATH);
        
        // Spawn handler thread
        let driver = self.driver.clone();
        let module_manager = self.module_manager.clone();
        
        thread::spawn(move || {
            for stream in listener.incoming() {
                match stream {
                    Ok(stream) => {
                        if let Err(e) = handle_client(stream, &driver, &module_manager) {
                            error!("Client handler error: {}", e);
                        }
                    }
                    Err(e) => {
                        error!("Socket accept error: {}", e);
                    }
                }
            }
        });
        
        Ok(())
    }
}

fn handle_client(stream: std::os::unix::net::UnixStream, 
                 driver: &AuroraDriver,
                 module_manager: &ModuleManager) -> Result<()> {
    use std::io::{Read, Write};
    
    let mut stream = stream;
    let mut buffer = [0u8; 4096];
    
    let n = stream.read(&mut buffer)?;
    let request = String::from_utf8_lossy(&buffer[..n]);
    
    debug!("Received request: {}", request);
    
    // Parse and handle request
    let response = match request.trim() {
        "ping" => "pong".to_string(),
        "version" => format!("AuroraSU v{}", AURORA_VERSION),
        "modules" => {
            match module_manager.list_modules() {
                Ok(modules) => modules.join("\n"),
                Err(e) => format!("Error: {}", e),
            }
        }
        "health" => {
            match driver.get_redundancy_status() {
                Ok(status) => format!("State: {:?}, Safe mode: {}", 
                    status.state, status.in_safe_mode),
                Err(e) => format!("Error: {}", e),
            }
        }
        _ => "Unknown command".to_string(),
    };
    
    stream.write_all(response.as_bytes())?;
    Ok(())
}

fn main() -> Result<()> {
    let args = Args::parse();
    
    // Initialize logging
    env_logger::Builder::from_default_env()
        .filter_level(if args.debug { 
            log::LevelFilter::Debug 
        } else { 
            log::LevelFilter::Info 
        })
        .init();
    
    // Check if running as root
    if Uid::effective().as_raw() != 0 {
        anyhow::bail!("This program must run as root");
    }
    
    match args.command {
        Some(CliCommand::Install) => cli::install::run(),
        Some(CliCommand::Uninstall) => cli::uninstall::run(),
        Some(CliCommand::PatchBoot) => cli::boot_patch::run(),
        Some(CliCommand::ListModules) => cli::modules::list(),
        Some(CliCommand::InstallModule) => cli::modules::install(),
        Some(CliCommand::RemoveModule) => cli::modules::remove(),
        Some(CliCommand::Health) => cli::health::run(),
        Some(CliCommand::Recovery) => cli::recovery::run(),
        _ => {
            // Run as daemon
            let mut daemon = AuroraDaemon::new()?;
            daemon.run_daemon(&args)
        }
    }
}
