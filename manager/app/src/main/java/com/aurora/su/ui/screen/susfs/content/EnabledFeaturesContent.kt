package com.aurora.su.ui.screen.susfs.content

import androidx.compose.runtime.Composable
import com.aurora.su.ui.LocalUiMode
import com.aurora.su.ui.UiMode
import com.aurora.su.ui.screen.susfs.content.miuix.EnabledFeaturesContentMiuix
import com.aurora.su.ui.screen.susfs.content.material.EnabledFeaturesContentMaterial
import com.aurora.su.ui.screen.susfs.util.SuSFSManager

@Composable
fun EnabledFeaturesContent(
    enabledFeatures: List<SuSFSManager.EnabledFeature>,
    onRefresh: () -> Unit
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> EnabledFeaturesContentMiuix(
            enabledFeatures = enabledFeatures,
            onRefresh = onRefresh
        )
        UiMode.Material -> EnabledFeaturesContentMaterial(
            enabledFeatures = enabledFeatures,
            onRefresh = onRefresh
        )
    }
}
