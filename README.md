# NexaKit

**NexaKit** *(formerly MediaForge Studio)* is an all-in-one Android media & document utility suite built with **Kotlin**, **Jetpack Compose (Material 3)**, **Haze Frosted Glass**, **ML Kit On-Device OCR**, **Room Database**, **PDFBox-Android**, and **Google Gemini Streaming REST API**.

Designed around an Apple-inspired **True Liquid Glass** aesthetic with real-time frosted blur, spring-animated sliding pill indicators, and instant **Dark Mode**, **Light Mode**, and **System Default** theming.

---

## Download & Install APK (`v1.4.1`)

- **Direct Download (`v1.4.1`):** [app-release.apk](https://github.com/rtbrnjn/MediaForge-Studio/releases/download/v1.4.1/app-release.apk)
- **All Releases:** [GitHub Releases](https://github.com/rtbrnjn/MediaForge-Studio/releases)

### Panduan Instalasi Cepat
1. Unduh `app-release.apk` dari halaman Releases di atas.
2. Buka file APK di perangkat Android Anda (Android 8.0 / API 26+). Jika diminta, izinkan *Install from unknown sources*.
3. Pengguna **v1.2.0+** yang memakai rilis signed dari GitHub dapat langsung memperbarui tanpa uninstall (`applicationId` dan keystore sama).

---

## What's New in v1.4.1 ("Architecture Refactor, Hilt DI & Upload-First Media Flow")
- **Clean Upload-First Media Lab:** Layar awal Media Lab kini fokus menampilkan area unggah foto bersih tanpa menu bertumpuk — opsi Compressor, Mockup, dan Collage langsung muncul setelah foto dipilih.
- **Dagger Hilt Dependency Injection & Modular Architecture:** Seluruh ViewModel, DataStore, Room DAO, client AI (`AiRouter`), dan koordinasi Share Target kini diinjeksi via Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`) serta file UI/ViewModel dipecah menjadi modul terfokus.
- **Optimized On-Device ML Kit OCR:** Re-use `TextRecognizer` dan buffer bitmap adaptif (`1120px`, batas tinggi `2000px`) untuk menekan konsumsi memori saat memindai PDF, lengkap dengan penghentian dini akurat saat batas karakter (`12K` / `30K` / `80K`) tercapai.
- **Bug Report & Suggestion Menu:** Kartu **"Laporkan Bug atau Kirim Saran"** di Settings mendukung format laporan Markdown otomatis dengan info versi perangkat (tanpa API key) siap dikirim ke GitHub Issues atau disalin.
- **Automated Compose UI Tests & Unit Tests:** Penambahan Compose UI Test (`NexaKitComposeUiTest` & `NexaKitInstrumentedUiTest`) bersama suite unit test JUnit.

## Highlights in v1.4.0 ("Dual-Engine AI Studio & True Liquid Glass")
- **Dual-Engine AI Studio (Groq LPU Ultra-Fast + Google Gemini):**
  - Mendukung penuh **Groq REST API** (`https://api.groq.com/openai/v1/chat/completions`) dengan SSE streaming real-time:
    - **PDF Summarizer & Chat:** `llama-3.3-70b-versatile` (respons kilat & akurat).
    - **AI Product Copywriter & Vision:** `meta-llama/llama-4-scout-17b-16e-instruct` (otomatis fallback ke `llama-3.2-11b-vision-preview`) dengan kompresi gambar `768px` / `75%` JPEG.
  - **Smart Auto-Routing & Auto-Fallback:** Default otomatis memakai **Groq (Ultra-Fast)** saat Groq API Key tersedia, menampilkan badge dinamis (`Groq LPU · Llama 3.3` / `Gemini`), dan otomatis beralih ke mesin cadangan jika terjadi gangguan (`503`/rate-limit).
- **Dual Key Encrypted Vault & Panduan Interaktif API Key di Settings:**
  - Kolom terpisah untuk **Groq API Key (`gsk_...`)** (verifikasi ping ke `/openai/v1/models`) dan **Google Gemini API Key (`AIza...`)** (`gemini-3.8-flash` / `gemini-3.5-flash-lite`), tersimpan terenkripsi via `EncryptedSharedPreferences`.
  - Kartu **"Cara Mendapatkan API Key Gratis?"** yang membuka panduan langkah Bahasa Indonesia + tombol langsung ke **Groq Console** (`console.groq.com`) dan **Google AI Studio** (`aistudio.google.com/apikey`).
- **Penyempurnaan True Liquid Glass:**
  - `LiquidGlassBackdrop` ambient mesh berpadu kartu transparan (`alpha = 0.06f` Dark / `0.60f` Light), frosted blur nyata, garis refleksi gradasi diagonal `1.dp`, *sliding spring pill*, dan padding aman bawah `120.dp` di semua layar.

## Previous Highlights (v1.3.0)
- **Animated Ambient Mesh Backdrop (`LiquidGlassBackdrop`)** — Pendaran cahaya radial lembut (biru safir, teal zamrud, dan indigo di Dark Mode; biru langit & mint lembut di Light Mode) bergerak perlahan di balik seluruh layar dan membias melalui kartu kaca di atasnya.
- **Layered Translucent Glass Material (`LiquidGlassCard` & `LiquidGlassPill`)** — Kartu kaca berlapis dengan opasitas adaptif (`0.08f` Dark / `0.65f` Light), frosted blur nyata via `Haze`, garis tepi pantulan cahaya gradasi diagonal (`35%` -> `6%`), kilau atas tipis, tata letak lega, serta tombol aksi kapsul kaca.
- **Floating Frosted Top Header, Navbar & Smooth Transitions** — Dock navigasi atas & bawah mengambang dengan efek blur latar nyata, spring sliding pill (`dampingRatio = 0.8f`, `stiffness = 360f`), animasi transisi `fadeIn + scaleIn(0.98f)`, serta padding aman bawah (`118.dp`).
- **Gemini 3.8 Default & Auto-Migration** — Model bawaan diperbarui ke `gemini-3.8-flash` (ditambah `gemini-3.8-flash-lite` dan `gemini-4-hydrogen` + opsi custom model di **Dropdown Selector** Settings), migrasi otomatis dari `gemini-2.5-flash` di DataStore, kompresi foto otomatis (`768px`, `75%` JPEG), dan streaming SSE real-time.
- **Updated Liquid Glass App Icon** — Ikon aplikasi baru beresolusi tinggi dengan prism kaca tembus pandang dan ikon dokumen/media untuk semua densitas launcher Android.

---

## Core Modules

### 1. Media Lab (100% Offline)
- **Batch Image Compressor & EXIF Privacy Cleaner** — Multi-select gambar, atur kualitas (`10%–95%`), resolusi maksimum (`Original`, `1080p`, `720p`), format (`WEBP`, `JPEG`), estimasi ukuran real-time, dan penghapusan tag GPS/EXIF via `ExifInterface`.
- **Minimalist Product Mockup Framer** — Kanvas `1:1`, `4:5`, `9:16` dengan pilihan solid studio & gradient lembut, bezel kaca, radius sudut, bayangan elevasi, dan watermark teks.
- **Clean Grid Collage Maker** — Gabungkan 2–4 foto menjadi kolase PNG beresolusi tinggi ke `Pictures/NexaKit`.

### 2. PDF24-Style Pocket Suite (100% Offline · 12 Tools)
1. **PDF to Word (`.docx`)** — Ekstrak teks dengan `PDFTextStripper` dan kemas menjadi `.docx` standar OOXML via `ZipOutputStream`.
2. **Word (`.docx`) & Text to PDF** — Parsing `word/document.xml` via `ZipInputStream` + `XmlPullParser` (atau Markdown/teks) menjadi PDF A4 via `PdfDocument`.
3. **Images to PDF** — Susun banyak foto menjadi satu dokumen PDF (`A4` / `Fit to image`).
4. **PDF to Images** — Render seluruh atau sebagian halaman PDF menjadi JPG/PNG (`100–300 DPI`).
5. **Merge PDFs** — Gabungkan dan atur ulang urutan beberapa file PDF.
6. **Split / Extract Pages** — Pilih halaman lewat thumbnail visual atau rentang (`1-3, 6`).
7. **Compress PDF** — Perkecil ukuran PDF scan/gambar dengan preset `Low`, `Balanced`, atau `High`.
8. **Rotate & Delete Pages** — Putar (`90°`/`180°`/`270°`) atau hapus halaman tertentu.
9. **Add Page Numbers** — Stempel nomor halaman (`Page 1 of N`, `1 / N`, `1`) yang sadar rotasi halaman.
10. **Add Text Watermark** — Watermark teks diagonal transparan dengan pengaturan opasitas & warna.
11. **Protect / Encrypt PDF** — Enkripsi kata sandi 128-bit (`StandardProtectionPolicy`) beserta izin cetak/salin.
12. **Unlock / Decrypt PDF** — Hapus proteksi kata sandi pada PDF milik pengguna.

### 3. Gemini AI Copilot & AI History Vault
- **Smart PDF Assistant** — Ekstraksi halaman selektif, **ML Kit Local OCR** untuk scanned PDF + opsi **Vision Mode**, budget karakter (`12K` / `30K` / `80K`), dan streaming SSE real-time.
- **Product Photo Copywriter** — Auto-kompresi (`768px` / `75%` JPEG) + streaming judul produk, 3 hook video pendek, dan caption + hashtag siap salin.
- **AI History Vault** — Tersimpan offline di Room Database (`nexakit_ai_vault.db`).
- **Android Share Target** — Kirim gambar/PDF langsung dari WhatsApp, Galeri, atau File Manager ke NexaKit.

---

## Build from Source

```bash
./gradlew assembleDebug testDebugUnitTest
./gradlew assembleRelease
```
