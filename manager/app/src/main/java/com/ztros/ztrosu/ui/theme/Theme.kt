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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// LocalCardElevation for providing card elevation to child components
val LocalCardElevation = compositionLocalOf { 1.dp }

private val DarkColorScheme = darkColorScheme(
    primary = PRIMARY,
    secondary = PRIMARY_DARK,
    tertiary = SECONDARY_DARK,
    background = Color(0xFF1A1A1E),
    surface = Color(0xFF202024),
    surfaceVariant = Color(0xFF2A2A30),
    onBackground = Color(0xFFE0E0E4),
    onSurface = Color(0xFFE4E4E8),
    onSurfaceVariant = Color(0xFFC0C0C4),
    outline = Color(0xFF606068),
    outlineVariant = Color(0xFF404048),
)

private val LightColorScheme = lightColorScheme(
    primary = PRIMARY,
    secondary = PRIMARY_LIGHT,
    tertiary = SECONDARY_LIGHT,
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFFE8E8E8),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF202020),
    onSurfaceVariant = Color(0xFF404040),
    outline = Color(0xFF808080),
    outlineVariant = Color(0xFFB0B0B0),
)

private val IceAbyssDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00B4D8),
    onPrimary = Color(0xFF003444),
    primaryContainer = Color(0xFF004C66),
    onPrimaryContainer = Color(0xFFC8F4FF),
    secondary = Color(0xFF4A627A),
    onSecondary = Color(0xFFDDE7F0),
    secondaryContainer = Color(0xFF33485E),
    onSecondaryContainer = Color(0xFFD8E3EC),
    tertiary = Color(0xFF00B4D8),
    background = Color(0xFF0A1929),
    onBackground = Color(0xFFE0E8EF),
    surface = Color(0xFF0F1F30),
    onSurface = Color(0xFFE0E8EF),
    surfaceVariant = Color(0xFF3E4A56),
    onSurfaceVariant = Color(0xFFBFC9D2),
    outline = Color(0xFF89939E),
    outlineVariant = Color(0xFF3E4A56),
    surfaceContainerLowest = Color(0xFF0A141E),
    surfaceContainerLow = Color(0xFF111D2B),
    surfaceContainer = Color(0xFF152235),
    surfaceContainerHigh = Color(0xFF1A2A3D),
    surfaceContainerHighest = Color(0xFF253548),
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
    surface = Color(0xE6E6F4FA),              // 浅水蓝（90%透明）- 保持与背景一致
    onSurface = ICE_ABYSS_ON_BACKGROUND,
    surfaceVariant = Color(0xFFDAE5EB),
    onSurfaceVariant = ICE_ABYSS_ON_SURFACE_VARIANT,
    outline = ICE_ABYSS_OUTLINE,
    outlineVariant = Color(0xFFBEC9CF),
    inverseSurface = Color(0xFF0A1929),
    inverseOnSurface = Color(0xFFE6F4FA),
    inversePrimary = Color(0xFF00B4D8),
    surfaceContainerLowest = Color(0xE0E6F4FA), // 浅水蓝系
    surfaceContainerLow = Color(0xE6E6F4FA),
    surfaceContainer = Color(0xDDE6F4FA),
    surfaceContainerHigh = Color(0xD4E6F4FA),
    surfaceContainerHighest = Color(0xCCE6F4FA),
)

// === 血月 (Blood Moon) Theme ===
private val BloodMoonLightColorScheme = lightColorScheme(
    primary = Color(0xFFC44536),        // 柔和砖红，不刺眼
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE4E1), // 浅珊瑚
    onPrimaryContainer = Color(0xFF8B3A2F),
    secondary = Color(0xFFD4A574),       // 暖棕
    tertiary = Color(0xFFE8B4B8),        // 柔粉
    background = Color(0xFFFFF8F5),      // 暖白
    surface = Color(0xFFFFF5F0),         // 暖白
    surfaceContainerLowest = Color(0xFFFFF8F5),
    surfaceContainerLow = Color(0xFFFFF5F0),
    surfaceContainer = Color(0xFFFFEFE8),
    surfaceContainerHigh = Color(0xFFFFE8E0),
    surfaceContainerHighest = Color(0xFFFFE0D5),
)

private val BloodMoonDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE57373),         // 柔红
    onPrimary = Color(0xFF4A1C1C),
    primaryContainer = Color(0xFF5C2020),
    onPrimaryContainer = Color(0xFFFFD6D6),
    secondary = Color(0xFFB08060),       // 暗棕
    tertiary = Color(0xFFD4A0A4),        // 暗粉
    background = Color(0xFF1A0A0A),      // 极深红黑
    surface = Color(0xFF2A1515),         // 深红灰
    surfaceContainerLowest = Color(0xFF140808),
    surfaceContainerLow = Color(0xFF1A0A0A),
    surfaceContainer = Color(0xFF251010),
    surfaceContainerHigh = Color(0xFF301515),
    surfaceContainerHighest = Color(0xFF3A1A1A),
)

