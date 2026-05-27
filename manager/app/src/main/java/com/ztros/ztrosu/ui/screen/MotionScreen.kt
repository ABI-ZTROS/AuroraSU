package com.ztros.ztrosu.ui.screen

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun MotionScreen(navigator: DestinationsNavigator) {
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

    var animSpeed by rememberSaveable {
        mutableFloatStateOf(prefs.getFloat("motion_animation_speed", 1.0f))
    }

    // Pre-resolve all string resources
    val pageTransitionTitle = stringResource(R.string.motion_animation)
    val pageTransitionSummary = stringResource(R.string.motion_animation_desc)
    val cardAnimationTitle = stringResource(R.string.motion_card_animation)
    val cardAnimationSummary = stringResource(R.string.motion_card_animation_desc)
    val pullToRefreshTitle = stringResource(R.string.motion_pull_refresh)
    val pullToRefreshSummary = stringResource(R.string.motion_pull_refresh_desc)
    val animSpeedTitle = stringResource(R.string.settings_check_update)
    val animPreviewTitle = stringResource(R.string.customization)

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
            // Animation Toggles Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    var pageTransitionEnabled by rememberSaveable {
                        mutableStateOf(prefs.getBoolean("motion_page_transition", true))
                    }
                    SwitchItem(
                        icon = Icons.Filled.SwapHoriz,
                        title = pageTransitionTitle,
                        summary = pageTransitionSummary,
                        checked = pageTransitionEnabled,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    ) {
                        prefs.edit { putBoolean("motion_page_transition", it) }
                        pageTransitionEnabled = it
                        val activity = context as? com.ztros.ztrosu.ui.MainActivity
                        activity?.setPageTransition(it)
                    }

                    var cardAnimationEnabled by rememberSaveable {
                        mutableStateOf(prefs.getBoolean("motion_card_animation", true))
                    }
                    SwitchItem(
                        icon = Icons.Filled.Animation,
                        title = cardAnimationTitle,
                        summary = cardAnimationSummary,
                        checked = cardAnimationEnabled,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    ) {
                        prefs.edit { putBoolean("motion_card_animation", it) }
                        cardAnimationEnabled = it
                    }

                    var pullToRefreshEnabled by rememberSaveable {
                        mutableStateOf(prefs.getBoolean("motion_pull_to_refresh", true))
                    }
                    SwitchItem(
                        icon = Icons.Filled.Refresh,
                        title = pullToRefreshTitle,
                        summary = pullToRefreshSummary,
                        checked = pullToRefreshEnabled,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    ) {
                        prefs.edit { putBoolean("motion_pull_to_refresh", it) }
                        pullToRefreshEnabled = it
                    }
                }
            }

            // Animation Speed Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Filled.Speed, contentDescription = null)
                        Text(
                            text = animSpeedTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "%.1fx".format(animSpeed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = animSpeed,
                        onValueChange = {
                            animSpeed = it
                            prefs.edit { putFloat("motion_animation_speed", it) }
                            val activity = context as? com.ztros.ztrosu.ui.MainActivity
                            activity?.setAnimationSpeed(it)
                        },
                        valueRange = 0.5f..2.0f,
                        steps = 14
                    )
                }
            }

            // Animation Preview Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = animPreviewTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    AnimationPreviewBox(animSpeed = animSpeed)
                }
            }

            Spacer(Modifier)
        }
    }
}

@Composable
private fun AnimationPreviewBox(animSpeed: Float) {
    var animated by remember { mutableStateOf(false) }
    val animEnabled = true

    LaunchedEffect(animEnabled) {
        while (true) {
            animated = !animated
            delay((1000L / animSpeed).toLong())
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "preview")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (1000 / animSpeed).toInt()),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(
                    color = if (animated) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    shape = RoundedCornerShape(12.dp)
                )
                .animateContentSize()
        )
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
                text = stringResource(R.string.motion_title),
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
private fun MotionPreview() {
    MotionScreen(EmptyDestinationsNavigator)
}
