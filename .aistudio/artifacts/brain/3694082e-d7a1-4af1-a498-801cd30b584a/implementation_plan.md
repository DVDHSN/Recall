# Phase 4: Harden Recall — Reliability, Performance, Privacy, and Release Readiness

Comprehensively audit, harden, and verify Recall for production-grade reliability, memory safety, recoverable background indexing, database migration integrity, and benchmarked search quality.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following key decisions were confirmed with the user during Phase 1 clarification:
> - **Battery & Constraint Policy**: Immediate execution regardless of battery state to guarantee timely indexing for newly captured or imported screenshots.
> - **Failed Image Retry Strategy**: Exponential backoff capped at 3 retries, with a user-facing manual retry override in Diagnostics and Settings.
> - **Search Quality Evaluation**: Comprehensive automated benchmark suite reporting Recall@5, Precision@5, and nDCG@5 separately for visual, text-heavy, and hybrid queries.

---

## 1. Overview & Core Concept

Phase 4 hardens the entire Recall pipeline against real-world mobile edge conditions: abrupt app kills, low-memory pressure, media permission revocations, corrupted image files, interrupted background jobs, and database schema migrations. It proves search accuracy using a ground-truth evaluation benchmark and verifies that user privacy is strictly preserved at the OS level (zero internet permissions).

---

## 2. Key Hardening Areas

### A. Indexing Reliability & Exponential Backoff
- **Retry Limiting & Quarantine**: Track `retryCount` (0–3) and `lastAttemptAt` in `ScreenshotEntity`. Corrupted or unreadable files will not trigger endless retry loops or waste battery. After 3 attempts, images are quarantined as `FAILED` with explicit error descriptions.
- **Manual Retry Override**: Add a "Retry Failed Images" action in the UI (Diagnostics and Settings) allowing users to reset failed items after fixing permissions or files.
- **Interrupted Batch Recovery**: On app launch and indexer start, any records stuck in `PROCESSING` status are atomically reset to `PENDING` so no work is permanently lost.
- **Bitmap & Memory Safety**: Guarantee all decoded bitmaps are downscaled via `inSampleSize` and explicitly recycled inside `finally` blocks, eliminating memory leaks during large batch indexing.

### B. Room Database Schema & Non-Destructive Migration
- **Schema Upgrade (Version 4 -> Version 5)**:
  - Add `retryCount INTEGER NOT NULL DEFAULT 0` and `lastAttemptAt INTEGER NOT NULL DEFAULT 0` to `screenshots`.
  - Provide safe SQLite `ALTER TABLE` in `MIGRATION_4_5` so existing user libraries and vector embeddings are never destroyed.
  - Implement an automated migration test verifying data preservation.

### C. Search Evaluation Framework (nDCG@5, Precision@5, Recall@5)
- Extend `SearchEngineEvaluationTest` to evaluate separate query categories:
  1. **Visual-Semantic Queries**: e.g., sunset over ocean, red sports car, mountain view.
  2. **Text-Heavy / OCR Queries**: e.g., Wi-Fi password, Python traceback, 404 error, lease rent.
  3. **Mixed / Hybrid Queries**: e.g., science notes diagram, coconut chickpea curry recipe, GPU rasterization comparison.
- Calculate and output **Precision@5**, **Recall@5**, and **nDCG@5** with normalized discounted cumulative gain.

### D. Android Permissions & Privacy Verification
- Verify `AndroidManifest.xml` contains zero `INTERNET` permission.
- Ensure logging does not leak private user OCR text, passwords, or image thumbnails.
- Gracefully handle `SecurityException` and partial media permission (`READ_MEDIA_VISUAL_USER_SELECTED`).

### E. Ask Recall & On-Device QA Robustness
- Verify safe handling of empty questions, non-receipt queries, ambiguous OCR, and cancellation without affecting normal image search.

---

## 3. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Indexing Reliability Flow                       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
       ┌────────────────────────────┴───────────────────────────┐
       ▼                                                        ▼
┌──────────────────────────────┐        ┌────────────────────────────────┐
│   ScreenshotDiscovery        │        │      ScreenshotIndexer         │
│ - isUriAccessible check      │        │ - Bitmap downscale & recycle   │
│ - SecurityException safety   │        │ - Try-catch-finally guarantee  │
└──────────────┬───────────────┘        └───────────────┬────────────────┘
               │                                        │
               └────────────────────┬───────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                Room Database (Version 5 Migration)                     │
│  - retryCount: Int (capped at 3)                                       │
│  - lastAttemptAt: Long                                                 │
│  - MIGRATION_4_5: Non-destructive ALTER TABLE                          │
│  - resetInterruptedProcessing() on startup                             │
└────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                Evaluation Framework & Benchmark Suite                  │
│  - Visual Queries: Recall@5, Precision@5, nDCG@5                       │
│  - Text Queries: Recall@5, Precision@5, nDCG@5                         │
│  - Hybrid Queries: Recall@5, Precision@5, nDCG@5                       │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Implementation Steps

1. **Room Database Version 5 Upgrade & Migration**:
   - Update `ScreenshotEntity` with `retryCount` and `lastAttemptAt`.
   - Update `RecallDatabase` version to 5 and add `MIGRATION_4_5`.
   - Add `resetFailedForManualRetry()` and update query methods in `ScreenshotDao`.
2. **Hardened Indexer & Recovery Logic**:
   - In `ScreenshotIndexer`, ensure `bitmap.recycle()` in `finally` blocks.
   - Implement exponential retry tracking: if failure occurs, increment `retryCount`; cap at 3 attempts.
   - In `ScreenshotRepository`, ensure `resetInterruptedProcessing()` runs during discovery scan.
3. **Diagnostics & Settings UI Enhancements**:
   - Add "Retry Failed Images" affordance in `SettingsScreen` and `DiagnosticsScreen`.
4. **Comprehensive Evaluation Suite (Recall@5, Precision@5, nDCG@5)**:
   - Expand `SearchEngineEvaluationTest` with segmented reporting for Visual, Text, and Hybrid queries with exact nDCG formulas.
5. **Automated Migration & Reliability Tests**:
   - Add `MigrationV4ToV5Test` to assert schema upgrades preserve all fields.
   - Add `IndexingRecoveryTest` verifying that interrupted and failed images recover cleanly without infinite loops.
6. **Full Build & Verification**:
   - Run `gradle :app:testDebugUnitTest` and `compile_applet`.
