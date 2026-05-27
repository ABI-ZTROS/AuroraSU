package com.ztros.ztrosu.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PRIMARY,
    secondary = PRIMARY_DARK,
    tertiary = SECONDARY_DARK
)

private val LightColorScheme = lightColorScheme(
    primary = PRIMARY,
    secondary = PRIMARY_LIGHT,
    tertiary = SECONDARY_LIGHT
)

private val IceAbyssColorScheme = lightColorScheme(
    primary = ICE_ABYSS_PRIMARY,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF002029),
    secondary = ICE_ABYSS_PRIMARY.copy(alpha = 0.7f),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F7FA),
    onSecondaryContainer = Color(0xFF002029),
    tertiary = Color(0xFF4A657D),
    onTertiary = Color.White,
    background = ICE_ABYSS_BACKGROUND,
    onBackground = ICE_ABYSS_ON_BACKGROUND,
    surface = Color(0xE6FFFFFF),              // 90% 白色 - 磨砂玻璃基础
    onSurface = ICE_ABYSS_ON_BACKGROUND,
    surfaceVariant = Color(0xFFDAE5EB),
    onSurfaceVariant = ICE_ABYSS_ON_SURFACE_VARIANT,
    outline = ICE_ABYSS_OUTLINE,
    outlineVariant = Color(0xFFBEC9CF),
    inverseSurface = Color(0xFF0A1929),
    inverseOnSurface = Color(0xFFE6F4FA),
    inversePrimary = Color(0xFF00B4D8),
    surfaceContainerLowest = Color(0xF2FFFFFF), // 95% 白色
    surfaceContainerLow = Color(0xE6FFFFFF),    // 90% 白色
    surfaceContainer = Color(0xDDFFFFFF),       // 87% 白色
    surfaceContainerHigh = Color(0xD4FFFFFF),    // 83% 白色
    surfaceContainerHighest = Color(0xCCFFFFFF), // 80% 白色
)

val LocalThemePreset = compositionLocalOf { "default" }

fun Color.blend(other: Color, ratio: Float): Color {
    val inverse = 1f - ratio
    return Color(
        red = red * inverse + other.red * ratio,
        green = green * inverse + other.green * ratio,
        blue = blue * inverse + other.blue * ratio,
        alpha = alpha
    )
}

