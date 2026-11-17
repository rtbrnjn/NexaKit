# NexaKit

**NexaKit** *(formerly MediaForge Studio)* is an all-in-one Android media & document utility suite built with **Kotlin**, **Jetpack Compose (Material 3)**, **Haze Frosted Glass**, **ML Kit On-Device OCR**, **Room Database**, **PDFBox-Android**, and **Google Gemini Streaming REST API**.

Designed around an Apple-inspired **True Liquid Glass** aesthetic with real-time frosted blur, spring-animated sliding pill indicators, and instant **Dark Mode**, **Light Mode**, and **System Default** theming.

---

## Download & Install APK (`v1.2.0`)

- **Direct Download (`v1.2.0`):** [app-release.apk](https://github.com/rtbrnjn/MediaForge-Studio/releases/download/v1.2.0/app-release.apk)
- **All Releases:** [GitHub Releases](https://github.com/rtbrnjn/MediaForge-Studio/releases)

### Panduan Instalasi Cepat
1. Unduh `app-release.apk` dari halaman Releases di atas.
2. Buka file APK di perangkat Android Anda (Android 8.0 / API 26+). Jika diminta, izinkan *Install from unknown sources*.
3. Pengguna **v1.0.0 / v1.1.0** yang memakai rilis signed dari GitHub dapat langsung memperbarui tanpa uninstall (`applicationId` dan keystore sama).

---

## What's New in v1.2.0
- **True Liquid Glass Frosted UI** — Menggunakan `dev.chrisbanes.haze` (`haze` + `haze-blur`) pada Bottom Navigation Bar & Top Segmented Tabs untuk blur konten secara real-time (Android 12+), bingkai kaca `28.dp` dengan refleksi gradasi tipis, dan bayangan lembut.
- **Animated Sliding Liquid Pill & Smooth Transitions** — Indikator tab meluncur mulus dengan spring physics (`dampingRatio = 0.78f`, `stiffness = 380f`), transisi halus `AnimatedContent` & `NavHost`, serta padding bawah aman (`112–114.dp`) di semua halaman gulir.
- **Turbo Gemini AI & Local ML Kit OCR** — Scanned PDF otomatis diproses secara lokal menggunakan **ML Kit Text Recognition** (0 gambar diunggah kecuali **Vision Mode** diaktifkan), disertai `:streamGenerateContent?alt=sse`, `thinkingBudget: 0`, `maxOutputTokens: 1024`, dan status real-time.
- **Compact Settings Model Dropdown** — Daftar chip model yang panjang kini diringkas menjadi **Liquid Glass Dropdown Selector** berisi model rekomendasi (`gemini-2.5-flash`, `gemini-2.5-flash-lite`, `gemini-2.5-pro`) serta opsi input custom model.

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
