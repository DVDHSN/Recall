package com.example.core.repository

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.example.core.database.RecallDatabase
import com.example.core.database.ScreenshotDao
import com.example.core.database.ScreenshotEntity
import com.example.core.indexing.IndexingWorker
import com.example.core.indexing.ScreenshotIndexer
import com.example.core.indexing.SyntheticScreenshotGenerator
import com.example.core.media.ScreenshotDiscovery
import com.example.core.ml.embedding.MobileClipEmbeddingEngine
import com.example.core.ml.embedding.OnnxTextEmbeddingEngine
import com.example.core.ml.embedding.TextEmbeddingEngine
import com.example.core.ml.embedding.VisualEmbeddingEngine
import com.example.core.ml.ocr.MlKitOcrEngine
import com.example.core.ml.ocr.OcrEngine
import com.example.core.model.DuplicateGroup
import com.example.core.model.IndexStats
import com.example.core.model.IndexingStatus
import com.example.core.model.MediaPermissionState
import com.example.core.model.ScreenshotCategory
import com.example.core.model.ScreenshotItem
import com.example.core.model.SearchResult
import com.example.core.search.SemanticSearchEngine
import com.example.core.search.query.ParsedQuery
import com.example.core.search.query.SearchMode
import com.example.core.visual.PerceptualHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

