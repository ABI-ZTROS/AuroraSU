package com.aurora.su.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.view.Gravity;
import android.util.TypedValue;

import com.aurora.su.core.AuroraCore;

public class MainActivity extends Activity {
    
    private TextView statusText;
    private TextView statusBadge;
    private TextView moduleCount;
    private TextView grantedApps;
    private TextView deniedApps;
    private TextView uptimeText;
    private TextView kernelVersion;
    private TextView selinuxStatus;
    private TextView versionText;
    private LinearLayout rootContainer;
    private AuroraCore core;
    private Handler handler;
    private int refreshInterval = 2000;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        handler = new Handler(Looper.getMainLooper());
        core = new AuroraCore();
        
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.MATCH_PARENT
        ));
        scrollView.setBackgroundColor(Color.parseColor("#F8F9FA"));
        
        rootContainer = new LinearLayout(this);
        rootContainer.setOrientation(LinearLayout.VERTICAL);
        rootContainer.setPadding(dpToPx(12), dpToPx(16), dpToPx(12), dpToPx(24));
        
        addHeader();
        addMainStatusGauge();
        addStatsGrid();
        addSecurityDashboard();
        addSystemInfoPanel();
        addModuleActivityPanel();
        addQuickActions();
        addFooter();
        
        scrollView.addView(rootContainer);
        setContentView(scrollView);
        
        startAutoRefresh();
    }
    
    private void addHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        
        TextView title = new TextView(this);
        title.setText("AuroraSU");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTextColor(Color.parseColor("#7C4DFF"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        title.setLayoutParams(titleParams);
        header.addView(title);
        
        TextView versionBadge = new TextView(this);
        versionBadge.setText("v1.0");
        versionBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        versionBadge.setTextColor(Color.parseColor("#7C4DFF"));
        versionBadge.setBackground(createBadgeBackground(Color.parseColor("#F3E5F5")));
        versionBadge.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        header.addView(versionBadge);
        
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headerParams.setMargins(0, 0, 0, dpToPx(16));
        header.setLayoutParams(headerParams);
        rootContainer.addView(header);
    }
    
    private void addMainStatusGauge() {
        LinearLayout gaugeCard = createDashboardCard();
        
        LinearLayout gaugeContainer = new LinearLayout(this);
        gaugeContainer.setOrientation(LinearLayout.HORIZONTAL);
        gaugeContainer.setGravity(Gravity.CENTER_VERTICAL);
        
        LinearLayout leftSection = new LinearLayout(this);
        leftSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        leftSection.setLayoutParams(leftParams);
        
        TextView statusLabel = new TextView(this);
        statusLabel.setText("系统状态");
        statusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusLabel.setTextColor(Color.parseColor("#757575"));
        leftSection.addView(statusLabel);
        
        statusBadge = new TextView(this);
        statusBadge.setText("● 运行正常");
        statusBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        statusBadge.setTextColor(Color.parseColor("#4CAF50"));
        statusBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        statusBadge.setPadding(0, dpToPx(4), 0, dpToPx(8));
        leftSection.addView(statusBadge);
        
        TextView subtitle = new TextView(this);
        subtitle.setText("内核级 Root 权限管理");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitle.setTextColor(Color.parseColor("#9E9E9E"));
        leftSection.addView(subtitle);
        
        uptimeText = new TextView(this);
        uptimeText.setText("运行时间: 计算中...");
        uptimeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        uptimeText.setTextColor(Color.parseColor("#757575"));
        uptimeText.setPadding(0, dpToPx(8), 0, 0);
        leftSection.addView(uptimeText);
        
        gaugeContainer.addView(leftSection);
        
        LinearLayout rightSection = new LinearLayout(this);
        rightSection.setOrientation(LinearLayout.VERTICAL);
        rightSection.setGravity(Gravity.CENTER);
        
        TextView percentLabel = new TextView(this);
        percentLabel.setText("100%");
        percentLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        percentLabel.setTextColor(Color.parseColor("#7C4DFF"));
        percentLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        rightSection.addView(percentLabel);
        
        TextView percentSub = new TextView(this);
        percentSub.setText("系统健康度");
        percentSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        percentSub.setTextColor(Color.parseColor("#9E9E9E"));
        rightSection.addView(percentSub);
        
        gaugeContainer.addView(rightSection);
        
        gaugeCard.addView(gaugeContainer);
        rootContainer.addView(gaugeCard);
    }
    
    private void addStatsGrid() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        
        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        leftParams.setMargins(0, 0, dpToPx(6), 0);
        leftCol.setLayoutParams(leftParams);
        
        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        rightParams.setMargins(dpToPx(6), 0, 0, 0);
        rightCol.setLayoutParams(rightParams);
        
        leftCol.addView(createStatCard("已加载模块", "0", "#2196F3", "模块"));
        leftCol.addView(createStatCard("已授权应用", "0", "#4CAF50", "应用"));
        rightCol.addView(createStatCard("已拒绝请求", "0", "#F44336", "请求"));
        rightCol.addView(createStatCard("今日调用", "0", "#FF9800", "次"));
        
        grid.addView(leftCol);
        grid.addView(rightCol);
        
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        gridParams.setMargins(0, 0, 0, dpToPx(12));
        grid.setLayoutParams(gridParams);
        rootContainer.addView(grid);
    }
    
    private void addSecurityDashboard() {
        LinearLayout securityCard = createDashboardCard();
        
        TextView title = new TextView(this);
        title.setText("安全监控");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(Color.parseColor("#212121"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dpToPx(12));
        securityCard.addView(title);
        
        LinearLayout itemsContainer = new LinearLayout(this);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        
        selinuxStatus = createSecurityItem(itemsContainer, "SELinux 状态", "Enforcing", Color.parseColor("#4CAF50"));
        kernelVersion = createSecurityItem(itemsContainer, "内核版本", "检测中...", Color.parseColor("#757575"));
        createSecurityItem(itemsContainer, "安全补丁", "2025-01-01", Color.parseColor("#2196F3"));
        createSecurityItem(itemsContainer, "Root 隐藏", "已启用", Color.parseColor("#4CAF50"));
        
        securityCard.addView(itemsContainer);
        rootContainer.addView(securityCard);
    }
    
    private TextView createSecurityItem(LinearLayout parent, String label, String value, int valueColor) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setPadding(0, dpToPx(8), 0, dpToPx(8));
        
        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        labelTv.setTextColor(Color.parseColor("#757575"));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        labelTv.setLayoutParams(labelParams);
        item.addView(labelTv);
        
        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        valueTv.setTextColor(valueColor);
        valueTv.setTypeface(null, android.graphics.Typeface.BOLD);
        item.addView(valueTv);
        
        parent.addView(item);
        
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#F0F0F0"));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(1)
        );
        divider.setLayoutParams(divParams);
        parent.addView(divider);
        
        return valueTv;
    }
    
    private void addSystemInfoPanel() {
        LinearLayout infoCard = createDashboardCard();
        
        TextView title = new TextView(this);
        title.setText("系统信息");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(Color.parseColor("#212121"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dpToPx(12));
        infoCard.addView(title);
        
        LinearLayout infoGrid = new LinearLayout(this);
        infoGrid.setOrientation(LinearLayout.HORIZONTAL);
        
        String[][] infoData = {
            {"Android 版本", "14"},
            {"API 级别", "34"},
            {"架构", "arm64"},
            {"设备", "检测中..."}
        };
        
        for (int i = 0; i < infoData.length; i += 2) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            );
            col.setLayoutParams(colParams);
            
            for (int j = i; j < Math.min(i + 2, infoData.length); j++) {
                TextView label = new TextView(this);
                label.setText(infoData[j][0]);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                label.setTextColor(Color.parseColor("#9E9E9E"));
                label.setPadding(0, dpToPx(4), 0, 0);
                col.addView(label);
                
                TextView value = new TextView(this);
                value.setText(infoData[j][1]);
                value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                value.setTextColor(Color.parseColor("#212121"));
                value.setTypeface(null, android.graphics.Typeface.BOLD);
                value.setPadding(0, 0, 0, dpToPx(8));
                col.addView(value);
            }
            
            infoGrid.addView(col);
        }
        
        infoCard.addView(infoGrid);
        rootContainer.addView(infoCard);
    }
    
    private void addModuleActivityPanel() {
        LinearLayout activityCard = createDashboardCard();
        
        TextView title = new TextView(this);
        title.setText("模块活动");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(Color.parseColor("#212121"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dpToPx(12));
        activityCard.addView(title);
        
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        
        String[][] legendData = {
            {"● 系统模块", "#2196F3"},
            {"● 用户模块", "#4CAF50"},
            {"● 待更新", "#FF9800"}
        };
        
        for (String[] item : legendData) {
            TextView legendItem = new TextView(this);
            legendItem.setText(item[0]);
            legendItem.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            legendItem.setTextColor(Color.parseColor(item[1]));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            );
            legendItem.setLayoutParams(params);
            legend.addView(legendItem);
        }
        
        activityCard.addView(legend);
        
        TextView noActivity = new TextView(this);
        noActivity.setText("暂无模块活动记录");
        noActivity.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        noActivity.setTextColor(Color.parseColor("#9E9E9E"));
        noActivity.setGravity(Gravity.CENTER);
        noActivity.setPadding(0, dpToPx(24), 0, dpToPx(24));
        activityCard.addView(noActivity);
        
        rootContainer.addView(activityCard);
    }
    
    private void addQuickActions() {
        LinearLayout actionsCard = createDashboardCard();
        
        TextView title = new TextView(this);
        title.setText("快捷操作");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(Color.parseColor("#212121"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dpToPx(12));
        actionsCard.addView(title);
        
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        
        actionsRow.addView(createActionButton("模块管理", "#7C4DFF", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "模块管理功能开发中...", Toast.LENGTH_SHORT).show();
            }
        }));
        
        actionsRow.addView(createActionButton("超级用户", "#4CAF50", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "超级用户管理开发中...", Toast.LENGTH_SHORT).show();
            }
        }));
        
        actionsRow.addView(createActionButton("日志", "#FF9800", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "日志查看器开发中...", Toast.LENGTH_SHORT).show();
            }
        }));
        
        actionsRow.addView(createActionButton("设置", "#757575", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "设置页面开发中...", Toast.LENGTH_SHORT).show();
            }
        }));
        
        actionsCard.addView(actionsRow);
        rootContainer.addView(actionsCard);
    }
    
    private Button createActionButton(String text, String colorHex, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(createActionButtonBackground(Color.parseColor(colorHex)));
        btn.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        btn.setOnClickListener(listener);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        params.setMargins(dpToPx(4), 0, dpToPx(4), 0);
        btn.setLayoutParams(params);
        
        return btn;
    }
    
    private void addFooter() {
        TextView footer = new TextView(this);
        footer.setText("AuroraSU v1.0.0 | Build 2025.01.01\nMade with ❤️ by AuroraSU Team");
        footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        footer.setTextColor(Color.parseColor("#BDBDBD"));
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dpToPx(16), 0, dpToPx(8));
        rootContainer.addView(footer);
    }
    
    private LinearLayout createDashboardCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createCardBackground());
        card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(params);
        return card;
    }
    
    private LinearLayout createStatCard(String label, String value, String colorHex, String unit) {
        LinearLayout card = createDashboardCard();
        
        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        valueTv.setTextColor(Color.parseColor(colorHex));
        valueTv.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(valueTv);
        
        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        labelTv.setTextColor(Color.parseColor("#757575"));
        labelTv.setPadding(0, dpToPx(2), 0, 0);
        card.addView(labelTv);
        
        return card;
    }
    
    private GradientDrawable createCardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dpToPx(16));
        drawable.setStroke(dpToPx(1), Color.parseColor("#E8E8E8"));
        return drawable;
    }
    
    private GradientDrawable createBadgeBackground(int bgColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(bgColor);
        drawable.setCornerRadius(dpToPx(8));
        return drawable;
    }
    
    private GradientDrawable createActionButtonBackground(int bgColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(bgColor);
        drawable.setCornerRadius(dpToPx(12));
        return drawable;
    }
    
    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            getResources().getDisplayMetrics()
        );
    }
    
    private void startAutoRefresh() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshData();
                handler.postDelayed(this, refreshInterval);
            }
        }, 500);
    }
    
    private void refreshData() {
        try {
            boolean isWorking = core.isAuroraSUWorking();
            if (isWorking) {
                statusBadge.setText("● 运行正常");
                statusBadge.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                statusBadge.setText("● 未激活");
                statusBadge.setTextColor(Color.parseColor("#F44336"));
            }
            
            uptimeText.setText("运行时间: " + getUptimeString());
            
        } catch (Exception e) {
            statusBadge.setText("● 检测失败");
            statusBadge.setTextColor(Color.parseColor("#FF9800"));
        }
    }
    
    private String getUptimeString() {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader("/proc/uptime")
            );
            String line = reader.readLine();
            reader.close();
            
            double uptimeSeconds = Double.parseDouble(line.split(" ")[0]);
            long days = (long) (uptimeSeconds / 86400);
            long hours = (long) ((uptimeSeconds % 86400) / 3600);
            long minutes = (long) ((uptimeSeconds % 3600) / 60);
            
            if (days > 0) {
                return days + "天 " + hours + "小时";
            } else if (hours > 0) {
                return hours + "小时 " + minutes + "分钟";
            } else {
                return minutes + "分钟";
            }
        } catch (Exception e) {
            return "未知";
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
