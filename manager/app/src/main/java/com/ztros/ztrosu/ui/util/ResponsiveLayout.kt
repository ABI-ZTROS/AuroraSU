package com.ztros.ztrosu.ui.util

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window size class for responsive layout decisions
 */
enum class WindowSizeClass {
    COMPACT,    // < 600dp (phone portrait)
    MEDIUM,     // 600-840dp (phone landscape, small tablet)
    EXPANDED    // > 840dp (tablet, foldable)
}

/**
 * Local provider for current window size class
 */
val LocalWindowSizeClass = compositionLocalOf { WindowSizeClass.COMPACT }

/**
 * Provides window size class to all child composables based on current screen width.
 * Usage: Wrap your Scaffold content with this.
 */
@Composable
fun ResponsiveLayout(
    content: @Composable () -> Unit
) {
    BoxWithConstraints {
        val windowSizeClass = when {
            maxWidth < 600.dp -> WindowSizeClass.COMPACT
            maxWidth < 840.dp -> WindowSizeClass.MEDIUM
            else -> WindowSizeClass.EXPANDED
        }

        CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
            content()
        }
    }
}

/**
 * Returns responsive padding based on window size class.
 * Compact: 16dp, Medium: 24dp, Expanded: 32dp
 */
@Composable
fun responsiveHorizontalPadding(): Dp = when (LocalWindowSizeClass.current) {
    WindowSizeClass.COMPACT -> 16.dp
    WindowSizeClass.MEDIUM -> 24.dp
    WindowSizeClass.EXPANDED -> 32.dp
}

/**
 * Returns responsive card arrangement spacing based on window size.
 */
@Composable
fun responsiveSpacing(): Dp = when (LocalWindowSizeClass.current) {
    WindowSizeClass.COMPACT -> 12.dp
    WindowSizeClass.MEDIUM -> 16.dp
    WindowSizeClass.EXPANDED -> 20.dp
}

/**
 * Returns whether the current layout should use a two-column grid.
 */
@Composable
fun shouldUseTwoColumns(): Boolean = LocalWindowSizeClass.current >= WindowSizeClass.MEDIUM

/**
 * Returns the number of grid columns based on window size.
 */
@Composable
fun gridColumns(): Int = when (LocalWindowSizeClass.current) {
    WindowSizeClass.COMPACT -> 1
    WindowSizeClass.MEDIUM -> 2
    WindowSizeClass.EXPANDED -> 3
}
