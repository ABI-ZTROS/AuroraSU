package com.ztros.ztrosu.ui.screen

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shadow
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.ztros.ztrosu.ui.util.refreshActivity
import com.ztros.ztrosu.ui.util.LocalSnackbarHost
import com.ztros.ztrosu.ui.util.LocaleHelper
import com.ztros.ztrosu.ui.util.VibrationHelper

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
                                        java.util.Locale.Builder()
                                            .setLanguage(parts[0])
                                            .setRegion(parts[1])
                                            .build()
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

            // === Color Theme Preset (Ice Abyss) ===
            var selectedPreset by rememberSaveable {
                mutableStateOf(prefs.getString("theme_preset", "default") ?: "default")
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.theme_preset_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = stringResource(R.string.theme_preset_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated border properties for theme presets
                val defaultBorderWidth by animateFloatAsState(
                    targetValue = if (selectedPreset == "default") 2.5f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "defaultBorderWidth"
                )
                val defaultBorderColor by animateColorAsState(
                    targetValue = if (selectedPreset == "default") MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                    label = "defaultBorderColor"
                )
                val iceBorderWidth by animateFloatAsState(
                    targetValue = if (selectedPreset == "ice_abyss") 2.5f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "iceBorderWidth"
                )
                val iceBorderColor by animateColorAsState(
                    targetValue = if (selectedPreset == "ice_abyss") Color(0xFF00B4D8) else Color.Gray.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                    label = "iceBorderColor"
                )
                val bloodMoonBorderWidth by animateFloatAsState(
                    targetValue = if (selectedPreset == "blood_moon") 2.5f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "bloodMoonBorderWidth"
                )
                val bloodMoonBorderColor by animateColorAsState(
                    targetValue = if (selectedPreset == "blood_moon") Color(0xFFC44536) else Color.Gray.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                    label = "bloodMoonBorderColor"
                )
                val heavenlyPalaceBorderWidth by animateFloatAsState(
                    targetValue = if (selectedPreset == "heavenly_palace") 2.5f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "heavenlyPalaceBorderWidth"
                )
                val heavenlyPalaceBorderColor by animateColorAsState(
                    targetValue = if (selectedPreset == "heavenly_palace") Color(0xFFD4A017) else Color.Gray.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                    label = "heavenlyPalaceBorderColor"
                )
                val azureSkyBorderWidth by animateFloatAsState(
                    targetValue = if (selectedPreset == "azure_sky") 2.5f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "azureSkyBorderWidth"
                )
                val azureSkyBorderColor by animateColorAsState(
                    targetValue = if (selectedPreset == "azure_sky") Color(0xFF4A90D9) else Color.Gray.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                    label = "azureSkyBorderColor"
                )
                val freshLemonBorderWidth by animateFloatAsState(
                    targetValue = if (selectedPreset == "fresh_lemon") 2.5f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "freshLemonBorderWidth"
                )
                val freshLemonBorderColor by animateColorAsState(
                    targetValue = if (selectedPreset == "fresh_lemon") Color(0xFFA8D830) else Color.Gray.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                    label = "freshLemonBorderColor"
                )
                val dragonFruitBorderWidth by animateFloatAsState(
                    targetValue = if (selectedPreset == "dragon_fruit") 2.5f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "dragonFruitBorderWidth"
                )
                val dragonFruitBorderColor by animateColorAsState(
                    targetValue = if (selectedPreset == "dragon_fruit") Color(0xFFE070B0) else Color.Gray.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                    label = "dragonFruitBorderColor"
                )
                val divineYellowBorderWidth by animateFloatAsState(
                    targetValue = if (selectedPreset == "divine_yellow") 2.5f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "divineYellowBorderWidth"
                )
                val divineYellowBorderColor by animateColorAsState(
                    targetValue = if (selectedPreset == "divine_yellow") Color(0xFFE8B820) else Color.Gray.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                    label = "divineYellowBorderColor"
                )

                // Default preset
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        selectedPreset = "default"
                        prefs.edit { putString("theme_preset", "default") }
                        (context as? MainActivity)?.setThemePreset("default")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                        prefs.edit {
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(defaultBorderWidth.dp, defaultBorderColor, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.theme_preset_default), style = MaterialTheme.typography.labelMedium)
                }

                // Ice Abyss preset
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        selectedPreset = "ice_abyss"
                        prefs.edit { putString("theme_preset", "ice_abyss") }
                        (context as? MainActivity)?.setThemePreset("ice_abyss")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                        prefs.edit {
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFE6F4FA))
                            .border(iceBorderWidth.dp, iceBorderColor, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AcUnit, contentDescription = null, tint = Color(0xFF00B4D8), modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.theme_preset_ice_abyss), style = MaterialTheme.typography.labelMedium)
                }

                // Blood Moon preset
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        selectedPreset = "blood_moon"
                        prefs.edit { putString("theme_preset", "blood_moon") }
                        (context as? MainActivity)?.setThemePreset("blood_moon")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                        prefs.edit {
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFFF8F5))
                            .border(bloodMoonBorderWidth.dp, bloodMoonBorderColor, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Whatshot, contentDescription = null, tint = Color(0xFFC44536), modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.theme_preset_blood_moon), style = MaterialTheme.typography.labelMedium)
                }

                // Heavenly Palace preset
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        selectedPreset = "heavenly_palace"
                        prefs.edit { putString("theme_preset", "heavenly_palace") }
                        (context as? MainActivity)?.setThemePreset("heavenly_palace")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                        prefs.edit {
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFFFAF0))
                            .border(heavenlyPalaceBorderWidth.dp, heavenlyPalaceBorderColor, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFD4A017), modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.theme_preset_heavenly_palace), style = MaterialTheme.typography.labelMedium)
                }

                // Azure Sky preset
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        selectedPreset = "azure_sky"
                        prefs.edit { putString("theme_preset", "azure_sky") }
                        (context as? MainActivity)?.setThemePreset("azure_sky")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                        prefs.edit {
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF5FAFF))
                            .border(azureSkyBorderWidth.dp, azureSkyBorderColor, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Cloud, contentDescription = null, tint = Color(0xFF4A90D9), modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.theme_preset_azure_sky), style = MaterialTheme.typography.labelMedium)
                }

                // Fresh Lemon preset
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        selectedPreset = "fresh_lemon"
                        prefs.edit { putString("theme_preset", "fresh_lemon") }
                        (context as? MainActivity)?.setThemePreset("fresh_lemon")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                        prefs.edit {
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFFFEF8))
                            .border(freshLemonBorderWidth.dp, freshLemonBorderColor, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Eco, contentDescription = null, tint = Color(0xFFA8D830), modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.theme_preset_fresh_lemon), style = MaterialTheme.typography.labelMedium)
                }

                // Dragon Fruit preset
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        selectedPreset = "dragon_fruit"
                        prefs.edit { putString("theme_preset", "dragon_fruit") }
                        (context as? MainActivity)?.setThemePreset("dragon_fruit")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                        prefs.edit {
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFFFAF8))
                            .border(dragonFruitBorderWidth.dp, dragonFruitBorderColor, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFE070B0), modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.theme_preset_dragon_fruit), style = MaterialTheme.typography.labelMedium)
                }

                // Divine Yellow preset
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        selectedPreset = "divine_yellow"
                        prefs.edit { putString("theme_preset", "divine_yellow") }
                        (context as? MainActivity)?.setThemePreset("divine_yellow")
                        (context as? MainActivity)?.setAccentColor(-1)
                        (context as? MainActivity)?.setDynamicColor(false)
                        prefs.edit {
                            putLong("material_you_accent_color", -1)
                            putInt("material_you_accent_color_index", -1)
                            putBoolean("material_you_dynamic_color", false)
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFFFEF8))
                            .border(divineYellowBorderWidth.dp, divineYellowBorderColor, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.WbSunny, contentDescription = null, tint = Color(0xFFE8B820), modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.theme_preset_divine_yellow), style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(16.dp))

            var useBanner by rememberSaveable {
                mutableStateOf(
                    prefs.getBoolean("use_banner", true)
                )
            }
            if (ksuVersion != null) {
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

            var enableAmoled by rememberSaveable {
                mutableStateOf(
                    prefs.getBoolean("enable_amoled", false)
                )
            }
            if (isSystemInDarkTheme()) {
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

            // === Dark Mode Independent Control ===
            Spacer(Modifier.height(16.dp))
            
            var darkMode by rememberSaveable {
                mutableStateOf(
                    prefs.getString("dark_mode", "system") ?: "system"
                )
            }
            
            val darkModeTitle = stringResource(R.string.dark_mode_title)
            Text(
                text = darkModeTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
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
                    Spacer(Modifier.height(4.dp))
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
                    Spacer(Modifier.height(4.dp))
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
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.dark_mode_system), style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(16.dp))

            // === Blur Effect Switch ===
            var blurEnabled by rememberSaveable {
                mutableStateOf(
                    prefs.getBoolean("blur_enabled", false)
                )
            }
            SwitchItem(
                icon = Icons.Filled.BlurOn,
                title = stringResource(R.string.blur_enabled_title),
                summary = stringResource(R.string.blur_enabled_summary),
                checked = blurEnabled
            ) { enabled ->
                VibrationHelper.vibrate(context, prefs.getBoolean("vibration_enabled", false))
                prefs.edit { putBoolean("blur_enabled", enabled) }
                (context as? MainActivity)?.setBlurEnabled(enabled)
                blurEnabled = enabled
            }

            Spacer(Modifier.height(8.dp))

            // === Card Elevation Slider ===
            var elevationValue by rememberSaveable {
                mutableStateOf(
                    prefs.getFloat("card_elevation", 1f)
                )
            }
            
            ListItem(
                leadingContent = { Icon(Icons.Filled.Shadow, contentDescription = null) },
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
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

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

            Spacer(Modifier.height(8.dp))

            // === UI Density Scale Slider ===
            var densityScale by rememberSaveable {
                mutableStateOf(
                    prefs.getFloat("density_scale", 1f)
                )
            }
            
            ListItem(
                leadingContent = { Icon(Icons.Filled.ZoomOutMap, contentDescription = null) },
                headlineContent = { 
                    Text(
                        text = stringResource(R.string.density_scale_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                supportingContent = { Text(String.format("%.1fx", densityScale)) }
            )
            Slider(
                value = densityScale,
                onValueChange = { 
                    VibrationHelper.vibrate(context, prefs.getBoolean("vibration_enabled", false))
                    densityScale = it
                },
                onValueChangeFinished = {
                    prefs.edit { putFloat("density_scale", densityScale) }
                    (context as? MainActivity)?.setDensityScale(densityScale)
                },
                valueRange = 0.8f..1.4f,
                steps = 6,  // 0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4
                modifier = Modifier.padding(horizontal = 16.dp)
            )
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
