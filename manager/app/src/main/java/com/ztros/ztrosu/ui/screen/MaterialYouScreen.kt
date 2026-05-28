package com.ztros.ztrosu.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Pre-resolve all string resources
    val dynamicColorTitle = stringResource(R.string.material_you_dynamic)
    val dynamicColorSummary = stringResource(R.string.material_you_dynamic_desc)
    val accentColorTitle = stringResource(R.string.material_you_accent)
    val accentColorSummary = stringResource(R.string.material_you_accent_desc)
    val fontScaleTitle = stringResource(R.string.material_you_font_scale)
    val cornerRadiusTitle = stringResource(R.string.material_you_shape)
    val previewText = stringResource(R.string.material_you_rounded)

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
                .let { modifier ->
                    if (bottomBarScrollConnection != null) {
                        modifier
                            .nestedScroll(bottomBarScrollConnection)
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Theme Preset Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "主题预设",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Text(
                        text = "选择预设主题配色方案",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    var selectedPreset by rememberSaveable {
                        mutableStateOf(prefs.getString("theme_preset", "default") ?: "default")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Default preset
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                selectedPreset = "default"
                                prefs.edit { putString("theme_preset", "default") }
                                val activity = context as? com.ztros.ztrosu.ui.MainActivity
                                activity?.setThemePreset("default")
                                activity?.setAccentColor(-1)
                                context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
                                    putLong("material_you_accent_color", -1)
                                    putInt("material_you_accent_color_index", -1)
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .then(
                                        if (selectedPreset == "default") {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                        } else {
                                            Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("默认", style = MaterialTheme.typography.labelSmall)
                        }

                        // Ice Abyss preset
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                selectedPreset = "ice_abyss"
                                prefs.edit { putString("theme_preset", "ice_abyss") }
                                val activity = context as? com.ztros.ztrosu.ui.MainActivity
                                activity?.setThemePreset("ice_abyss")
                                activity?.setAccentColor(-1)
                                context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
                                    putLong("material_you_accent_color", -1)
                                    putInt("material_you_accent_color_index", -1)
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE6F4FA))
                                    .then(
                                        if (selectedPreset == "ice_abyss") {
                                            Modifier.border(2.dp, Color(0xFF00B4D8), RoundedCornerShape(12.dp))
                                        } else {
                                            Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.AcUnit, contentDescription = null, tint = Color(0xFF00B4D8), modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("冰渊", style = MaterialTheme.typography.labelSmall)
                        }

                        // Blood Moon preset
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                selectedPreset = "blood_moon"
                                prefs.edit { putString("theme_preset", "blood_moon") }
                                val activity = context as? com.ztros.ztrosu.ui.MainActivity
                                activity?.setThemePreset("blood_moon")
                                activity?.setAccentColor(-1)
                                context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
                                    putLong("material_you_accent_color", -1)
                                    putInt("material_you_accent_color_index", -1)
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFFF8F5))
                                    .then(
                                        if (selectedPreset == "blood_moon") {
                                            Modifier.border(2.dp, Color(0xFFC44536), RoundedCornerShape(12.dp))
                                        } else {
                                            Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Whatshot, contentDescription = null, tint = Color(0xFFC44536), modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("血月", style = MaterialTheme.typography.labelSmall)
                        }

                        // Heavenly Palace preset
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                selectedPreset = "heavenly_palace"
                                prefs.edit { putString("theme_preset", "heavenly_palace") }
                                val activity = context as? com.ztros.ztrosu.ui.MainActivity
                                activity?.setThemePreset("heavenly_palace")
                                activity?.setAccentColor(-1)
                                context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
                                    putLong("material_you_accent_color", -1)
                                    putInt("material_you_accent_color_index", -1)
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFFFAF0))
                                    .then(
                                        if (selectedPreset == "heavenly_palace") {
                                            Modifier.border(2.dp, Color(0xFFD4A017), RoundedCornerShape(12.dp))
                                        } else {
                                            Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFD4A017), modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("天宫", style = MaterialTheme.typography.labelSmall)
                        }

                        // Azure Sky preset
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                selectedPreset = "azure_sky"
                                prefs.edit { putString("theme_preset", "azure_sky") }
                                val activity = context as? com.ztros.ztrosu.ui.MainActivity
                                activity?.setThemePreset("azure_sky")
                                activity?.setAccentColor(-1)
                                context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
                                    putLong("material_you_accent_color", -1)
                                    putInt("material_you_accent_color_index", -1)
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF5FAFF))
                                    .then(
                                        if (selectedPreset == "azure_sky") {
                                            Modifier.border(2.dp, Color(0xFF4A90D9), RoundedCornerShape(12.dp))
                                        } else {
                                            Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Cloud, contentDescription = null, tint = Color(0xFF4A90D9), modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("苍穹", style = MaterialTheme.typography.labelSmall)
                        }

                        // Fresh Lemon preset
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                selectedPreset = "fresh_lemon"
                                prefs.edit { putString("theme_preset", "fresh_lemon") }
                                val activity = context as? com.ztros.ztrosu.ui.MainActivity
                                activity?.setThemePreset("fresh_lemon")
                                activity?.setAccentColor(-1)
                                context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
                                    putLong("material_you_accent_color", -1)
                                    putInt("material_you_accent_color_index", -1)
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFFFEF8))
                                    .then(
                                        if (selectedPreset == "fresh_lemon") {
                                            Modifier.border(2.dp, Color(0xFFA8D830), RoundedCornerShape(12.dp))
                                        } else {
                                            Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Eco, contentDescription = null, tint = Color(0xFFA8D830), modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("清新柠檬", style = MaterialTheme.typography.labelSmall)
                        }

                        // Dragon Fruit preset
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                selectedPreset = "dragon_fruit"
                                prefs.edit { putString("theme_preset", "dragon_fruit") }
                                val activity = context as? com.ztros.ztrosu.ui.MainActivity
                                activity?.setThemePreset("dragon_fruit")
                                activity?.setAccentColor(-1)
                                context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
                                    putLong("material_you_accent_color", -1)
                                    putInt("material_you_accent_color_index", -1)
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFFFAF8))
                                    .then(
                                        if (selectedPreset == "dragon_fruit") {
                                            Modifier.border(2.dp, Color(0xFFE070B0), RoundedCornerShape(12.dp))
                                        } else {
                                            Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFE070B0), modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("火龙果", style = MaterialTheme.typography.labelSmall)
                        }

                        // Divine Yellow preset
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                selectedPreset = "divine_yellow"
                                prefs.edit { putString("theme_preset", "divine_yellow") }
                                val activity = context as? com.ztros.ztrosu.ui.MainActivity
                                activity?.setThemePreset("divine_yellow")
                                activity?.setAccentColor(-1)
                                context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
                                    putLong("material_you_accent_color", -1)
                                    putInt("material_you_accent_color_index", -1)
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFFFEF8))
                                    .then(
                                        if (selectedPreset == "divine_yellow") {
                                            Modifier.border(2.dp, Color(0xFFE8B820), RoundedCornerShape(12.dp))
                                        } else {
                                            Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.WbSunny, contentDescription = null, tint = Color(0xFFE8B820), modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("神域黄", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Dynamic Color Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    var dynamicColorEnabled by rememberSaveable {
                        mutableStateOf(prefs.getBoolean("material_you_dynamic_color", true))
                    }
                    SwitchItem(
                        icon = Icons.Filled.AutoAwesome,
                        title = dynamicColorTitle,
                        summary = dynamicColorSummary,
                        checked = dynamicColorEnabled,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
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
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = accentColorTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Text(
                        text = accentColorSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    var selectedAccentIndex by rememberSaveable {
                        mutableIntStateOf(prefs.getInt("material_you_accent_color_index", 0))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        accentColors.forEachIndexed { index, color ->
                            val isSelected = selectedAccentIndex == index
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
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
                                    .clickable {
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
                                        modifier = Modifier.size(20.dp)
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
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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

            Spacer(Modifier)
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
