/*
 * AuroraSU - Modules Management Activity
 *
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.ui;

import java.io.File;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.aurora.su.core.LogManager;
import com.aurora.su.core.ModuleManager;
import com.aurora.su.core.ModuleManager.InstallResult;
import com.aurora.su.core.ModuleManager.ModuleInfo;
import com.aurora.su.core.ModuleManager.ModuleUpdateInfo;

/**
 * ModulesActivity - 模块管理页面
 * 显示已安装模块列表，支持启用/禁用/删除/安装模块
 */
public class ModulesActivity extends Activity {

    private static final String TAG = "ModulesActivity";
    private static final int REQUEST_PICK_MODULE = 1001;

    private LinearLayout moduleListContainer;
    private TextView emptyView;
    private TextView moduleCountText;
    private ModuleManager moduleManager;
    private LogManager logManager;
    private Handler handler;
    private List<ModuleInfo> currentModules;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        moduleManager = new ModuleManager(this);
        logManager = new LogManager(this);
        handler = new Handler(Looper.getMainLooper());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#F8F9FA"));

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24));

        // 标题栏
        addTitleBar(rootLayout);

        // 操作栏
        addActionButtons(rootLayout);

        // 模块数量
        moduleCountText = new TextView(this);
        moduleCountText.setText("已安装模块: 0");
        moduleCountText.setTextSize(13);
        moduleCountText.setTextColor(Color.parseColor("#757575"));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        countParams.setMargins(0, dpToPx(4), 0, dpToPx(12));
        moduleCountText.setLayoutParams(countParams);
        rootLayout.addView(moduleCountText);

        // 模块列表容器
        moduleListContainer = new LinearLayout(this);
        moduleListContainer.setOrientation(LinearLayout.VERTICAL);
        rootLayout.addView(moduleListContainer);

        // 空视图
        emptyView = new TextView(this);
        emptyView.setText("暂无已安装的模块\n点击上方按钮安装新模块");
        emptyView.setTextSize(16);
        emptyView.setTextColor(Color.parseColor("#9E9E9E"));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, dpToPx(48), 0, dpToPx(48));
        emptyView.setVisibility(View.GONE);
        rootLayout.addView(emptyView);

        scrollView.addView(rootLayout);
        setContentView(scrollView);

        loadModules();
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
        title.setText("模块管理");
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

    private void addActionButtons(LinearLayout parent) {
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dpToPx(12));
        actionRow.setLayoutParams(params);

        // 安装模块按钮
        Button installBtn = createActionButton("安装模块", "#7C4DFF");
        installBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openModulePicker();
            }
        });
        actionRow.addView(installBtn);

        // 检查更新按钮
        Button updateBtn = createActionButton("检查更新", "#FF9800");
        updateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkUpdates();
            }
        });
        actionRow.addView(updateBtn);

        parent.addView(actionRow);
    }

    private Button createActionButton(String text, String colorHex) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(13);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(createButtonBackground(Color.parseColor(colorHex)));
        btn.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        params.setMargins(0, 0, dpToPx(8), 0);
        btn.setLayoutParams(params);
        return btn;
    }

    private void loadModules() {
        currentModules = moduleManager.getInstalledModules();
        renderModuleList(currentModules);
    }

    private void renderModuleList(List<ModuleInfo> modules) {
        moduleListContainer.removeAllViews();
        moduleCountText.setText("已安装模块: " + modules.size());

        if (modules.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }

        emptyView.setVisibility(View.GONE);

        for (final ModuleInfo module : modules) {
            View moduleView = createModuleItemView(module);
            moduleListContainer.addView(moduleView);
        }
    }

    private View createModuleItemView(final ModuleInfo module) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createCardBackground());
        card.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(8));
        card.setLayoutParams(cardParams);

        // 模块名称和状态
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView nameText = new TextView(this);
        nameText.setText(module.name.isEmpty() ? module.id : module.name);
        nameText.setTextSize(16);
        nameText.setTextColor(Color.parseColor("#212121"));
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);
        nameText.setSingleLine(true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        nameParams.setMargins(0, 0, dpToPx(8), 0);
        nameText.setLayoutParams(nameParams);
        headerRow.addView(nameText);

        // 状态标签
        TextView statusBadge = new TextView(this);
        if (module.pendingRemove) {
            statusBadge.setText("待删除");
            statusBadge.setTextColor(Color.parseColor("#F44336"));
            statusBadge.setBackground(createBadgeBackground(Color.parseColor("#FFEBEE")));
        } else if (module.enabled) {
            statusBadge.setText("已启用");
            statusBadge.setTextColor(Color.parseColor("#4CAF50"));
            statusBadge.setBackground(createBadgeBackground(Color.parseColor("#E8F5E9")));
        } else {
            statusBadge.setText("已禁用");
            statusBadge.setTextColor(Color.parseColor("#FF9800"));
            statusBadge.setBackground(createBadgeBackground(Color.parseColor("#FFF3E0")));
        }
        statusBadge.setTextSize(11);
        statusBadge.setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2));
        headerRow.addView(statusBadge);

        card.addView(headerRow);

        // 模块详情
        TextView idText = new TextView(this);
        idText.setText("ID: " + module.id);
        idText.setTextSize(12);
        idText.setTextColor(Color.parseColor("#9E9E9E"));
        idText.setPadding(0, dpToPx(4), 0, 0);
        card.addView(idText);

        if (!module.version.isEmpty()) {
            TextView versionText = new TextView(this);
            versionText.setText("版本: " + module.version
                + " | 作者: " + (module.author.isEmpty() ? "未知" : module.author)
                + " | 大小: " + module.getFormattedSize());
            versionText.setTextSize(12);
            versionText.setTextColor(Color.parseColor("#757575"));
            versionText.setPadding(0, dpToPx(2), 0, 0);
            card.addView(versionText);
        }

        if (!module.description.isEmpty()) {
            TextView descText = new TextView(this);
            descText.setText(module.description);
            descText.setTextSize(12);
            descText.setTextColor(Color.parseColor("#616161"));
            descText.setPadding(0, dpToPx(4), 0, dpToPx(4));
            descText.setMaxLines(3);
            card.addView(descText);
        }

        // 操作按钮
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, dpToPx(8), 0, 0);
        buttonRow.setGravity(Gravity.END);

        if (!module.pendingRemove) {
            // 启用/禁用按钮
            Button toggleBtn = new Button(this);
            toggleBtn.setText(module.enabled ? "禁用" : "启用");
            toggleBtn.setTextSize(12);
            toggleBtn.setTextColor(module.enabled ? Color.parseColor("#FF9800") : Color.parseColor("#4CAF50"));
            toggleBtn.setBackground(createSmallButtonBackground(
                module.enabled ? Color.parseColor("#FFF3E0") : Color.parseColor("#E8F5E9")));
            toggleBtn.setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4));
            toggleBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleModule(module);
                }
            });
            buttonRow.addView(toggleBtn);
        }

        // 删除按钮
        Button removeBtn = new Button(this);
        removeBtn.setText("删除");
        removeBtn.setTextSize(12);
        removeBtn.setTextColor(Color.parseColor("#F44336"));
        removeBtn.setBackground(createSmallButtonBackground(Color.parseColor("#FFEBEE")));
        removeBtn.setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4));
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        removeParams.setMargins(dpToPx(8), 0, 0, 0);
        removeBtn.setLayoutParams(removeParams);
        removeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRemoveConfirmDialog(module);
            }
        });
        buttonRow.addView(removeBtn);

        card.addView(buttonRow);
        return card;
    }

    private void toggleModule(final ModuleInfo module) {
        boolean success;
        if (module.enabled) {
            success = moduleManager.disableModule(module.id);
            if (success) {
                logManager.addLog("system", "MODULE", "Module disabled: " + module.id);
                Toast.makeText(this, "已禁用 " + module.name, Toast.LENGTH_SHORT).show();
            }
        } else {
            success = moduleManager.enableModule(module.id);
            if (success) {
                logManager.addLog("system", "MODULE", "Module enabled: " + module.id);
                Toast.makeText(this, "已启用 " + module.name, Toast.LENGTH_SHORT).show();
            }
        }

        if (!success) {
            Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show();
        }

        loadModules();
    }

    private void showRemoveConfirmDialog(final ModuleInfo module) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("删除模块");
        builder.setMessage("确定要删除模块 " + module.name + " (" + module.id + ") 吗？\n\n"
            + "删除后需要重启设备才能完全生效。");
        builder.setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                boolean success = moduleManager.removeModule(module.id);
                if (success) {
                    logManager.addLog("system", "MODULE", "Module removed: " + module.id);
                    Toast.makeText(ModulesActivity.this, "模块已标记删除，重启后生效", Toast.LENGTH_SHORT).show();
                    loadModules();
                } else {
                    Toast.makeText(ModulesActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void openModulePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择模块文件"), REQUEST_PICK_MODULE);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "未找到文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_MODULE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                installModuleFromUri(uri);
            }
        }
    }

    private void installModuleFromUri(Uri uri) {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("正在安装模块...");
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCancelable(false);
        dialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 复制文件到临时目录
                    String tempPath = getCacheDir().getAbsolutePath() + "/temp_module.zip";
                    java.io.InputStream is = getContentResolver().openInputStream(uri);
                    java.io.FileOutputStream os = new java.io.FileOutputStream(tempPath);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        os.write(buffer, 0, len);
                    }
                    os.close();
                    is.close();

                    // 安装模块
                    final InstallResult result = moduleManager.installModule(tempPath);

                    // 清理临时文件
                    new File(tempPath).delete();

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dialog.dismiss();
                            if (result.success) {
                                logManager.addLog("system", "MODULE",
                                    "Module installed: " + result.moduleInfo.id);
                                Toast.makeText(ModulesActivity.this,
                                    result.message, Toast.LENGTH_SHORT).show();
                                loadModules();
                            } else {
                                Toast.makeText(ModulesActivity.this,
                                    result.message, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dialog.dismiss();
                            Toast.makeText(ModulesActivity.this,
                                "安装失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void checkUpdates() {
        Toast.makeText(this, "正在检查模块更新...", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<ModuleUpdateInfo> updates = moduleManager.checkModuleUpdates();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (updates.isEmpty()) {
                            Toast.makeText(ModulesActivity.this, "所有模块已是最新版本", Toast.LENGTH_SHORT).show();
                        } else {
                            StringBuilder sb = new StringBuilder();
                            for (ModuleUpdateInfo update : updates) {
                                sb.append(update.moduleName)
                                    .append(": ").append(update.currentVersion)
                                    .append(" -> ").append(update.latestVersion).append("\n");
                            }
                            Toast.makeText(ModulesActivity.this,
                                "发现 " + updates.size() + " 个更新:\n" + sb.toString(),
                                Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    // ---- UI 辅助方法 ----

    private GradientDrawable createCardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dpToPx(12));
        drawable.setStroke(1, Color.parseColor("#EEEEEE"));
        return drawable;
    }

    private GradientDrawable createButtonBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dpToPx(8));
        return drawable;
    }

    private GradientDrawable createBadgeBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dpToPx(6));
        return drawable;
    }

    private GradientDrawable createSmallButtonBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dpToPx(6));
        return drawable;
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp,
            getResources().getDisplayMetrics()
        );
    }
}
