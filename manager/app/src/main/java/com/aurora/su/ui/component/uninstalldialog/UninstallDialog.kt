package com.aurora.su.ui.component.uninstalldialog

import androidx.compose.runtime.Composable
import com.aurora.su.ui.LocalUiMode
import com.aurora.su.ui.UiMode

@Composable
fun UninstallDialog(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> UninstallDialogMiuix(show, onDismissRequest)
        UiMode.Material -> UninstallDialogMaterial(show, onDismissRequest)
    }
}
