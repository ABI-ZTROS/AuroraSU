package com.ztros.ztrosu.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.activity.viewModels
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlin.math.abs
import kotlinx.coroutines.launch
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.HomeScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ExecuteModuleActionScreenDestination
import com.ramcosta.composedestinations.generated.destinations.FlashScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SuperUserScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingScreenDestination
import com.ramcosta.composedestinations.utils.isRouteOnBackStackAsState
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import com.ztros.ztrosu.Natives
import com.ztros.ztrosu.ksuApp
import com.ztros.ztrosu.ui.screen.BottomBarDestination
import com.ztros.ztrosu.ui.screen.FlashIt
import com.ztros.ztrosu.ui.theme.KernelSUTheme
import com.ztros.ztrosu.ui.util.*
import com.ztros.ztrosu.ui.viewmodel.ModuleViewModel
import com.ztros.ztrosu.ui.viewmodel.SuperUserViewModel
import dev.chrisbanes.haze.HazeState
import com.ztros.ztrosu.ui.component.GlassSurface
import com.ztros.ztrosu.ui.component.LocalBlurEnabled
import com.ztros.ztrosu.ui.component.LocalHazeState

data class ScrollState(
    val isScrollingDown: MutableState<Boolean>,
    val scrollOffset: MutableState<Float>,
    val previousScrollOffset: MutableState<Float>
)

val LocalScrollState = compositionLocalOf<ScrollState?> { null }

@Composable
fun rememberScrollConnection(
    isScrollingDown: MutableState<Boolean>,
    scrollOffset: MutableState<Float>,
    previousScrollOffset: MutableState<Float>,
    threshold: Float = 50f
): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                
                // Update scroll offset
                val newOffset = scrollOffset.value + delta
                scrollOffset.value = newOffset
                
                // Calculate the scroll delta from previous offset
                val scrollDelta = previousScrollOffset.value - newOffset
                
                // Only update direction if scroll delta exceeds threshold
                if (abs(scrollDelta) > threshold) {
                    isScrollingDown.value = scrollDelta > 0
                    previousScrollOffset.value = newOffset
                }
                
                return Offset.Zero
            }
            
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Reset offset tracking after fling
                previousScrollOffset.value = scrollOffset.value
                return super.onPostFling(consumed, available)
            }
        }
    }
}

fun Modifier.trackScroll(
    isScrollingDown: MutableState<Boolean>,
    scrollOffset: MutableState<Float>,
    previousScrollOffset: MutableState<Float>,
    threshold: Float = 50f
): Modifier {
    val scrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            
            // Update scroll offset
            val newOffset = scrollOffset.value + delta
            scrollOffset.value = newOffset
            
            // Calculate the scroll delta from previous offset
            val scrollDelta = previousScrollOffset.value - newOffset
            
            // Only update direction if scroll delta exceeds threshold
            if (abs(scrollDelta) > threshold) {
                isScrollingDown.value = scrollDelta > 0
                previousScrollOffset.value = newOffset
            }
            
            return Offset.Zero
        }
        
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // Reset offset tracking after fling
            previousScrollOffset.value = scrollOffset.value
            return super.onPostFling(consumed, available)
        }
    }
    
    return this.nestedScroll(scrollConnection)
}

class MainActivity : ComponentActivity() {

    var zipUri by mutableStateOf<ArrayList<Uri>?>(null)
    enum class NavigateLocation { SUPERUSER, MODULES, SETTINGS }
    var navigateLoc by mutableStateOf<NavigateLocation?>(null)
    var moduleActionId by mutableStateOf<String?>(null)
    var amoledModeState = mutableStateOf(false)
    var themePresetState = mutableStateOf("default")
    var dynamicColorState = mutableStateOf(true)
    var accentColorState = mutableStateOf(-1L)
    var fontScaleState = mutableStateOf(1f)
    var cornerRadiusState = mutableStateOf(16f)
    var pageTransitionState = mutableStateOf(true)
    var animationSpeedState = mutableStateOf(1f)
    // New UI settings states
    var darkModeState = mutableStateOf("system")  // "light", "dark", "system"
    var blurEnabledState = mutableStateOf(false)
    var elevationState = mutableStateOf(1f)
    var vibrationEnabledState = mutableStateOf(false)
    var densityScaleState = mutableStateOf(1f)
    val hazeState = HazeState()
    private val handler = Handler(Looper.getMainLooper())

