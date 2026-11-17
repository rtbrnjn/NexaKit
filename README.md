# MediaForge Studio

All-in-one Android media & document utility suite built with **Kotlin**, **Jetpack Compose (Material 3)**, **Coroutines/Flow**, **PDFBox-Android**, **Android PdfRenderer/PdfDocument**, and **Google Gemini REST API**.

Designed around a calm, understated **Liquid Glass** aesthetic with seamless **Dark Mode**, **Light Mode**, and **System Default** theming.

---

## Download APK

Pre-built signed Release APKs are published on **[GitHub Releases](https://github.com/rtbrnjn/MediaForge-Studio/releases)**:
- **Direct Download (`v1.0.0`):** [app-release.apk](https://github.com/rtbrnjn/MediaForge-Studio/releases/download/v1.0.0/app-release.apk) (`~8.6 MB`, signed & R8-minified)

---

## Features

### 1. Media Lab (100% Offline)
- **Batch Image Compressor & EXIF Privacy Cleaner** — Multi-select images, adjust quality (`10%–95%`), target max dimension (`Original`, `1080p`, `720p`), and format (`WEBP`, `JPEG`), view real-time before/after file size estimates, and strip sensitive GPS/EXIF metadata via `ExifInterface`.
- **Minimalist Product Mockup Framer** — Frame product photos onto `1:1`, `4:5`, or `9:16` canvases with solid studio colors or soft gradient backdrops, frosted glass bezel toggle, adjustable corner radius, padding, elevation shadow, and custom watermark text.
- **Clean Grid Collage Maker** — Combine 2–4 photos into balanced layouts (`Side by side`, `Stacked`, `Grid`, `Feature`) with customizable spacing and corner radius, exporting 2160px PNGs to `Pictures/MediaForge`.

### 2. PDF24-Style Pocket Suite (100% Offline)
1. **PDF to Word (`.docx`)** — Extract text via `PDFTextStripper` and package standards-compliant OOXML `.docx` archives via `ZipOutputStream`.
2. **Word (`.docx`) & Text to PDF** — Parse `word/document.xml` via `ZipInputStream` + `XmlPullParser` (or Markdown / plain text) into cleanly paginated A4 PDFs using `PdfDocument`.
3. **Images to PDF** — Multi-select & reorder images into A4 or Fit-to-Image PDFs.
4. **PDF to Images** — Render all or selected PDF pages into crisp PNG/JPG files (`100–300 DPI`) in `Pictures/MediaForge/PDF_Export`.
5. **Merge PDFs** — Combine & reorder multiple PDFs into a single document.
6. **Split / Extract Pages** — Visual page thumbnail grid + range parser (e.g. `1-3, 6`).
7. **Compress PDF** — Optimize embedded page images using `Low`, `Balanced`, or `High` presets.
8. **Rotate & Delete Pages** — Visual per-page `90°` / `180°` / `270°` rotation and deletion.
9. **Add Page Numbers** — Rotation-aware `Page 1 of N`, `1 / N`, or `1` stamps at bottom-center or bottom-right.
10. **Add Text Watermark** — Diagonal translucent text stamps with opacity and ink color presets.
11. **Protect / Encrypt PDF** — 128-bit password encryption (`StandardProtectionPolicy`) with print & copy permissions.
12. **Unlock / Decrypt PDF** — Remove password restrictions from user-owned encrypted PDFs.

### 3. Gemini AI Copilot (BYO API Key)
- **Smart PDF Summarizer & Q&A** — Extracts document text locally (with rendered page fallback for scanned PDFs) and connects to Gemini (`gemini-2.5-flash` / `gemini-2.5-pro`) for executive summaries, key takeaways, Indonesian/English translation, and interactive Q&A.
- **Product Photo Copywriter** — Generates a product title, 3 short review hooks, and a ready-to-copy social/marketplace caption + hashtags.
- **Graceful Offline Fallback** — Displays a clear offline status pill while keeping all Media Lab and PDF Suite tools 100% functional without internet.

### 4. Settings & Storage Management
- **Appearance Switcher** — Instant `System`, `Light`, and `Dark` theme switching persisted via Preferences DataStore.
- **Encrypted API Vault** — Stores the user's Gemini API key locally via `EncryptedSharedPreferences` with show/hide toggle and live **Verify Key** check.
- **Default Export Preferences & Cache** — Configure default compression quality (`80%`), default PDF page size (`A4`), auto-strip EXIF toggle, view cumulative storage saved, clear temporary cache, browse output folders, and manage export history.

---

## Build from Source

```bash
# Debug build & unit tests
./gradlew assembleDebug testDebugUnitTest

# Signed release build (requires keystore.properties / release-key.jks)
./gradlew assembleRelease
```

### Tech Stack
- Kotlin • Jetpack Compose (Material 3) • Navigation Compose • Coroutines & Flow
- Preferences DataStore • Jetpack Security (`EncryptedSharedPreferences`) • `ExifInterface`
- `com.tom-roush:pdfbox-android:2.0.27.0` • Android `PdfRenderer` / `PdfDocument` • `MediaStore` Scoped Storage
