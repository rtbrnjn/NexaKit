package com.mediaforge.studio

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mediaforge.studio.core.Permissions
import com.mediaforge.studio.data.ThemeMode
import com.mediaforge.studio.ui.components.LiquidBackground
import com.mediaforge.studio.ui.components.LocalHazeState
import com.mediaforge.studio.ui.components.liquidGlassChrome
import com.mediaforge.studio.ui.screens.AiTool
import com.mediaforge.studio.ui.screens.GeminiAiScreen
import com.mediaforge.studio.ui.screens.MediaLabScreen
import com.mediaforge.studio.ui.screens.MediaTool
import com.mediaforge.studio.ui.screens.PdfSuiteScreen
import com.mediaforge.studio.ui.screens.PdfTool
import com.mediaforge.studio.ui.screens.ResultSheet
import com.mediaforge.studio.ui.screens.SettingsScreen
import com.mediaforge.studio.ui.screens.ShareIntakeSheet
import com.mediaforge.studio.ui.theme.Glass
import com.mediaforge.studio.ui.theme.MediaForgeTheme
import com.mediaforge.studio.ui.theme.isThemeDark
import com.mediaforge.studio.vm.AiVaultViewModel
import com.mediaforge.studio.vm.CollageViewModel
import com.mediaforge.studio.vm.CompressPdfViewModel
import com.mediaforge.studio.vm.CompressorViewModel
import com.mediaforge.studio.vm.CopywriterViewModel
import com.mediaforge.studio.vm.EditPdfViewModel
import com.mediaforge.studio.vm.ImagesToPdfViewModel
import com.mediaforge.studio.vm.MergePdfViewModel
import com.mediaforge.studio.vm.MockupViewModel
import com.mediaforge.studio.vm.PageNumbersViewModel
import com.mediaforge.studio.vm.PdfAiViewModel
import com.mediaforge.studio.vm.PdfToDocxViewModel
import com.mediaforge.studio.vm.PdfToImagesViewModel
import com.mediaforge.studio.vm.ProtectPdfViewModel
import com.mediaforge.studio.vm.SettingsViewModel
import com.mediaforge.studio.vm.SplitPdfViewModel
import com.mediaforge.studio.vm.TextToPdfViewModel
import com.mediaforge.studio.vm.UnlockPdfViewModel
import com.mediaforge.studio.vm.WatermarkPdfViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
        if (savedInstanceState == null) handleIncomingIntent(intent)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        intent ?: return
        val uris = ArrayList<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
            }
            else -> return
        }
        if (uris.isEmpty() && intent.clipData != null) {
            val clip = intent.clipData!!
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris += it }
        }
        if (uris.isNotEmpty()) {
            (application as MediaForgeApp).container.receiveSharedUris(uris)
        }
    }
}

