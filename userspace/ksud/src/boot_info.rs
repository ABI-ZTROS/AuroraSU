use std::path::Path;
use std::process::Command;

use anyhow::Result;

use crate::utils;

/// Check if the device supports A/B partitions by reading ro.build.ab_update
pub fn is_ab_device() -> Result<bool> {
    let val = utils::getprop("ro.build.ab_update").unwrap_or_default();
    let is_ab = val.trim().to_lowercase() == "true";
    println!("{}", if is_ab { "true" } else { "false" });
    Ok(is_ab)
}

/// Get the current slot suffix (e.g. "_a" or "_b"), optionally toggled for OTA
pub fn get_slot_suffix(ota: bool) -> Result<String> {
    let mut slot_suffix = utils::getprop("ro.boot.slot_suffix").unwrap_or_default();

    if !slot_suffix.is_empty() && ota {
        slot_suffix = if slot_suffix == "_a" {
            "_b".to_string()
        } else {
            "_a".to_string()
        };
    }

    println!("{slot_suffix}");
    Ok(slot_suffix)
}

/// Detect the default boot partition (boot or init_boot) based on kernel release and device
pub fn get_default_partition() -> Result<String> {
    let slot_suffix = get_slot_suffix_raw(false);

    // Check kernel release to determine if init_boot is relevant
    let uname_output = Command::new("uname").arg("-r").output()?;
    let release = String::from_utf8_lossy(&uname_output.stdout).trim().to_string();
    let skip_init_boot = release.contains("android12-");

    let init_boot_path = format!("/dev/block/by-name/init_boot{slot_suffix}");

    // If init_boot partition exists and kernel doesn't require skipping it, prefer init_boot
    if !skip_init_boot && Path::new(&init_boot_path).exists() {
        println!("init_boot");
        Ok("init_boot".to_string())
    } else {
        println!("boot");
        Ok("boot".to_string())
    }
}

/// List available boot partitions (boot, init_boot, vendor_boot) for the current slot
pub fn list_available_partitions() -> Result<Vec<String>> {
    let slot_suffix = get_slot_suffix_raw(false);
    let candidates = ["boot", "init_boot", "vendor_boot"];

    let mut available = Vec::new();
    for name in &candidates {
        let path = format!("/dev/block/by-name/{name}{slot_suffix}");
        if Path::new(&path).exists() {
            available.push(name.to_string());
        }
    }

    for p in &available {
        println!("{p}");
    }

    Ok(available)
}

/// Internal helper: get slot suffix without printing (used by other functions)
fn get_slot_suffix_raw(ota: bool) -> String {
    let output = utils::getprop("ro.boot.slot_suffix").unwrap_or_default();

    if !output.is_empty() && ota {
        if output == "_a" {
            "_b".to_string()
        } else {
            "_a".to_string()
        }
    } else {
        output
    }
}
