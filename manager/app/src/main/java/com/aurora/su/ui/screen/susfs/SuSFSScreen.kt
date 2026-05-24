package com.aurora.su.ui.screen.susfs

import androidx.compose.runtime.Composable
import com.aurora.su.ui.LocalUiMode
import com.aurora.su.ui.UiMode

@Composable
fun SuSFSScreen() {
    when (LocalUiMode.current) {
        UiMode.Miuix -> SuSFSMiuix()
        UiMode.Material -> SuSFSMaterial()
    }
}