@Composable
private fun MediaForgeRoot() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as MediaForgeApp
    val container = app.container
    val scope = rememberCoroutineScope()

    val rootHazeState = rememberHazeState()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: TopTab.MEDIA_LAB.route

    // Activity-scoped ViewModels so incoming Android Share intents can populate any tool directly.
    val settingsVm: SettingsViewModel = viewModel()
    val compressorVm: CompressorViewModel = viewModel()
    val mockupVm: MockupViewModel = viewModel()
    val collageVm: CollageViewModel = viewModel()
    val pdfToDocxVm: PdfToDocxViewModel = viewModel()
    val textToPdfVm: TextToPdfViewModel = viewModel()
    val imagesToPdfVm: ImagesToPdfViewModel = viewModel()
    val pdfToImagesVm: PdfToImagesViewModel = viewModel()
    val mergeVm: MergePdfViewModel = viewModel()
    val splitVm: SplitPdfViewModel = viewModel()
    val compressPdfVm: CompressPdfViewModel = viewModel()
    val editPdfVm: EditPdfViewModel = viewModel()
    val pageNumbersVm: PageNumbersViewModel = viewModel()
    val watermarkVm: WatermarkPdfViewModel = viewModel()
    val protectVm: ProtectPdfViewModel = viewModel()
    val unlockVm: UnlockPdfViewModel = viewModel()
    val copyVm: CopywriterViewModel = viewModel()
    val pdfAiVm: PdfAiViewModel = viewModel()
    val aiVaultVm: AiVaultViewModel = viewModel()

    var requestedMediaTool by remember { mutableStateOf<MediaTool?>(null) }
    var requestedPdfTool by remember { mutableStateOf<PdfTool?>(null) }
    var requestedAiTool by remember { mutableStateOf<AiTool?>(null) }

    val results by container.results.collectAsState()
    val incomingShare by container.incomingShare.collectAsState()
    val shareDispatch by container.shareDispatch.collectAsState()

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

    LaunchedEffect(shareDispatch) {
        val req = shareDispatch ?: return@LaunchedEffect
        when (req.action) {
            ShareAction.COMPRESS_IMAGES -> {
                compressorVm.addImages(req.images)
                requestedMediaTool = MediaTool.COMPRESS
                navigateTo(TopTab.MEDIA_LAB)
            }
            ShareAction.MOCKUP_IMAGE -> {
                req.images.firstOrNull()?.let { mockupVm.setProduct(it) }
                requestedMediaTool = MediaTool.MOCKUP
                navigateTo(TopTab.MEDIA_LAB)
            }
            ShareAction.COLLAGE_IMAGES -> {
                collageVm.setPhotos(req.images)
                requestedMediaTool = MediaTool.COLLAGE
                navigateTo(TopTab.MEDIA_LAB)
            }
            ShareAction.IMAGES_TO_PDF -> {
                imagesToPdfVm.add(req.images)
                requestedPdfTool = PdfTool.IMAGES_TO_PDF
                navigateTo(TopTab.PDF_SUITE)
            }
            ShareAction.AI_COPYWRITER -> {
                req.images.firstOrNull()?.let { copyVm.pick(it) }
                requestedAiTool = AiTool.COPYWRITER
                navigateTo(TopTab.GEMINI_AI)
            }
            ShareAction.AI_PDF_SUMMARY -> {
                req.pdfs.firstOrNull()?.let { pdfAiVm.pick(it) }
                requestedAiTool = AiTool.PDF_CHAT
                navigateTo(TopTab.GEMINI_AI)
            }
            ShareAction.PDF_TO_WORD -> {
                req.pdfs.firstOrNull()?.let { pdfToDocxVm.pick(it) }
                requestedPdfTool = PdfTool.PDF_TO_WORD
                navigateTo(TopTab.PDF_SUITE)
            }
            ShareAction.PDF_TO_IMAGES -> {
                req.pdfs.firstOrNull()?.let { pdfToImagesVm.pick(it) }
                requestedPdfTool = PdfTool.PDF_TO_IMAGES
                navigateTo(TopTab.PDF_SUITE)
            }
            ShareAction.MERGE_PDFS -> {
                mergeVm.add(req.pdfs)
                requestedPdfTool = PdfTool.MERGE
                navigateTo(TopTab.PDF_SUITE)
            }
            ShareAction.SPLIT_PDF -> {
                req.pdfs.firstOrNull()?.let { splitVm.pick(it) }
                requestedPdfTool = PdfTool.SPLIT
                navigateTo(TopTab.PDF_SUITE)
            }
            ShareAction.COMPRESS_PDF -> {
                req.pdfs.firstOrNull()?.let { compressPdfVm.pick(it) }
                requestedPdfTool = PdfTool.COMPRESS
                navigateTo(TopTab.PDF_SUITE)
            }
        }
        container.consumeShareDispatch()
    }

    CompositionLocalProvider(LocalHazeState provides rootHazeState) {
        LiquidBackground {
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Content captured by Haze so floating navbar blurs scrolling content smoothly underneath.
                Box(
                    Modifier
                        .fillMaxSize()
                        .hazeSource(rootHazeState)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = TopTab.MEDIA_LAB.route,
                        enterTransition = {
                            fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 18 }
                        },
                        exitTransition = {
                            fadeOut(tween(180))
                        },
                        popEnterTransition = {
                            fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 18 }
                        },
                        popExitTransition = {
                            fadeOut(tween(180))
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(TopTab.MEDIA_LAB.route) {
                            MediaLabScreen(
                                compressorVm = compressorVm,
                                mockupVm = mockupVm,
                                collageVm = collageVm,
                                requestedTool = requestedMediaTool,
                                onToolHandled = { requestedMediaTool = null }
                            )
                        }
                        composable(TopTab.PDF_SUITE.route) {
                            PdfSuiteScreen(
                                pdfToDocxVm = pdfToDocxVm,
                                textToPdfVm = textToPdfVm,
                                imagesToPdfVm = imagesToPdfVm,
                                pdfToImagesVm = pdfToImagesVm,
                                mergeVm = mergeVm,
                                splitVm = splitVm,
                                compressVm = compressPdfVm,
                                editVm = editPdfVm,
                                pageNumbersVm = pageNumbersVm,
                                watermarkVm = watermarkVm,
                                protectVm = protectVm,
                                unlockVm = unlockVm,
                                requestedTool = requestedPdfTool,
                                onToolHandled = { requestedPdfTool = null }
                            )
                        }
                        composable(TopTab.GEMINI_AI.route) {
                            GeminiAiScreen(
                                settingsVm = settingsVm,
                                onOpenVault = { navigateTo(TopTab.SETTINGS) },
                                copyVm = copyVm,
                                pdfAiVm = pdfAiVm,
                                vaultVm = aiVaultVm,
                                requestedTool = requestedAiTool,
                                onToolHandled = { requestedAiTool = null }
                            )
                        }
                        composable(TopTab.SETTINGS.route) { SettingsScreen(settingsVm) }
                    }
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

            ShareIntakeSheet(
                share = incomingShare,
                onSelect = container::dispatchShareAction,
                onDismiss = container::dismissIncomingShare
            )

            ResultSheet(
                files = results,
                onDismiss = container::dismissResults
            )
        }
    }
}

@Composable
private fun LiquidGlassBottomBar(
    currentRoute: String,
    onSelect: (TopTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = TopTab.entries
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val isDark = Glass.IsDark
    val pillShape = RoundedCornerShape(22.dp)

    val pillFill = Glass.Cyan.copy(alpha = if (isDark) 0.18f else 0.14f)
    val pillBorder = remember(isDark) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (isDark) 0.28f else 0.80f),
                Color.White.copy(alpha = 0.06f)
            )
        )
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .liquidGlassChrome(
                shape = RoundedCornerShape(28.dp),
                hazeState = LocalHazeState.current,
                blurRadius = 24.dp,
                shadowElevation = 16.dp
            )
            .padding(6.dp)
    ) {
        val tabWidth = maxWidth / tabs.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
            label = "bottomPillSlide"
        )

        Box(Modifier.fillMaxWidth().height(52.dp)) {
            // Animated sliding liquid pill behind tab icons/labels
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(pillShape)
                    .background(pillFill)
                    .border(1.dp, pillBorder, pillShape)
            )

            Row(Modifier.fillMaxSize()) {
                tabs.forEach { tab ->
                    val selected = tab.route == currentRoute
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(pillShape)
                            .clickable(interactionSource = interaction, indication = null) { onSelect(tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
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
    }
}
