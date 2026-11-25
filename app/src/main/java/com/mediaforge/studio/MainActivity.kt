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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaforge.studio.core.Permissions
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.data.SettingsStore
import com.mediaforge.studio.data.ThemeMode
import com.mediaforge.studio.ui.components.LiquidGlassBackdrop
import com.mediaforge.studio.ui.components.LocalBackdropHazeState
import com.mediaforge.studio.ui.components.LocalHazeState
import com.mediaforge.studio.ui.components.LocalPagerGestureCoordinator
import com.mediaforge.studio.ui.components.PagerGestureCoordinator
import com.mediaforge.studio.ui.components.diagonalGlassBorder
import com.mediaforge.studio.ui.components.liquidGlassChrome
import com.mediaforge.studio.ui.components.supportsNativeLiquidGlassBlur
import com.mediaforge.studio.ui.screens.AiTool
import com.mediaforge.studio.ui.screens.AppUpdateDialog
import com.mediaforge.studio.ui.screens.GeminiAiScreen
import com.mediaforge.studio.ui.screens.MediaLabScreen
import com.mediaforge.studio.ui.screens.MediaTool
import com.mediaforge.studio.ui.screens.PdfRenameDialog
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
import com.mediaforge.studio.vm.DocumentScannerViewModel
import com.mediaforge.studio.vm.EditPdfViewModel
import com.mediaforge.studio.vm.ExtractPdfTextViewModel
import com.mediaforge.studio.vm.ImageCreationViewModel
import com.mediaforge.studio.vm.ImagesToPdfViewModel
import com.mediaforge.studio.vm.MergePdfViewModel
import com.mediaforge.studio.vm.MockupViewModel
import com.mediaforge.studio.vm.PageNumbersViewModel
import com.mediaforge.studio.vm.PdfAiViewModel
import com.mediaforge.studio.vm.PdfToDocxViewModel
import com.mediaforge.studio.vm.PdfToImagesViewModel
import com.mediaforge.studio.vm.PromptGeneratorViewModel
import com.mediaforge.studio.vm.ProtectPdfViewModel
import com.mediaforge.studio.vm.SettingsViewModel
import com.mediaforge.studio.vm.SplitPdfViewModel
import com.mediaforge.studio.vm.TextToPdfViewModel
import com.mediaforge.studio.vm.UnlockPdfViewModel
import com.mediaforge.studio.vm.WatermarkPdfViewModel
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class TopTab(val label: String, val icon: ImageVector) {
    MEDIA_LAB("Media Lab", Icons.Rounded.PhotoLibrary),
    PDF_SUITE("PDF Suite", Icons.Rounded.PictureAsPdf),
    GEMINI_AI("AI Studio", Icons.Rounded.AutoAwesome),
    SETTINGS("Settings", Icons.Rounded.Tune)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var coordinator: AppCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIncomingIntent(intent)
        setContent {
            val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
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
                MediaForgeRoot(settings = settings, coordinator = coordinator)
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
            coordinator.receiveSharedUris(uris)
        }
    }
}

