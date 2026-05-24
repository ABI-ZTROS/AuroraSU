package com.aurora.su.ui.kernelFlash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.aurora.su.ui.LocalUiMode
import com.aurora.su.ui.UiMode
import com.aurora.su.ui.screen.install.InstallMethod

@Composable
fun rememberAnyKernel3State(
    installMethodState: MutableState<InstallMethod?>,
    preselectedKernelUri: String?,
    horizonKernelSummary: String,
    isAbDevice: Boolean
) = when (LocalUiMode.current) {
    UiMode.Miuix -> rememberAnyKernel3StateMiuix(
        installMethodState = installMethodState,
        preselectedKernelUri = preselectedKernelUri,
        horizonKernelSummary = horizonKernelSummary,
        isAbDevice = isAbDevice
    )
    UiMode.Material -> rememberAnyKernel3StateMaterial(
        installMethodState = installMethodState,
        preselectedKernelUri = preselectedKernelUri,
        horizonKernelSummary = horizonKernelSummary,
        isAbDevice = isAbDevice
    )
}

@Composable
fun KpmPatchSelectionDialog(
    show: Boolean,
    currentOption: KpmPatchOption,
    onDismiss: () -> Unit,
    onOptionSelected: (KpmPatchOption) -> Unit
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> KpmPatchSelectionDialogMiuix(
            show = show,
            currentOption = currentOption,
            onDismiss = onDismiss,
            onOptionSelected = onOptionSelected
        )
        UiMode.Material -> KpmPatchSelectionDialogMaterial(
            show = show,
            currentOption = currentOption,
            onDismiss = onDismiss,
            onOptionSelected = onOptionSelected
        )
    }
}


