package com.aurora.su.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.aurora.su.ui.UiMode
import com.aurora.su.ui.theme.AppSettings

@Immutable
data class MainActivityUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val enableFloatingBottomBarBlur: Boolean,
    val uiMode: UiMode,
)
