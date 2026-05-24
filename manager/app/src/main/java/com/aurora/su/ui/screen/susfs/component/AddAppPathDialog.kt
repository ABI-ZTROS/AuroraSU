package com.aurora.su.ui.screen.susfs.component

import android.content.pm.PackageInfo
import androidx.compose.runtime.Composable
import com.aurora.su.ui.LocalUiMode
import com.aurora.su.ui.UiMode
import com.aurora.su.ui.screen.susfs.component.miuix.AddAppPathDialogMiuix
import com.aurora.su.ui.screen.susfs.component.material.AddAppPathDialogMaterial
import com.aurora.su.ui.screen.susfs.util.SuSFSManager

@Composable
fun AddAppPathDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    isLoading: Boolean,
    apps: List<SuSFSManager.AppInfo> = emptyList(),
    onLoadApps: () -> Unit,
    existingSusPaths: Set<String> = emptySet()
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> AddAppPathDialogMiuix(
            showDialog = showDialog,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            isLoading = isLoading,
            apps = apps,
            onLoadApps = onLoadApps,
            existingSusPaths = existingSusPaths
        )
        UiMode.Material -> AddAppPathDialogMaterial(
            showDialog = showDialog,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            isLoading = isLoading,
            apps = apps,
            onLoadApps = onLoadApps,
            existingSusPaths = existingSusPaths
        )
    }
}
