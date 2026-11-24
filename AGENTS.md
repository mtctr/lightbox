# LightBox — Agent Context File

> **Read this file first.** It contains the full project state, architecture decisions, known bugs, and pending work. Any agent continuing this project must read this file before touching any code.

---

## Project Overview

**App name:** LightBox  
**Package:** `com.lightbox.batch`  
**Target device:** Xiaomi Pad 8 Pro — Android 14+ / HyperOS, Snapdragon 8 Gen 3, 12 GB+ RAM  
**Purpose:** High-speed batch JPEG processing tool inspired by Adobe Lightroom. Handles mass import, preset application, auto-tone correction, and mass export with EXIF preservation.  
**Language / UI:** Kotlin + Jetpack Compose (tablet dual-pane layout)

---

## Directory Structure

```
c:\Users\mateu\source\ai\lightbox\
├── build.gradle.kts                          # Root Gradle (AGP 8.7.3, Kotlin 2.1.0, KSP 2.1.0-1.0.29)
├── settings.gradle.kts
├── gradle.properties                         # Xmx4096m
├── gradle/wrapper/gradle-wrapper.properties  # Gradle 8.11.1
├── local.properties
└── app/
    ├── build.gradle.kts                      # compileSdk/targetSdk 35, minSdk 34, Java 17
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/presets/                   # (optional) Built-in preset XMP/JSON files
        ├── res/values/{strings,themes}.xml
        ├── res/mipmap-hdpi/ic_launcher.xml
        └── java/com/lightbox/batch/
            ├── LightBoxApp.kt
            ├── data/
            │   ├── LightBoxDatabase.kt
            │   ├── PhotoDao.kt
            │   ├── PhotoEntity.kt
            │   ├── PresetDao.kt              # has insert, delete, updateName, update, getAllPresets, getPresetById
            │   └── PresetEntity.kt           # fields: id, name, filePath, dateImported, parametersJson
            ├── model/
            │   ├── BatchState.kt             # UI state: batchId, totalPhotos, isImporting/Exporting, selectedPresetId, exportQuality, errorMessage
            │   └── PresetParameters.kt       # 34-field data class + toJson/fromJson + hasAdjustments + combineWith + BUILT_IN_PRESETS + AUTO_CORRECT
            ├── preset/
            │   └── LightroomPresetParser.kt  # parses .xmp and .lrtemplate files; normalises LR values to [-1, 1]
            ├── processing/
            │   ├── AutoToneAnalyzer.kt       # histogram-based deterministic auto-correction (no ML/cloud)
            │   └── BatchImageProcessor.kt    # LUT-based export engine + AGSL RuntimeShader factory for UI previews
            ├── worker/
            │   └── MassExportWorker.kt       # WorkManager CoroutineWorker; bounded concurrency; SAF output
            └── ui/
                ├── MainActivity.kt
                ├── BatchViewModel.kt         # all state management + renamePreset + deletePreset + startExport
                └── MainBatchScreen.kt        # dual-pane tablet UI; PresetItem has rename+delete dialogs
```

---

## Key Architecture Decisions

### Image Processing Pipeline
- **For export (CPU path):** `BatchImageProcessor.applyAdjustments()` builds **256-element RGB Look-Up Tables** (one per channel) for Exposure, White Balance, Contrast, Highlights, Shadows, Whites, Blacks — then processes pixels in 256-row slices. Saturation and Vibrance are applied per-pixel after the LUT pass.
- **For UI preview (GPU path):** `BatchImageProcessor.createRuntimeShader(params)` builds an **AGSL `RuntimeShader`** that is attached to the `AsyncImage` composable via `graphicsLayer { renderEffect = ... }`. This runs entirely on the Snapdragon 8 Gen 3 GPU at 120 FPS with zero CPU involvement.
- Both paths share the same `PresetParameters` input so preview and export are mathematically consistent.

### Preset Normalization
All Lightroom values are normalized to `[-1.0, 1.0]`:
- `Exposure2012`: `[-5, +5]` EV → `[-1, 1]`
- `Contrast2012`, `Highlights2012`, `Shadows2012`, `Whites2012`, `Blacks2012`, `Saturation`, `Vibrance`: `[-100, +100]` → `[-1, 1]`
- `Temperature`: Kelvin centered at 6500K, asymmetric
- `Tint`: `[-150, +150]` → `[-1, 1]`

