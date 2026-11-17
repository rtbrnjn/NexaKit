package com.mediaforge.studio

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mediaforge.studio.core.Permissions
import com.mediaforge.studio.data.ThemeMode
import com.mediaforge.studio.ui.components.LiquidBackground
import com.mediaforge.studio.ui.screens.GeminiAiScreen
import com.mediaforge.studio.ui.screens.MediaLabScreen
import com.mediaforge.studio.ui.screens.PdfSuiteScreen
import com.mediaforge.studio.ui.screens.ResultSheet
import com.mediaforge.studio.ui.screens.SettingsScreen
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.ui.theme.MediaForgeTheme
import com.mediaforge.studio.ui.theme.isThemeDark
import com.mediaforge.studio.vm.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class TopTab(val route: String, val label: String, val icon: ImageVector) {
    MEDIA_LAB("media_lab", "Media Lab", Icons.Rounded.PhotoLibrary),
    PDF_SUITE("pdf_suite", "PDF Suite", Icons.Rounded.PictureAsPdf),
    GEMINI_AI("gemini_ai", "Gemini AI", Icons.Rounded.AutoAwesome),
    SETTINGS("settings", "Settings", Icons.Rounded.Tune)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val container = (application as MediaForgeApp).container
            val themeMode by container.settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val dark = isThemeDark(themeMode)

            SideEffect {
                val style = if (dark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            MediaForgeTheme(themeMode = themeMode) {
                MediaForgeRoot()
            }
        }
    }
}

@Composable
private fun MediaForgeRoot() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as MediaForgeApp
    val container = app.container
    val scope = rememberCoroutineScope()

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: TopTab.MEDIA_LAB.route

    val settingsVm: SettingsViewModel = viewModel()
    val results by container.results.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { container.settings.setPermissionAsked(true) }
    }

    LaunchedEffect(Unit) {
        val alreadyAsked = container.settings.permissionAsked.first()
        if (!alreadyAsked && !Permissions.granted(ctx)) {
            permLauncher.launch(Permissions.required())
        }
    }

    fun navigateTo(tab: TopTab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LiquidBackground {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            NavHost(
                navController = navController,
                startDestination = TopTab.MEDIA_LAB.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(TopTab.MEDIA_LAB.route) { MediaLabScreen() }
                composable(TopTab.PDF_SUITE.route) { PdfSuiteScreen() }
                composable(TopTab.GEMINI_AI.route) {
                    GeminiAiScreen(
                        settingsVm = settingsVm,
                        onOpenVault = { navigateTo(TopTab.SETTINGS) }
                    )
                }
                composable(TopTab.SETTINGS.route) { SettingsScreen(settingsVm) }
            }

            LiquidGlassBottomBar(
                currentRoute = currentRoute,
                onSelect = ::navigateTo,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        ResultSheet(
            files = results,
            onDismiss = container::dismissResults
        )
    }
}

@Composable
private fun LiquidGlassBottomBar(
    currentRoute: String,
    onSelect: (TopTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(26.dp)
    val isDark = Glass.IsDark
    Row(
        modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 14.dp else 10.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.10f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.10f)
            )
            .clip(shape)
            .background(Glass.NavFill)
            .border(1.dp, Glass.Stroke, shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopTab.entries.forEach { tab ->
            val selected = currentRoute == tab.route
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.0f else 0.96f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                label = "navScale"
            )
            val interaction = remember { MutableInteractionSource() }

            Column(
                Modifier
                    .weight(1f)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (selected) Modifier
                            .background(Glass.Cyan.copy(alpha = if (isDark) 0.16f else 0.12f))
                            .border(1.dp, Glass.Cyan.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                        else Modifier
                    )
                    .clickable(interactionSource = interaction, indication = null) { onSelect(tab) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) Glass.Cyan else Glass.TextSecondary,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) Glass.TextPrimary else Glass.TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
