package com.ztros.ztrosu.ui.screen

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import com.ztros.ztrosu.ui.LocalScrollState
import com.ztros.ztrosu.ui.rememberScrollConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.dropUnlessResumed
import com.ztros.ztrosu.ui.MainActivity
import com.maxkeppeker.sheets.core.models.base.Header
import com.maxkeppeker.sheets.core.models.base.rememberUseCaseState
import com.maxkeppeler.sheets.list.ListDialog
import com.maxkeppeler.sheets.list.models.ListOption
import com.maxkeppeler.sheets.list.models.ListSelection
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.ztros.ztrosu.Natives
import com.ztros.ztrosu.R
import com.ztros.ztrosu.ksuApp
import com.ztros.ztrosu.ui.component.SwitchItem
import com.ztros.ztrosu.ui.component.rememberCustomDialog
import com.ztros.ztrosu.ui.component.GlassCard
import com.ztros.ztrosu.ui.util.refreshActivity
import com.ztros.ztrosu.ui.util.LocalSnackbarHost
import com.ztros.ztrosu.ui.util.LocaleHelper
import com.ztros.ztrosu.ui.util.VibrationHelper
import com.ztros.ztrosu.ui.util.responsiveHorizontalPadding

/** Color source mode for mutual exclusion between preset/dynamic/accent */
enum class ColorSource { PRESET, DYNAMIC, ACCENT }