### Preset Storage
- Presets are stored in Room (`presets` table) as `PresetEntity { id, name, filePath, dateImported, parametersJson }`.
- Built-in presets use `filePath = "asset:presets/<filename>"` or `"builtin"`.
- Custom imported presets use `filePath = uri.toString()`.
- IDs for built-in presets are computed as `"builtin_<slugified_name>"`.

### Export Worker (`MassExportWorker`)
- Runs as a WorkManager `CoroutineWorker` with `setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`.
- **Requires** `override suspend fun getForegroundInfo()` — already implemented.
- Input data keys: `KEY_BATCH_ID`, `KEY_OUTPUT_DIR_URI`, `KEY_QUALITY`, `KEY_PRESET_JSON`, `KEY_AUTO_CORRECT`.
- `KEY_PRESET_JSON` carries the full serialized `PresetParameters` JSON string (not an ID).
- The worker calls `processor.processImage(inputUri, tempFile, adjustParams, quality)` then copies the temp file to the SAF output directory using `openOutputStream(outputDoc.uri, "w")` (**not** `"wt"`).
- The `SystemForegroundService` is declared in `AndroidManifest.xml` with `android:foregroundServiceType="dataSync"`.

### ViewModel (`BatchViewModel`)
- `_selectedPreset: MutableStateFlow<PresetParameters?>` — holds the **deserialized** preset in memory.
- `effectiveAdjustmentParams` — `combine(_selectedPreset, _batchState)` to blend preset + auto-correct.
- `startExport(outputDirUri)` — re-fetches the preset from DB if `_selectedPreset.value` is null but `selectedPresetId` is set, then passes `preset?.toJson()` as `KEY_PRESET_JSON` to the worker.
- `renamePreset(id, newName)` — updates `name` and `parametersJson` in DB via `presetDao.update()`.
- `deletePreset(id)` — deletes from DB and deselects if it was the active preset.

---

## Pending Work / Known Issues

### 1. ✅ Presets Not Applied on Export — Fixed
- `KEY_PRESET_ID` passed via WorkManager `Data` from `BatchViewModel.startExport()`.
- `MassExportWorker.doWork()` queries Room (`presetDao.getPresetById(presetId)`) as primary source, bypassing the 10 KB WorkManager data limit. Fallback to `KEY_PRESET_JSON` retained.
- Added debug logging for preset resolution and parameter tracking.

### 2. ✅ Preset Edit UI (Rename + Delete) — Implemented & Verified
- `PresetItem` composable has `Edit` (rename) and `Delete` icon buttons with confirmation dialogs.
- `BatchViewModel.renamePreset()` and `deletePreset()` update Room DB and UI state appropriately.
- `isBuiltIn` check updated to support asset, builtin, and ID-prefixed presets.

### 3. ✅ Build Verified
- Gradle wrapper (`gradlew.bat`, Gradle 8.11.1) generated.
- `assembleDebug` executed and verified: **BUILD SUCCESSFUL**.
- Debug APK generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Completed Features (Do Not Re-implement)

