/*
 * AuroraSU - Settings Activity
 *
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.aurora.su.core.LogManager;
import com.aurora.su.core.RootManager;
import com.aurora.su.core.SecurityManager;
import com.aurora.su.core.SecurityManager.SafetyNetResult;
import com.aurora.su.util.DeviceUtils;
import com.aurora.su.util.PreferencesManager;

/**
 * SettingsActivity - 设置页面
 * Root 隐藏开关、多用户管理、日志设置、安全检测、关于页面入口
 */
public class SettingsActivity extends Activity {

    private static final String TAG = "SettingsActivity";

    private PreferencesManager prefs;
    private SecurityManager securityManager;
    private RootManager rootManager;
    private LogManager logManager;

    private Switch rootHideSwitch;
    private Switch autoGrantSwitch;
    private Switch notificationSwitch;
    private Switch bootSwitch;
    private Switch multiUserSwitch;
    private Switch debugLogSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = new PreferencesManager(this);
        securityManager = new SecurityManager(this);
        rootManager = new RootManager(this);
        logManager = new LogManager(this);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#F8F9FA"));

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24));

        // 标题栏
        addTitleBar(rootLayout);

        // 安全设置
        addSectionTitle(rootLayout, "安全设置");
        addRootHideSetting(rootLayout);
        addSafetyNetCheck(rootLayout);
        addAutoGrantSetting(rootLayout);

        // 通知设置
        addSectionTitle(rootLayout, "通知设置");
        addNotificationSetting(rootLayout);

        // 系统设置
        addSectionTitle(rootLayout, "系统设置");
        addBootSetting(rootLayout);
        addMultiUserSetting(rootLayout);
        addDebugLogSetting(rootLayout);

        // 日志设置
        addSectionTitle(rootLayout, "日志设置");
        addLogRetentionSetting(rootLayout);
        addClearLogsButton(rootLayout);

        // 关于
        addSectionTitle(rootLayout, "关于");
        addAboutEntry(rootLayout);
        addVersionEntry(rootLayout);
        addOpenSourceEntry(rootLayout);

        scrollView.addView(rootLayout);
        setContentView(scrollView);
    }

    private void addTitleBar(LinearLayout parent) {
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dpToPx(16));
        titleBar.setLayoutParams(params);

        Button backButton = new Button(this);
        backButton.setText("<");
        backButton.setTextSize(18);
        backButton.setTextColor(Color.parseColor("#7C4DFF"));
        backButton.setBackgroundColor(Color.TRANSPARENT);
        backButton.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });
        titleBar.addView(backButton);

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(22);
        title.setTextColor(Color.parseColor("#212121"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        title.setLayoutParams(titleParams);
        titleBar.addView(title);

        parent.addView(titleBar);
    }

    private void addSectionTitle(LinearLayout parent, String title) {
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText(title);
        sectionTitle.setTextSize(13);
        sectionTitle.setTextColor(Color.parseColor("#7C4DFF"));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(0, dpToPx(16), 0, dpToPx(8));
        parent.addView(sectionTitle);
    }

    private void addRootHideSetting(LinearLayout parent) {
        LinearLayout item = createSettingItem("Root 隐藏", "隐藏 Root 权限，防止应用检测");
        rootHideSwitch = new Switch(this);
        rootHideSwitch.setChecked(prefs.getBoolean("root_hide_enabled", false));
        rootHideSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                boolean success = securityManager.toggleRootHide(isChecked);
                if (success) {
                    prefs.putBoolean("root_hide_enabled", isChecked);
                    Toast.makeText(SettingsActivity.this,
                        isChecked ? "Root 隐藏已启用" : "Root 隐藏已禁用",
                        Toast.LENGTH_SHORT).show();
                    logManager.addLog("system", "SETTINGS",
                        "Root hide " + (isChecked ? "enabled" : "disabled"));
                } else {
                    rootHideSwitch.setChecked(!isChecked);
                    Toast.makeText(SettingsActivity.this, "操作失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        item.addView(rootHideSwitch);
        parent.addView(item);
    }

    private void addSafetyNetCheck(LinearLayout parent) {
        LinearLayout item = createSettingItem("SafetyNet 检测", "检查设备是否通过 SafetyNet 验证");
        item.setClickable(true);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runSafetyNetCheck();
            }
        });
        parent.addView(item);
    }

    private void addAutoGrantSetting(LinearLayout parent) {
        LinearLayout item = createSettingItem("自动授权", "自动批准已知安全应用的 Root 请求");
        autoGrantSwitch = new Switch(this);
        autoGrantSwitch.setChecked(prefs.getBoolean("auto_grant_enabled", false));
        autoGrantSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.putBoolean("auto_grant_enabled", isChecked);
                logManager.addLog("system", "SETTINGS",
                    "Auto grant " + (isChecked ? "enabled" : "disabled"));
            }
        });
        item.addView(autoGrantSwitch);
        parent.addView(item);
    }

    private void addNotificationSetting(LinearLayout parent) {
        LinearLayout item = createSettingItem("Root 请求通知", "当应用请求 Root 权限时显示通知");
        notificationSwitch = new Switch(this);
        notificationSwitch.setChecked(prefs.getBoolean("notification_enabled", true));
        notificationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.putBoolean("notification_enabled", isChecked);
            }
        });
        item.addView(notificationSwitch);
        parent.addView(item);
    }

    private void addBootSetting(LinearLayout parent) {
        LinearLayout item = createSettingItem("开机自启", "设备启动后自动启动 AuroraSU 服务");
        bootSwitch = new Switch(this);
        bootSwitch.setChecked(prefs.getBoolean("boot_enabled", true));
        bootSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.putBoolean("boot_enabled", isChecked);
                logManager.addLog("system", "SETTINGS",
                    "Boot service " + (isChecked ? "enabled" : "disabled"));
            }
        });
        item.addView(bootSwitch);
        parent.addView(item);
    }

    private void addMultiUserSetting(LinearLayout parent) {
        LinearLayout item = createSettingItem("多用户管理", "为不同用户配置独立的 Root 授权策略");
        multiUserSwitch = new Switch(this);
        multiUserSwitch.setChecked(prefs.getBoolean("multi_user_enabled", false));
        multiUserSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.putBoolean("multi_user_enabled", isChecked);
                Toast.makeText(SettingsActivity.this,
                    isChecked ? "多用户模式已启用" : "多用户模式已禁用",
                    Toast.LENGTH_SHORT).show();
            }
        });
        item.addView(multiUserSwitch);
        parent.addView(item);
    }

    private void addDebugLogSetting(LinearLayout parent) {
        LinearLayout item = createSettingItem("调试日志", "启用详细日志记录用于问题排查");
        debugLogSwitch = new Switch(this);
        debugLogSwitch.setChecked(prefs.getBoolean("debug_log_enabled", false));
        debugLogSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.putBoolean("debug_log_enabled", isChecked);
            }
        });
        item.addView(debugLogSwitch);
        parent.addView(item);
    }

    private void addLogRetentionSetting(LinearLayout parent) {
        LinearLayout item = createSettingItem("日志保留天数", "超过保留期限的日志将自动清理");
        item.setClickable(true);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogRetentionDialog();
            }
        });
        parent.addView(item);
    }

    private void addClearLogsButton(LinearLayout parent) {
        LinearLayout item = createSettingItem("清除所有日志", "清除所有 Root 操作日志");
        item.setClickable(true);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                    .setTitle("清除日志")
                    .setMessage("确定要清除所有日志吗？")
                    .setPositiveButton("清除", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            logManager.clearLogs();
                            Toast.makeText(SettingsActivity.this, "日志已清除", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }
        });
        parent.addView(item);
    }

    private void addAboutEntry(LinearLayout parent) {
        LinearLayout item = createSettingItem("关于 AuroraSU", "版本信息、开源许可、团队信息");
        item.setClickable(true);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, AboutActivity.class));
            }
        });
        parent.addView(item);
    }

    private void addVersionEntry(LinearLayout parent) {
        LinearLayout item = createSettingItem("版本", "AuroraSU v1.0.0 (Build 2026.01.01)");
        parent.addView(item);
    }

    private void addOpenSourceEntry(LinearLayout parent) {
        LinearLayout item = createSettingItem("开源许可", "查看使用的开源库和许可证");
        item.setClickable(true);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, AboutActivity.class));
            }
        });
        parent.addView(item);
    }

    private void runSafetyNetCheck() {
        Toast.makeText(this, "正在检测 SafetyNet...", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final SafetyNetResult result = securityManager.checkSafetyNet();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle("SafetyNet 检测结果")
                            .setMessage(result.getSummary()
                                + "\n\n基本完整性: " + (result.basicIntegrity ? "通过" : "未通过")
                                + "\nCTS 配置文件: " + (result.ctsProfileMatch ? "匹配" : "不匹配")
                                + "\nRoot 检测绕过: " + (result.rootDetectionBypassed ? "是" : "否"))
                            .setPositiveButton("确定", null)
                            .show();
                    }
                });
            }
        }).start();
    }

    private void showLogRetentionDialog() {
        final int currentDays = prefs.getInt("log_retention_days", 30);
        String[] options = {"7 天", "14 天", "30 天", "60 天", "90 天", "永久保留"};
        final int[] values = {7, 14, 30, 60, 90, 0};

        int selectedIndex = 2; // 默认 30 天
        for (int i = 0; i < values.length; i++) {
            if (values[i] == currentDays) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
            .setTitle("日志保留天数")
            .setSingleChoiceItems(options, selectedIndex, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    prefs.putInt("log_retention_days", values[which]);
                    dialog.dismiss();
                    Toast.makeText(SettingsActivity.this,
                        "日志保留天数已设置为 " + options[which], Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // ---- UI 辅助方法 ----

    private LinearLayout createSettingItem(String title, String subtitle) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setBackground(createCardBackground());
        item.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        item.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dpToPx(4));
        item.setLayoutParams(params);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        textLayout.setLayoutParams(textParams);

        TextView titleText = new TextView(this);
        titleText.setText(title);
        titleText.setTextSize(15);
        titleText.setTextColor(Color.parseColor("#212121"));
        textLayout.addView(titleText);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subtitleText = new TextView(this);
            subtitleText.setText(subtitle);
            subtitleText.setTextSize(12);
            subtitleText.setTextColor(Color.parseColor("#9E9E9E"));
            subtitleText.setPadding(0, dpToPx(2), 0, 0);
            textLayout.addView(subtitleText);
        }

        item.addView(textLayout);
        return item;
    }

    private GradientDrawable createCardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dpToPx(12));
        drawable.setStroke(1, Color.parseColor("#EEEEEE"));
        return drawable;
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp,
            getResources().getDisplayMetrics()
        );
    }
}
