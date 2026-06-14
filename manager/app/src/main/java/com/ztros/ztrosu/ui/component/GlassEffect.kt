package com.ztros.ztrosu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Local provider for blur enabled state across the app
 */
val LocalBlurEnabled = compositionLocalOf { false }

/**
 * Local provider for HazeState (shared blur source)
 */
val LocalHazeState = compositionLocalOf { HazeState() }

/**
 * A composable that provides haze blur source to all child composables.
 * Place this at the root of your content area (behind all content).
 */
@Composable
fun HazeBlurSource(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val hazeState = LocalHazeState.current
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        ) {
            content()
        }
    }
}

/**
 * A glassmorphism card with frosted glass effect.
 * Automatically applies blur when blur is enabled in settings.
 * Falls back to a semi-transparent surface when blur is disabled.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    blurRadius: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    content: @Composable BoxScope.() -> Unit
) {
    val blurEnabled = LocalBlurEnabled.current
    val hazeState = LocalHazeState.current

    val backgroundColor = if (blurEnabled) {
        tint
    } else {
        MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (blurEnabled) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            blurRadius = blurRadius,
                            tint = HazeTint(tint.copy(alpha = 0.7f)),
                        )
                    )
                } else {
                    Modifier
                }
            )
    ) {
        content()
    }
}

/**
 * A glassmorphism surface for bottom bars, navigation bars, etc.
 * Thinner and more subtle than GlassCard.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = MaterialTheme.shapes.extraLarge,
    blurRadius: Dp = 30.dp,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f),
    content: @Composable BoxScope.() -> Unit
) {
    val blurEnabled = LocalBlurEnabled.current
    val hazeState = LocalHazeState.current

    val backgroundColor = if (blurEnabled) {
        tint
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (blurEnabled) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            blurRadius = blurRadius,
                            tint = HazeTint(tint.copy(alpha = 0.8f)),
                        )
                    )
                } else {
                    Modifier
                }
            )
    ) {
        content()
    }
}

/**
 * A thin glassmorphism divider/separator.
 */
@Composable
fun GlassDivider(
    modifier: Modifier = Modifier
) {
    val blurEnabled = LocalBlurEnabled.current
    val hazeState = LocalHazeState.current

    if (blurEnabled) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        blurRadius = 10.dp,
                        tint = HazeTint(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    )
                )
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
    }
}