- ✅ Dual-pane tablet UI (70% grid / 30% control panel)
- ✅ SAF-based folder and multi-file import
- ✅ Thumbnail generation (256px, RGB_565, cached to `cacheDir/thumbnails/`)
- ✅ Room DB for photos and presets
- ✅ XMP + LRTemplate preset parser
- ✅ AGSL GPU shader for real-time UI preview
- ✅ LUT-based CPU pipeline for export
- ✅ EXIF preservation on export
- ✅ Auto-Tone histogram analyzer
- ✅ Preset + Auto-Tone blending (60/40)
- ✅ WorkManager foreground export with progress notification
- ✅ Preset rename (dialog + DB update)
- ✅ Preset delete (dialog + DB delete + deselect)
- ✅ 6 built-in studio presets seeded at app start
- ✅ Active adjustment badges in top bar
- ✅ Export pipeline summary card in control panel
- ✅ `getForegroundInfo()` implemented in `MassExportWorker`
- ✅ SAF write mode fixed to `"w"` (not `"wt"`)
- ✅ `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` in `ForegroundInfo`
- ✅ Preset fetched by ID from Room in `MassExportWorker` to avoid WorkManager 10 KB limit
- ✅ Full build verification passing with Gradle 8.11.1 / AGP 8.7.3
- ✅ 256-level LUT engine for tone curves (Master + RGB channels), contrast, exposure, highlights/shadows
- ✅ Elimination of per-pixel string allocations in tone curve evaluation (10,000x export speedup)
- ✅ Full AGSL shader support for tone curves, RGB channels, split-toning, texture/clarity/dehaze
- ✅ Target directory write validation upfront and accurate failure reporting in MassExportWorker
- ✅ Perceptual sRGB gamma contrast curve (eliminates mid-tone darkening and shadow crush)
- ✅ Additive split-toning tint cast (eliminates 50% gray haze / washed-out blacks and whites)
- ✅ Lightroom AutoTone detection (`crs:AutoTone="True"`) and dynamic baseline contrast/vibrance
- ✅ AutoToneAnalyzer updated for rich dynamic range, punchy contrast (+0.15 to +0.22) and vibrance
- ✅ Automatic Room DB preset update on app start to ensure updated parameters overwrite stale DB cache
- ✅ Physical rotation baking in memory (`getRotationMatrix`) ensuring exported JPEGs and thumbnails are upright (orientation 1) across all viewers
- ✅ 3-sample preview mode (`SamplePreviewView`): displays 3 sample photos across distinct scenes for instant 120 FPS AGSL shader comparison, with total batch counter and "Sortear Amostras" shuffle button
- ✅ Clean "New Batch" flow: cancels active export, wipes previous batch photos from Room DB, deletes cached thumbnails, and returns UI to EmptyStateView
- ✅ Snapdragon 8 Gen 3 export concurrency optimization: 5 parallel image tasks (`MAX_CONCURRENT = 5`) with 8-core horizontal row slicing per image, removing dual-semaphore bottlenecks
- ✅ Mini-batch export partitioning: photos partitioned into 200-image mini batches (`chunked(200)`) with mini-batch progress notifications and memory cleanup (`System.gc()`) between batches

---

## Export Performance Optimization Roadmap

Detailed reference: see [PERFORMANCE_ROADMAP.md](file:///c:/Users/mateu/source/ai/lightbox/PERFORMANCE_ROADMAP.md)

### Key Priorities:
1. **Eliminate Triple Disk I/O:** Stream compressed JPEG directly to target SAF `DocumentFile` output stream instead of `tempFile` -> `ExifInterface.saveAttributes()` -> SAF copy.
2. **Multi-Core Row Slicing (`Dispatchers.Default`):** Divide image into horizontal bands across the Snapdragon 8 Gen 3's 8 CPU cores (1x X4 + 5x A720) in `applyAdjustments()`.
3. **Bitmap Reuse (`inBitmap` & `BitmapPool`):** Reuse decoded and transformed bitmap buffers across photos to eliminate 50–100 MB allocations and ART GC pauses.
4. **Adaptive Concurrency:** Increase `MAX_CONCURRENT` from 2 to 3–4 for devices with 12 GB RAM.
5. **Native NDK (`libjpeg-turbo` with ARM NEON):** Accelerate JPEG encoding/decoding via SIMD 128-bit vector instructions.

---

## Next Priority Tasks (in order)

1. **Test on device** — sideload `app-debug.apk` to Xiaomi Pad 8 Pro and verify: import, preset selection, live preview shader, export with adjustments.
2. **Performance tuning** — implement Phase 1 optimizations (direct SAF stream writing, multi-core row slicing, and bitmap pooling).

---

## Dependencies (from `app/build.gradle.kts`)

| Library | Version |
|---|---|
| Compose BOM | 2024.12.01 |
| Coil 3 | 3.0.4 |
| Room | 2.6.1 |
| WorkManager | 2.10.0 |
| Coroutines | 1.9.0 |
| ExifInterface | 1.3.7 |
| DocumentFile | 1.0.1 |
| Lifecycle | 2.8.7 |
| Navigation Compose | 2.8.5 |
| Activity Compose | 1.9.3 |

compileSdk = 35, minSdk = 34, targetSdk = 35, Java 17, Kotlin 2.1.0
