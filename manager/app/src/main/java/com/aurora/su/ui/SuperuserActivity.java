/*
 * AuroraSU - Superuser Management Activity
 *
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.aurora.su.core.LogManager;
import com.aurora.su.core.RootManager;
import com.aurora.su.core.RootManager.GrantedApp;

/**
 * SuperuserActivity - 超级用户管理页面
 * 显示已授权应用列表，支持撤销授权、搜索过滤
 */
public class SuperuserActivity extends Activity {

    private static final String TAG = "SuperuserActivity";

    private LinearLayout appListContainer;
    private TextView emptyView;
    private TextView countText;
    private EditText searchInput;
    private RootManager rootManager;
    private LogManager logManager;
    private Handler handler;
    private List<GrantedApp> currentApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rootManager = new RootManager(this);
        logManager = new LogManager(this);
        handler = new Handler(Looper.getMainLooper());
        currentApps = new ArrayList<>();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#F8F9FA"));

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24));

        // 标题栏
        addTitleBar(rootLayout);

        // 搜索栏
        addSearchBar(rootLayout);

        // 统计信息
        addStatsBar(rootLayout);

        // 应用列表容器
        appListContainer = new LinearLayout(this);
        appListContainer.setOrientation(LinearLayout.VERTICAL);
        rootLayout.addView(appListContainer);

        // 空视图
        emptyView = new TextView(this);
        emptyView.setText("暂无已授权的应用");
        emptyView.setTextSize(16);
        emptyView.setTextColor(Color.parseColor("#9E9E9E"));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, dpToPx(48), 0, dpToPx(48));
        emptyView.setVisibility(View.GONE);
        rootLayout.addView(emptyView);

        scrollView.addView(rootLayout);
        setContentView(scrollView);

        loadGrantedApps();

        // 处理来自 RootRequestReceiver 的请求
        handleIncomingRequest();
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
            public void onClick(View v) {
                finish();
            }
        });
        titleBar.addView(backButton);

        TextView title = new TextView(this);
        title.setText("超级用户管理");
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

    private void addSearchBar(LinearLayout parent) {
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setBackground(createCardBackground());
        searchBar.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dpToPx(12));
        searchBar.setLayoutParams(params);

        searchInput = new EditText(this);
        searchInput.setHint("搜索应用...");
        searchInput.setTextSize(14);
        searchInput.setTextColor(Color.parseColor("#212121"));
        searchInput.setHintTextColor(Color.parseColor("#BDBDBD"));
        searchInput.setBackground(null);
        searchInput.setSingleLine(true);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        searchInput.setLayoutParams(inputParams);
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                filterApps(s.toString());
            }
        });
        searchBar.addView(searchInput);

        parent.addView(searchBar);
    }

    private void addStatsBar(LinearLayout parent) {
        LinearLayout statsBar = new LinearLayout(this);
        statsBar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dpToPx(12));
        statsBar.setLayoutParams(params);

        countText = new TextView(this);
        countText.setText("已授权: 0 个应用");
        countText.setTextSize(13);
        countText.setTextColor(Color.parseColor("#757575"));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        countText.setLayoutParams(countParams);
        statsBar.addView(countText);

        parent.addView(statsBar);
    }

    private void loadGrantedApps() {
        currentApps = rootManager.getAllGrantedApps();
        renderAppList(currentApps);
    }

    private void filterApps(String query) {
        if (query == null || query.trim().isEmpty()) {
            renderAppList(currentApps);
            return;
        }

        String lowerQuery = query.toLowerCase(Locale.US);
        List<GrantedApp> filtered = new ArrayList<>();
        for (GrantedApp app : currentApps) {
            if (app.packageName.toLowerCase(Locale.US).contains(lowerQuery)
                || app.appName.toLowerCase(Locale.US).contains(lowerQuery)) {
                filtered.add(app);
            }
        }
        renderAppList(filtered);
    }

    private void renderAppList(List<GrantedApp> apps) {
        appListContainer.removeAllViews();

        countText.setText("已授权: " + apps.size() + " 个应用");

        if (apps.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }

        emptyView.setVisibility(View.GONE);

        for (final GrantedApp app : apps) {
            View appView = createAppItemView(app);
            appListContainer.addView(appView);
        }
    }

    private View createAppItemView(final GrantedApp app) {
        LinearLayout card = createCardView();

        LinearLayout infoLayout = new LinearLayout(this);
        infoLayout.setOrientation(LinearLayout.HORIZONTAL);
        infoLayout.setGravity(Gravity.CENTER_VERTICAL);

        // 应用图标
        ImageView iconView = new ImageView(this);
        android.graphics.drawable.Drawable icon = app.getAppIcon(this);
        if (icon != null) {
            iconView.setImageDrawable(icon);
        } else {
            iconView.setBackgroundColor(Color.parseColor("#E0E0E0"));
        }
        int iconSize = dpToPx(40);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMargins(0, 0, dpToPx(12), 0);
        iconView.setLayoutParams(iconParams);
        iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        infoLayout.addView(iconView);

        // 应用信息
        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        textLayout.setLayoutParams(textParams);

        TextView nameText = new TextView(this);
        nameText.setText(app.appName.isEmpty() ? app.packageName : app.appName);
        nameText.setTextSize(15);
        nameText.setTextColor(Color.parseColor("#212121"));
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);
        nameText.setSingleLine(true);
        textLayout.addView(nameText);

        TextView pkgText = new TextView(this);
        pkgText.setText(app.packageName);
        pkgText.setTextSize(11);
        pkgText.setTextColor(Color.parseColor("#9E9E9E"));
        pkgText.setSingleLine(true);
        textLayout.addView(pkgText);

        TextView detailText = new TextView(this);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        String grantTime = app.grantedAt > 0 ? sdf.format(new Date(app.grantedAt)) : "未知";
        detailText.setText("授权时间: " + grantTime + " | 使用次数: " + app.useCount);
        detailText.setTextSize(11);
        detailText.setTextColor(Color.parseColor("#757575"));
        textLayout.addView(detailText);

        infoLayout.addView(textLayout);

        // 撤销按钮
        Button revokeBtn = new Button(this);
        revokeBtn.setText("撤销");
        revokeBtn.setTextSize(12);
        revokeBtn.setTextColor(Color.parseColor("#F44336"));
        revokeBtn.setBackground(createRevokeButtonBackground());
        revokeBtn.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        revokeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRevokeConfirmDialog(app);
            }
        });
        infoLayout.addView(revokeBtn);

        card.addView(infoLayout);
        return card;
    }

    private void showRevokeConfirmDialog(final GrantedApp app) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("撤销 Root 权限");
        builder.setMessage("确定要撤销 " + (app.appName.isEmpty() ? app.packageName : app.appName)
            + " 的 Root 权限吗？\n\n包名: " + app.packageName);
        builder.setPositiveButton("撤销", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                boolean success = rootManager.revokeRoot(app.packageName);
                if (success) {
                    logManager.addLog(app.packageName, "REVOKE", "Root access revoked by user from SuperuserActivity");
                    Toast.makeText(SuperuserActivity.this, "已撤销 " + app.appName + " 的 Root 权限", Toast.LENGTH_SHORT).show();
                    loadGrantedApps();
                } else {
                    Toast.makeText(SuperuserActivity.this, "撤销失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void handleIncomingRequest() {
        Intent intent = getIntent();
        if (intent == null) return;

        String requestPkg = intent.getStringExtra("request_package");
        if (requestPkg != null && !requestPkg.isEmpty()) {
            showGrantDialog(requestPkg);
        }
    }

    private void showGrantDialog(final String packageName) {
        String appName = packageName;
        try {
            PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            if (label != null) appName = label.toString();
        } catch (PackageManager.NameNotFoundException e) {
            // 使用包名
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Root 权限请求");
        builder.setMessage(appName + " (" + packageName + ") 正在请求 Root 权限。\n\n是否授予权限？");
        builder.setPositiveButton("授权", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                boolean success = rootManager.grantRoot(packageName);
                if (success) {
                    logManager.addLog(packageName, "GRANT", "Root access granted by user from dialog");
                    Toast.makeText(SuperuserActivity.this, "已授权 " + packageName, Toast.LENGTH_SHORT).show();
                    loadGrantedApps();
                } else {
                    Toast.makeText(SuperuserActivity.this, "授权失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("拒绝", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                logManager.addLog(packageName, "DENY", "Root access denied by user from dialog");
                Toast.makeText(SuperuserActivity.this, "已拒绝 " + packageName, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    // ---- UI 辅助方法 ----

    private LinearLayout createCardView() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createCardBackground());
        card.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dpToPx(8));
        card.setLayoutParams(params);
        return card;
    }

    private GradientDrawable createCardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dpToPx(12));
        drawable.setStroke(1, Color.parseColor("#EEEEEE"));
        return drawable;
    }

    private GradientDrawable createRevokeButtonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.parseColor("#FFF3F3"));
        drawable.setCornerRadius(dpToPx(8));
        drawable.setStroke(1, Color.parseColor("#FFCDD2"));
        return drawable;
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp,
            getResources().getDisplayMetrics()
        );
    }
}
