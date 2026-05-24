package com.aurora.su.core;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * AuroraSU Core - Main interface for AuroraSU functionality
 */
public class AuroraCore {
    
    private static final String TAG = "AuroraCore";
    private static final String AURORA_BASE_DIR = "/data/adb/aurora";
    private static final String MODULES_DIR = AURORA_BASE_DIR + "/modules";
    private static final String CONFIG_FILE = AURORA_BASE_DIR + "/config.json";
    private static final String GRANTED_APPS_FILE = AURORA_BASE_DIR + "/granted_apps.json";
    
    private Context context;
    
    public AuroraCore() {
        // Initialize without context for basic operations
    }
    
    public AuroraCore(Context context) {
        this.context = context;
    }
    
    /**
     * Check if AuroraSU is properly installed and working
     */
    public boolean isAuroraSUWorking() {
        try {
            // Check if daemon socket exists
            File socketFile = new File("/dev/aurora_socket");
            if (socketFile.exists()) {
                return true;
            }
            
            // Check if base directory exists
            File baseDir = new File(AURORA_BASE_DIR);
            if (baseDir.exists() && baseDir.isDirectory()) {
                return true;
            }
            
            // Check for kernel module
            File ksuDir = new File("/data/adb/ksu");
            if (ksuDir.exists()) {
                return true;
            }
            
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking AuroraSU status", e);
            return false;
        }
    }
    
    /**
     * Get version information
     */
    public String getVersion() {
        return "1.0.0";
    }
    
    /**
     * Get the number of installed modules
     */
    public int getModuleCount() {
        try {
            File modulesDir = new File(MODULES_DIR);
            if (!modulesDir.exists() || !modulesDir.isDirectory()) {
                // Try KSU modules as fallback
                File ksuModules = new File("/data/adb/modules");
                if (ksuModules.exists() && ksuModules.isDirectory()) {
                    String[] modules = ksuModules.list();
                    return modules != null ? modules.length : 0;
                }
                return 0;
            }
            String[] modules = modulesDir.list();
            return modules != null ? modules.length : 0;
        } catch (Exception e) {
            Log.e(TAG, "Error getting module count", e);
            return 0;
        }
    }
    
