package com.ztros.ztrosu.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.GlanceId
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ztros.ztrosu.MainActivity

class ZtrOsSuWidget : GlanceAppWidget() {

    companion object {
        private const val TAG = "ZtrOsSuWidget"
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent(context)
        }
    }
}

class ZtrOsSuWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ZtrOsSuWidget()
}

@Composable
private fun WidgetContent(context: Context) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(ColorProvider(Color(0xFF1C1B1F)))
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "ZTR_OS SU",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color(0xFFD0BCFF))
                )
            )
            Spacer(modifier = GlanceModifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .cornerRadius(4.dp)
                        .background(ColorProvider(Color(0xFF4CAF50)))
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = "Root: Active",
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = ColorProvider(Color(0xFFE0E0E0))
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .cornerRadius(4.dp)
                        .background(ColorProvider(Color(0xFFFF9800)))
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = "SELinux: Enforcing",
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = ColorProvider(Color(0xFFBDBDBD))
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Modules: 0",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = ColorProvider(Color(0xFF9E9E9E))
                )
            )
        }
    }
}