@Composable
private fun MediaForgeRoot(
    settings: SettingsStore,
    coordinator: AppCoordinator
) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val chromeHazeState = if (supportsNativeLiquidGlassBlur()) rememberHazeState() else null
    val pagerGestureCoordinator = remember { PagerGestureCoordinator() }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { TopTab.entries.size })
    val currentTabPage by remember(pagerState) { derivedStateOf { pagerState.currentPage } }

    // Activity-scoped ViewModels so incoming Android Share intents can populate any tool directly.
    val settingsVm: SettingsViewModel = viewModel()
    val compressorVm: CompressorViewModel = viewModel()
    val mockupVm: MockupViewModel = viewModel()
    val collageVm: CollageViewModel = viewModel()
    val scannerVm: DocumentScannerViewModel = viewModel()
    val extractTextVm: ExtractPdfTextViewModel = viewModel()
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
    val promptVm: PromptGeneratorViewModel = viewModel()
    val imageVm: ImageCreationViewModel = viewModel()
    val aiVaultVm: AiVaultViewModel = viewModel()

    var requestedMediaTool by remember { mutableStateOf<MediaTool?>(null) }
    var requestedPdfTool by remember { mutableStateOf<PdfTool?>(null) }
    var requestedAiTool by remember { mutableStateOf<AiTool?>(null) }
    var recentFiles by remember { mutableStateOf<List<SavedFile>>(emptyList()) }

    val results by coordinator.results.collectAsState()
    val incomingShare by coordinator.incomingShare.collectAsState()
    val shareDispatch by coordinator.shareDispatch.collectAsState()
    val pendingPdfSave by coordinator.pendingPdfSave.collectAsState()
    val savingPendingPdf by coordinator.savingPendingPdf.collectAsState()
    val completionHapticTick by coordinator.completionHapticTick.collectAsState()
    val showUpdateDialog by settingsVm.showUpdateDialog.collectAsState()
    val availableUpdate by settingsVm.availableUpdate.collectAsState()
    val updateDownloadState by settingsVm.updateDownloadState.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { settings.setPermissionAsked(true) }
    }

    // Defer disk history scan, permission query, and GitHub update check until after frame 0 renders smoothly.
    LaunchedEffect(Unit) {
        delay(800L)
        launch {
            settingsVm.history.collect { recentFiles = it }
        }
        val alreadyAsked = withContext(Dispatchers.IO) { settings.permissionAsked.first() }
        if (!alreadyAsked && !Permissions.granted(ctx)) {
            permLauncher.launch(Permissions.required())
        }
        settingsVm.checkForUpdates(userInitiated = false)
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
    }

    LaunchedEffect(completionHapticTick) {
        if (completionHapticTick > 0L) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun navigateTo(tab: TopTab) {
        scope.launch {
            pagerState.animateScrollToPage(
                page = tab.ordinal,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f)
            )
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
        coordinator.consumeShareDispatch()
    }

    CompositionLocalProvider(
        LocalBackdropHazeState provides null,
        LocalHazeState provides chromeHazeState,
        LocalPagerGestureCoordinator provides pagerGestureCoordinator
    ) {
        LiquidGlassBackdrop(
            backdropHaze = null,
            chromeHaze = chromeHazeState
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 0,
                    userScrollEnabled = !pagerGestureCoordinator.isLocked,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (TopTab.entries[page]) {
                        TopTab.MEDIA_LAB -> MediaLabScreen(
                            compressorVm = compressorVm,
                            mockupVm = mockupVm,
                            collageVm = collageVm,
                            requestedTool = requestedMediaTool,
                            onToolHandled = { requestedMediaTool = null },
                            onScanDocument = {
                                requestedPdfTool = PdfTool.SCAN_DOCUMENT
                                navigateTo(TopTab.PDF_SUITE)
                            },
                            isTabActive = currentTabPage == TopTab.MEDIA_LAB.ordinal,
                            recentFiles = recentFiles,
                            onRemoveRecent = { settingsVm.forget(it, deleteFile = false) }
                        )
                        TopTab.PDF_SUITE -> PdfSuiteScreen(
                            scannerVm = scannerVm,
                            extractTextVm = extractTextVm,
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
                            onToolHandled = { requestedPdfTool = null },
                            isTabActive = currentTabPage == TopTab.PDF_SUITE.ordinal,
                            recentFiles = recentFiles,
                            onRemoveRecent = { settingsVm.forget(it, deleteFile = false) },
                            onSendExtractedToAi = { src, text, usedOcr ->
                                pdfAiVm.loadExtractedDocument(
                                    src = src,
                                    text = text,
                                    usedOcr = usedOcr,
                                    range = extractTextVm.pageRange.value,
                                    autoSummarize = true
                                )
                                requestedAiTool = AiTool.PDF_CHAT
                                navigateTo(TopTab.GEMINI_AI)
                            }
                        )
                        TopTab.GEMINI_AI -> GeminiAiScreen(
                            settingsVm = settingsVm,
                            onOpenVault = { navigateTo(TopTab.SETTINGS) },
                            copyVm = copyVm,
                            pdfAiVm = pdfAiVm,
                            promptVm = promptVm,
                            imageVm = imageVm,
                            vaultVm = aiVaultVm,
                            requestedTool = requestedAiTool,
                            onToolHandled = { requestedAiTool = null }
                        )
                        TopTab.SETTINGS -> SettingsScreen(settingsVm)
                    }
                }

                val density = LocalDensity.current
                val imeVisible = WindowInsets.ime.getBottom(density) > 0
                AnimatedVisibility(
                    visible = !imeVisible,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    LiquidGlassBottomBar(
                        pagerState = pagerState,
                        onSelect = ::navigateTo
                    )
                }
            }

            ShareIntakeSheet(
                share = incomingShare,
                onSelect = coordinator::dispatchShareAction,
                onDismiss = coordinator::dismissIncomingShare
            )

            PdfRenameDialog(
                pending = pendingPdfSave,
                saving = savingPendingPdf,
                onConfirm = coordinator::commitPendingPdfSave,
                onDismiss = coordinator::cancelPendingPdfSave
            )

            ResultSheet(
                files = results,
                onDismiss = coordinator::dismissResults
            )

            if (showUpdateDialog) {
                AppUpdateDialog(
                    update = availableUpdate,
                    downloadState = updateDownloadState,
                    onStartDownload = settingsVm::startUpdateDownload,
                    onCancelDownload = settingsVm::cancelUpdateDownload,
                    onInstallPermissionResult = settingsVm::onInstallPermissionReturned,
                    onDismiss = settingsVm::dismissUpdateDialog
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassBottomBar(
    pagerState: PagerState,
    onSelect: (TopTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = TopTab.entries
    val isDark = Glass.IsDark
    val pillShape = RoundedCornerShape(22.dp)
    val density = LocalDensity.current
    val highlightedIndex by remember(pagerState) {
        derivedStateOf {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .roundToInt()
                .coerceIn(0, tabs.lastIndex)
        }
    }

    val pillFill = remember(isDark) {
        if (isDark) {
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.06f))
            )
        } else {
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.90f), Color.White.copy(alpha = 0.62f))
            )
        }
    }
    val pillBorder = diagonalGlassBorder(isDark = isDark, strong = true)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .liquidGlassChrome(
                shape = RoundedCornerShape(28.dp),
                hazeState = LocalHazeState.current,
                blurRadius = 14.dp,
                shadowElevation = 14.dp,
                strongBorder = true
            )
            .padding(6.dp)
    ) {
        val tabWidth = maxWidth / tabs.size
        val tabWidthPx = with(density) { tabWidth.toPx() }
        val liftPx = with(density) { 1.5.dp.toPx() }

        Box(Modifier.fillMaxWidth().height(56.dp)) {
            // Liquid Morphing Pill: glides 1:1 with HorizontalPager offset and stretches organically mid-flight (0 recompositions/frame)
            Box(
                Modifier
                    .graphicsLayer {
                        val rawProgress = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                            .coerceIn(0f, tabs.lastIndex.toFloat())
                        val distFromRest = abs(rawProgress - rawProgress.roundToInt())
                        val morph = sin(distFromRest * Math.PI.toFloat())
                        translationX = tabWidthPx * rawProgress
                        scaleX = 1f + 0.16f * morph
                        scaleY = 1f - 0.08f * morph
                    }
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(pillShape)
                    .background(pillFill)
                    .border(1.dp, pillBorder, pillShape)
            )

            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == highlightedIndex
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(pillShape)
                            .clickable(interactionSource = interaction, indication = null) { onSelect(tab) }
                            .graphicsLayer {
                                val rawProgress = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                    .coerceIn(0f, tabs.lastIndex.toFloat())
                                val proximity = (1f - abs(rawProgress - index)).coerceIn(0f, 1f)
                                val s = 0.92f + 0.12f * proximity
                                scaleX = s
                                scaleY = s
                                alpha = 0.62f + 0.38f * proximity
                                translationY = -liftPx * proximity
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) (if (isDark) Glass.TextPrimary else Glass.Cyan) else Glass.TextSecondary,
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