@Composable
fun KernelSUTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    amoledMode: Boolean = false,
    themePreset: String = "default",  // "default", "ice_abyss"
    accentColor: Long = -1,  // -1 表示使用默认
    fontScale: Float = 1f,
    cornerRadius: Float = 16f,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        themePreset == "ice_abyss" -> IceAbyssColorScheme
        amoledMode && darkTheme && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamicScheme = dynamicDarkColorScheme(context)
            dynamicScheme.copy(
                background = AMOLED_BLACK,
                surface = AMOLED_BLACK,
                surfaceVariant = dynamicScheme.surfaceVariant.blend(AMOLED_BLACK, 0.6f),
                surfaceContainer = dynamicScheme.surfaceContainer.blend(AMOLED_BLACK, 0.6f),
                surfaceContainerLow = dynamicScheme.surfaceContainerLow.blend(AMOLED_BLACK, 0.6f),
                surfaceContainerLowest = dynamicScheme.surfaceContainerLowest.blend(AMOLED_BLACK, 0.6f),
                surfaceContainerHigh = dynamicScheme.surfaceContainerHigh.blend(AMOLED_BLACK, 0.6f),
                surfaceContainerHighest = dynamicScheme.surfaceContainerHighest.blend(AMOLED_BLACK, 0.6f)
            )
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        amoledMode && darkTheme -> {
            DarkColorScheme.copy(
                background = AMOLED_BLACK,
                surface = AMOLED_BLACK,
                surfaceVariant = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
                surfaceContainer = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
                surfaceContainerLow = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
                surfaceContainerLowest = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
                surfaceContainerHigh = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
                surfaceContainerHighest = DARK_GREY.blend(AMOLED_BLACK, 0.8f),
            )
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 应用自定义强调色
    val finalColorScheme = if (accentColor != -1L) {
        val customPrimary = Color(accentColor.toULong())
        colorScheme.copy(
            primary = customPrimary,
            onPrimary = Color.White,
            primaryContainer = customPrimary.copy(alpha = 0.15f),
            onPrimaryContainer = customPrimary,
            secondary = customPrimary.copy(alpha = 0.7f),
            onSecondary = Color.White,
            secondaryContainer = customPrimary.copy(alpha = 0.15f),
            tertiary = customPrimary.copy(alpha = 0.5f),
        )
    } else {
        colorScheme
    }

    SystemBarStyle(
        darkMode = darkTheme
    )

    CompositionLocalProvider(LocalThemePreset provides themePreset) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = if (fontScale != 1f) {
                Typography.copy(
                    bodyLarge = Typography.bodyLarge.copy(fontSize = Typography.bodyLarge.fontSize * fontScale),
                    bodyMedium = Typography.bodyMedium.copy(fontSize = Typography.bodyMedium.fontSize * fontScale),
                    bodySmall = Typography.bodySmall.copy(fontSize = Typography.bodySmall.fontSize * fontScale),
                    titleLarge = Typography.titleLarge.copy(fontSize = Typography.titleLarge.fontSize * fontScale),
                    titleMedium = Typography.titleMedium.copy(fontSize = Typography.titleMedium.fontSize * fontScale),
                    titleSmall = Typography.titleSmall.copy(fontSize = Typography.titleSmall.fontSize * fontScale),
                    labelLarge = Typography.labelLarge.copy(fontSize = Typography.labelLarge.fontSize * fontScale),
                    labelMedium = Typography.labelMedium.copy(fontSize = Typography.labelMedium.fontSize * fontScale),
                    labelSmall = Typography.labelSmall.copy(fontSize = Typography.labelSmall.fontSize * fontScale),
                    headlineLarge = Typography.headlineLarge.copy(fontSize = Typography.headlineLarge.fontSize * fontScale),
                    headlineMedium = Typography.headlineMedium.copy(fontSize = Typography.headlineMedium.fontSize * fontScale),
                    headlineSmall = Typography.headlineSmall.copy(fontSize = Typography.headlineSmall.fontSize * fontScale),
                    displayLarge = Typography.displayLarge.copy(fontSize = Typography.displayLarge.fontSize * fontScale),
                    displayMedium = Typography.displayMedium.copy(fontSize = Typography.displayMedium.fontSize * fontScale),
                    displaySmall = Typography.displaySmall.copy(fontSize = Typography.displaySmall.fontSize * fontScale),
                )
            } else {
                Typography
            },
            shapes = Shapes(
                extraSmall = RoundedCornerShape(cornerRadius.dp * 0.4f),
                small = RoundedCornerShape(cornerRadius.dp * 0.5f),
                medium = RoundedCornerShape(cornerRadius.dp),
                large = RoundedCornerShape(cornerRadius.dp * 1.2f),
                extraLarge = RoundedCornerShape(cornerRadius.dp * 1.5f),
            ),
            content = content
        )
    }
}

@Composable
private fun SystemBarStyle(
    darkMode: Boolean,
    statusBarScrim: Color = Color.Transparent,
    navigationBarScrim: Color = Color.Transparent,
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    SideEffect {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                statusBarScrim.toArgb(),
                statusBarScrim.toArgb(),
            ) { darkMode },
            navigationBarStyle = when {
                darkMode -> SystemBarStyle.dark(
                    navigationBarScrim.toArgb()
                )

                else -> SystemBarStyle.light(
                    navigationBarScrim.toArgb(),
                    navigationBarScrim.toArgb(),
                )
            }
        )
    }
}