package com.mediaforge.studio.vm

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.mediaforge.studio.AppCoordinator
import com.mediaforge.studio.core.BitmapUtils
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.OutputKind
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.core.Storage
import com.mediaforge.studio.data.SettingsStore
import com.mediaforge.studio.media.CanvasRatio
import com.mediaforge.studio.media.CollageAspect
import com.mediaforge.studio.media.CollageParams
import com.mediaforge.studio.media.CollagePhotoMode
import com.mediaforge.studio.media.CollageRatioCategory
import com.mediaforge.studio.media.CollageRenderer
import com.mediaforge.studio.media.CollageStyle
import com.mediaforge.studio.media.CompressOptions
import com.mediaforge.studio.media.DeviceFrameStyle
import com.mediaforge.studio.media.ImageCompressor
import com.mediaforge.studio.media.MaxDimension
import com.mediaforge.studio.media.MockBackground
import com.mediaforge.studio.media.MockupParams
import com.mediaforge.studio.media.MockupRenderer
import com.mediaforge.studio.media.OutputFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

// =============================================================== Compressor

data class CompressItem(
    val uri: Uri,
    val name: String,
    val originalBytes: Long,
    val hasGps: Boolean,
    val estimatedBytes: Long? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class CompressorViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator,
    private val settings: SettingsStore,
    private val compressor: ImageCompressor
) : ToolViewModel(app, coordinator) {
    private val sizeCache = HashMap<Pair<Uri, CompressOptions>, Long>()

    private val _items = MutableStateFlow<List<CompressItem>>(emptyList())
    val items: StateFlow<List<CompressItem>> = _items

    private val _options = MutableStateFlow(CompressOptions())
    val options: StateFlow<CompressOptions> = _options

    private val _estimating = MutableStateFlow(false)
    val estimating: StateFlow<Boolean> = _estimating

    init {
        viewModelScope.launch {
            val defQuality = settings.defaultQuality.first()
            val defStrip = settings.autoStripExif.first()
            _options.update { it.copy(quality = defQuality, stripExif = defStrip) }
        }
        viewModelScope.launch {
            combine(_items.map { list -> list.map { it.uri } }.distinctUntilChanged(), _options) { uris, o -> uris to o }
                .debounce(300)
                .collectLatest { (uris, opts) -> estimate(uris, opts) }
        }
    }

    private suspend fun estimate(uris: List<Uri>, opts: CompressOptions) = withContext(Dispatchers.Default) {
        if (uris.isEmpty()) return@withContext
        _estimating.value = true
        try {
            for (uri in uris) {
                ensureActive()
                val size = sizeCache[uri to opts] ?: runCatching { compressor.estimate(uri, opts) }.getOrDefault(0L)
                    .also { sizeCache[uri to opts] = it }
                _items.update { list -> list.map { if (it.uri == uri) it.copy(estimatedBytes = size) else it } }
            }
        } finally {
            _estimating.value = false
        }
    }

    fun addImages(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = _items.value.map { it.uri }.toSet()
            val fresh = uris.filter { it !in existing }.map { uri ->
                CompressItem(
                    uri = uri,
                    name = FileUtils.queryName(ctx, uri),
                    originalBytes = FileUtils.querySize(ctx, uri),
                    hasGps = BitmapUtils.hasGps(ctx, uri)
                )
            }
            _items.update { it + fresh }
        }
    }

    fun remove(uri: Uri) = _items.update { list -> list.filterNot { it.uri == uri } }
    fun clear() { _items.value = emptyList() }
    fun setQuality(q: Int) = _options.update { it.copy(quality = q.coerceIn(10, 95)) }
    fun setMaxDimension(d: MaxDimension) = _options.update { it.copy(maxDimension = d) }
    fun setFormat(f: OutputFormat) = _options.update { it.copy(format = f) }
    fun setStripExif(v: Boolean) = _options.update { it.copy(stripExif = v) }

    fun compressAll() {
        val list = _items.value
        if (list.isEmpty()) return
        val opts = _options.value
        runTask("Compressing images…", write = true) {
            val tmpDir = File(ctx.cacheDir, "compress_out").apply { mkdirs() }
            val saved = ArrayList<SavedFile>()
            var before = 0L
            var after = 0L
            var gpsRemoved = 0
            var failed = 0
            list.forEachIndexed { i, item ->
                setProgress("Compressing ${i + 1} / ${list.size}", i.toFloat() / list.size)
                val tmp = File(tmpDir, "${System.nanoTime()}.${opts.format.ext}")
                try {
                    val result = compressor.compress(item.uri, opts, tmp)
                    val name = "${FileUtils.baseName(item.name)}_docpix.${opts.format.ext}"
                    saved += Storage.saveFile(ctx, OutputKind.IMAGE, null, name, opts.format.mime, "Image Compressor", tmp)
                    before += item.originalBytes
                    after += result.bytes
                    if (opts.stripExif && item.hasGps) gpsRemoved++
                } catch (_: Exception) {
                    failed++
                } finally {
                    tmp.delete()
                }
            }
            if (saved.isNotEmpty()) {
                val delta = (before - after).coerceAtLeast(0L)
                settings.addBytesSaved(delta)
                publish(saved)
                val metaMsg = when {
                    !opts.stripExif -> " EXIF retained."
                    gpsRemoved > 0 -> " GPS location removed from $gpsRemoved photo(s)."
                    else -> " EXIF metadata stripped."
                }
                showInfo("Saved ${saved.size} image(s): ${FileUtils.formatBytes(before)} → ${FileUtils.formatBytes(after)}.$metaMsg" +
                    if (failed > 0) " $failed failed." else "")
            } else showError("No image could be compressed.")
        }
    }
}