/**
 * @author twj
 * @date 2025/6/1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CustomizationScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    // Bottom bar scroll tracking
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

    val isManager = Natives.isManager
    val ksuVersion = if (isManager) Natives.version else null

    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp

    Scaffold(
        topBar = {
            TopBar(
                onBack = dropUnlessResumed {
                    navigator.popBackStack()
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackBarHost, modifier = Modifier.padding(bottom = navBarPadding)) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
    
        Column(
            modifier = Modifier
                .padding(paddingValues)
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
        ) {

            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

            // Track language state with current app locale
            var currentAppLocale by remember { mutableStateOf(LocaleHelper.getCurrentAppLocale(context)) }
            
            // Listen for preference changes
            LaunchedEffect(Unit) {
                currentAppLocale = LocaleHelper.getCurrentAppLocale(context)
            }

            // Language setting with selection dialog
            val languageDialog = rememberCustomDialog { dismiss ->
                // Check if should use system language settings
                if (LocaleHelper.useSystemLanguageSettings) {
                    // Android 13+ - Jump to system settings
                    LocaleHelper.launchSystemLanguageSettings(context)
                    dismiss()
                } else {
                    // Android < 13 - Show app language selector
                    // Dynamically detect supported locales from resources
                    val supportedLocales = remember {
                        val locales = mutableListOf<java.util.Locale>()
                        
                        // Add system default first
                        locales.add(java.util.Locale.ROOT) // This will represent "System Default"
                        
                        // Dynamically detect available locales by checking resource directories
                        val resourceDirs = listOf(
                            "zh-rCN", "zh-rTW"
                        )
                        
                        resourceDirs.forEach { dir ->
                            try {
                                val locale = when {
                                    dir.contains("-r") -> {
                                        val parts = dir.split("-r")
                                        if (parts.size >= 2) {
                                            java.util.Locale.Builder()
                                                .setLanguage(parts[0])
                                                .setRegion(parts[1])
                                                .build()
                                        } else {
                                            java.util.Locale.Builder()
                                                .setLanguage(dir)
                                                .build()
                                        }
                                    }
                                    else -> java.util.Locale.Builder()
                                        .setLanguage(dir)
                                        .build()
                                }
                                
                                // Test if this locale has translated resources
                                val config = android.content.res.Configuration()
                                config.setLocale(locale)
                                val localizedContext = context.createConfigurationContext(config)
                                
                                // Try to get a translated string to verify the locale is supported
                                val testString = localizedContext.getString(R.string.settings_language)
                                val defaultString = context.getString(R.string.settings_language)
                                
                                // If the string is different or it's English, it's supported
                                if (testString != defaultString || locale.language == "en") {
                                    locales.add(locale)
                                }
                            } catch (_: Exception) {
                                // Skip unsupported locales
                            }
                        }
                        
                        // Sort by display name
                        val sortedLocales = locales.drop(1).sortedBy { it.getDisplayName(it) }
                        mutableListOf<java.util.Locale>().apply {
                            add(locales.first()) // System default first
                            addAll(sortedLocales)
                        }
                    }
                    
                    val allOptions = supportedLocales.map { locale ->
                        val tag = if (locale == java.util.Locale.ROOT) {
                            "system"
                        } else if (locale.country.isEmpty()) {
                            locale.language
                        } else {
                            "${locale.language}_${locale.country}"
                        }
                        
                        val displayName = if (locale == java.util.Locale.ROOT) {
                            context.getString(R.string.system_default)
                        } else {
                            locale.getDisplayName(locale)
                        }
                        
                        tag to displayName
                    }
                    
                    val currentLocale = prefs.getString("app_locale", "system") ?: "system"
                    val options = allOptions.map { (tag, displayName) ->
                        ListOption(
                            titleText = displayName,
                            selected = currentLocale == tag
                        )
                    }
                    
                    var selectedIndex by remember { 
                        mutableIntStateOf(allOptions.indexOfFirst { (tag, _) -> currentLocale == tag })
                    }
                    
                    ListDialog(
                        state = rememberUseCaseState(
                            visible = true,
                            onFinishedRequest = {
                                if (selectedIndex >= 0 && selectedIndex < allOptions.size) {
                                    val newLocale = allOptions[selectedIndex].first
                                    prefs.edit { putString("app_locale", newLocale) }
                                    
                                    // Update local state immediately
                                    currentAppLocale = LocaleHelper.getCurrentAppLocale(context)
                                    
                                    // Apply locale change immediately for Android < 13
                                    refreshActivity(context)
                                }
                                dismiss()
                            },
                            onCloseRequest = {
                                dismiss()
                            }
                        ),
                        header = Header.Default(
                            title = stringResource(R.string.settings_language),
                        ),
                        selection = ListSelection.Single(
                            showRadioButtons = true,
                            options = options
                        ) { index, _ ->
                            selectedIndex = index
                        }
                    )
                }
            }

            val language = stringResource(id = R.string.settings_language)
            
            // Compute display name based on current app locale (similar to the reference implementation)
            val currentLanguageDisplay = remember(currentAppLocale) {
                val locale = currentAppLocale
                if (locale != null) {
                    locale.getDisplayName(locale)
                } else {
                    context.getString(R.string.system_default)
                }
            }
            
            ListItem(
                leadingContent = { Icon(Icons.Filled.Translate, language) },
                headlineContent = { Text(
                    text = language,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                ) },
                supportingContent = { Text(currentLanguageDisplay) },
                modifier = Modifier.clickable {
                    languageDialog.show()
                }
            )

            // Theme style selection
            val themeStyleTitle = stringResource(R.string.theme_style_title)
            val themeStyleSummary = stringResource(R.string.theme_style_summary)

            val themeStyles = listOf(
                "wild" to stringResource(R.string.theme_style_wild),
                "md3" to stringResource(R.string.theme_style_md3),
                "miui_x" to stringResource(R.string.theme_style_miui_x)
            )

            val currentThemeStyle = prefs.getString("theme_style", "wild") ?: "wild"
            val currentThemeDisplay = themeStyles.firstOrNull { it.first == currentThemeStyle }?.second
                ?: themeStyles[0].second

            val themeStyleDialog = rememberCustomDialog { dismiss ->
                val options = themeStyles.map { (_, displayName) ->
                    ListOption(
                        titleText = displayName,
                        selected = currentThemeStyle == themeStyles.first { it.second == displayName }.first
                    )
                }

                var selectedIndex by remember {
                    mutableIntStateOf(themeStyles.indexOfFirst { it.first == currentThemeStyle }.coerceAtLeast(0))
                }

                ListDialog(
                    state = rememberUseCaseState(
                        visible = true,
                        onFinishedRequest = {
                            if (selectedIndex >= 0 && selectedIndex < themeStyles.size) {
                                val newStyle = themeStyles[selectedIndex].first
                                prefs.edit { putString("theme_style", newStyle) }
                                // Theme will be applied on next app restart
                                refreshActivity(context)
                            }
                            dismiss()
                        },
                        onCloseRequest = {
                            dismiss()
                        }
                    ),
                    header = Header.Default(
                        title = themeStyleTitle,
                    ),
                    selection = ListSelection.Single(
                        showRadioButtons = true,
                        options = options
                    ) { index, _ ->
                        selectedIndex = index
                    }
                )
            }

            ListItem(
                leadingContent = { Icon(Icons.Filled.Palette, themeStyleTitle) },
                headlineContent = { Text(
                    text = themeStyleTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                ) },
                supportingContent = { Text(currentThemeDisplay) },
                modifier = Modifier.clickable {
                    themeStyleDialog.show()
                }
            )

            var colorSource by remember {
                mutableStateOf(
                    when {
                        prefs.getBoolean("material_you_dynamic_color", false) -> ColorSource.DYNAMIC
                        prefs.getLong("material_you_accent_color", -1) != -1L -> ColorSource.ACCENT
                        else -> ColorSource.PRESET
                    }
                )
            }

            // === Color Source: Theme Preset ===
            var selectedPreset by remember {
                mutableStateOf(prefs.getString("theme_preset", "default") ?: "default")
            }

            LaunchedEffect(Unit) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "theme_preset") {
                        selectedPreset = prefs.getString("theme_preset", "default") ?: "default"
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.theme_preset_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = responsiveHorizontalPadding())
            )
            Text(
                text = stringResource(R.string.theme_preset_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = responsiveHorizontalPadding())
            )
            Spacer(Modifier.height(8.dp))

            val isDark = isSystemInDarkTheme()
            val presetAlpha by animateFloatAsState(
                targetValue = if (colorSource == ColorSource.PRESET) 1f else 0.4f,
                animationSpec = tween(300),
                label = "presetAlpha"
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = responsiveHorizontalPadding())
                    .alpha(presetAlpha),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top)
            ) {
                // Default preset
                ThemePresetCard(
                    name = "default",
                    isSelected = selectedPreset == "default" && colorSource == ColorSource.PRESET,
                    onClick = {
                        colorSource = ColorSource.PRESET
                        selectedPreset = "default"
                        prefs.edit {
                            putString("theme_preset", "default")
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                        (context as? MainActivity)?.setThemePreset("default")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                    },
                    backgroundColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Filled.Palette,
                    iconTint = MaterialTheme.colorScheme.primary,
                    label = stringResource(R.string.theme_preset_default),
                )

                // Ice Abyss preset
                ThemePresetCard(
                    name = "ice_abyss",
                    isSelected = selectedPreset == "ice_abyss" && colorSource == ColorSource.PRESET,
                    onClick = {
                        colorSource = ColorSource.PRESET
                        selectedPreset = "ice_abyss"
                        prefs.edit {
                            putString("theme_preset", "ice_abyss")
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                        (context as? MainActivity)?.setThemePreset("ice_abyss")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                    },
                    backgroundColor = if (isDark) Color(0xFF1A2A33) else Color(0xFFE6F4FA),
                    selectedBorderColor = Color(0xFF00B4D8),
                    icon = Icons.Filled.AcUnit,
                    iconTint = Color(0xFF00B4D8),
                    label = stringResource(R.string.theme_preset_ice_abyss),
                )

                // Blood Moon preset
                ThemePresetCard(
                    name = "blood_moon",
                    isSelected = selectedPreset == "blood_moon" && colorSource == ColorSource.PRESET,
                    onClick = {
                        colorSource = ColorSource.PRESET
                        selectedPreset = "blood_moon"
                        prefs.edit {
                            putString("theme_preset", "blood_moon")
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                        (context as? MainActivity)?.setThemePreset("blood_moon")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                    },
                    backgroundColor = if (isDark) Color(0xFF331A1A) else Color(0xFFFFF8F5),
                    selectedBorderColor = Color(0xFFC44536),
                    icon = Icons.Filled.Whatshot,
                    iconTint = Color(0xFFC44536),
                    label = stringResource(R.string.theme_preset_blood_moon),
                )

                // Heavenly Palace preset
                ThemePresetCard(
                    name = "heavenly_palace",
                    isSelected = selectedPreset == "heavenly_palace" && colorSource == ColorSource.PRESET,
                    onClick = {
                        colorSource = ColorSource.PRESET
                        selectedPreset = "heavenly_palace"
                        prefs.edit {
                            putString("theme_preset", "heavenly_palace")
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                        (context as? MainActivity)?.setThemePreset("heavenly_palace")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                    },
                    backgroundColor = if (isDark) Color(0xFF332B1A) else Color(0xFFFFFAF0),
                    selectedBorderColor = Color(0xFFD4A017),
                    icon = Icons.Filled.Star,
                    iconTint = Color(0xFFD4A017),
                    label = stringResource(R.string.theme_preset_heavenly_palace),
                )

                // Azure Sky preset
                ThemePresetCard(
                    name = "azure_sky",
                    isSelected = selectedPreset == "azure_sky" && colorSource == ColorSource.PRESET,
                    onClick = {
                        colorSource = ColorSource.PRESET
                        selectedPreset = "azure_sky"
                        prefs.edit {
                            putString("theme_preset", "azure_sky")
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                        (context as? MainActivity)?.setThemePreset("azure_sky")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                    },
                    backgroundColor = if (isDark) Color(0xFF1A2633) else Color(0xFFF5FAFF),
                    selectedBorderColor = Color(0xFF4A90D9),
                    icon = Icons.Filled.Cloud,
                    iconTint = Color(0xFF4A90D9),
                    label = stringResource(R.string.theme_preset_azure_sky),
                )

                // Fresh Lemon preset
                ThemePresetCard(
                    name = "fresh_lemon",
                    isSelected = selectedPreset == "fresh_lemon" && colorSource == ColorSource.PRESET,
                    onClick = {
                        colorSource = ColorSource.PRESET
                        selectedPreset = "fresh_lemon"
                        prefs.edit {
                            putString("theme_preset", "fresh_lemon")
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                        (context as? MainActivity)?.setThemePreset("fresh_lemon")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                    },
                    backgroundColor = if (isDark) Color(0xFF2B2B1A) else Color(0xFFFFFEF8),
                    selectedBorderColor = Color(0xFFA8D830),
                    icon = Icons.Filled.Eco,
                    iconTint = Color(0xFFA8D830),
                    label = stringResource(R.string.theme_preset_fresh_lemon),
                )

                // Dragon Fruit preset
                ThemePresetCard(
                    name = "dragon_fruit",
                    isSelected = selectedPreset == "dragon_fruit" && colorSource == ColorSource.PRESET,
                    onClick = {
                        colorSource = ColorSource.PRESET
                        selectedPreset = "dragon_fruit"
                        prefs.edit {
                            putString("theme_preset", "dragon_fruit")
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                        (context as? MainActivity)?.setThemePreset("dragon_fruit")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                    },
                    backgroundColor = if (isDark) Color(0xFF331A2B) else Color(0xFFFFFAF8),
                    selectedBorderColor = Color(0xFFE070B0),
                    icon = Icons.Filled.Favorite,
                    iconTint = Color(0xFFE070B0),
                    label = stringResource(R.string.theme_preset_dragon_fruit),
                )

                // Divine Yellow preset
                ThemePresetCard(
                    name = "divine_yellow",
                    isSelected = selectedPreset == "divine_yellow" && colorSource == ColorSource.PRESET,
                    onClick = {
                        colorSource = ColorSource.PRESET
                        selectedPreset = "divine_yellow"
                        prefs.edit {
                            putString("theme_preset", "divine_yellow")
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                        (context as? MainActivity)?.setThemePreset("divine_yellow")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                    },
                    backgroundColor = if (isDark) Color(0xFF332B1A) else Color(0xFFFFFEF8),
                    selectedBorderColor = Color(0xFFE8B820),
                    icon = Icons.Filled.WbSunny,
                    iconTint = Color(0xFFE8B820),
                    label = stringResource(R.string.theme_preset_divine_yellow),
                )
            }

            Spacer(Modifier.height(16.dp))

            var useBanner by rememberSaveable {
                mutableStateOf(
                    prefs.getBoolean("use_banner", true)
                )
            }
            AnimatedVisibility(visible = ksuVersion != null) {
                SwitchItem(
                    icon = Icons.Filled.ViewCarousel,
                    title = stringResource(id = R.string.settings_banner),
                    summary = stringResource(id = R.string.settings_banner_summary),
                    checked = useBanner
                ) {
                    prefs.edit { putBoolean("use_banner", it) }
                    useBanner = it
                }
            }

            // === Dark Mode Independent Control ===
            var darkMode by rememberSaveable {
                mutableStateOf(
                    prefs.getString("dark_mode", "system") ?: "system"
                )
            }

            var enableAmoled by rememberSaveable {
                mutableStateOf(
                    prefs.getBoolean("enable_amoled", false)
                )
            }
            AnimatedVisibility(visible = darkMode != "light") {
                val activity = LocalContext.current as? MainActivity
                SwitchItem(
                    icon = Icons.Filled.Contrast,
                    title = stringResource(id = R.string.settings_amoled_mode),
                    summary = stringResource(id = R.string.settings_amoled_mode_summary),
                    checked = enableAmoled
                ) { checked ->
                    activity?.setAmoledMode(checked)
                    enableAmoled = checked
                }
            }

            Spacer(Modifier.height(16.dp))

            val darkModeTitle = stringResource(R.string.dark_mode_title)
            Text(
                text = darkModeTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = responsiveHorizontalPadding())
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = responsiveHorizontalPadding()),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val activity = LocalContext.current as? MainActivity

                // Light mode
                val lightBorderWidth by animateFloatAsState(
                    targetValue = if (darkMode == "light") 2.5f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "lightBorderWidth"
                )
                val lightBorderColor by animateColorAsState(
                    targetValue = if (darkMode == "light") MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                    label = "lightBorderColor"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        VibrationHelper.vibrateClick(context, prefs.getBoolean("vibration_enabled", false))
                        darkMode = "light"
                        prefs.edit { putString("dark_mode", "light") }
                        activity?.setDarkMode("light")
                        if (enableAmoled) {
                            activity?.setAmoledMode(false)
                            enableAmoled = false
                            prefs.edit { putBoolean("enable_amoled", false) }
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(lightBorderWidth.dp, lightBorderColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.LightMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.dark_mode_light), style = MaterialTheme.typography.labelMedium)
                }

                // Dark mode
                val darkBorderWidth by animateFloatAsState(
                    targetValue = if (darkMode == "dark") 2.5f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "darkBorderWidth"
                )
                val darkBorderColor by animateColorAsState(
                    targetValue = if (darkMode == "dark") MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                    label = "darkBorderColor"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        VibrationHelper.vibrateClick(context, prefs.getBoolean("vibration_enabled", false))
                        darkMode = "dark"
                        prefs.edit { putString("dark_mode", "dark") }
                        activity?.setDarkMode("dark")
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(darkBorderWidth.dp, darkBorderColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.dark_mode_dark), style = MaterialTheme.typography.labelMedium)
                }

                // System (follow system)
                val systemBorderWidth by animateFloatAsState(
                    targetValue = if (darkMode == "system") 2.5f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "systemBorderWidth"
                )
                val systemBorderColor by animateColorAsState(
                    targetValue = if (darkMode == "system") MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                    label = "systemBorderColor"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        VibrationHelper.vibrateClick(context, prefs.getBoolean("vibration_enabled", false))
                        darkMode = "system"
                        prefs.edit { putString("dark_mode", "system") }
                        activity?.setDarkMode("system")
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(systemBorderWidth.dp, systemBorderColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Contrast, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.dark_mode_system), style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(16.dp))

            // === Blur Effect Switch with Live Preview ===
            var blurEnabled by rememberSaveable {
                mutableStateOf(
                    prefs.getBoolean("blur_enabled", false)
                )
            }
            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = responsiveHorizontalPadding())) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.BlurOn, contentDescription = null)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.blur_enabled_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.blur_enabled_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = blurEnabled,
                            onCheckedChange = { enabled ->
                                VibrationHelper.vibrate(context, prefs.getBoolean("vibration_enabled", false))
                                prefs.edit { putBoolean("blur_enabled", enabled) }
                                (context as? MainActivity)?.setBlurEnabled(enabled)
                                blurEnabled = enabled
                            }
                        )
                    }
                    // Live preview
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (blurEnabled) "毛玻璃效果已开启" else "毛玻璃效果已关闭",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }

            // === Dynamic Color Switch ===
            var dynamicColorEnabled by rememberSaveable {
                mutableStateOf(prefs.getBoolean("material_you_dynamic_color", true))
            }
            val dynamicAlpha by animateFloatAsState(
                targetValue = if (colorSource == ColorSource.DYNAMIC) 1f else 0.4f,
                animationSpec = tween(300),
                label = "dynamicAlpha"
            )
            Box(modifier = Modifier.alpha(dynamicAlpha)) {
                SwitchItem(
                    icon = Icons.Filled.AutoAwesome,
                    title = stringResource(R.string.material_you_dynamic),
                    summary = stringResource(R.string.material_you_dynamic_desc),
                    checked = dynamicColorEnabled
                ) {
                    val enabled = it
                    if (enabled) {
                        colorSource = ColorSource.DYNAMIC
                    } else {
                        colorSource = ColorSource.PRESET
                    }
                    prefs.edit { putBoolean("material_you_dynamic_color", enabled) }
                    dynamicColorEnabled = enabled
                    (context as? MainActivity)?.setDynamicColor(enabled)
                    if (enabled) {
                        (context as? MainActivity)?.setAccentColor(-1)
                        prefs.edit {
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                        }
                    }
                }
            }

            // === Accent Color Selection ===
            val accentColors = listOf(
                Color(0xFF6750A4), Color(0xFFE91E63), Color(0xFF2196F3),
                Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFF9C27B0),
                Color(0xFF00BCD4), Color(0xFFFF5722)
            )
            var selectedAccentIndex by rememberSaveable {
                mutableIntStateOf(prefs.getInt("material_you_accent_color_index", 0))
            }

            val accentAlpha by animateFloatAsState(
                targetValue = if (colorSource == ColorSource.ACCENT) 1f else 0.4f,
                animationSpec = tween(300),
                label = "accentAlpha"
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.material_you_accent),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = responsiveHorizontalPadding())
            )
            Text(
                text = stringResource(R.string.material_you_accent_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = responsiveHorizontalPadding())
            )
            Spacer(Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = responsiveHorizontalPadding())
                    .alpha(accentAlpha),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                accentColors.forEachIndexed { index, color ->
                    val isSelected = selectedAccentIndex == index && colorSource == ColorSource.ACCENT
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isSelected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                                }
                            )
                            .clickable {
                                colorSource = ColorSource.ACCENT
                                selectedAccentIndex = index
                                prefs.edit { putInt("material_you_accent_color_index", index) }
                                prefs.edit { putLong("material_you_accent_color", color.value.toLong()) }
                                (context as? MainActivity)?.setAccentColor(color.value.toLong())
                                (context as? MainActivity)?.setDynamicColor(false)
                                (context as? MainActivity)?.setThemePreset("default")
                                prefs.edit {
                                    putBoolean("material_you_dynamic_color", false)
                                    putString("theme_preset", "default")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            var fontScale by rememberSaveable {
                mutableFloatStateOf(prefs.getFloat("material_you_font_scale", 1.0f))
            }

            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = responsiveHorizontalPadding())) {
                Column {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.TextFields, contentDescription = null) },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.material_you_font_scale),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = { Text("%.1fx".format(fontScale)) }
                    )
                    Slider(
                        value = fontScale,
                        onValueChange = {
                            fontScale = it
                            prefs.edit { putFloat("material_you_font_scale", it) }
                            (context as? MainActivity)?.setFontScale(it)
                            // Keep density scale in sync with font scale for consistent proportions
                            prefs.edit { putFloat("density_scale", it) }
                            (context as? MainActivity)?.setDensityScale(it)
                        },
                        valueRange = 0.8f..1.4f,
                        steps = 5,
                        modifier = Modifier.padding(horizontal = responsiveHorizontalPadding())
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            var cornerRadius by rememberSaveable {
                mutableFloatStateOf(prefs.getFloat("material_you_corner_radius", 16f))
            }

            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = responsiveHorizontalPadding())) {
                Column {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.RoundedCorner, contentDescription = null) },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.material_you_shape),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = { Text("%.0fdp".format(cornerRadius)) }
                    )
                    Slider(
                        value = cornerRadius,
                        onValueChange = {
                            cornerRadius = it
                            prefs.edit { putFloat("material_you_corner_radius", it) }
                            (context as? MainActivity)?.setCornerRadius(it)
                        },
                        valueRange = 0f..28f,
                        steps = 13,
                        modifier = Modifier.padding(horizontal = responsiveHorizontalPadding())
                    )
                    // Preview
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = responsiveHorizontalPadding(), vertical = 8.dp),
                        shape = RoundedCornerShape(cornerRadius.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(
                            modifier = Modifier.padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.material_you_rounded),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // === Card Elevation Slider ===
            var elevationValue by rememberSaveable {
                mutableStateOf(
                    prefs.getFloat("card_elevation", 1f)
                )
            }

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = responsiveHorizontalPadding())
            ) {
                Column {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Layers, contentDescription = null) },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.card_elevation_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = { Text("${elevationValue.toInt()}dp") }
                    )
                    Slider(
                        value = elevationValue,
                        onValueChange = {
                            VibrationHelper.vibrate(context, prefs.getBoolean("vibration_enabled", false))
                            elevationValue = it
                        },
                        onValueChangeFinished = {
                            prefs.edit { putFloat("card_elevation", elevationValue) }
                            (context as? MainActivity)?.setElevation(elevationValue)
                        },
                        valueRange = 0f..12f,
                        steps = 12,
                        modifier = Modifier.padding(horizontal = responsiveHorizontalPadding())
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // === Vibration Feedback Switch ===
            var vibrationEnabled by rememberSaveable {
                mutableStateOf(
                    prefs.getBoolean("vibration_enabled", false)
                )
            }
            SwitchItem(
                icon = Icons.Filled.Vibration,
                title = stringResource(R.string.vibration_enabled_title),
                summary = stringResource(R.string.vibration_enabled_summary),
                checked = vibrationEnabled
            ) { enabled ->
                // Trigger vibration to demonstrate the effect when enabling
                if (enabled) {
                    VibrationHelper.vibrateClick(context, true)
                }
                prefs.edit { putBoolean("vibration_enabled", enabled) }
                (context as? MainActivity)?.setVibrationEnabled(enabled)
                vibrationEnabled = enabled
            }

            // === Motion Settings Section ===
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.motion_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = responsiveHorizontalPadding())
            )
            Spacer(Modifier.height(8.dp))

            var pageTransitionEnabled by rememberSaveable {
                mutableStateOf(prefs.getBoolean("motion_page_transition", true))
            }
            SwitchItem(
                icon = Icons.Filled.SwapHoriz,
                title = stringResource(R.string.motion_animation),
                summary = stringResource(R.string.motion_animation_desc),
                checked = pageTransitionEnabled
            ) {
                prefs.edit { putBoolean("motion_page_transition", it) }
                pageTransitionEnabled = it
                (context as? MainActivity)?.setPageTransition(it)
            }

            var cardAnimationEnabled by rememberSaveable {
                mutableStateOf(prefs.getBoolean("motion_card_animation", true))
            }
            SwitchItem(
                icon = Icons.Filled.Animation,
                title = stringResource(R.string.motion_card_animation),
                summary = stringResource(R.string.motion_card_animation_desc),
                checked = cardAnimationEnabled
            ) {
                prefs.edit { putBoolean("motion_card_animation", it) }
                cardAnimationEnabled = it
            }

            var pullToRefreshEnabled by rememberSaveable {
                mutableStateOf(prefs.getBoolean("motion_pull_to_refresh", true))
            }
            SwitchItem(
                icon = Icons.Filled.Refresh,
                title = stringResource(R.string.motion_pull_refresh),
                summary = stringResource(R.string.motion_pull_refresh_desc),
                checked = pullToRefreshEnabled
            ) {
                prefs.edit { putBoolean("motion_pull_to_refresh", it) }
                pullToRefreshEnabled = it
            }

            // Animation Speed
            Spacer(Modifier.height(16.dp))
            var animSpeed by rememberSaveable {
                mutableFloatStateOf(prefs.getFloat("motion_animation_speed", 1.0f))
            }

            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = responsiveHorizontalPadding())) {
                Column {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Speed, contentDescription = null) },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.settings_check_update),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = { Text("%.1fx".format(animSpeed)) }
                    )
                    Slider(
                        value = animSpeed,
                        onValueChange = {
                            animSpeed = it
                            prefs.edit { putFloat("motion_animation_speed", it) }
                            (context as? MainActivity)?.setAnimationSpeed(it)
                        },
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                        modifier = Modifier.padding(horizontal = responsiveHorizontalPadding())
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePresetCard(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    backgroundColor: Color,
    selectedBorderColor: Color,
    icon: ImageVector,
    iconTint: Color,
    label: String,
) {
    // 使用 tween 替代 spring 动画，避免快速切换时闪烁
    val borderWidth by animateFloatAsState(
        targetValue = if (isSelected) 2.5f else 1f,
        animationSpec = tween(200),
        label = "${name}BorderWidth"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) selectedBorderColor else Color.Gray.copy(alpha = 0.3f),
        animationSpec = tween(200),
        label = "${name}BorderColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(IntrinsicSize.Min)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .border(borderWidth.dp, borderColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onBack: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        title = { Text(
                text = stringResource(R.string.customization),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            ) }, navigationIcon = {
            IconButton(
                onClick = onBack
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
        },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Preview
@Composable
private fun CustomizationPreview() {
    CustomizationScreen(EmptyDestinationsNavigator)
}
