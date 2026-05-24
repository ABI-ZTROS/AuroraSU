/*
 * AuroraSU - Log Activity
 *
 * Copyright (C) 2026 AuroraSU Team
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.aurora.su.ui;

import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.aurora.su.core.LogManager;
import com.aurora.su.core.LogManager.LogEntry;

/**
 * LogActivity - 日志页面
 * 显示 root 操作日志，支持搜索、过滤、导出和清除
 */
public class LogActivity extends Activity {

    private static final String TAG = "LogActivity";

    private LinearLayout logListContainer;
    private TextView emptyView;
    private TextView statsText;
    private EditText searchInput;
    private Spinner filterSpinner;
    private LogManager logManager;
    private Handler handler;
    private String currentFilter = "ALL";
    private String currentQuery = "";

    private static final String[] FILTER_OPTIONS = {
        "全部", "授权", "撤销", "使用", "拒绝", "错误"
    };
    private static final String[] FILTER_VALUES = {
        "ALL", "GRANT", "REVOKE", "USE", "DENY", "ERROR"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        logManager = new LogManager(this);
        handler = new Handler(Looper.getMainLooper());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#F8F9FA"));

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24));

        // 标题栏
        addTitleBar(rootLayout);

        // 搜索和过滤栏
        addFilterBar(rootLayout);

        // 统计信息
        statsText = new TextView(this);
        statsText.setText("加载中...");
        statsText.setTextSize(12);
        statsText.setTextColor(Color.parseColor("#9E9E9E"));
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statsParams.setMargins(0, dpToPx(4), 0, dpToPx(8));
        statsText.setLayoutParams(statsParams);
        rootLayout.addView(statsText);

        // 日志列表容器
        logListContainer = new LinearLayout(this);
        logListContainer.setOrientation(LinearLayout.VERTICAL);
        rootLayout.addView(logListContainer);

        // 空视图
        emptyView = new TextView(this);
        emptyView.setText("暂无日志记录");
        emptyView.setTextSize(16);
        emptyView.setTextColor(Color.parseColor("#9E9E9E"));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, dpToPx(48), 0, dpToPx(48));
        emptyView.setVisibility(View.GONE);
        rootLayout.addView(emptyView);

        // 底部操作按钮
        addBottomActions(rootLayout);

        scrollView.addView(rootLayout);
        setContentView(scrollView);

        loadLogs();
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
        title.setText("Root 日志");
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

    private void addFilterBar(LinearLayout parent) {
        LinearLayout filterBar = new LinearLayout(this);
        filterBar.setOrientation(LinearLayout.HORIZONTAL);
        filterBar.setBackground(createCardBackground());
        filterBar.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dpToPx(12));
        filterBar.setLayoutParams(params);

        // 过滤下拉
        filterSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, FILTER_OPTIONS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(adapter);
        filterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                currentFilter = FILTER_VALUES[position];
                loadLogs();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        spinnerParams.setMargins(0, 0, dpToPx(8), 0);
        filterSpinner.setLayoutParams(spinnerParams);
        filterBar.addView(filterSpinner);

        // 搜索框
        searchInput = new EditText(this);
        searchInput.setHint("搜索...");
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
                currentQuery = s.toString();
                loadLogs();
            }
        });
        filterBar.addView(searchInput);

        parent.addView(filterBar);
    }

    private void addBottomActions(LinearLayout parent) {
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dpToPx(16), 0, 0);
        actionRow.setLayoutParams(params);

        Button exportBtn = new Button(this);
        exportBtn.setText("导出日志");
        exportBtn.setTextSize(13);
        exportBtn.setTextColor(Color.WHITE);
        exportBtn.setBackground(createButtonBackground(Color.parseColor("#2196F3")));
        exportBtn.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        LinearLayout.LayoutParams exportParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        exportParams.setMargins(0, 0, dpToPx(8), 0);
        exportBtn.setLayoutParams(exportParams);
        exportBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { exportLogs(); }
        });
        actionRow.addView(exportBtn);

        Button clearBtn = new Button(this);
        clearBtn.setText("清除日志");
        clearBtn.setTextSize(13);
        clearBtn.setTextColor(Color.WHITE);
        clearBtn.setBackground(createButtonBackground(Color.parseColor("#F44336")));
        clearBtn.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        clearParams.setMargins(dpToPx(8), 0, 0, 0);
        clearBtn.setLayoutParams(clearParams);
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showClearConfirmDialog(); }
        });
        actionRow.addView(clearBtn);

        parent.addView(actionRow);
    }

    private void loadLogs() {
        List<LogEntry> logs;
        if ("ALL".equals(currentFilter) && (currentQuery == null || currentQuery.isEmpty())) {
            logs = logManager.getLogs(0, 200);
        } else {
            String filterType = "ALL".equals(currentFilter) ? null : currentFilter;
            String query = (currentQuery != null && !currentQuery.isEmpty()) ? currentQuery : null;
            logs = logManager.filterLogs(filterType, query);
        }

        updateStats(logs);
        renderLogList(logs);
    }

    private void updateStats(List<LogEntry> logs) {
        Map<String, Object> stats = logManager.getLogStats();
        int total = (int) stats.getOrDefault("total_entries", 0);
        int today = (int) stats.getOrDefault("today_entries", 0);
        statsText.setText("显示 " + logs.size() + " 条 (共 " + total + " 条, 今日 " + today + " 条)");
    }

    private void renderLogList(List<LogEntry> logs) {
        logListContainer.removeAllViews();

        if (logs.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }

        emptyView.setVisibility(View.GONE);

        // 最多显示 200 条
        int displayCount = Math.min(logs.size(), 200);
        for (int i = 0; i < displayCount; i++) {
            View logView = createLogItemView(logs.get(i));
            logListContainer.addView(logView);
        }

        if (logs.size() > 200) {
            TextView moreText = new TextView(this);
            moreText.setText("还有 " + (logs.size() - 200) + " 条日志未显示");
            moreText.setTextSize(12);
            moreText.setTextColor(Color.parseColor("#9E9E9E"));
            moreText.setGravity(Gravity.CENTER);
            moreText.setPadding(0, dpToPx(8), 0, dpToPx(8));
            logListContainer.addView(moreText);
        }
    }

    private View createLogItemView(LogEntry entry) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(createCardBackground());
        card.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(4));
        card.setLayoutParams(cardParams);
        card.setGravity(Gravity.CENTER_VERTICAL);

        // 操作类型标签
        TextView actionBadge = new TextView(this);
        actionBadge.setText(entry.getActionDisplay());
        actionBadge.setTextSize(10);
        int actionColor = getActionColor(entry.action);
        actionBadge.setTextColor(actionColor);
        actionBadge.setBackground(createBadgeBackground(actionColor, 0x15));
        actionBadge.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        badgeParams.setMargins(0, 0, dpToPx(8), 0);
        actionBadge.setLayoutParams(badgeParams);
        card.addView(actionBadge);

        // 日志内容
        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        textLayout.setLayoutParams(textParams);

        TextView pkgText = new TextView(this);
        pkgText.setText(entry.packageName);
        pkgText.setTextSize(13);
        pkgText.setTextColor(Color.parseColor("#212121"));
        pkgText.setTypeface(null, android.graphics.Typeface.BOLD);
        pkgText.setSingleLine(true);
        textLayout.addView(pkgText);

        TextView msgText = new TextView(this);
        msgText.setText(entry.message);
        msgText.setTextSize(11);
        msgText.setTextColor(Color.parseColor("#757575"));
        msgText.setSingleLine(true);
        textLayout.addView(msgText);

        card.addView(textLayout);

        // 时间
        TextView timeText = new TextView(this);
        timeText.setText(entry.getFormattedTime());
        timeText.setTextSize(10);
        timeText.setTextColor(Color.parseColor("#BDBDBD"));
        card.addView(timeText);

        return card;
    }

    private int getActionColor(String action) {
        if (action == null) return Color.parseColor("#757575");
        switch (action) {
            case "GRANT": return Color.parseColor("#4CAF50");
            case "REVOKE": return Color.parseColor("#F44336");
            case "USE": return Color.parseColor("#2196F3");
            case "DENY": return Color.parseColor("#FF9800");
            case "ERROR": return Color.parseColor("#F44336");
            case "BOOT": return Color.parseColor("#9C27B0");
            case "MODULE": return Color.parseColor("#00BCD4");
            default: return Color.parseColor("#757575");
        }
    }

    private void exportLogs() {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("正在导出日志...");
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCancelable(false);
        dialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String path = logManager.exportLogs();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        if (path != null) {
                            Toast.makeText(LogActivity.this, "日志已导出到: " + path, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(LogActivity.this, "导出失败，请检查外部存储权限", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void showClearConfirmDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("清除日志");
        builder.setMessage("确定要清除所有 Root 日志吗？\n\n旧日志将自动备份。");
        builder.setPositiveButton("清除", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                boolean success = logManager.clearLogs();
                if (success) {
                    Toast.makeText(LogActivity.this, "日志已清除", Toast.LENGTH_SHORT).show();
                    loadLogs();
                } else {
                    Toast.makeText(LogActivity.this, "清除失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // ---- UI 辅助方法 ----

    private GradientDrawable createCardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dpToPx(8));
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

    private GradientDrawable createBadgeBackground(int textColor, int bgColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(bgColor);
        drawable.setCornerRadius(dpToPx(4));
        return drawable;
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp,
            getResources().getDisplayMetrics()
        );
    }
}
