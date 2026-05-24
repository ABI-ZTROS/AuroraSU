/*
 * AuroraSU - Event Handler
 * 
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

use anyhow::Result;
use crate::aurora_uapi::AuroraDriver;

pub struct EventHandler {
    driver: AuroraDriver,
}

impl EventHandler {
    pub fn new(driver: &AuroraDriver) -> Self {
        Self {
            driver: driver.clone(),
        }
    }
    
    pub fn start(&self) -> Result<()> {
        log::info!("Starting event handler...");
        Ok(())
    }
}