class ScreenshotRepository(
    private val context: Context,
    private val database: RecallDatabase = RecallDatabase.getInstance(context),
    private val discovery: ScreenshotDiscovery = ScreenshotDiscovery(context),
    private val ocrEngine: OcrEngine = MlKitOcrEngine.getInstance(),
    private val embeddingEngine: TextEmbeddingEngine = OnnxTextEmbeddingEngine.getInstance(context),
    private val visualEngine: VisualEmbeddingEngine = MobileClipEmbeddingEngine.getInstance(context)
) {
    private val dao: ScreenshotDao = database.screenshotDao()
    private val indexer = ScreenshotIndexer(discovery, ocrEngine, embeddingEngine, dao, visualEngine)
    private val searchEngine = SemanticSearchEngine(
        embeddingEngine = embeddingEngine,
        screenshotDao = dao,
        visualEngine = visualEngine
    )
    private val generator = SyntheticScreenshotGenerator(context)

    private val _mediaPermissionState = MutableStateFlow(discovery.getMediaPermissionState())
    val mediaPermissionState = _mediaPermissionState.asStateFlow()

    private val _isIndexingActive = MutableStateFlow(false)
    val isIndexingActive = _isIndexingActive.asStateFlow()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val indexingMutex = Mutex()
    private var activeIndexingJob: Job? = null
    private var mediaObserverDebounceJob: Job? = null

    private val _currentProcessingName = MutableStateFlow<String?>(null)
    val currentProcessingName = _currentProcessingName.asStateFlow()

    private val _lastSearchLatencyMs = MutableStateFlow(0L)
    val lastSearchLatencyMs = _lastSearchLatencyMs.asStateFlow()

    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicateGroups = _duplicateGroups.asStateFlow()

    val allScreenshotsFlow: Flow<List<ScreenshotItem>> = dao.getAllFlow().map { entities ->
        entities.map { it.toDomain() }
    }

    val indexStatsFlow: Flow<IndexStats> = combine(
        listOf(
            dao.getTotalCountFlow(),
            dao.getIndexedCountFlow(),
            dao.getEmptyTextCountFlow(),
            dao.getFailedCountFlow(),
            dao.getPendingCountFlow(),
            _isIndexingActive,
            _currentProcessingName,
            _lastSearchLatencyMs,
            _duplicateGroups
        )
    ) { args: Array<Any?> ->
        val total = args[0] as Int
        val indexed = args[1] as Int
        val emptyText = args[2] as Int
        val failed = args[3] as Int
        val pending = args[4] as Int
        val isIndexing = args[5] as Boolean
        val currentName = args[6] as? String
        val latency = args[7] as Long
        @Suppress("UNCHECKED_CAST")
        val dupGroups = args[8] as List<DuplicateGroup>

        val progressPercent = if (total > 0) ((indexed + emptyText).toFloat() / total.toFloat()) * 100f else 0f
        IndexStats(
            totalDiscovered = total,
            totalIndexed = indexed,
            totalEmptyText = emptyText,
            totalFailed = failed,
            totalPending = pending,
            isIndexingActive = isIndexing,
            currentProcessingName = currentName,
            currentProgressPercent = progressPercent,
            modelVersion = embeddingEngine.modelVersion,
            modelDimensions = embeddingEngine.embeddingDimension,
            visualModelVersion = visualEngine.modelVersion,
            lastSearchLatencyMs = latency,
            duplicateGroupCount = dupGroups.size
        )
    }

    init {
        registerMediaStoreObserver()
    }

    private fun registerMediaStoreObserver() {
        try {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    // Debounce rapid MediaStore change notifications
                    mediaObserverDebounceJob?.cancel()
                    mediaObserverDebounceJob = appScope.launch {
                        delay(1200)
                        if (discovery.getMediaPermissionState() != MediaPermissionState.DENIED) {
                            Log.d(TAG, "MediaStore change detected, reconciling images...")
                            discoverAndReconcile()
                        }
                    }
                }
            }
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not register MediaStore content observer: ${e.message}")
        }
    }

    fun updatePermissionState(): MediaPermissionState {
        val newState = discovery.getMediaPermissionState()
        _mediaPermissionState.value = newState
        return newState
    }

    suspend fun discoverAndReconcile(screenshotsOnly: Boolean = false): Int = withContext(Dispatchers.IO) {
        val permState = updatePermissionState()
        if (permState == MediaPermissionState.DENIED) {
            Log.d(TAG, "discoverAndReconcile: Media permission DENIED; skipping reconciliation.")
            return@withContext 0
        }

        val discovered = discovery.discoverScreenshots(screenshotsOnly = screenshotsOnly)
        val existingIds = dao.getAllMediaStoreIds().toSet()

        if (discovered.isNotEmpty()) {
            val newEntities = mutableListOf<ScreenshotEntity>()

            for (item in discovered) {
                if (!existingIds.contains(item.mediaStoreId)) {
                    newEntities.add(ScreenshotEntity.fromDomain(item))
                } else {
                    // Check if modified on disk
                    val existing = dao.getByMediaStoreId(item.mediaStoreId)
                    if (existing != null && (existing.dateModified != item.dateModified || existing.sizeBytes != item.sizeBytes)) {
                        dao.update(
                            existing.copy(
                                dateModified = item.dateModified,
                                sizeBytes = item.sizeBytes,
                                indexingStatus = IndexingStatus.NEEDS_REINDEX
                            )
                        )
                    }
                }
            }

            if (newEntities.isNotEmpty()) {
                // Insert in safe batches
                newEntities.chunked(100).forEach { batch ->
                    dao.insertAll(batch)
                }
            }

            // Deletion handling:
            if (permState == MediaPermissionState.FULL_ACCESS) {
                // When FULL_ACCESS is granted, any MediaStore ID not present in discovered was deleted
                val currentMediaIds = discovered.map { it.mediaStoreId }
                dao.deleteMissing(currentMediaIds)
            }
            // In PARTIAL_ACCESS mode, we DO NOT delete unselected MediaStore items from database,
            // because MediaStore only returns the subset chosen by user.
        } else if (permState == MediaPermissionState.FULL_ACCESS && !screenshotsOnly) {
            // Entire media collection is empty on device
            dao.deleteAllMediaStoreItems()
        }

        refreshDuplicateGroups()
        discovered.size
    }

    suspend fun indexPendingVisualEmbeddings(batchLimit: Int = 20): Int = withContext(Dispatchers.IO) {
        val pendingVisual = dao.getAwaitingVisualEmbedding(batchLimit)
        if (pendingVisual.isEmpty()) return@withContext 0

        var count = 0
        for (entity in pendingVisual) {
            if (!_isIndexingActive.value) break // Cooperative pause
            val bitmap = discovery.loadDownscaledBitmap(entity.contentUri, maxDimension = 224)
            if (bitmap != null) {
                try {
                    val vec = visualEngine.generateImageEmbedding(bitmap)
                    dao.updateVisualEmbedding(
                        id = entity.id,
                        visualBlob = vec,
                        visualModelVersion = visualEngine.modelVersion,
                        visualDimension = visualEngine.embeddingDimension,
                        status = "READY"
                    )
                    count++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed computing visual embedding for image ${entity.id}: ${e.message}")
                } finally {
                    bitmap.recycle()
                }
            } else {
                dao.updateVisualEmbedding(
                    id = entity.id,
                    visualBlob = null,
                    visualModelVersion = visualEngine.modelVersion,
                    visualDimension = 0,
                    status = "FAILED"
                )
            }
            delay(10)
        }
        count
    }

    suspend fun indexPendingScreenshots(batchLimit: Int = 50): Int = withContext(Dispatchers.IO) {
        if (!indexingMutex.tryLock()) {
            Log.d(TAG, "Indexing is already running, skipping overlapping execution")
            return@withContext 0
        }

        _isIndexingActive.value = true
        var indexedCount = 0

        try {
            // Recover any items interrupted while PROCESSING (e.g. app restart or crash)
            dao.resetInterruptedProcessing()

            // Phase 1: Fast OCR & Text Embedding
            while (_isIndexingActive.value) {
                val pending = dao.getAwaitingIndexing(batchLimit)
                if (pending.isEmpty()) break

                for (entity in pending) {
                    if (!_isIndexingActive.value) break // Cooperative pause
                    _currentProcessingName.value = entity.displayName
                    val success = indexer.indexScreenshot(entity)
                    if (success) indexedCount++
                    // Brief throttle to maintain device responsiveness
                    delay(20)
                }
            }

            // Phase 2: Background Visual Embeddings for images needing visual vectors
            while (_isIndexingActive.value) {
                val visualBatchCount = indexPendingVisualEmbeddings(batchLimit = 20)
                if (visualBatchCount == 0) break
                delay(20)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in indexPendingScreenshots", e)
        } finally {
            _currentProcessingName.value = null
            _isIndexingActive.value = false
            indexingMutex.unlock()
            refreshDuplicateGroups()
        }
        indexedCount
    }

    fun startIndexing() {
        if (_isIndexingActive.value) return
        _isIndexingActive.value = true

        // 1. Enqueue background WorkManager so indexing continues when app is minimized or phone is locked
        try {
            IndexingWorker.enqueue(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not enqueue background worker: ${e.message}")
        }

        // 2. Launch persistent application-scope coroutine for immediate live progress
        activeIndexingJob?.cancel()
        activeIndexingJob = appScope.launch {
            indexPendingScreenshots()
        }
    }

    fun stopIndexing() {
        _isIndexingActive.value = false
        _currentProcessingName.value = null
        activeIndexingJob?.cancel()
        try {
            IndexingWorker.cancel(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not cancel background worker: ${e.message}")
        }
    }

    fun parseQuery(rawQuery: String, overrideMode: SearchMode? = null): ParsedQuery {
        return searchEngine.parseQuery(rawQuery, overrideMode)
    }

    suspend fun search(query: String, overrideMode: SearchMode? = null): List<SearchResult> = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        val results = searchEngine.search(query, overrideMode)
        _lastSearchLatencyMs.value = System.currentTimeMillis() - start
        results
    }

    suspend fun findVisuallySimilar(targetId: Long): List<SearchResult> = withContext(Dispatchers.Default) {
        searchEngine.findVisuallySimilar(targetId)
    }

    suspend fun refreshDuplicateGroups() = withContext(Dispatchers.IO) {
        val items = dao.getAllDuplicateHashInfo()
        val groups = mutableListOf<DuplicateGroup>()
        val processedIds = HashSet<Long>()

        for (i in items.indices) {
            val itemA = items[i]
            if (processedIds.contains(itemA.id)) continue

            val matchedDuplicates = mutableListOf<ScreenshotItem>()
            var isExact = false

            for (j in (i + 1) until items.size) {
                val itemB = items[j]
                if (processedIds.contains(itemB.id)) continue

                val isExactMatch = itemA.contentHash.isNotEmpty() && itemA.contentHash == itemB.contentHash
                val isNearMatch = PerceptualHasher.isNearDuplicate(itemA.perceptualHash, itemB.perceptualHash, threshold = 8)

                if (isExactMatch || isNearMatch) {
                    val fullItemB = dao.getById(itemB.id)?.toDomain()
                    if (fullItemB != null) {
                        matchedDuplicates.add(fullItemB)
                        processedIds.add(itemB.id)
                    }
                    if (isExactMatch) isExact = true
                }
            }

            if (matchedDuplicates.isNotEmpty()) {
                val fullItemA = dao.getById(itemA.id)?.toDomain()
                if (fullItemA != null) {
                    processedIds.add(itemA.id)
                    groups.add(
                        DuplicateGroup(
                            groupId = "group_${itemA.id}",
                            isExactDuplicate = isExact,
                            representative = fullItemA,
                            duplicates = matchedDuplicates,
                            similarityDescription = if (isExact) "Identical byte hash copy" else "Near-duplicate visual match (diff <= 8 bits)"
                        )
                    )
                }
            }
        }
        _duplicateGroups.value = groups
    }

    fun getScreenshotsByCategory(category: ScreenshotCategory): Flow<List<ScreenshotItem>> {
        return dao.getByCategoryFlow(category.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getScreenshotsByImageSource(imageSource: String): Flow<List<ScreenshotItem>> {
        return dao.getByImageSourceFlow(imageSource).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getScreenshotById(id: Long): ScreenshotItem? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    suspend fun reindexItem(id: Long): Boolean = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext false
        val ok = indexer.indexScreenshot(entity)
        if (ok) refreshDuplicateGroups()
        ok
    }

    suspend fun clearIndex() = withContext(Dispatchers.IO) {
        dao.clearAll()
        _duplicateGroups.value = emptyList()
    }

    suspend fun resetAllForReindexing() = withContext(Dispatchers.IO) {
        dao.resetAllForReindexing()
    }

    suspend fun seedSampleScreenshots(): Int = withContext(Dispatchers.IO) {
        val count = generator.generateSampleScreenshots()
        discoverAndReconcile()
        count
    }

    suspend fun importImages(uris: List<android.net.Uri>): Int = withContext(Dispatchers.IO) {
        val newEntities = mutableListOf<ScreenshotEntity>()
        for (uri in uris) {
            val item = discovery.extractItemFromUri(uri)
            if (item != null) {
                val existing = dao.getByMediaStoreId(item.mediaStoreId)
                if (existing == null) {
                    newEntities.add(ScreenshotEntity.fromDomain(item))
                }
            }
        }
        if (newEntities.isNotEmpty()) {
            dao.insertAll(newEntities)
            refreshDuplicateGroups()
        }
        newEntities.size
    }

    companion object {
        private const val TAG = "ScreenshotRepository"

        @Volatile
        private var INSTANCE: ScreenshotRepository? = null

        fun getInstance(context: Context): ScreenshotRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = ScreenshotRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