    val moduleViewModel: ModuleViewModel by viewModels()
    val superUserViewModel: SuperUserViewModel by viewModels()

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.applyLanguage(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            if (superUserViewModel.appList.isEmpty()) {
                superUserViewModel.fetchAppList()
            }
            if (moduleViewModel.moduleList.isEmpty()) {
                moduleViewModel.fetchModuleList()
            }
        }

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        try {
            val prefsInit = getSharedPreferences("settings", MODE_PRIVATE)
            amoledModeState.value = prefsInit.getBoolean("enable_amoled", false)
            themePresetState.value = prefsInit.getString("theme_preset", "default") ?: "default"
            dynamicColorState.value = prefsInit.getBoolean("material_you_dynamic_color", true)
            accentColorState.value = prefsInit.getLong("material_you_accent_color", -1)
            fontScaleState.value = prefsInit.getFloat("material_you_font_scale", 1f)
            cornerRadiusState.value = prefsInit.getFloat("material_you_corner_radius", 16f)
            pageTransitionState.value = prefsInit.getBoolean("motion_page_transition", true)
            animationSpeedState.value = prefsInit.getFloat("motion_animation_speed", 1f)
            // Read new UI settings
            darkModeState.value = prefsInit.getString("dark_mode", "system") ?: "system"
            blurEnabledState.value = prefsInit.getBoolean("blur_enabled", false)
            elevationState.value = prefsInit.getFloat("card_elevation", 1f)
            vibrationEnabledState.value = prefsInit.getBoolean("vibration_enabled", false)
            densityScaleState.value = prefsInit.getFloat("density_scale", 1f)
        } catch (e: Exception) { Log.w("MainActivity", "Error: ${e.message}") }

        // Set window background for theme presets
        try {
            val prefsBg = getSharedPreferences("settings", MODE_PRIVATE)
            val themePreset = prefsBg.getString("theme_preset", "default") ?: "default"
            val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val bgColor = when (themePreset) {
                "ice_abyss" -> if (isDark) 0xFF0A1929.toInt() else 0xFFE6F4FA.toInt()
                "blood_moon" -> if (isDark) 0xFF1A0A0A.toInt() else 0xFFFFF8F5.toInt()
                "heavenly_palace" -> if (isDark) 0xFF0A0A14.toInt() else 0xFFFFFAF0.toInt()
                "azure_sky" -> if (isDark) 0xFF0A1020.toInt() else 0xFFF5FAFF.toInt()
                "fresh_lemon" -> if (isDark) 0xFF0A1408.toInt() else 0xFFFFFEF8.toInt()
                "dragon_fruit" -> if (isDark) 0xFF140A14.toInt() else 0xFFFFFAF8.toInt()
                "divine_yellow" -> if (isDark) 0xFF0A0A08.toInt() else 0xFFFFFEF8.toInt()
                else -> android.graphics.Color.TRANSPARENT
            }
            window.decorView.setBackgroundColor(bgColor)
        } catch (e: Exception) { Log.w("MainActivity", "Error: ${e.message}") }

