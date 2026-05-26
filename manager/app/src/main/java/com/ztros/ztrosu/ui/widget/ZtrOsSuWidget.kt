package com.ztros.ztrosu.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceState
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.FontWeight
import androidx.glance.background
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.ztros.ztrosu.ui.MainActivity

class ZtrOsSuWidget : GlanceAppWidget() {

    companion object {
        private const val PREFS_NAME = "ztr_os_su_widget_prefs"
        private const val KEY_LAST_UPDATE = "last_update"
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent()
        }
    }
}

class ZtrOsSuWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ZtrOsSuWidget()
}

@Composable
private fun WidgetContent() {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(ColorProvider(day = android.graphics.Color.WHITE, night = android.graphics.Color.parseColor("#1E1E1E")))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // App name
            Text(
                text = "ZTR_OS SU",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Root status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .cornerRadius(4.dp)
                        .background(ColorProvider(day = android.graphics.Color.parseColor("#4CAF50"), night = android.graphics.Color.parseColor("#66BB6A")))
                ) {}
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Root: Active",
                    fontSize = 14.sp
                )
            }

            // SELinux status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .cornerRadius(4.dp)
                        .background(ColorProvider(day = android.graphics.Color.parseColor("#FF9800"), night = android.graphics.Color.parseColor("#FFA726")))
                ) {}
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SELinux: Enforcing",
                    fontSize = 14.sp
                )
            }

            // Module count
            Text(
                text = "Modules: 0",
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Last updated
            Text(
                text = "Updated: --",
                fontSize = 12.sp
            )
        }
    }
}
