# RECALL — Phase 2: True On-Device Visual Semantic Search

Recall currently indexes text extracted via local OCR, spatial perceptual color layout hashes, and text sentence embeddings. In Phase 2, we introduce **cross-modal visual semantic search**, enabling users to search their entire photo and screenshot library using natural language descriptions of visual content—even for images containing zero readable text.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were selected and confirmed during Phase 1 clarification:
> - **Cross-Modal Model Architecture**: **MobileCLIP-S0 (INT8 Quantized)** (~35 MB footprint, 512-dimensional shared image-text embedding space, Apple open-source / MIT license).
> - **Indexing Strategy**: **Phased Indexing** (OCR runs immediately for rapid search readiness; 512-dim visual embeddings are computed in a background worker queue with bounded batching).
> - **Search Fusion Strategy**: **Adaptive Query-Intent Fusion** (dynamically determines whether a query is primarily visual, textual/technical, or mixed, and adjusts scoring weights accordingly).

---

## 1. Overview & Core Concept

- **What It Does**: Users can type descriptive visual queries such as *"a red Ferrari at night"*, *"a golden sunset over the ocean"*, *"a cat sleeping on a sofa"*, or *"a blue and white science diagram"*, and Recall will retrieve the matching images based on actual visual semantics.
- **True Cross-Modal Alignment**: Visual images and text queries are projected into the **same 512-dimensional normalized hypersphere**, allowing direct cosine similarity calculation between words and image pixels.
- **Privacy & Offline Constraint**: 100% on-device inference using ONNX Runtime Mobile (`ai.onnxruntime:onnxruntime-android`). Zero network permissions or server communication.

---

## 2. Technical Architecture & System Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                              RECALL 2.0                                │
│                   Visual Semantic & Hybrid Search                      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
           ┌────────────────────────┴────────────────────────┐
           ▼                                                 ▼
┌───────────────────────┐                         ┌───────────────────────┐
│     USER SEARCH       │                         │   IMAGE DISCOVERY     │
│  "sunset over ocean"  │                         │ MediaStore / Gallery  │
└──────────┬────────────┘                         └──────────┬────────────┘
           │                                                 │
           ▼                                                 ▼
┌───────────────────────┐                         ┌───────────────────────┐
│   Query Parser &      │                         │   Phased Indexer      │
│ Intent Classification │                         │   Phase 1: Fast OCR   │
│ (Visual vs Textual)   │                         │   Phase 2: Visual Emb │
└──────────┬────────────┘                         └──────────┬────────────┘
           │                                                 │
           ▼                                                 ▼
┌───────────────────────┐                         ┌───────────────────────┐
│ MobileCLIP Text Enc   │                         │ MobileCLIP Vision Enc │
│ (512-dim Normalized)  │                         │ 224x224 RGB -> 512-dim│
└──────────┬────────────┘                         └──────────┬────────────┘
           │                                                 │
           └────────────────────────┬────────────────────────┘
                                    │
                                    ▼
                         ┌───────────────────────┐
                         │   Adaptive Fusion     │
                         │   • Visual Cosine     │
                         │   • Semantic OCR      │
                         │   • Lexical Hits      │
                         │   • RRF Ranking       │
                         └──────────┬────────────┘
                                    │
                                    ▼
                         ┌───────────────────────┐
                         │  Ranked Search UI     │
                         │  • Visual Relevance   │
                         │  • Matched Signals    │
                         └───────────────────────┘
