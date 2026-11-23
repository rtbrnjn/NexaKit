# DocPix

**DocPix** *(formerly NexaKit / MediaForge Studio)* is an all-in-one Android document scanner, media lab & PDF utility suite built with **Kotlin**, **Jetpack Compose (Material 3)**, **Haze Frosted Glass**, **ML Kit Document Scanner & OCR**, **Room Database**, **PDFBox-Android**, and **Groq / Google Gemini Streaming REST API**.

Designed around a clean, understated **Monochrome Liquid Glass** aesthetic (`#0B0E13` Dark / `#F4F6F9` Light) with translucent frosted surfaces, crisp `1.dp` borders, and seamless **Dark Mode**, **Light Mode**, and **System Default** theming.

---

## Download & Install APK (`v1.4.3`)

- **Direct Download (`v1.4.3`):** [DocPix.apk](https://github.com/rtbrnjn/NexaKit/releases/download/v1.4.3/DocPix.apk)
- **All Releases:** [GitHub Releases](https://github.com/rtbrnjn/NexaKit/releases)

### Panduan Instalasi Cepat
1. Unduh `DocPix.apk` dari halaman Releases di atas.
2. Buka file APK di perangkat Android Anda (Android 8.0 / API 26+). Jika diminta, izinkan *Install from unknown sources*.
3. Pengguna **NexaKit v1.2.0 – v1.4.1** dan **DocPix v1.4.2** yang memakai rilis signed dari GitHub dapat langsung menimpa instalasi lama tanpa menghapus data atau API Key tersimpan (`applicationId` dan keystore release tetap sama).

---

## What's New in v1.4.3 ("PDF Rename Dialog, Swipe Navigation, Multi-Ratio Collage & Modern Device Mockups")
- **Opsi Rename File PDF Sebelum Simpan (PDF Suite):** Seluruh pemroses PDF (Merge, Split, Compress, Images to PDF, Document Scanner ke PDF, Word to PDF, Rotate & Delete, Page Numbers, Watermark, Protect, dan Unlock) kini menampilkan **Liquid Glass Rename Dialog** pre-terisi rapi (mis. `DocPix_Merged_20261123`) beserta penambahan otomatis `.pdf` sebelum disimpan ke `Documents/DocPix`.
- **Navigasi Swipe / Slide Antar Menu (`HorizontalPager`):** Pindah mulus antar **Media Lab**, **PDF Suite**, **AI Studio**, dan **Settings** dengan geser kiri/kanan, sinkronisasi 1:1 pada *sliding pill* indikator dock bawah, serta proteksi **Anti-Bentrok Gesture** agar seret slider kualitas/radius dan strip horizontal tidak memicu perpindahan tab utama secara tidak sengaja.
- **Kolase / Grid Foto Multi-Rasio & Mode Fit/Fill:** Dukungan penuh **Square (`1:1`)**, **Portrait (`4:5` & `9:16`)**, dan **Landscape (`16:9` & `4:3`)** lewat *Liquid Glass Pill Selector*, plus opsi **Fill (Penuh)** vs **Fit (Utuh)** tanpa pemotongan paksa, kontrol *Grid Spacing* dan *Corner Radius*.
- **Peningkatan Device Mockup Studio:** Empat pilihan frame realistis (**Smartphone Punch-Hole**, **Frameless Glass**, **Browser Window**, dan **Tablet**), latar belakang polos estetik (**Obsidian Dark**, **Pure White**, **Soft Pastel Pink `#FFD1DC`**, **Slate Gray**, serta **Blurred Photo** yang mengambil warna dominan foto secara otomatis), pantulan kaca tipis, slider padding kanvas, dan rasio ekspor `1:1`, `4:5`, `9:16`, serta `16:9`.

## Highlights in v1.4.2 ("DocPix Rebrand, Document Scanner, Auto-Update & Monochrome Liquid Glass")
- **Document Scanner Offline (ML Kit Document Scanner API):** Tambahan fitur **Scan Document** di **PDF Suite** (beserta pintasan langsung di **Media Lab**) dengan deteksi tepi kertas otomatis, auto-crop perspektif, penghapus bayangan, filter dokumen tajam, dukungan multi-halaman & impor galeri, serta ekspor langsung sebagai **PDF (`Documents/DocPix`)** atau **Gambar JPG (`Pictures/DocPix`)**.
- **Complete Rebrand to DocPix & `DocPix.apk`:** Rebranding menyeluruh nama aplikasi, judul header layar, direktori penyimpanan (`Documents/DocPix` & `Pictures/DocPix`), ikon aplikasi resmi `logo_docpix.png` (adaptif + mipmap penuh di `#0E1116`), serta aset rilis **`DocPix.apk`**.
- **In-App Auto-Update Checker (`UpdateChecker`):** Pengecekan versi otomatis saat aplikasi dibuka (plus tombol manual **"Cek Pembaruan"** di Settings) melalui GitHub Releases API (`rtbrnjn/DocPix` dengan fallback otomatis ke `rtbrnjn/NexaKit`), lengkap dengan **Liquid Glass Update Dialog** berisi catatan rilis dan unduh langsung `DocPix.apk`.
- **Monochrome Clean Liquid Glass UI:** Penyempurnaan UI/UX dengan latar belakang solid tenang (`#0B0E13` Dark / `#F4F6F9` Light), ambient mist minimal tanpa gradasi mencolok, serta kartu, dialog, segmented bar, dan floating bottom bar kaca transparan bersih.

## Previous Highlights (v1.4.1)
- **Clean Upload-First Media Lab:** Layar awal Media Lab fokus menampilkan area unggah foto bersih + pintasan scan dokumen tanpa menu bertumpuk — opsi Compressor, Mockup, dan Collage langsung muncul setelah foto dipilih.
- **Dagger Hilt Dependency Injection & Modular Architecture:** Seluruh ViewModel, DataStore, Room DAO, client AI (`AiRouter`), dan koordinasi Share Target diinjeksi via Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`).
- **Optimized On-Device ML Kit OCR:** Re-use `TextRecognizer` dan buffer bitmap adaptif (`1120px`, batas tinggi `2000px`).
- **Bug Report & Suggestion Menu:** Kartu **"Laporkan Bug atau Kirim Saran"** di Settings mendukung format laporan Markdown otomatis ke GitHub Issues atau salin cepat.
- **Automated Compose UI Tests & Unit Tests:** Suite Compose UI Test dan unit test JUnit terotomasi.

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
- **Modern Device Mockup Framer** — Pilihan frame *Smartphone (Punch-Hole)*, *Frameless Glass*, *Browser Window*, dan *Tablet* di kanvas `1:1`, `4:5`, `9:16`, atau `16:9` dengan backdrop *Blurred Photo*, warna solid bersih (*Obsidian Dark*, *Pure White*, *Soft Pastel Pink `#FFD1DC`*, *Slate Gray*), pantulan kaca opsional, shadow, dan watermark.
- **Clean Grid Collage Maker** — Gabungkan 2–4 foto dalam rasio `1:1`, `4:5`, `9:16`, `16:9`, atau `4:3` dengan mode *Fill / Fit (Utuh)*, pengaturan spasi & radius ke `Pictures/DocPix`.

### 2. PDF24-Style Pocket Suite (100% Offline · 13 Tools)
1. **Scan Document** — Pemindai dokumen kamera berbasis ML Kit dengan auto-crop, hapus bayangan, dan ekspor langsung ke **PDF (`Documents/DocPix`)** atau **Gambar JPG (`Pictures/DocPix`)**.
2. **PDF to Word (`.docx`)** — Ekstrak teks dengan `PDFTextStripper` dan kemas menjadi `.docx` standar OOXML via `ZipOutputStream`.
3. **Word (`.docx`) & Text to PDF** — Parsing `word/document.xml` via `ZipInputStream` + `XmlPullParser` (atau Markdown/teks) menjadi PDF A4 via `PdfDocument`.
4. **Images to PDF** — Susun banyak foto menjadi satu dokumen PDF (`A4` / `Fit to image`).
5. **PDF to Images** — Render seluruh atau sebagian halaman PDF menjadi JPG/PNG (`100–300 DPI`).
6. **Merge PDFs** — Gabungkan dan atur ulang urutan beberapa file PDF.
7. **Split / Extract Pages** — Pilih halaman lewat thumbnail visual atau rentang (`1-3, 6`).
8. **Compress PDF** — Perkecil ukuran PDF scan/gambar dengan preset `Low`, `Balanced`, atau `High`.
9. **Rotate & Delete Pages** — Putar (`90°`/`180°`/`270°`) atau hapus halaman tertentu.
10. **Add Page Numbers** — Stempel nomor halaman (`Page 1 of N`, `1 / N`, `1`) yang sadar rotasi halaman.
11. **Add Text Watermark** — Watermark teks diagonal transparan dengan pengaturan opasitas & warna.
12. **Protect / Encrypt PDF** — Enkripsi kata sandi 128-bit (`StandardProtectionPolicy`) beserta izin cetak/salin.
13. **Unlock / Decrypt PDF** — Hapus proteksi kata sandi pada PDF milik pengguna.

### 3. Gemini AI Copilot & AI History Vault
- **Smart PDF Assistant** — Ekstraksi halaman selektif, **ML Kit Local OCR** untuk scanned PDF + opsi **Vision Mode**, budget karakter (`12K` / `30K` / `80K`), dan streaming SSE real-time.
- **Product Photo Copywriter** — Auto-kompresi (`768px` / `75%` JPEG) + streaming judul produk, 3 hook video pendek, dan caption + hashtag siap salin.
- **AI History Vault** — Tersimpan offline di Room Database (`nexakit_ai_vault.db`).
- **Android Share Target** — Kirim gambar/PDF langsung dari WhatsApp, Galeri, atau File Manager ke DocPix.

---

## Build from Source

```bash
./gradlew assembleDebug testDebugUnitTest
./gradlew assembleRelease
```
