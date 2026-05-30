package com.ztros.ztrosu.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.ztros.ztrosu.R
import com.ztros.ztrosu.ui.LocalScrollState
import com.ztros.ztrosu.ui.component.SwitchItem
import com.ztros.ztrosu.ui.rememberScrollConnection
import com.ztros.ztrosu.ui.util.LocalSnackbarHost

private val accentColors = listOf(
    Color(0xFF6750A4),
    Color(0xFFE91E63),
    Color(0xFF2196F3),
    Color(0xFF4CAF50),
    Color(0xFFFF9800),
    Color(0xFF9C27B0),
    Color(0xFF00BCD4),
    Color(0xFFFF5722)
)

/**
 * Theme preset data class for cleaner code
 */
private data class ThemePreset(
    val id: String,
    val name: String,
    val backgroundColor: Color,
    val borderColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconTint: Color
)

/**
 * Reusable theme preset button component with consistent sizing and ripple effect
 */
@Composable
private fun ThemePresetButton(
    preset: ThemePreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 36.dp)
            ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(preset.backgroundColor)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, preset.borderColor, RoundedCornerShape(12.dp))
                    } else {
                        Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = preset.icon,
                contentDescription = null,
                tint = preset.iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun MaterialYouScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val bottomBarScrollState = LocalScrollState.current
    val bottomBarScrollConnection = if (bottomBarScrollState != null) {
        rememberScrollConnection(
            isScrollingDown = bottomBarScrollState.isScrollingDown,
            scrollOffset = bottomBarScrollState.scrollOffset,
            previousScrollOffset = bottomBarScrollState.previousScrollOffset,
            threshold = 30f
        )
    } else null
    val snackBarHost = LocalSnackbarHost.current

    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp

    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    // Pre-resolve all string resources
    val dynamicColorTitle = stringResource(R.string.material_you_dynamic)
    val dynamicColorSummary = stringResource(R.string.material_you_dynamic_desc)
    val accentColorTitle = stringResource(R.string.material_you_accent)
    val accentColorSummary = stringResource(R.string.material_you_accent_desc)
    val fontScaleTitle = stringResource(R.string.material_you_font_scale)
    val cornerRadiusTitle = stringResource(R.string.material_you_shape)
    val previewText = stringResource(R.string.material_you_rounded)

    // Theme presets list - use remembered colors outside remember block
    val defaultSurface = MaterialTheme.colorScheme.surface
    val defaultPrimary = MaterialTheme.colorScheme.primary
    val themePresets = remember(defaultSurface, defaultPrimary) {
        listOf(
            ThemePreset("default", "默认", defaultSurface, defaultPrimary, Icons.Filled.Palette, defaultPrimary),
            ThemePreset("ice_abyss", "冰渊", Color(0xFFE6F4FA), Color(0xFF00B4D8), Icons.Filled.AcUnit, Color(0xFF00B4D8)),
            ThemePreset("blood_moon", "血月", Color(0xFFFFF8F5), Color(0xFFC44536), Icons.Filled.Whatshot, Color(0xFFC44536)),
            ThemePreset("heavenly_palace", "天宫", Color(0xFFFFFAF0), Color(0xFFD4A017), Icons.Filled.Star, Color(0xFFD4A017)),
            ThemePreset("azure_sky", "苍穹", Color(0xFFF5FAFF), Color(0xFF4A90D9), Icons.Filled.Cloud, Color(0xFF4A90D9)),
            ThemePreset("fresh_lemon", "清新柠檬", Color(0xFFFFFEF8), Color(0xFFA8D830), Icons.Filled.Eco, Color(0xFFA8D830)),
            ThemePreset("dragon_fruit", "火龙果", Color(0xFFFFFAF8), Color(0xFFE070B0), Icons.Filled.Favorite, Color(0xFFE070B0)),
            ThemePreset("divine_yellow", "神域黄", Color(0xFFFFFEF8), Color(0xFFE8B820), Icons.Filled.WbSunny, Color(0xFFE8B820))
        )
    }

    Scaffold(
        topBar = {
            TopBar(
                onBack = dropUnlessResumed { navigator.popBackStack() },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackBarHost, modifier = Modifier.padding(bottom = navBarPadding)) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .then(
                    if (bottomBarScrollConnection != null) {
                        Modifier.nestedScroll(bottomBarScrollConnection)
                    } else {
                        Modifier
                    }
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Theme Preset Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "主题预设",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "选择预设主题配色方案",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var selectedPreset by rememberSaveable {
                        mutableStateOf(prefs.getString("theme_preset", "default") ?: "default")
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        maxItemsInEachRow = 4
                    ) {
                        themePresets.forEach { preset ->
                            ThemePresetButton(
                                preset = preset,
                                isSelected = selectedPreset == preset.id,
                                onClick = {
                                    selectedPreset = preset.id
                                    prefs.edit { putString("theme_preset", preset.id) }
                                    val activity = context as? com.ztros.ztrosu.ui.MainActivity
                                    activity?.setThemePreset(preset.id)
                                    activity?.setAccentColor(-1)
                                    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
                                        putLong("material_you_accent_color", -1)
                                        putInt("material_you_accent_color_index", -1)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Dynamic Color Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var dynamicColorEnabled by rememberSaveable {
                        mutableStateOf(prefs.getBoolean("material_you_dynamic_color", true))
                    }
                    SwitchItem(
                        icon = Icons.Filled.AutoAwesome,
                        title = dynamicColorTitle,
                        summary = dynamicColorSummary,
                        checked = dynamicColorEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    ) {
                        prefs.edit { putBoolean("material_you_dynamic_color", it) }
                        dynamicColorEnabled = it
                        val activity = context as? com.ztros.ztrosu.ui.MainActivity
                        activity?.setDynamicColor(it)
                        if (it) {
                            activity?.setAccentColor(-1)
                            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
                                putLong("material_you_accent_color", -1)
                                putInt("material_you_accent_color_index", -1)
                            }
                        }
                    }
                }
            }

            // Accent Color Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = accentColorTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = accentColorSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var selectedAccentIndex by rememberSaveable {
                        mutableIntStateOf(prefs.getInt("material_you_accent_color_index", 0))
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        accentColors.forEachIndexed { index, color ->
                            val isSelected = selectedAccentIndex == index
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                width = 3.dp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                shape = CircleShape
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false, radius = 24.dp)
                                    ) {
                                        selectedAccentIndex = index
                                        prefs.edit { putInt("material_you_accent_color_index", index) }
                                        prefs.edit { putLong("material_you_accent_color", color.value.toLong()) }
                                        val activity = context as? com.ztros.ztrosu.ui.MainActivity
                                        activity?.setAccentColor(color.value.toLong())
                                        activity?.setDynamicColor(false)
                                        activity?.setThemePreset("default")
                                        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
                                            putBoolean("material_you_dynamic_color", false)
                                            putString("theme_preset", "default")
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Font Scale Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var fontScale by rememberSaveable {
                        mutableFloatStateOf(prefs.getFloat("material_you_font_scale", 1.0f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Filled.TextFields, contentDescription = null)
                        Text(
                            text = fontScaleTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "%.1fx".format(fontScale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = fontScale,
                        onValueChange = {
                            fontScale = it
                            prefs.edit { putFloat("material_you_font_scale", it) }
                            val activity = context as? com.ztros.ztrosu.ui.MainActivity
                            activity?.setFontScale(it)
                        },
                        valueRange = 0.8f..1.4f,
                        steps = 5
                    )
                }
            }

            // Corner Radius Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var cornerRadius by rememberSaveable {
                        mutableFloatStateOf(prefs.getFloat("material_you_corner_radius", 16f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Filled.RoundedCorner, contentDescription = null)
                        Text(
                            text = cornerRadiusTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "%.0fdp".format(cornerRadius),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = cornerRadius,
                        onValueChange = {
                            cornerRadius = it
                            prefs.edit { putFloat("material_you_corner_radius", it) }
                            val activity = context as? com.ztros.ztrosu.ui.MainActivity
                            activity?.setCornerRadius(it)
                        },
                        valueRange = 0f..28f,
                        steps = 13
                    )

                    // Preview
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(cornerRadius.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(
                            modifier = Modifier.padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = previewText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onBack: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.material_you_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Preview
@Composable
private fun MaterialYouPreview() {
    MaterialYouScreen(EmptyDestinationsNavigator)
}