# LightBox — Export Performance Optimization Roadmap

> **Target Device:** Xiaomi Pad 8 Pro (Qualcomm Snapdragon 8 Gen 3, 12 GB+ LPDDR5X RAM, UFS 4.0 Storage, Android 14+ / HyperOS)  
> **Target Workload:** High-speed batch processing of 1,000+ high-resolution (12 MP – 50 MP) JPEGs with EXIF preservation.

---

## 1. Diagnostic: Current Bottlenecks

While the Look-Up Table (LUT) transformation runs in under 0.05 ms, batch export throughput is constrained by:

1. **Triple Disk I/O per Image:**
   - Step 1: `bitmap.compress(JPEG)` writes to a temporary file (`tempFile`).
   - Step 2: `ExifInterface.saveAttributes()` reads `tempFile`, rewrites metadata, and rewrites the entire file to flash storage a second time.
   - Step 3: `copyToOutputDir()` streams bytes from `tempFile` to the SAF `DocumentFile` output stream (a 3rd disk write).
   - *Impact:* High flash wear, elevated device temperature, and unnecessary disk I/O latency for 10–25 MB camera JPEGs.

2. **Single-Threaded Row Slicing in `applyAdjustments()`:**
   - The Snapdragon 8 Gen 3 features 8 high-performance CPU cores (1x Cortex-X4 @ 3.3 GHz, 5x Cortex-A720 @ 3.2 GHz, 2x Cortex-A520 @ 2.3 GHz).
   - Currently, each photo's pixel buffer is iterated in a single sequential loop on one core.

3. **Memory Churn & Garbage Collection Pauses:**
   - Each photo allocates a new `Bitmap.createBitmap(width, height, ARGB_8888)` (50 MB – 100 MB of native heap) followed by `recycle()`.
   - In a 100+ photo batch, allocating and recycling gigabytes of bitmap memory triggers frequent ART runtime GC pauses.

---

## 2. Optimization Roadmap

### Phase 1: High-ROI Kotlin & Coroutines Optimizations (Estimated 3x – 5x Speedup)

#### 1.1 Direct SAF Stream Writing (Eliminate Intermediate Temp Files)
- **Action:** Compress directly into the target SAF `openOutputStream(outputDoc.uri, "w")` or an in-memory buffer with EXIF header injection.
- **Benefit:** Cuts disk writes from 3x to 1x per photo; reduces flash latency by ~60%.

#### 1.2 Parallel Multi-Core Row Slicing (`Dispatchers.Default`)
- **Action:** Split each image into horizontal bands corresponding to available CPU cores and process rows in parallel:
  ```kotlin
  val numBands = Runtime.getRuntime().availableProcessors().coerceIn(4, 8)
  val bandHeight = height / numBands
  coroutineScope {
      (0 until numBands).map { band ->
          async(Dispatchers.Default) {
              val startY = band * bandHeight
              val rows = if (band == numBands - 1) height - startY else bandHeight
              // Process band with LUT lookups
          }
      }.awaitAll()
  }
  ```
- **Benefit:** Full saturation of Cortex-X4 and Cortex-A720 cores, reducing 24 MP LUT processing to ~10–15 ms.

#### 1.3 Reusable Bitmap Pool (`BitmapFactory.Options.inBitmap`)
- **Action:** Implement a fixed-size `BitmapPool` (2–3 buffers sized to standard camera resolutions like 4000x3000 / 6000x4000) using `inBitmap` and `inMutable = true`. Reutilize row pixel buffers across photos in the same batch.
- **Benefit:** Eliminates 99% of bitmap allocations and stops GC pauses.

#### 1.4 Adaptive Concurrency for 12 GB RAM
- **Action:** On devices with >= 12 GB RAM, tune `MAX_CONCURRENT` from 2 to 3–4 concurrent photo workers, staying well within a 1.2 GB safe memory footprint.

---

### Phase 2: Native Acceleration via C++ / NDK (Estimated 5x – 10x Speedup)

#### 2.1 `libjpeg-turbo` with ARM NEON SIMD
- **Action:** Replace Android's single-threaded `BitmapFactory.decodeStream` and `Bitmap.compress` with an NDK module integrating `libjpeg-turbo`.
- **Benefit:** Decodes and encodes JPEGs using 128-bit ARM NEON vector instructions; encoding 24 MP JPEGs drops from ~250 ms to ~40 ms.

#### 2.2 Native SIMD LUT Application
- **Action:** Implement the LUT and secondary adjustment pass in C++ using ARM NEON intrinsics (`vld1q_u8`, `vtbl1_u8`):
  - Maps 16 pixels per clock cycle directly in vector registers.
- **Benefit:** Full image transformation runs in ~3–5 ms.

---

### Phase 3: Hardware-Dedicated Acceleration

#### 3.1 Qualcomm Spectra ISP / MediaCodec Hardware Encoding
- **Action:** Pipe YUV/RGB buffers to `MediaCodec` configured for hardware JPEG/HEIC encoding directly on Qualcomm's dedicated image processor.
- **Benefit:** Hardware-accelerated encoding with near-zero CPU and battery utilization.
