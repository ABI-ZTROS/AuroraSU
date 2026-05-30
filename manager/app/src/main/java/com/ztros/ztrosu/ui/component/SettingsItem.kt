package com.ztros.ztrosu.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemColors
import com.dergoogler.mmrl.ui.component.LabelItem
import com.dergoogler.mmrl.ui.component.text.TextRow

@Composable
fun SwitchItem(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    beta: Boolean = false,
    modifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.colors(),
    onCheckedChange: (Boolean) -> Unit,
) {
    val switchInteractionSource = remember { MutableInteractionSource() }
    // 使用 derivedStateOf 优化状态管理，避免不必要的重组
    val alphaValue by remember(enabled) {
        derivedStateOf { if (enabled) 1f else 0.5f }
    }
    // 使用 rememberUpdatedState 确保回调始终使用最新值
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

    ListItem(
        modifier = modifier,
        colors = colors,
        headlineContent = {
            TextRow(
                leadingContent = if (beta) {
                    {
                        LabelItem(
                            modifier = Modifier.alpha(alphaValue),
                            text = "Beta"
                        )
                    }
                } else null
            ) {
                Text(
                    modifier = Modifier.alpha(alphaValue),
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        leadingContent = icon?.let {
            {
                Icon(
                    modifier = Modifier.alpha(alphaValue),
                    imageVector = icon,
                    contentDescription = title
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = { newValue ->
                    // 防抖处理：只有当状态真正改变时才回调
                    if (newValue != checked) {
                        currentOnCheckedChange(newValue)
                    }
                },
                interactionSource = switchInteractionSource
            )
        },
        supportingContent = {
            if (summary != null) {
                Text(
                    modifier = Modifier.alpha(alphaValue),
                    text = summary
                )
            }
        }
    )
}

@Composable
fun RadioItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(title)
        },
        leadingContent = {
            RadioButton(selected = selected, onClick = onClick)
        }
    )
}