    /**
     * Get list of installed modules
     */
    public List<ModuleInfo> getModules() {
        List<ModuleInfo> modules = new ArrayList<>();
        try {
            File modulesDir = new File(MODULES_DIR);
            if (!modulesDir.exists()) {
                return modules;
            }
            
            File[] moduleDirs = modulesDir.listFiles();
            if (moduleDirs == null) {
                return modules;
            }
            
            for (File moduleDir : moduleDirs) {
                if (moduleDir.isDirectory()) {
                    ModuleInfo info = readModuleInfo(moduleDir);
                    if (info != null) {
                        modules.add(info);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting modules", e);
        }
        return modules;
    }
    
    private ModuleInfo readModuleInfo(File moduleDir) {
        try {
            File moduleProp = new File(moduleDir, "module.prop");
            if (!moduleProp.exists()) {
                return null;
            }
            
            ModuleInfo info = new ModuleInfo();
            info.id = moduleDir.getName();
            info.path = moduleDir.getAbsolutePath();
            
            BufferedReader reader = new BufferedReader(new FileReader(moduleProp));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("name=")) {
                    info.name = line.substring(5);
                } else if (line.startsWith("version=")) {
                    info.version = line.substring(8);
                } else if (line.startsWith("author=")) {
                    info.author = line.substring(7);
                } else if (line.startsWith("description=")) {
                    info.description = line.substring(12);
                }
            }
            reader.close();
            
            // Check if module is enabled
            File disableFile = new File(moduleDir, "disable");
            info.enabled = !disableFile.exists();
            
            return info;
        } catch (Exception e) {
            Log.e(TAG, "Error reading module info", e);
            return null;
        }
    }
    
    /**
     * Get count of apps granted root access
     */
    public int getGrantedAppsCount() {
        try {
            // Try to read from granted apps file
            File grantedFile = new File(GRANTED_APPS_FILE);
            if (grantedFile.exists()) {
                String content = readFile(grantedFile);
                JSONArray apps = new JSONArray(content);
                return apps.length();
            }
            
            // Fallback: count from config
            File configFile = new File(CONFIG_FILE);
            if (configFile.exists()) {
                String content = readFile(configFile);
                JSONObject config = new JSONObject(content);
                if (config.has("granted_apps")) {
                    return config.getJSONArray("granted_apps").length();
                }
            }
            
            return 0;
        } catch (Exception e) {
            Log.e(TAG, "Error getting granted apps count", e);
            return 0;
        }
    }
    
    /**
     * Get list of granted apps
     */
    public List<AppInfo> getGrantedApps() {
        List<AppInfo> apps = new ArrayList<>();
        try {
            File grantedFile = new File(GRANTED_APPS_FILE);
            if (!grantedFile.exists()) {
                return apps;
            }
            
            String content = readFile(grantedFile);
            JSONArray appArray = new JSONArray(content);
            
            for (int i = 0; i < appArray.length(); i++) {
                JSONObject appObj = appArray.getJSONObject(i);
                AppInfo app = new AppInfo();
                app.packageName = appObj.optString("package", "");
                app.granted = appObj.optBoolean("granted", false);
                app.timestamp = appObj.optLong("timestamp", 0);
                apps.add(app);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting granted apps", e);
        }
        return apps;
    }
    
    /**
     * Grant root access to an app
     */
    public boolean grantApp(String packageName) {
        try {
            List<AppInfo> apps = getGrantedApps();
            
            // Check if already granted
            for (AppInfo app : apps) {
                if (app.packageName.equals(packageName)) {
                    return true;
                }
            }
            
            // Add new grant
            AppInfo newApp = new AppInfo();
            newApp.packageName = packageName;
            newApp.granted = true;
            newApp.timestamp = System.currentTimeMillis();
            apps.add(newApp);
            
            // Save to file
            return saveGrantedApps(apps);
        } catch (Exception e) {
            Log.e(TAG, "Error granting app", e);
            return false;
        }
    }
    
    /**
     * Revoke root access from an app
     */
    public boolean revokeApp(String packageName) {
        try {
            List<AppInfo> apps = getGrantedApps();
            // Remove app with matching package name
            for (int i = apps.size() - 1; i >= 0; i--) {
                if (apps.get(i).packageName.equals(packageName)) {
                    apps.remove(i);
                }
            }
            return saveGrantedApps(apps);
        } catch (Exception e) {
            Log.e(TAG, "Error revoking app", e);
            return false;
        }
    }
    
    private boolean saveGrantedApps(List<AppInfo> apps) {
        try {
            JSONArray array = new JSONArray();
            for (AppInfo app : apps) {
                JSONObject obj = new JSONObject();
                obj.put("package", app.packageName);
                obj.put("granted", app.granted);
                obj.put("timestamp", app.timestamp);
                array.put(obj);
            }
            
            File grantedFile = new File(GRANTED_APPS_FILE);
            grantedFile.getParentFile().mkdirs();
            
            FileWriter writer = new FileWriter(grantedFile);
            writer.write(array.toString());
            writer.close();
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving granted apps", e);
            return false;
        }
    }
    
    /**
     * Enable a module
     */
    public boolean enableModule(String moduleId) {
        try {
            File moduleDir = new File(MODULES_DIR, moduleId);
            if (!moduleDir.exists()) {
                return false;
            }
            
            File disableFile = new File(moduleDir, "disable");
            if (disableFile.exists()) {
                return disableFile.delete();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error enabling module", e);
            return false;
        }
    }
    
    /**
     * Disable a module
     */
    public boolean disableModule(String moduleId) {
        try {
            File moduleDir = new File(MODULES_DIR, moduleId);
            if (!moduleDir.exists()) {
                return false;
            }
            
            File disableFile = new File(moduleDir, "disable");
            if (!disableFile.exists()) {
                return disableFile.createNewFile();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error disabling module", e);
            return false;
        }
    }
    
    /**
     * Remove a module
     */
    public boolean removeModule(String moduleId) {
        try {
            File moduleDir = new File(MODULES_DIR, moduleId);
            if (!moduleDir.exists()) {
                return false;
            }
            
            return deleteRecursive(moduleDir);
        } catch (Exception e) {
            Log.e(TAG, "Error removing module", e);
            return false;
        }
    }
    
    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }
    
    private String readFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();
        return content.toString();
    }
    
    /**
     * Module information class
     */
    public static class ModuleInfo {
        public String id;
        public String name;
        public String version;
        public String author;
        public String description;
        public String path;
        public boolean enabled;
    }
    
    /**
     * App information class
     */
    public static class AppInfo {
        public String packageName;
        public boolean granted;
        public long timestamp;
    }
}
