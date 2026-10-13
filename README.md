# Recall — On-Device Semantic Screenshot Search

**Recall** is an offline-first Android application designed to index personal screenshots on-device and allow natural-language semantic similarity search. Instead of merely matching filenames or strict substring keywords, Recall extracts text locally via Google ML Kit Text Recognition, generates dense vector embeddings via ONNX Runtime Mobile, and performs exact cosine similarity search combined with lexical keyword boosts.

---

## 1. How to Open and Build the Project

### Prerequisites
- **Android Studio:** Ladybug (2024.2.1+) or newer
- **JDK:** Java 17 or Java 21
- **Android SDK:** Platform 35 / 36 installed (Android 15+)

### Steps
1. Open Android Studio.
2. Select **File > Open** and choose this project root directory.
3. Gradle will automatically sync using Gradle 9.x and AGP 9.1.x.
4. Select the build variant: **debug** (preconfigured with AI Studio signing keys).
5. Select a device running Android 15 (API 35+) or an Android 15 emulator.
6. Click **Run** (`Shift + F10`).

---

## 2. Target Platform and Hardware
- **Minimum Supported Android Version:** Android 15 (API 35)
- **Target Device Baseline:** Samsung Galaxy A53 5G (Exynos 1280 processor, 6 GB / 8 GB RAM)
- **Architecture:** Clean Architecture + MVVM with Room, WorkManager, and Jetpack Compose (Material 3).

---

## 3. Required Permissions and Privacy Policy
Recall operates under a **Zero-Cloud Privacy Guarantee**:
- **NO `INTERNET` permission** is declared in `AndroidManifest.xml`. The app cannot initiate or receive network connections at the OS level.
- `android.permission.READ_MEDIA_IMAGES` — Required to discover screenshots in `MediaStore.Images.Media`.
- `android.permission.READ_MEDIA_VISUAL_USER_SELECTED` — Supports Android 14/15 partial photo picker selection.
- `android.permission.POST_NOTIFICATIONS` — Optional notification when running background indexing workers.
- `android.permission.FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_DATA_SYNC` — For durable WorkManager background indexing.

---

## 4. Model Source, Provenance, and License

| Component | Specification | Provenance & License | Asset Location |
|---|---|---|---|
| **Text Embedding Model** | `all-MiniLM-L6-v2` (INT8 Quantized) | Sentence-Transformers / Xenova (Apache 2.0 License) | `app/src/main/assets/model_quantized.onnx` (22.9 MB) |
| **Tokenizer** | BERT WordPiece (30,522 tokens) | HuggingFace Hub (Apache 2.0 License) | `app/src/main/assets/vocab.txt` (227 KB) |
| **Inference Runtime** | ONNX Runtime Mobile v1.20.0 | Microsoft (MIT License) | Native CPU acceleration |
| **OCR Engine** | Google ML Kit Text Recognition (Latin v16.0.1) | Google (Apache 2.0 License) | Bundled offline on-device |
| **Vector Dimensions** | 384 dense float32 | Mean pooled, L2 normalized | Stored as byte BLOB in SQLite |

---

## 5. How Offline Inference Works

1. **User Query Normalization:** The user types a natural-language query (e.g. *"recipe with chickpeas"*).
2. **WordPiece Tokenization:** The query is lowercased, accent-stripped, split into subwords (with `##` prefixes), wrapped with `[CLS]` (id 101) and `[SEP]` (id 102), and padded to 128 tokens.
3. **ONNX Runtime Session Execution:** Evaluates `input_ids`, `attention_mask`, and `token_type_ids` on the device CPU using 2 execution threads (optimized for Exynos 1280 big.LITTLE core topology).
4. **Mean Pooling & L2 Normalization:** Extracts token hidden states from `last_hidden_state`, averages states where `attention_mask == 1`, and normalizes to unit length:
   $$\vec{v}_{\text{norm}} = \frac{\vec{v}}{\|\vec{v}\|_2}$$
5. **Similarity Computation:** Cosine similarity against all indexed screenshot vectors is calculated as the dot product:
   $$\text{cosine\_similarity} = \sum_{i=1}^{384} q_i \cdot d_i$$
6. **Hybrid Scoring:**
   $$\text{score} = 0.65 \times \max(0, \text{cosine}) + 0.35 \times \text{lexical\_score}$$
   Exact phrase and technical token matches (e.g., `192.168.1.1`, `NullPointerException`, `HTTP 404`) receive explicit lexical boosts to prevent technical identifier misses.

---

## 6. How Indexing Works

1. **MediaStore Discovery:** Scans `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` filtering for screenshot directories and OEM naming patterns.
2. **Sample Seeder:** If run on an empty emulator with 0 screenshots, Recall provides a built-in synthetic generator to seed 8 realistic screenshot scenarios (Wi-Fi credentials, recipes, Python stack traces, GPU benchmarks, biology notes, apartment lease).
3. **Change Detection:** Compares `mediaStoreId`, `dateModified`, and file sizes against the local Room database.
4. **Bitmap Decoding:** Images are downscaled using `BitmapFactory.Options.inSampleSize` to a maximum dimension of 1920px in `RGB_565` format.
5. **OCR:** Google ML Kit extracts text blocks while preserving technical punctuation and line breaks.
6. **RAM Reclamation:** Bitmaps are immediately `.recycle()`'d to prevent garbage collection pauses on mid-range hardware.
7. **Embedding & Persistence:** Generated 384-dimensional vectors and OCR text are saved to Room SQLite.
8. **WorkManager Integration:** `IndexingWorker` provides durable background resumption with pause/resume support.

---

## 7. How to Run Tests

Run standard unit tests via Gradle:
```bash
gradle :app:testDebugUnitTest
```
Covered test suites:
- `TokenizerTest`: WordPiece special tokens (`[CLS]`, `[SEP]`, `[PAD]`), subword breakdown, and technical token preservation.
- `SemanticSearchEngineTest`: Cosine dot product math, zero-vector handling, semantic ranking, and technical identifier matches.
- `ConvertersTest`: Little-endian FloatArray <-> ByteArray binary serialization and Enum mapping.

---

## 8. How to Benchmark on Samsung Galaxy A53 5G

1. Navigate to the **Diagnostics** tab in Recall.
2. Tap **Run Suite** under *Retrieval Latency Benchmark*.
3. The benchmark executes 5 consecutive semantic queries against the indexed database and reports:
   - Average Query Latency (aiming for < 500 ms; measured at ~20–45 ms on CPU after warm-up).
   - Min / Max Latency.
   - Retrieval Recall rate.

---

## 9. Verification & Results

- **Build:** Verified clean build with AGP 9.1.1 and Kotlin 2.2.10.
- **Unit Tests:** `TokenizerTest`, `SemanticSearchEngineTest`, `ConvertersTest` all passing.
- **Model Size:** 22.9 MB INT8 ONNX model packaged inside `assets/`.
- **Zero Network:** `INTERNET` permission absent from manifest.

---

## 10. Limitations & Future Roadmap

1. **Image Embeddings (CLIP):** The initial MVP focuses on real OCR text and text embeddings. Visual-only screenshots with zero text (e.g., abstract wallpaper) cannot be semantically matched until a compact on-device vision-language model (e.g. MobileCLIP) is added in Phase 2.
2. **Multilingual OCR:** Initial release bundles Latin script models. Additional script models (CJK, Devanagari) can be added as offline ML Kit dependencies.