```

---

## 3. Key Architectural Components

### A. Cross-Modal Embedding Engine (`VisualEmbeddingEngine.kt`)
- **Interface**:
  - `generateImageEmbedding(bitmap: Bitmap): FloatArray`
  - `generateTextEmbedding(text: String): FloatArray`
  - `modelVersion: String`, `embeddingDimension: Int = 512`
- **Preprocessing**:
  - Images: Downsampled to $224 \times 224$ RGB, normalized with ImageNet mean `[0.48145466, 0.4578275, 0.40821073]` and std `[0.26862954, 0.26130258, 0.27577711]`.
  - Text: Simple BPE tokenizer with `[BOS]` and `[EOS]` padding up to 77 tokens.
  - Normalization: L2 unit normalization ($\|\vec{v}\|_2 = 1.0$) for both image and text outputs so dot product equals cosine similarity.
- **Memory Safety**:
  - Model weights loaded lazily on first visual operation.
  - Image downsampling uses in-place bitmap recycling to prevent out-of-memory errors on mid-range devices.

### B. Phased Visual Indexing Pipeline
- **Phase 1 (Immediate Text Indexing)**:
  - Scans image $\to$ runs OCR $\to$ sentence embeddings.
  - Image is immediately searchable via text. Marked with `visualEmbeddingStatus = PENDING`.
- **Phase 2 (Background Visual Queue)**:
  - Background worker reads batch of 20 images needing visual vectors.
  - Computes 512-dim MobileCLIP visual vectors and stores in `visualBlob`.
  - Marks image with `visualEmbeddingStatus = READY` and records `visualModelVersion = "MobileCLIP-S0-INT8"`.
  - Yields cooperatively if user navigates or device memory is constrained.

### C. Adaptive Query-Intent Retrieval (`VisualRetriever.kt` & `CandidateFusion.kt`)
- Queries are classified into:
  1. **Visual-Dominant** (e.g. *"sunset over ocean"*, *"red car"*, *"cat sleeping"*):
     - Weighted: **75% Visual Vector Similarity + 25% Lexical/OCR**.
     - Images without OCR text can score 0.90+ and rank #1.
  2. **Textual/Technical-Dominant** (e.g. *"192.168.1.1"*, *"NullPointerException"*, *"due date 1st"*):
     - Weighted: **75% Exact Lexical / OCR + 25% Visual Context**.
  3. **Balanced Hybrid** (e.g. *"circuit board diagram"*, *"recipe with chickpeas"*, *"apartment contract"*):
     - Weighted: **50% Visual Similarity + 35% OCR Semantics + 15% Lexical**.
- Explanations in search results clearly show the retrieval signal (e.g., *"High visual similarity: 88% visual match to 'sunset over ocean'"*).

### D. Database Migration (`MIGRATION_3_4`)
- Room database schema upgraded to **version 4**:
  - `visualModelVersion TEXT NOT NULL DEFAULT ''`
  - `visualDimension INTEGER NOT NULL DEFAULT 0`
  - `visualEmbeddingStatus TEXT NOT NULL DEFAULT 'PENDING'`
  - Index added on `(visualEmbeddingStatus)` for fast queue polling.
- Zero data loss for existing Phase 1 user libraries.

---

## 4. Evaluation & Quality Benchmark Plan

Create a comprehensive evaluation suite (`VisualSearchEvaluationTest.kt`) with 25 diverse test scenarios:
1. **Scenic & Natural Photos** (sunsets, oceans, forests, mountains) with zero text.
2. **Objects & Vehicles** (red cars, laptops, product boxes).
3. **Animals & Pets** (cats on furniture, dogs running).
4. **Diagrams & Scientific Notes** (flowcharts, circuit diagrams, anatomy).
5. **Receipts & Documents** (paper receipts, leases, invoices).
6. **Visually Similar Distractors** (blue sky vs blue ocean; red apple vs red car).

**Measurable Metrics**:
- Recall@5 ($\ge 90\%$)
- Precision@5
- Average query search latency ($< 250\text{ ms}$)
- Visual vs Hybrid comparison report

---

## 5. Verification Plan

1. **Unit & Robolectric Tests**:
   - `VisualEmbeddingEngineTest`: verifies L2 normalization, embedding dimensions, and cross-modal alignment.
   - `VisualRetrieverTest`: verifies text-free image retrieval from visual prompts.
   - `DatabaseMigrationTest`: verifies schema upgrade from v3 to v4.
   - `VisualSearchEvaluationTest`: verifies benchmark precision and latency metrics.
   - Run: `gradle :app:testDebugUnitTest`.
2. **Applet Compilation**:
   - Run `compile_applet` to verify clean build.
