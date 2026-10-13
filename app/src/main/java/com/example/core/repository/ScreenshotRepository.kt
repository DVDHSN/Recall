package com.example.core.repository

import android.content.Context
import android.util.Log
import com.example.core.database.RecallDatabase
import com.example.core.database.ScreenshotDao
import com.example.core.database.ScreenshotEntity
import com.example.core.indexing.ScreenshotIndexer
import com.example.core.indexing.SyntheticScreenshotGenerator
import com.example.core.media.ScreenshotDiscovery
import com.example.core.ml.embedding.OnnxTextEmbeddingEngine
import com.example.core.ml.embedding.TextEmbeddingEngine
import com.example.core.ml.ocr.MlKitOcrEngine
import com.example.core.ml.ocr.OcrEngine
import com.example.core.model.DuplicateGroup
import com.example.core.model.IndexStats
import com.example.core.model.IndexingStatus
import com.example.core.model.ScreenshotCategory
import com.example.core.model.ScreenshotItem
import com.example.core.model.SearchResult
import com.example.core.search.SemanticSearchEngine
import com.example.core.search.query.ParsedQuery
import com.example.core.search.query.SearchMode
import com.example.core.visual.PerceptualHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ScreenshotRepository(
    private val context: Context,
    private val database: RecallDatabase = RecallDatabase.getInstance(context),
    private val discovery: ScreenshotDiscovery = ScreenshotDiscovery(context),
    private val ocrEngine: OcrEngine = MlKitOcrEngine.getInstance(),
    private val embeddingEngine: TextEmbeddingEngine = OnnxTextEmbeddingEngine.getInstance(context)
) {
    private val dao: ScreenshotDao = database.screenshotDao()
    private val indexer = ScreenshotIndexer(discovery, ocrEngine, embeddingEngine, dao)
    private val searchEngine = SemanticSearchEngine(embeddingEngine, dao)
    private val generator = SyntheticScreenshotGenerator(context)

    private val _isIndexingActive = MutableStateFlow(false)
    val isIndexingActive = _isIndexingActive.asStateFlow()

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
            lastSearchLatencyMs = latency,
            duplicateGroupCount = dupGroups.size
        )
    }

    suspend fun discoverAndReconcile(): Int = withContext(Dispatchers.IO) {
        var discovered = discovery.discoverScreenshots()

        // If no screenshots exist (e.g. fresh emulator), generate realistic samples
        if (discovered.isEmpty()) {
            val generated = generator.generateSampleScreenshots()
            if (generated > 0) {
                discovered = discovery.discoverScreenshots()
            }
        }

        if (discovered.isNotEmpty()) {
            val existingIds = dao.getAllMediaStoreIds().toSet()
            val newEntities = mutableListOf<ScreenshotEntity>()

            for (item in discovered) {
                if (!existingIds.contains(item.mediaStoreId)) {
                    newEntities.add(ScreenshotEntity.fromDomain(item))
                } else {
                    // Check if modified
                    val existing = dao.getByMediaStoreId(item.mediaStoreId)
                    if (existing != null && existing.dateModified != item.dateModified) {
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
                dao.insertAll(newEntities)
            }

            // Remove deleted items
            val currentMediaIds = discovered.map { it.mediaStoreId }
            dao.deleteMissing(currentMediaIds)
        }

        refreshDuplicateGroups()
        discovered.size
    }

    suspend fun indexPendingScreenshots(batchLimit: Int = 100): Int = withContext(Dispatchers.IO) {
        if (_isIndexingActive.value) return@withContext 0

        _isIndexingActive.value = true
        var indexedCount = 0

        try {
            val pending = dao.getAwaitingIndexing(batchLimit)
            for (entity in pending) {
                if (!_isIndexingActive.value) break // Cooperative pause
                _currentProcessingName.value = entity.displayName
                val success = indexer.indexScreenshot(entity)
                if (success) indexedCount++
                // Brief pause to prevent thermal saturation
                kotlinx.coroutines.delay(40)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in indexPendingScreenshots", e)
        } finally {
            _currentProcessingName.value = null
            _isIndexingActive.value = false
            refreshDuplicateGroups()
        }
        indexedCount
    }

    fun stopIndexing() {
        _isIndexingActive.value = false
        _currentProcessingName.value = null
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