// === 天宫 (Heavenly Palace) Theme ===
private val HeavenlyPalaceLightColorScheme = lightColorScheme(
    primary = Color(0xFFD4A017),         // 柔金
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFF4D6),
    onPrimaryContainer = Color(0xFF8B6914),
    secondary = Color(0xFFB8860B),       // 暗金
    tertiary = Color(0xFFE8D4A0),        // 浅金
    background = Color(0xFFFFFAF0),      // 暖白
    surface = Color(0xFFFFF8F0),
    surfaceContainerLowest = Color(0xFFFFFAF0),
    surfaceContainerLow = Color(0xFFFFF8F0),
    surfaceContainer = Color(0xFFFFF4E8),
    surfaceContainerHigh = Color(0xFFFFF0D8),
    surfaceContainerHighest = Color(0xFFFFEBC8),
)

private val HeavenlyPalaceDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8C547),         // 亮金
    onPrimary = Color(0xFF3A2A0A),
    primaryContainer = Color(0xFF5A4010),
    onPrimaryContainer = Color(0xFFFFE8A0),
    secondary = Color(0xFFC4A030),
    tertiary = Color(0xFFD8B860),
    background = Color(0xFF0A0A14),      // 极深蓝黑
    surface = Color(0xFF151520),
    surfaceContainerLowest = Color(0xFF080810),
    surfaceContainerLow = Color(0xFF0A0A14),
    surfaceContainer = Color(0xFF12121A),
    surfaceContainerHigh = Color(0xFF1A1A24),
    surfaceContainerHighest = Color(0xFF222230),
)

// === 苍穹 (Azure Sky) Theme ===
private val AzureSkyLightColorScheme = lightColorScheme(
    primary = Color(0xFF4A90D9),         // 柔蓝
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F0FF),
    onPrimaryContainer = Color(0xFF1A5090),
    secondary = Color(0xFF6AADD8),
    tertiary = Color(0xFF90C8E8),
    background = Color(0xFFF5FAFF),      // 浅蓝白
    surface = Color(0xFFF0F8FF),
    surfaceContainerLowest = Color(0xFFF5FAFF),
    surfaceContainerLow = Color(0xFFF0F8FF),
    surfaceContainer = Color(0xFFE8F4FF),
    surfaceContainerHigh = Color(0xFFE0F0FF),
    surfaceContainerHighest = Color(0xFFD8ECFF),
)

private val AzureSkyDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6AADFF),
    onPrimary = Color(0xFF0A2A50),
    primaryContainer = Color(0xFF1A4080),
    onPrimaryContainer = Color(0xFFD0E8FF),
    secondary = Color(0xFF4A80C0),
    tertiary = Color(0xFF3A60A0),
    background = Color(0xFF0A1020),      // 极深蓝黑
    surface = Color(0xFF151A30),
    surfaceContainerLowest = Color(0xFF080A14),
    surfaceContainerLow = Color(0xFF0A1020),
    surfaceContainer = Color(0xFF121828),
    surfaceContainerHigh = Color(0xFF1A2038),
    surfaceContainerHighest = Color(0xFF222848),
)

// === 清新柠檬 (Fresh Lemon) Theme ===
private val FreshLemonLightColorScheme = lightColorScheme(
    primary = Color(0xFFA8D830),         // 柔柠檬绿
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF0FFE0),
    onPrimaryContainer = Color(0xFF5A8010),
    secondary = Color(0xFFC0E840),
    tertiary = Color(0xFFD8F050),
    background = Color(0xFFFFFEF8),      // 极浅暖白
    surface = Color(0xFFFFFEF0),
    surfaceContainerLowest = Color(0xFFFFFEF8),
    surfaceContainerLow = Color(0xFFFFFEF0),
    surfaceContainer = Color(0xFFF8FFE8),
    surfaceContainerHigh = Color(0xFFF0FFE0),
    surfaceContainerHighest = Color(0xFFE8FFD8),
)

private val FreshLemonDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB8E840),
    onPrimary = Color(0xFF1A3010),
    primaryContainer = Color(0xFF2A5010),
    onPrimaryContainer = Color(0xFFE0FFA0),
    secondary = Color(0xFF90C030),
    tertiary = Color(0xFF70A020),
    background = Color(0xFF0A1408),      // 极深绿黑
    surface = Color(0xFF151A10),
    surfaceContainerLowest = Color(0xFF080A04),
    surfaceContainerLow = Color(0xFF0A1408),
    surfaceContainer = Color(0xFF121810),
    surfaceContainerHigh = Color(0xFF1A2014),
    surfaceContainerHighest = Color(0xFF22281A),
)

