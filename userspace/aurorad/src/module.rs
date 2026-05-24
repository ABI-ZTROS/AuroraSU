/*
 * AuroraSU - Module Manager
 * 
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

use anyhow::Result;
use crate::aurora_uapi::AuroraDriver;

#[derive(Clone)]
pub struct ModuleManager {
    driver: AuroraDriver,
}

impl ModuleManager {
    pub fn new(driver: &AuroraDriver) -> Result<Self> {
        Ok(Self {
            driver: driver.clone(),
        })
    }
    
    pub fn load_all_modules(&self) -> Result<()> {
        log::info!("Loading all modules...");
        Ok(())
    }
    
    pub fn list_modules(&self) -> Result<Vec<String>> {
        Ok(vec!["example-module".to_string()])
    }
}