// =============================================================== Mockup

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class MockupViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator
) : ToolViewModel(app, coordinator) {
    private val _uri = MutableStateFlow<Uri?>(null)
    val uri: StateFlow<Uri?> = _uri

    private val _product = MutableStateFlow<Bitmap?>(null)

    private val _params = MutableStateFlow(MockupParams())
    val params: StateFlow<MockupParams> = _params

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview

    init {
        viewModelScope.launch {
            combine(_product.filterNotNull(), _params) { b, p -> b to p }
                .debounce(40)
                .mapLatest { (b, p) -> MockupRenderer.render(b, p, 720) }
                .flowOn(Dispatchers.Default)
                .collect { _preview.value = it }
        }
    }

    fun setProduct(uri: Uri) {
        _uri.value = uri
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = BitmapUtils.decode(ctx, uri, 1600)
            if (bmp == null) showError("Could not open that image.") else _product.value = bmp
        }
    }

    fun clear() {
        _uri.value = null
        _product.value = null
        _preview.value = null
    }

    fun update(block: (MockupParams) -> MockupParams) = _params.update(block)
    fun setRatio(r: CanvasRatio) = update { it.copy(ratio = r) }
    fun setFrameStyle(f: DeviceFrameStyle) = update { it.copy(frameStyle = f) }
    fun setBackground(b: MockBackground) = update { it.copy(background = b) }

    fun export() {
        val src = _uri.value ?: return
        val p = _params.value
        runTask("Rendering high-res mockup…", write = true) {
            val product = BitmapUtils.decode(ctx, src, 3000) ?: error("Could not open that image.")
            val out = MockupRenderer.render(product, p, 1440)
            val name = "DocPix_Mockup_${p.ratio.w}x${p.ratio.h}_${FileUtils.timestamp()}.png"
            val saved = Storage.save(ctx, OutputKind.IMAGE, null, name, "image/png", "Mockup Studio") {
                out.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            out.recycle()
            publish(listOf(saved))
        }
    }
}

// =============================================================== Collage

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollageViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator
) : ToolViewModel(app, coordinator) {
    private val _photos = MutableStateFlow<List<Uri>>(emptyList())
    val photos: StateFlow<List<Uri>> = _photos

    private val _params = MutableStateFlow(CollageParams())
    val params: StateFlow<CollageParams> = _params

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview

    private val smallCache = HashMap<Uri, Bitmap>()

    init {
        viewModelScope.launch {
            combine(_photos, _params) { u, p -> u to p }
                .debounce(40)
                .mapLatest { (uris, p) ->
                    if (uris.size < 2) null
                    else {
                        val bitmaps = uris.mapNotNull { u ->
                            synchronized(smallCache) { smallCache[u] }
                                ?: BitmapUtils.decode(ctx, u, 900)?.also { synchronized(smallCache) { smallCache[u] = it } }
                        }
                        if (bitmaps.size < 2) null else CollageRenderer.render(bitmaps, p, 720)
                    }
                }
                .flowOn(Dispatchers.Default)
                .collect { _preview.value = it }
        }
    }

    fun setPhotos(uris: List<Uri>) {
        _photos.value = uris.distinct().take(4)
        if (uris.size > 4) showInfo("A collage holds up to 4 photos – the first 4 were used.")
    }

    fun clear() {
        _photos.value = emptyList()
        _preview.value = null
    }

    fun remove(i: Int) = _photos.update { l -> l.filterIndexed { idx, _ -> idx != i } }
    fun move(i: Int, delta: Int) = _photos.update { l ->
        val j = i + delta
        if (j !in l.indices) l else l.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }

    fun update(block: (CollageParams) -> CollageParams) = _params.update(block)
    fun setStyle(s: CollageStyle) = update { it.copy(style = s) }
    fun setAspect(a: CollageAspect) = update { it.copy(aspect = a) }
    fun setPhotoMode(m: CollagePhotoMode) = update { it.copy(photoMode = m) }
    fun setRatioCategory(cat: CollageRatioCategory) {
        val current = _params.value.aspect
        if (current.category == cat) return
        val fallback = CollageAspect.entries.first { it.category == cat }
        setAspect(fallback)
    }

    fun export() {
        val uris = _photos.value
        if (uris.size < 2) { showError("Pick at least 2 photos."); return }
        val p = _params.value
        runTask("Rendering high-res collage…", write = true) {
            val bitmaps = uris.map { BitmapUtils.decode(ctx, it, 2400) ?: error("Could not open a selected photo.") }
            val out = CollageRenderer.render(bitmaps, p, 2160)
            bitmaps.forEach { it.recycle() }
            val name = "DocPix_Collage_${p.aspect.w}x${p.aspect.h}_${FileUtils.timestamp()}.png"
            val saved = Storage.save(ctx, OutputKind.IMAGE, null, name, "image/png", "Collage Maker") {
                out.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            out.recycle()
            publish(listOf(saved))
        }
    }
}
