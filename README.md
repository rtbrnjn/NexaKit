# NexaKit

**NexaKit** *(formerly MediaForge Studio)* is an all-in-one Android media & document utility suite built with **Kotlin**, **Jetpack Compose (Material 3)**, **Coroutines/Flow**, **Room Database**, **PDFBox-Android**, **Android PdfRenderer/PdfDocument**, and **Google Gemini Streaming REST API**.

Designed around a calm, Apple-inspired **Liquid Glass** aesthetic with instant **Dark Mode**, **Light Mode**, and **System Default** theming.

---

## Download & Install APK (`v1.1.0`)

- **Direct Download (`v1.1.0`):** [app-release.apk](https://github.com/rtbrnjn/MediaForge-Studio/releases/download/v1.1.0/app-release.apk)
- **All Releases:** [GitHub Releases](https://github.com/rtbrnjn/MediaForge-Studio/releases)

### Panduan Instalasi Cepat
1. Unduh `app-release.apk` dari halaman Releases di atas.
2. Buka file APK di perangkat Android Anda (Android 8.0 / API 26+). Jika diminta, izinkan *Install from unknown sources*.
3. Pengguna **v1.0.0 (MediaForge Studio)** dapat langsung menimpa/update tanpa perlu uninstall (menggunakan `applicationId` dan keystore yang sama).

---

## What's New in v1.1.0
- **Rebranding ke NexaKit** — Tampilan nama aplikasi, header UI, folder output (`Pictures/NexaKit` & `Documents/NexaKit`), dan bagian About diperbarui ke NexaKit tanpa memutus jalur upgrade dari v1.0.0.
- **Real-Time Gemini AI SSE Streaming** — Menggunakan endpoint `:streamGenerateContent?alt=sse` dengan Kotlin `Flow` sehingga teks jawaban muncul kata demi kata secara langsung.
- **Low-Latency `thinkingConfig` & Payload Ringan** — Menggunakan `gemini-2.5-flash` dengan `thinkingBudget: 0` untuk menghilangkan jeda thinking, auto-resize gambar copywriter (`768px`, JPEG `75%`), serta pemilih rentang halaman & batas karakter cerdas untuk PDF.
- **Android Share Target** — Mendukung `ACTION_SEND` & `ACTION_SEND_MULTIPLE` (`image/*` dan `application/pdf`) langsung dari WhatsApp, Galeri, atau File Manager.
- **AI History Vault (Room Database)** — Menyimpan hasil ringkasan PDF, terjemahan, tanya-jawab, dan product copywriting secara lokal lengkap dengan filter, tombol Copy, dan Hapus.

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
- **Smart PDF Assistant** — Ekstraksi halaman selektif & budget karakter cerdas (`12K` / `30K` / `80K`) + streaming Q&A, ringkasan eksekutif, key takeaways, dan terjemahan ID ↔ EN.
- **Product Photo Copywriter** — Streaming judul produk, 3 hook video pendek, dan caption + hashtag siap salin.
- **AI History Vault** — Tersimpan offline di Room Database (`nexakit_ai_vault.db`).
- **Offline Graceful Mode** — Menampilkan badge status offline saat tanpa koneksi atau API key sambil menjaga Media Lab & PDF Suite 100% aktif.

### 4. Settings & Storage Manager
- Switcher tema instan (**System**, **Light**, **Dark**), Vault API Key terenkripsi (`EncryptedSharedPreferences`) + tombol **Verify Key**, preferensi default ekspor, penghitung total ruang tersimpan, pembersih cache, dan riwayat ekspor.

---

## Build from Source

```bash
./gradlew assembleDebug testDebugUnitTest
./gradlew assembleRelease
```