        val isManager = Natives.isManager
        if (isManager) install()

        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            intent.extras?.clear()
            intent = null
        }

        if(intent != null)
            handleIntent(intent)

        setContent {
            // Calculate effective dark theme based on user preference
            val effectiveDarkTheme = when (darkModeState.value) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()  // "system"
            }
            
            // Apply density scaling with CompositionLocalProvider
            // Get system density and apply user scaling factor
            val systemDensity = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = systemDensity * densityScaleState.value,
                    fontScale = fontScaleState.value
                ),
                LocalBlurEnabled provides blurEnabledState.value,
                LocalHazeState provides hazeState
            ) {
                KernelSUTheme(
                    darkTheme = effectiveDarkTheme,
                    dynamicColor = dynamicColorState.value,
                    amoledMode = amoledModeState.value,
                    themePreset = themePresetState.value,
                    accentColor = accentColorState.value,
                    fontScale = fontScaleState.value,
                    cornerRadius = cornerRadiusState.value,
                    cardElevation = elevationState.value,
                ) {
                val navController = rememberNavController()
                val snackBarHostState = remember { SnackbarHostState() }
                val currentDestination = navController.currentBackStackEntryAsState().value?.destination
                val bottomBarRoutes = remember {
                    BottomBarDestination.entries.map { it.direction.route }.toSet()
                }
                val navigator = navController.rememberDestinationsNavigator()

                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route

                val homeDestination = BottomBarDestination.entries.firstOrNull()
                val startRoute = homeDestination?.direction?.route

                if (homeDestination != null && startRoute != null) {
                    BackHandler(enabled = currentRoute != startRoute && currentRoute in bottomBarRoutes) {
                        navigator.navigate(homeDestination.direction) {
                            popUpTo(NavGraphs.root) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                // Track the last bottom bar destination index for directional animations
                var lastBottomBarIndex by remember { mutableStateOf(0) }
                var isBottomBarNavigation by remember { mutableStateOf(false) }
                
                // Scroll state for bottom bar visibility
                val isScrollingDown = remember { mutableStateOf(false) }
                val scrollOffset = remember { mutableStateOf(0f) }
                val previousScrollOffset = remember { mutableStateOf(0f) }
                
                // Remember the last valid navbar selection (persists across navbar hide/show)
                val lastValidNavbarSelection = remember { mutableStateOf(0) }

                LaunchedEffect(zipUri, navigateLoc, moduleActionId) {
                    val actionId = moduleActionId
                    if (actionId != null) {
                        navigator.navigate(ExecuteModuleActionScreenDestination(actionId))
                        moduleActionId = null
                    }

                    val uris = zipUri
                    if (!uris.isNullOrEmpty()) {
                        val component = intent?.component?.className
                        val flashIt = when {
                            component?.endsWith("FlashAnyKernel") == true -> FlashIt.FlashAnyKernel(uris.first())
                            else -> FlashIt.FlashModules(uris)
                        }
                        
                        navigator.navigate(
                            FlashScreenDestination(flashIt = flashIt)
                        )
                        zipUri = null
                    }

                    if (zipUri.isNullOrEmpty() && navigateLoc != null) {
                        when (navigateLoc) {
                            NavigateLocation.SUPERUSER -> navigator.navigate(SuperUserScreenDestination)
                            NavigateLocation.MODULES -> navigator.navigate(ModuleScreenDestination)
                            NavigateLocation.SETTINGS -> navigator.navigate(SettingScreenDestination)
                            else -> { /* no-op for exhaustiveness */ }
                        }
                        navigateLoc = null
                    }
                }

                val showBottomBar = when (currentDestination?.route) {
                    FlashScreenDestination.route -> false // Hide for FlashScreenDestination
                    ExecuteModuleActionScreenDestination.route -> false // Hide for ExecuteModuleActionScreen
                    else -> !isScrollingDown.value
                }

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        CompositionLocalProvider(
                            LocalSnackbarHost provides snackBarHostState,
                            LocalScrollState provides ScrollState(
                                isScrollingDown = isScrollingDown,
                                scrollOffset = scrollOffset,
                                previousScrollOffset = previousScrollOffset
                            )
                        ) {
                            ResponsiveLayout {
                            DestinationsNavHost(
                                modifier = Modifier
                                    .padding(innerPadding)
                                    .fillMaxSize(),
                                navGraph = NavGraphs.root,
                                navController = navController,
                                defaultTransitions = object : NavHostAnimatedDestinationStyle() {
                                    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                                        if (!pageTransitionState.value) {
                                            EnterTransition.None
                                        } else {
                                            val targetRoute = targetState.destination.route
                                            val initialRoute = initialState.destination.route

                                            val targetIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                                            val initialIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                                            when {
                                                // Bottom bar -> bottom bar: slide based on index direction
                                                targetIndex != -1 && initialIndex != -1 -> {
                                                    val offsetSign = if (targetIndex > initialIndex) 1 else -1
                                                    slideInHorizontally(initialOffsetX = { it * offsetSign }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                                }
                                                // Detail page -> bottom bar: slide in from left
                                                targetRoute in bottomBarRoutes && initialRoute !in bottomBarRoutes -> {
                                                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                                }
                                                // Bottom bar -> detail page: slide in from right
                                                else -> slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                            }
                                        }
                                    }

                                    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                                        if (!pageTransitionState.value) {
                                            ExitTransition.None
                                        } else {
                                            val targetRoute = targetState.destination.route
                                            val initialRoute = initialState.destination.route

                                            val targetIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                                            val initialIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                                            when {
                                                // Bottom bar -> bottom bar: slide out opposite direction
                                                targetIndex != -1 && initialIndex != -1 -> {
                                                    val offsetSign = if (targetIndex > initialIndex) -1 else 1
                                                    slideOutHorizontally(targetOffsetX = { it * offsetSign }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                                }
                                                // Bottom bar -> detail page: slide out to left
                                                initialRoute in bottomBarRoutes && targetRoute !in bottomBarRoutes -> {
                                                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                                }
                                                // Default
                                                else -> slideOutHorizontally(targetOffsetX = { -it }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                            }
                                        }
                                    }

                                    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                                        if (!pageTransitionState.value) {
                                            EnterTransition.None
                                        } else {
                                            val targetRoute = targetState.destination.route
                                            val initialRoute = initialState.destination.route

                                            val targetIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                                            val initialIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                                            when {
                                                // Bottom bar -> bottom bar pop: mirror of exit
                                                targetIndex != -1 && initialIndex != -1 -> {
                                                    val offsetSign = if (targetIndex > initialIndex) 1 else -1
                                                    slideInHorizontally(initialOffsetX = { it * offsetSign }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                                }
                                                // Returning from detail -> bottom bar: slide in from left
                                                targetRoute in bottomBarRoutes -> {
                                                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                                }
                                                else -> slideInHorizontally(initialOffsetX = { -it }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                            }
                                        }
                                    }

                                    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                                        if (!pageTransitionState.value) {
                                            ExitTransition.None
                                        } else {
                                            val targetRoute = targetState.destination.route
                                            val initialRoute = initialState.destination.route

                                            val targetIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                                            val initialIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                                            when {
                                                // Bottom bar -> bottom bar pop
                                                targetIndex != -1 && initialIndex != -1 -> {
                                                    val offsetSign = if (targetIndex > initialIndex) -1 else 1
                                                    slideOutHorizontally(targetOffsetX = { it * offsetSign }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                                }
                                                // Detail page closing: slide out to right
                                                initialRoute !in bottomBarRoutes -> {
                                                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                                }
                                                else -> slideOutHorizontally(targetOffsetX = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                                            }
                                        }
                                    }
                                }
                            )
                            }
                        }
                        
                        // Floating Bottom Bar as overlay
                        AnimatedVisibility(
                            visible = showBottomBar,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            BottomBar(navController, lastValidNavbarSelection)
                        }
                    }
                }
            }
        }
    }
    }

    fun setAmoledMode(enabled: Boolean) {
        try {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            prefs.edit().putBoolean("enable_amoled", enabled).apply()
        } catch (e: Exception) { Log.w("MainActivity", "Error: ${e.message}") }
        amoledModeState.value = enabled
    }

    fun setThemePreset(preset: String) {
        themePresetState.value = preset
        getSharedPreferences("settings", MODE_PRIVATE).edit { putString("theme_preset", preset) }
        // Update window background for theme presets
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bgColor = when (preset) {
            "ice_abyss" -> if (isDark) 0xFF0A1929.toInt() else 0xFFE6F4FA.toInt()
            "blood_moon" -> if (isDark) 0xFF1A0A0A.toInt() else 0xFFFFF8F5.toInt()
            "heavenly_palace" -> if (isDark) 0xFF0A0A14.toInt() else 0xFFFFFAF0.toInt()
            "azure_sky" -> if (isDark) 0xFF0A1020.toInt() else 0xFFF5FAFF.toInt()
            "fresh_lemon" -> if (isDark) 0xFF0A1408.toInt() else 0xFFFFFEF8.toInt()
            "dragon_fruit" -> if (isDark) 0xFF140A14.toInt() else 0xFFFFFAF8.toInt()
            "divine_yellow" -> if (isDark) 0xFF0A0A08.toInt() else 0xFFFFFEF8.toInt()
            else -> android.graphics.Color.TRANSPARENT
        }
        window.decorView.setBackgroundColor(bgColor)
    }

    fun setDynamicColor(enabled: Boolean) {
        dynamicColorState.value = enabled
        getSharedPreferences("settings", MODE_PRIVATE).edit { putBoolean("material_you_dynamic_color", enabled) }
    }

    fun setAccentColor(color: Long) {
        accentColorState.value = color
        getSharedPreferences("settings", MODE_PRIVATE).edit { putLong("material_you_accent_color", color) }
    }

    fun setFontScale(scale: Float) {
        fontScaleState.value = scale
        getSharedPreferences("settings", MODE_PRIVATE).edit { putFloat("material_you_font_scale", scale) }
    }

    fun setCornerRadius(radius: Float) {
        cornerRadiusState.value = radius
        getSharedPreferences("settings", MODE_PRIVATE).edit { putFloat("material_you_corner_radius", radius) }
    }

    fun setPageTransition(enabled: Boolean) {
        pageTransitionState.value = enabled
        getSharedPreferences("settings", MODE_PRIVATE).edit { putBoolean("motion_page_transition", enabled) }
    }

    fun setAnimationSpeed(speed: Float) {
        animationSpeedState.value = speed
        getSharedPreferences("settings", MODE_PRIVATE).edit { putFloat("motion_animation_speed", speed) }
    }

    // New UI settings setter methods
    fun setDarkMode(mode: String) {
        darkModeState.value = mode
        getSharedPreferences("settings", MODE_PRIVATE).edit { putString("dark_mode", mode) }
    }

    fun setBlurEnabled(enabled: Boolean) {
        blurEnabledState.value = enabled
        getSharedPreferences("settings", MODE_PRIVATE).edit { putBoolean("blur_enabled", enabled) }
    }

    fun setElevation(value: Float) {
        elevationState.value = value
        getSharedPreferences("settings", MODE_PRIVATE).edit { putFloat("card_elevation", value) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        vibrationEnabledState.value = enabled
        getSharedPreferences("settings", MODE_PRIVATE).edit { putBoolean("vibration_enabled", enabled) }
    }

    fun setDensityScale(scale: Float) {
        densityScaleState.value = scale
        getSharedPreferences("settings", MODE_PRIVATE).edit { putFloat("density_scale", scale) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        setIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val shortcutType = intent.getStringExtra("shortcut_type")
        if (shortcutType == "module_action") {
            moduleActionId = intent.getStringExtra("module_id")
        }

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                zipUri =
                    intent.data?.let { arrayListOf(it) }
                        ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableArrayListExtra("uris", Uri::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableArrayListExtra("uris")
                        }
            }

            "ACTION_SETTINGS" -> navigateLoc = NavigateLocation.SETTINGS
            "ACTION_SUPERUSER" -> navigateLoc = NavigateLocation.SUPERUSER
            "ACTION_MODULES" -> navigateLoc = NavigateLocation.MODULES
            else -> { /* ignore other actions */ }
        }
    }

@Composable
private fun BottomBar(
    navController: NavHostController,
    lastValidSelection: MutableState<Int>
) {
    val navigator = navController.rememberDestinationsNavigator()
    val isManager = Natives.isManager
    val fullFeatured = KernelDetect.isFullFeatured()

    val visibleDestinations = remember(fullFeatured) {
        BottomBarDestination.entries
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val isOnBackStack = visibleDestinations.map { destination ->
        navController.isRouteOnBackStackAsState(destination.direction).value
    }

    // Prefer an exact current-route match; fall back to whichever tab is on the back stack.
    val selectedIndex = run {
        val exactMatch = visibleDestinations.indexOfFirst { it.direction.route == currentRoute }
        if (exactMatch != -1) exactMatch
        else isOnBackStack.indexOfLast { it } // last tab whose route is somewhere on the stack
    }

    // Persist the selection so the indicator doesn't jump while the navbar is animating out/in.
    if (selectedIndex != -1) {
        lastValidSelection.value = selectedIndex
    }

    // Use current selection if on navbar, otherwise use last valid selection
    val effectiveSelectedIndex = if (selectedIndex != -1) selectedIndex else lastValidSelection.value
    
    // Animate the indicator position with jelly/spring effect
    val animatedSelectedIndex by animateFloatAsState(
        targetValue = effectiveSelectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "selectedIndex"
    )

    // Responsive padding based on screen width
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                bottom = WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding()
            )
    ) {
        val screenWidth = maxWidth
        val horizontalScreenPadding = when {
            screenWidth > 600.dp -> 32.dp // Tablet/Large screen
            screenWidth > 400.dp -> 24.dp // Normal phone
            else -> 16.dp // Small phone
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalScreenPadding, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassSurface(
                modifier = Modifier.wrapContentWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                val itemSize = 56.dp
                val itemSpacing = 4.dp
                val containerPadding = 7.dp // Reduced to match vertical padding
                
                // Calculate exact width based on items
                val navBarWidth = (itemSize * visibleDestinations.size) + 
                                 (itemSpacing * (visibleDestinations.size - 1)) + 
                                 (containerPadding * 2)
                
                Box(
                    modifier = Modifier
                        .width(navBarWidth)
                        .height(72.dp)
                ) {
                    var totalWidth by remember { mutableStateOf(0) }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = containerPadding)
                            .onSizeChanged { size ->
                                totalWidth = size.width
                            }
                    ) {
                        // Animated sliding indicator
                        if (totalWidth > 0 && visibleDestinations.isNotEmpty()) {
                            val density = LocalDensity.current
                            val itemSizePx = with(density) { itemSize.toPx() }
                            val itemSpacingPx = with(density) { itemSpacing.toPx() }
                            
                            // Calculate offset: each item position = (itemSize + spacing) * index
                            val indicatorOffset = (itemSizePx + itemSpacingPx) * animatedSelectedIndex
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 8.dp)
                                    .offset {
                                        androidx.compose.ui.unit.IntOffset(
                                            x = indicatorOffset.toInt(),
                                            y = 0
                                        )
                                    }
                                    .width(itemSize),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(itemSize)
                                        .background(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = MaterialTheme.shapes.large
                                        )
                                )
                            }
                        }
                        
                        // Navigation items
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            visibleDestinations.forEachIndexed { index, destination ->
                                    // Determine selection by checking if this is the effective selected index
                                    val isSelected = index == effectiveSelectedIndex
                                
                                Box(
                                    modifier = Modifier
                                        .size(itemSize)
                                        .clip(MaterialTheme.shapes.large)
                                        .clickable {
                                            // If already on this destination, do nothing to avoid reopening
                                            if (destination.direction.route == currentRoute) return@clickable

                                            // Always recreate the destination to avoid keeping saved state
                                            // which reduces memory usage by closing old destinations.
                                            navigator.navigate(destination.direction) {
                                                popUpTo(NavGraphs.root.startRoute) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isSelected) destination.iconSelected else destination.iconNotSelected,
                                        stringResource(destination.label),
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}