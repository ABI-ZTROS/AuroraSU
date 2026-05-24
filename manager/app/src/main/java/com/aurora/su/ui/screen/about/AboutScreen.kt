package com.aurora.su.ui.screen.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import com.aurora.su.BuildConfig
import com.aurora.su.R
import com.aurora.su.ui.LocalUiMode
import com.aurora.su.ui.UiMode
import com.aurora.su.ui.navigation3.LocalNavigator

@Composable
fun AboutScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val htmlString = stringResource(
        id = R.string.about_source_code,
        "<b><a href=\"https://github.com/ShirkNeko/AuroraSU\">GitHub</a></b>",
        "<b><a href=\"https://t.me/SukiKSU\">Telegram</a></b>",
        "<b>怡子曰曰</b>",
        "<b>明风 OuO</b>",
        "<b><a href=\"https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode.txt\">CC BY-NC-SA 4.0</a></b>"
    )
    val state = AboutUiState(
        title = stringResource(R.string.about),
        appName = stringResource(R.string.app_name),
        versionName = BuildConfig.VERSION_NAME,
        links = extractLinks(htmlString),
    )
    val actions = AboutScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onOpenLink = uriHandler::openUri,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> AboutScreenMiuix(state, actions)
        UiMode.Material -> AboutScreenMaterial(state, actions)
    }
}