// === 火龙果 (Dragon Fruit) Theme ===
private val DragonFruitLightColorScheme = lightColorScheme(
    primary = Color(0xFFE070B0),         // 柔粉紫
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFF0F8),
    onPrimaryContainer = Color(0xFF8A4060),
    secondary = Color(0xFFD090C0),
    tertiary = Color(0xFFC080B0),
    background = Color(0xFFFFFAF8),      // 暖白
    surface = Color(0xFFFFF8F5),
    surfaceContainerLowest = Color(0xFFFFFAF8),
    surfaceContainerLow = Color(0xFFFFF8F5),
    surfaceContainer = Color(0xFFFFF4F0),
    surfaceContainerHigh = Color(0xFFFFF0E8),
    surfaceContainerHighest = Color(0xFFFFECE0),
)

private val DragonFruitDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE890C0),
    onPrimary = Color(0xFF3A2040),
    primaryContainer = Color(0xFF502060),
    onPrimaryContainer = Color(0xFFFFE0F0),
    secondary = Color(0xFFB070A0),
    tertiary = Color(0xFF905080),
    background = Color(0xFF140A14),      // 极深紫黑
    surface = Color(0xFF201520),
    surfaceContainerLowest = Color(0xFF0A040A),
    surfaceContainerLow = Color(0xFF140A14),
    surfaceContainer = Color(0xFF1A1018),
    surfaceContainerHigh = Color(0xFF221820),
    surfaceContainerHighest = Color(0xFF2A2028),
)

// === 神域黄 (Divine Yellow) Theme ===
private val DivineYellowLightColorScheme = lightColorScheme(
    primary = Color(0xFFE8B820),         // 柔黄
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFF8E0),
    onPrimaryContainer = Color(0xFF8A6010),
    secondary = Color(0xFFD0A020),
    tertiary = Color(0xFFC09020),
    background = Color(0xFFFFFEF8),      // 暖白
    surface = Color(0xFFFFFCF0),
    surfaceContainerLowest = Color(0xFFFFFEF8),
    surfaceContainerLow = Color(0xFFFFFCF0),
    surfaceContainer = Color(0xFFFFF8E8),
    surfaceContainerHigh = Color(0xFFFFF4E0),
    surfaceContainerHighest = Color(0xFFFFF0D8),
)

private val DivineYellowDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8C040),
    onPrimary = Color(0xFF3A2010),
    primaryContainer = Color(0xFF5A3010),
    onPrimaryContainer = Color(0xFFFFE8A0),
    secondary = Color(0xFFC09030),
    tertiary = Color(0xFFA07020),
    background = Color(0xFF0A0A08),      // 极深黑
    surface = Color(0xFF151510),
    surfaceContainerLowest = Color(0xFF080804),
    surfaceContainerLow = Color(0xFF0A0A08),
    surfaceContainer = Color(0xFF121210),
    surfaceContainerHigh = Color(0xFF1A1A14),
    surfaceContainerHighest = Color(0xFF22221A),
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
    cardElevation: Float = 1f,  // Card elevation in dp
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        themePreset == "blood_moon" -> if (darkTheme) BloodMoonDarkColorScheme else BloodMoonLightColorScheme
        themePreset == "heavenly_palace" -> if (darkTheme) HeavenlyPalaceDarkColorScheme else HeavenlyPalaceLightColorScheme
        themePreset == "azure_sky" -> if (darkTheme) AzureSkyDarkColorScheme else AzureSkyLightColorScheme
        themePreset == "fresh_lemon" -> if (darkTheme) FreshLemonDarkColorScheme else FreshLemonLightColorScheme
        themePreset == "dragon_fruit" -> if (darkTheme) DragonFruitDarkColorScheme else DragonFruitLightColorScheme
        themePreset == "divine_yellow" -> if (darkTheme) DivineYellowDarkColorScheme else DivineYellowLightColorScheme
        themePreset == "ice_abyss" -> if (darkTheme) IceAbyssDarkColorScheme else IceAbyssColorScheme
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

    // 应用自定义强调色（仅在默认预设且非动态取色时生效）
    val finalColorScheme = when {
        accentColor != -1L && themePreset == "default" && !dynamicColor -> {
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
        }
        else -> colorScheme
    }

    SystemBarStyle(
        darkMode = darkTheme
    )

    CompositionLocalProvider(
        LocalThemePreset provides themePreset,
        LocalCardElevation provides cardElevation.dp
    ) {
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