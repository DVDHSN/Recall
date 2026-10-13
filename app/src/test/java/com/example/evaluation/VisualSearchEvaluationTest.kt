package com.example.evaluation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.DuplicateHashInfo
import com.example.core.database.ScreenshotDao
import com.example.core.database.ScreenshotEntity
import com.example.core.ml.embedding.MobileClipEmbeddingEngine
import com.example.core.ml.embedding.TextEmbeddingEngine
import com.example.core.model.IndexingStatus
import com.example.core.search.SemanticSearchEngine
import com.example.core.search.fusion.CandidateFusion
import com.example.core.search.lexical.LexicalRetriever
import com.example.core.search.query.QueryParser
import com.example.core.search.semantic.SemanticRetriever
import com.example.core.search.visual.VisualRetriever
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VisualSearchEvaluationTest {

    private lateinit var visualEngine: MobileClipEmbeddingEngine
    private lateinit var visualRetriever: VisualRetriever
    private lateinit var textEmbeddingEngine: TextEmbeddingEngine

    private class MockTextEmbeddingEngine : TextEmbeddingEngine {
        override val modelVersion: String = "mock-minilm-v1"
        override val embeddingDimension: Int = 4
        override suspend fun generateEmbedding(text: String): FloatArray {
            val lower = text.lowercase()
            return when {
                lower.contains("wifi") || lower.contains("password") -> floatArrayOf(0f, 1f, 0f, 0f)
                lower.contains("recipe") || lower.contains("curry") -> floatArrayOf(1f, 0f, 0f, 0f)
                lower.contains("error") || lower.contains("exception") -> floatArrayOf(0f, 0f, 1f, 0f)
                else -> floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
            }
        }
        override fun close() {}
    }

    private class VisualMockDao(private val corpus: List<ScreenshotEntity>) : ScreenshotDao {
        override fun getAllFlow(): Flow<List<ScreenshotEntity>> = flowOf(corpus)
        override suspend fun getPaged(limit: Int, offset: Int): List<ScreenshotEntity> = corpus
        override suspend fun getById(id: Long): ScreenshotEntity? = corpus.find { it.id == id }
        override suspend fun getByMediaStoreId(mediaStoreId: Long): ScreenshotEntity? = corpus.find { it.mediaStoreId == mediaStoreId }
        override suspend fun getAllMediaStoreIds(): List<Long> = corpus.map { it.mediaStoreId }
        override suspend fun getByStatus(status: IndexingStatus, limit: Int): List<ScreenshotEntity> = corpus.filter { it.indexingStatus == status }
        override suspend fun getAwaitingIndexing(limit: Int): List<ScreenshotEntity> = emptyList()
        override suspend fun getAllIndexedWithEmbeddings(): List<ScreenshotEntity> = corpus
        override suspend fun getAllSearchable(): List<ScreenshotEntity> = corpus
        override fun getByCategoryFlow(category: String): Flow<List<ScreenshotEntity>> = flowOf(corpus.filter { it.categoryLabels.contains(category) })
        override suspend fun getAllDuplicateHashInfo(): List<DuplicateHashInfo> = emptyList()
        override suspend fun getCandidateDuplicates(currentId: Long): List<ScreenshotEntity> = corpus.filter { it.id != currentId }
        override fun getTotalCountFlow(): Flow<Int> = flowOf(corpus.size)
        override fun getIndexedCountFlow(): Flow<Int> = flowOf(corpus.size)
        override fun getEmptyTextCountFlow(): Flow<Int> = flowOf(0)
        override fun getFailedCountFlow(): Flow<Int> = flowOf(0)
        override fun getPendingCountFlow(): Flow<Int> = flowOf(0)
        override suspend fun searchLexical(query: String): List<ScreenshotEntity> = emptyList()
        override suspend fun searchByDateRange(startSec: Long, endSec: Long): List<ScreenshotEntity> = corpus.filter { it.dateModified in startSec..endSec }
        override suspend fun insertAll(entities: List<ScreenshotEntity>): List<Long> = emptyList()
        override suspend fun insertOrUpdate(entity: ScreenshotEntity): Long = 0L
        override suspend fun update(entity: ScreenshotEntity) {}
        override suspend fun updateStatus(id: Long, status: IndexingStatus) {}
        override suspend fun deleteMissing(keepMediaStoreIds: List<Long>): Int = 0
        override suspend fun deleteById(id: Long) {}
        override suspend fun clearAll() {}
        override suspend fun resetAllForReindexing() {}
        override suspend fun retryFailed(): Int = 0
        override suspend fun resetInterruptedProcessing(): Int = 0
        override suspend fun getAwaitingVisualEmbedding(limit: Int): List<ScreenshotEntity> = emptyList()
        override suspend fun updateVisualEmbedding(id: Long, visualBlob: FloatArray?, visualModelVersion: String, visualDimension: Int, status: String) {}
        override fun getVisualPendingCountFlow(): Flow<Int> = flowOf(0)
        override suspend fun getAllWithVisualEmbeddings(): List<ScreenshotEntity> = corpus.filter { it.visualBlob != null }
        override fun getByImageSourceFlow(imageSource: String): Flow<List<ScreenshotEntity>> = flowOf(corpus.filter { it.imageSource == imageSource })
        override suspend fun deleteAllMediaStoreItems(): Int = 0
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        visualEngine = MobileClipEmbeddingEngine.getInstance(context)
        visualRetriever = VisualRetriever(visualEngine)
        textEmbeddingEngine = MockTextEmbeddingEngine()
    }

    private fun generateSyntheticVisualImage(action: (Canvas, Paint) -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        action(canvas, paint)
        return bitmap
    }

    @Test
    fun testVisualEmbeddingEngineNormalizationAndDimensions() = runBlocking {
        val redCarBitmap = generateSyntheticVisualImage { canvas, paint ->
            canvas.drawColor(Color.BLACK)
            paint.color = Color.RED
            canvas.drawRect(40f, 80f, 180f, 160f, paint) // Red vehicle silhouette
        }

        val embedding = visualEngine.generateImageEmbedding(redCarBitmap)
        assertEquals(512, embedding.size)

        // Verify L2 unit norm
        var sumSquares = 0.0
        for (v in embedding) sumSquares += (v * v)
        val norm = sqrt(sumSquares).toFloat()
        assertEquals(1.0f, norm, 1e-3f)

        // Verify text embedding
        val textEmbedding = visualEngine.generateTextEmbedding("A red Ferrari at night")
        assertEquals(512, textEmbedding.size)
        var textSum = 0.0
        for (v in textEmbedding) textSum += (v * v)
        assertEquals(1.0f, sqrt(textSum).toFloat(), 1e-3f)

        // Verify cross-modal cosine similarity
        val sim = MobileClipEmbeddingEngine.cosineSimilarity(textEmbedding, embedding)
        assertTrue("Red Ferrari text must have high visual similarity with red car image ($sim)", sim > 0.35f)
    }

    @Test
    fun test25VisualSearchEvaluationScenarios() = runBlocking {
        // 1. Build synthetic 25-image visual corpus covering key domains
        val corpusEntities = mutableListOf<ScreenshotEntity>()

        // Helper to register synthetic photo
        fun addVisualPhoto(
            id: Long,
            name: String,
            source: String,
            ocrText: String = "",
            painter: (Canvas, Paint) -> Unit
        ) {
            val bmp = generateSyntheticVisualImage(painter)
            val vector = visualEngine.extractMobileClipVisualVector(bmp)
            bmp.recycle()

            corpusEntities.add(
                ScreenshotEntity(
                    id = id,
                    mediaStoreId = 1000 + id,
                    contentUri = "content://media/$id",
                    displayName = name,
                    relativePath = "Pictures/$source",
                    mimeType = "image/jpeg",
                    width = 224,
                    height = 224,
                    sizeBytes = 25000,
                    dateAdded = 1791860000 + id,
                    dateModified = 1791860000 + id,
                    ocrText = ocrText,
                    indexingStatus = if (ocrText.isEmpty()) IndexingStatus.EMPTY_TEXT else IndexingStatus.INDEXED,
                    imageSource = source,
                    visualBlob = vector,
                    visualModelVersion = "MobileCLIP-S0-INT8",
                    visualDimension = 512,
                    visualEmbeddingStatus = "READY"
                )
            )
        }

        // Domain 1: Scenic & Natural Photos (Zero text)
        addVisualPhoto(1, "sunset_beach.jpg", "CAMERA") { canvas, paint ->
            paint.color = Color.rgb(255, 140, 0) // Golden sunset sky
            canvas.drawRect(0f, 0f, 224f, 112f, paint)
            paint.color = Color.rgb(0, 80, 180) // Deep blue ocean water
            canvas.drawRect(0f, 112f, 224f, 224f, paint)
        }
        addVisualPhoto(2, "mountain_peak_aerial.jpg", "CAMERA") { canvas, paint ->
            canvas.drawColor(Color.rgb(120, 120, 130)) // Rocky mountain landscape
            paint.color = Color.WHITE
            canvas.drawCircle(112f, 60f, 40f, paint) // Snowy mountain top
        }
        addVisualPhoto(3, "green_forest_trees.jpg", "CAMERA") { canvas, paint ->
            canvas.drawColor(Color.rgb(20, 140, 40)) // Lush green trees & foliage
        }
        addVisualPhoto(4, "deep_blue_ocean_waves.jpg", "CAMERA") { canvas, paint ->
            canvas.drawColor(Color.rgb(0, 70, 200)) // Pure ocean water
        }

        // Domain 2: Objects & Vehicles
        addVisualPhoto(5, "red_ferrari_night.jpg", "CAMERA") { canvas, paint ->
            canvas.drawColor(Color.BLACK) // Night background
            paint.color = Color.RED // Red sports car
            canvas.drawRect(40f, 90f, 190f, 160f, paint)
        }
        addVisualPhoto(6, "product_box_green_label.jpg", "DOWNLOAD") { canvas, paint ->
            canvas.drawColor(Color.rgb(230, 230, 230)) // Packaging box
            paint.color = Color.rgb(0, 180, 50) // Green product label
            canvas.drawRect(50f, 50f, 174f, 174f, paint)
        }
        addVisualPhoto(7, "black_laptop_desk.jpg", "CAMERA") { canvas, paint ->
            canvas.drawColor(Color.rgb(160, 110, 60)) // Wooden desk
            paint.color = Color.rgb(25, 25, 25) // Dark laptop
            canvas.drawRect(60f, 70f, 164f, 150f, paint)
        }

        // Domain 3: Animals & Pets
        addVisualPhoto(8, "cat_sleeping_sofa.jpg", "CAMERA") { canvas, paint ->
            canvas.drawColor(Color.rgb(180, 150, 120)) // Fabric sofa
            paint.color = Color.rgb(220, 120, 40) // Orange cat curled up
            canvas.drawCircle(112f, 112f, 45f, paint)
        }
        addVisualPhoto(9, "dog_running_park.jpg", "CAMERA") { canvas, paint ->
            canvas.drawColor(Color.rgb(50, 160, 40)) // Green park lawn
            paint.color = Color.rgb(130, 80, 40) // Brown dog
            canvas.drawCircle(112f, 112f, 35f, paint)
        }

        // Domain 4: Diagrams & Educational Science Notes
        addVisualPhoto(10, "science_diagram_blue.png", "DOCUMENTS_NOTES", "Nucleus Electron Cell Membrane") { canvas, paint ->
            canvas.drawColor(Color.WHITE) // Clean white paper
            paint.color = Color.BLUE
            paint.strokeWidth = 4f
            canvas.drawCircle(112f, 112f, 60f, paint) // Science cell diagram
            canvas.drawLine(20f, 20f, 200f, 20f, paint)
        }
        addVisualPhoto(11, "circuit_board_schematic.png", "DOCUMENTS_NOTES", "Resistor Capacitor VCC GND") { canvas, paint ->
            canvas.drawColor(Color.WHITE)
            paint.color = Color.DKGRAY
            canvas.drawRect(30f, 30f, 194f, 194f, paint)
        }

        // Domain 5: Receipts & Text Documents
        addVisualPhoto(12, "grocery_receipt.png", "DOCUMENTS_NOTES", "TOTAL $42.50 Chickpeas Milk Bread Date: Oct 12") { canvas, paint ->
            canvas.drawColor(Color.WHITE)
            paint.color = Color.BLACK
            for (y in 20..200 step 20) {
                canvas.drawLine(30f, y.toFloat(), 190f, y.toFloat(), paint)
            }
        }

        // Domain 6: Distractors & Challenging Variations
        addVisualPhoto(13, "blue_sky_clouds.jpg", "CAMERA") { canvas, paint ->
            canvas.drawColor(Color.rgb(135, 206, 250)) // Sky blue
            paint.color = Color.WHITE
            canvas.drawCircle(70f, 60f, 30f, paint)
        }
        addVisualPhoto(14, "red_apple_table.jpg", "CAMERA") { canvas, paint ->
            canvas.drawColor(Color.rgb(240, 240, 240))
            paint.color = Color.RED
            canvas.drawCircle(112f, 112f, 35f, paint)
        }
        addVisualPhoto(15, "yellow_taxi_street.jpg", "CAMERA") { canvas, paint ->
            canvas.drawColor(Color.DKGRAY)
            paint.color = Color.YELLOW
            canvas.drawRect(50f, 80f, 174f, 150f, paint)
        }

        val dao = VisualMockDao(corpusEntities)
        val searchEngine = SemanticSearchEngine(
            embeddingEngine = textEmbeddingEngine,
            screenshotDao = dao,
            queryParser = QueryParser(),
            lexicalRetriever = LexicalRetriever(),
            semanticRetriever = SemanticRetriever(textEmbeddingEngine),
            candidateFusion = CandidateFusion(),
            visualEngine = visualEngine,
            visualRetriever = visualRetriever
        )

        // 25 Evaluation Test Scenarios
        data class Scenario(val query: String, val expectedTopId: Long, val category: String)
        val scenarios = listOf(
            Scenario("A red Ferrari at night", 5, "Vehicles"),
            Scenario("red sports car in the dark", 5, "Vehicles"),
            Scenario("A golden sunset over the ocean", 1, "Scenery"),
            Scenario("sunset over water", 1, "Scenery"),
            Scenario("A cat sleeping on a sofa", 8, "Animals"),
            Scenario("sleeping pet on couch", 8, "Animals"),
            Scenario("A blue and white school science diagram", 10, "Diagrams"),
            Scenario("science diagram with blue circles", 10, "Diagrams"),
            Scenario("A product box with a green label", 6, "Objects"),
            Scenario("package with green label", 6, "Objects"),
            Scenario("A photo of a mountain from above", 2, "Scenery"),
            Scenario("aerial mountain view landscape", 2, "Scenery"),
            Scenario("deep blue ocean waves", 4, "Scenery"),
            Scenario("green forest trees", 3, "Scenery"),
            Scenario("brown dog running in park", 9, "Animals"),
            Scenario("black laptop computer on desk", 7, "Objects"),
            Scenario("blue sky with clouds", 13, "Scenery"),
            Scenario("red apple on white table", 14, "Objects"),
            Scenario("yellow taxi on dark street", 15, "Vehicles"),
            Scenario("circuit diagram schematic", 11, "Diagrams"),
            Scenario("grocery store receipt total", 12, "Documents"),
            Scenario("golden sunrise beach photo", 1, "Scenery"),
            Scenario("rocky mountain landscape", 2, "Scenery"),
            Scenario("green packaging box product", 6, "Objects"),
            Scenario("white science diagram notes", 10, "Diagrams")
        )

        var hitAt5Count = 0
        var totalLatencyMs = 0L

        println("=== RECALL PHASE 2: VISUAL SEMANTIC SEARCH EVALUATION REPORT ===")
        for ((idx, sc) in scenarios.withIndex()) {
            val start = System.currentTimeMillis()
            val results = searchEngine.search(sc.query, limit = 5)
            val duration = System.currentTimeMillis() - start
            totalLatencyMs += duration

            val topIds = results.map { it.screenshot.id }
            val hit = topIds.contains(sc.expectedTopId)
            if (hit) hitAt5Count++

            val topResult = results.firstOrNull()
            println("Scenario #${idx + 1} [${sc.category}]: \"${sc.query}\"")
            println("  -> Latency: ${duration}ms | Hit@5: $hit | Expected: ID ${sc.expectedTopId}")
            if (topResult != null) {
                println("  -> Top Match: ID ${topResult.screenshot.id} (${topResult.screenshot.displayName})")
                println("     VisualScore: ${topResult.visualScore} | CombinedScore: ${topResult.combinedScore}")
                println("     Signal: ${topResult.explanation}")
            }
        }

        val recallAt5 = (hitAt5Count.toDouble() / scenarios.size.toDouble()) * 100.0
        val avgLatency = totalLatencyMs.toDouble() / scenarios.size.toDouble()

        println("----------------------------------------------------------------")
        println("SUMMARY EVALUATION METRICS:")
        println("Total Queries Evaluated: ${scenarios.size}")
        println("Recall@5: ${"%.1f".format(recallAt5)}% (Target >= 90.0%)")
        println("Average Search Latency: ${"%.1f".format(avgLatency)} ms (Target < 250 ms)")
        println("================================================================")

        assertTrue("Recall@5 must be >= 90% (achieved ${recallAt5}%)", recallAt5 >= 90.0)
        assertTrue("Average latency must be < 250ms (achieved ${avgLatency}ms)", avgLatency < 250.0)
    }
}
