package com.ztros.ztrosu.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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

class ZtrOsSuWidget : GlanceAppWidget() {
    override fun provideGlance(context: Context, id: Int) {
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
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(ColorProvider(Color(0xFFFFFFFF)))
            .padding(16.dp)
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
                    color = ColorProvider(Color(0xFF212121))
                )
            )
            Spacer(modifier = GlanceModifier.height(8.dp))

            // Root status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.padding(vertical = 2.dp)
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .cornerRadius(4.dp)
                        .background(ColorProvider(Color(0xFF4CAF50)))
                ) {}
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = "Root: Active",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = ColorProvider(Color(0xFF212121))
                    )
                )
            }

            // SELinux status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.padding(vertical = 2.dp)
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .cornerRadius(4.dp)
                        .background(ColorProvider(Color(0xFFFF9800)))
                ) {}
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = "SELinux: Enforcing",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = ColorProvider(Color(0xFF212121))
                    )
                )
            }

            // Module count
            Text(
                text = "Modules: 0",
                style = TextStyle(
                    fontSize = 14.sp,
                    color = ColorProvider(Color(0xFF212121))
                )
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Last updated
            Text(
                text = "Updated: --",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = ColorProvider(Color(0xFF757575))
                )
            )
        }
    }
}
