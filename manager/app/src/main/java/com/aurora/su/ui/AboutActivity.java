/*
 * AuroraSU - About Activity
 *
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.ui;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.aurora.su.util.DeviceUtils;

/**
 * AboutActivity - 关于页面
 * 显示版本信息、开源许可、团队信息、系统详情
 */
public class AboutActivity extends Activity {

    private static final String TAG = "AboutActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#F8F9FA"));

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24));
        rootLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        // 标题栏
        addTitleBar(rootLayout);

        // 应用 Logo 和名称
        addAppHeader(rootLayout);

        // 版本信息
        addSectionTitle(rootLayout, "版本信息");
        addVersionInfo(rootLayout);

        // 系统信息
        addSectionTitle(rootLayout, "系统信息");
        addSystemInfo(rootLayout);

        // 开源许可
        addSectionTitle(rootLayout, "开源许可");
        addLicenseInfo(rootLayout);

        // 团队信息
        addSectionTitle(rootLayout, "团队");
        addTeamInfo(rootLayout);

        // 链接
        addSectionTitle(rootLayout, "链接");
        addLinks(rootLayout);

        // 页脚
        addFooter(rootLayout);

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
        title.setText("关于");
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

    private void addAppHeader(LinearLayout parent) {
        LinearLayout headerCard = createCard();

        // 应用名称
        TextView appName = new TextView(this);
        appName.setText("AuroraSU");
        appName.setTextSize(32);
        appName.setTextColor(Color.parseColor("#7C4DFF"));
        appName.setTypeface(null, android.graphics.Typeface.BOLD);
        appName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nameParams.setMargins(0, dpToPx(16), 0, dpToPx(4));
        appName.setLayoutParams(nameParams);
        headerCard.addView(appName);

        // 副标题
        TextView subtitle = new TextView(this);
        subtitle.setText("安全、高效的 Root 权限管理方案");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.parseColor("#757575"));
        subtitle.setGravity(Gravity.CENTER);
        headerCard.addView(subtitle);

        // 描述
        TextView desc = new TextView(this);
        desc.setText("AuroraSU 是一款基于内核的 Root 权限管理工具，"
            + "提供模块化扩展、安全防护和精细化的权限控制。");
        desc.setTextSize(13);
        desc.setTextColor(Color.parseColor("#9E9E9E"));
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dpToPx(12), 0, dpToPx(8));
        headerCard.addView(desc);

        parent.addView(headerCard);
    }

    private void addVersionInfo(LinearLayout parent) {
        LinearLayout card = createCard();

        String versionName = "1.0.0";
        int versionCode = 100;
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // 使用默认值
        }

        addInfoRow(card, "应用版本", "v" + versionName);
        addInfoRow(card, "版本代码", String.valueOf(versionCode));
        addInfoRow(card, "构建日期", "2026-01-01");
        addInfoRow(card, "内核版本", DeviceUtils.getKernelVersion(this));
        addInfoRow(card, "API 版本", String.valueOf(DeviceUtils.getApiLevel()));

        parent.addView(card);
    }

    private void addSystemInfo(LinearLayout parent) {
        LinearLayout card = createCard();

        addInfoRow(card, "设备型号", DeviceUtils.getDeviceModel());
        addInfoRow(card, "Android 版本", DeviceUtils.getAndroidVersion());
        addInfoRow(card, "API 级别", String.valueOf(DeviceUtils.getApiLevel()));
        addInfoRow(card, "CPU 架构", DeviceUtils.getArchitecture());
        addInfoRow(card, "安全补丁", DeviceUtils.getSecurityPatchLevel());

        parent.addView(card);
    }

    private void addLicenseInfo(LinearLayout parent) {
        LinearLayout card = createCard();

        String[] licenses = {
            "AuroraSU", "GPL-2.0-or-later",
            "Android Open Source Project", "Apache-2.0",
            "JSON.org", "MIT",
            "Zip utilities", "Public Domain"
        };

        for (int i = 0; i < licenses.length; i += 2) {
            addInfoRow(card, licenses[i], licenses[i + 1]);
        }

        // 许可证声明
        TextView notice = new TextView(this);
        notice.setText("本程序为自由软件，基于 GPL-2.0-or-later 许可证发布。\n"
            + "您可以自由使用、修改和分发本软件。");
        notice.setTextSize(12);
        notice.setTextColor(Color.parseColor("#9E9E9E"));
        notice.setPadding(0, dpToPx(12), 0, 0);
        card.addView(notice);

        parent.addView(card);
    }

    private void addTeamInfo(LinearLayout parent) {
        LinearLayout card = createCard();

        String[][] teamMembers = {
            {"AuroraSU Team", "核心开发团队"},
            {"Contributors", "开源社区贡献者"},
            {"Beta Testers", "内测用户"},
        };

        for (String[] member : teamMembers) {
            addInfoRow(card, member[0], member[1]);
        }

        TextView thanks = new TextView(this);
        thanks.setText("感谢所有为 AuroraSU 做出贡献的开发者和用户！");
        thanks.setTextSize(12);
        thanks.setTextColor(Color.parseColor("#9E9E9E"));
        thanks.setPadding(0, dpToPx(12), 0, 0);
        card.addView(thanks);

        parent.addView(card);
    }

    private void addLinks(LinearLayout parent) {
        LinearLayout card = createCard();

        String[][] links = {
            {"GitHub", "https://github.com/AuroraSU"},
            {"问题反馈", "https://github.com/AuroraSU/issues"},
            {"文档", "https://github.com/AuroraSU/wiki"},
            {"Telegram", "https://t.me/AuroraSU"},
        };

        for (final String[] link : links) {
            LinearLayout row = createInfoRow(link[0], link[1]);
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    android.content.Intent intent = new android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(link[1])
                    );
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        // 没有浏览器
                    }
                }
            });
            card.addView(row);
        }

        parent.addView(card);
    }

    private void addFooter(LinearLayout parent) {
        TextView footer = new TextView(this);
        footer.setText("Copyright (C) 2026 AuroraSU Team\n"
            + "Licensed under GPL-2.0-or-later");
        footer.setTextSize(11);
        footer.setTextColor(Color.parseColor("#BDBDBD"));
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dpToPx(24), 0, dpToPx(8));
        parent.addView(footer);
    }

    // ---- UI 辅助方法 ----

    private LinearLayout createCard() {
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

    private void addSectionTitle(LinearLayout parent, String title) {
        TextView sectionTitle = new TextView(this);
        sectionTitle.setText(title);
        sectionTitle.setTextSize(13);
        sectionTitle.setTextColor(Color.parseColor("#7C4DFF"));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(0, dpToPx(16), 0, dpToPx(8));
        parent.addView(sectionTitle);
    }

    private void addInfoRow(LinearLayout parent, String label, String value) {
        LinearLayout row = createInfoRow(label, value);
        parent.addView(row);
    }

    private LinearLayout createInfoRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dpToPx(4), 0, dpToPx(4));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(14);
        labelView.setTextColor(Color.parseColor("#757575"));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        labelView.setLayoutParams(labelParams);
        row.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(14);
        valueView.setTextColor(Color.parseColor("#212121"));
        valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(valueView);

        return row;
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
