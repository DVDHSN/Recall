package com.example.evaluation

import com.example.core.database.ScreenshotEntity
import com.example.core.model.IndexingStatus
import com.example.core.model.ScreenshotCategory

data class EvalQuery(
    val id: String,
    val queryText: String,
    val expectedDocIds: Set<Long>,
    val category: String,
    val expectedTopic: ScreenshotCategory? = null,
    val isExactImportant: Boolean = false,
    val hasDateConstraint: Boolean = false
)

object EvaluationDataset {

    // Test corpus representing synthetic realistic screenshot archive
    val testCorpus = listOf(
        ScreenshotEntity(
            id = 1,
            mediaStoreId = 101,
            contentUri = "content://media/1",
            displayName = "Screenshot_Wifi_University.png",
            relativePath = "Pictures/Screenshots",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 55000,
            dateAdded = 1791860000,
            dateModified = 1791869000, // Today
            ocrText = "SSID: Campus_HighSpeed_5G Security: WPA3 Password: BlueRiver#9281 IPv4: 192.168.1.142 Gateway: 192.168.1.1 DNS: 1.1.1.1",
            indexingStatus = IndexingStatus.INDEXED,
            embeddingDimension = 4,
            embeddingBlob = floatArrayOf(0f, 1f, 0f, 0f),
            perceptualHash = 0x1111222233334444L,
            contentHash = "hash_wifi_1",
            categoryLabels = "NETWORK_TECH"
        ),
        ScreenshotEntity(
            id = 2,
            mediaStoreId = 102,
            contentUri = "content://media/2",
            displayName = "Screenshot_Recipe_Curry.png",
            relativePath = "Pictures/Screenshots",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 62000,
            dateAdded = 1791780000,
            dateModified = 1791782000, // Yesterday
            ocrText = "Creamy Coconut Chickpea Curry: 2 cans organic chickpeas (garbanzo beans), 1 can coconut milk, diced onion, garlic, cumin, spinach.",
            indexingStatus = IndexingStatus.INDEXED,
            embeddingDimension = 4,
            embeddingBlob = floatArrayOf(1f, 0f, 0f, 0f),
            perceptualHash = 0x5555666677778888L,
            contentHash = "hash_recipe_2",
            categoryLabels = "RECIPES"
        ),
        ScreenshotEntity(
            id = 3,
            mediaStoreId = 103,
            contentUri = "content://media/3",
            displayName = "Screenshot_Python_NullPointer.png",
            relativePath = "Pictures/Screenshots",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 48000,
            dateAdded = 1791780000,
            dateModified = 1791781000, // Yesterday
            ocrText = "Traceback (most recent call last): File user_controller.py line 142 AttributeError: 'NoneType' object has no attribute profile. java.lang.NullPointerException in backend service. Error Code 500",
            indexingStatus = IndexingStatus.INDEXED,
            embeddingDimension = 4,
            embeddingBlob = floatArrayOf(0f, 0f, 1f, 0f),
            perceptualHash = 0x9999AAAABBBBCCCCuL.toLong(),
            contentHash = "hash_python_3",
            categoryLabels = "CODE_ERROR"
        ),
        ScreenshotEntity(
            id = 4,
            mediaStoreId = 104,
            contentUri = "content://media/4",
            displayName = "Screenshot_GPU_RTX4070.png",
            relativePath = "Pictures/Screenshots",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 70000,
            dateAdded = 1791600000,
            dateModified = 1791610000, // 3 days ago
            ocrText = "GeForce RTX 4070 Super (12GB GDDR6X) MSRP $599 Cyberpunk 2077 94 FPS vs Radeon RX 7800 XT 16GB $499 88 FPS benchmark comparison.",
            indexingStatus = IndexingStatus.INDEXED,
            embeddingDimension = 4,
            embeddingBlob = floatArrayOf(0f, 0f, 0f, 1f),
            perceptualHash = 0xDDDDEEEEFFFF0000uL.toLong(),
            contentHash = "hash_gpu_4",
            categoryLabels = "SHOPPING_PRICES"
        ),
        ScreenshotEntity(
            id = 5,
            mediaStoreId = 105,
            contentUri = "content://media/5",
            displayName = "Screenshot_Biology_Phototropism.png",
            relativePath = "Pictures/Screenshots",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 53000,
            dateAdded = 1791500000,
            dateModified = 1791510000, // 4 days ago
            ocrText = "Biology 201 Key Concept: Phototropism. Plants grow toward unilateral light stimulus mediated by auxin (IAA) hormone redistribution to shaded side of stem.",
            indexingStatus = IndexingStatus.INDEXED,
            embeddingDimension = 4,
            embeddingBlob = floatArrayOf(0.7f, 0.7f, 0f, 0f),
            perceptualHash = 0x1234123412341234L,
            contentHash = "hash_biology_5",
            categoryLabels = "STUDY_NOTES"
        ),
        ScreenshotEntity(
            id = 6,
            mediaStoreId = 106,
            contentUri = "content://media/6",
            displayName = "Screenshot_HTTP_404_Error.png",
            relativePath = "Pictures/Screenshots",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 41000,
            dateAdded = 1791400000,
            dateModified = 1791410000,
            ocrText = "GET https://api.stripe.com/v1/payment_intents/pi_9381729 Status: HTTP 404 Not Found error: resource_missing No such payment_intent",
            indexingStatus = IndexingStatus.INDEXED,
            embeddingDimension = 4,
            embeddingBlob = floatArrayOf(0f, 0f, 0.9f, 0f),
            perceptualHash = 0x5678567856785678L,
            contentHash = "hash_http_6",
            categoryLabels = "CODE_ERROR"
        ),
        ScreenshotEntity(
            id = 7,
            mediaStoreId = 107,
            contentUri = "content://media/7",
            displayName = "Screenshot_Apartment_Lease.png",
            relativePath = "Pictures/Screenshots",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 59000,
            dateAdded = 1791300000,
            dateModified = 1791310000,
            ocrText = "Apartment Lease Summary Tenant: Jordan Miller Address: 742 Evergreen Terrace Apt 3B Springfield Monthly Rent: $1,450.00 Due 1st of every month Access Code: #4829",
            indexingStatus = IndexingStatus.INDEXED,
            embeddingDimension = 4,
            embeddingBlob = floatArrayOf(0.2f, 0f, 0f, 0.8f),
            perceptualHash = 0x9ABC9ABC9ABC9ABCuL.toLong(),
            contentHash = "hash_lease_7",
            categoryLabels = "FINANCE_RECEIPTS"
        ),
        ScreenshotEntity(
            id = 8,
            mediaStoreId = 108,
            contentUri = "content://media/8",
            displayName = "Screenshot_Recipe_Curry_Copy.png", // Near-duplicate of 2
            relativePath = "Pictures/Screenshots",
            mimeType = "image/png",
            width = 1080,
            height = 1920,
            sizeBytes = 62100,
            dateAdded = 1791780005,
            dateModified = 1791782005, // Yesterday
            ocrText = "Creamy Coconut Chickpea Curry: 2 cans organic chickpeas (garbanzo beans), 1 can coconut milk, diced onion, garlic, cumin, spinach.",
            indexingStatus = IndexingStatus.INDEXED,
            embeddingDimension = 4,
            embeddingBlob = floatArrayOf(1f, 0f, 0f, 0f),
            perceptualHash = 0x5555666677778889L, // 1 bit diff from #2!
            contentHash = "hash_recipe_2_copy",
            categoryLabels = "RECIPES"
        )
    )

    // 65 comprehensive queries covering all test scenarios
    val queries: List<EvalQuery> = listOf(
        // Category 1: Recipes & Ingredients (10 queries)
        EvalQuery("Q01", "recipe with chickpeas", setOf(2L, 8L), "Recipe", ScreenshotCategory.RECIPES),
        EvalQuery("Q02", "chickpeas and coconut milk", setOf(2L, 8L), "Recipe", ScreenshotCategory.RECIPES),
        EvalQuery("Q03", "garbanzo beans curry", setOf(2L, 8L), "Recipe", ScreenshotCategory.RECIPES),
        EvalQuery("Q04", "dinner recipe with spinach and onion", setOf(2L, 8L), "Recipe", ScreenshotCategory.RECIPES),
        EvalQuery("Q05", "coconut milk stew", setOf(2L, 8L), "Recipe", ScreenshotCategory.RECIPES),
        EvalQuery("Q06", "cumin and garlic chickpea", setOf(2L, 8L), "Recipe", ScreenshotCategory.RECIPES),
        EvalQuery("Q07", "how to make chickpea curry", setOf(2L, 8L), "Recipe", ScreenshotCategory.RECIPES),
        EvalQuery("Q08", "creamy coconut chickpeas", setOf(2L, 8L), "Recipe", ScreenshotCategory.RECIPES),
        EvalQuery("Q09", "ingredients for garbanzo curry", setOf(2L, 8L), "Recipe", ScreenshotCategory.RECIPES),
        EvalQuery("Q10", "canned chickpeas recipe", setOf(2L, 8L), "Recipe", ScreenshotCategory.RECIPES),

        // Category 2: Programming Errors & Stacktraces (10 queries)
        EvalQuery("Q11", "Python null pointer error", setOf(3L), "Code", ScreenshotCategory.CODE_ERROR),
        EvalQuery("Q12", "AttributeError NoneType object", setOf(3L), "Code", ScreenshotCategory.CODE_ERROR),
        EvalQuery("Q13", "user_controller.py line 142", setOf(3L), "Code", ScreenshotCategory.CODE_ERROR, isExactImportant = true),
        EvalQuery("Q14", "Traceback most recent call last", setOf(3L), "Code", ScreenshotCategory.CODE_ERROR),
        EvalQuery("Q15", "NullPointerException stack trace", setOf(3L), "Code", ScreenshotCategory.CODE_ERROR),
        EvalQuery("Q16", "backend service 500 error", setOf(3L), "Code", ScreenshotCategory.CODE_ERROR),
        EvalQuery("Q17", "NoneType has no attribute profile", setOf(3L), "Code", ScreenshotCategory.CODE_ERROR),
        EvalQuery("Q18", "Python crash stacktrace", setOf(3L), "Code", ScreenshotCategory.CODE_ERROR),
        EvalQuery("Q19", "Null pointer exception Python", setOf(3L), "Code", ScreenshotCategory.CODE_ERROR),
        EvalQuery("Q20", "user_controller crash error", setOf(3L), "Code", ScreenshotCategory.CODE_ERROR),

        // Category 3: Network & Wi-Fi Details (8 queries)
        EvalQuery("Q21", "school wifi password", setOf(1L), "Network", ScreenshotCategory.NETWORK_TECH),
        EvalQuery("Q22", "Campus_HighSpeed_5G", setOf(1L), "Network", ScreenshotCategory.NETWORK_TECH, isExactImportant = true),
        EvalQuery("Q23", "Wi-Fi password BlueRiver#9281", setOf(1L), "Network", ScreenshotCategory.NETWORK_TECH, isExactImportant = true),
        EvalQuery("Q24", "router gateway credentials", setOf(1L), "Network", ScreenshotCategory.NETWORK_TECH),
        EvalQuery("Q25", "WPA3 security wifi", setOf(1L), "Network", ScreenshotCategory.NETWORK_TECH),
        EvalQuery("Q26", "DNS server 1.1.1.1", setOf(1L), "Network", ScreenshotCategory.NETWORK_TECH),
        EvalQuery("Q27", "campus network details", setOf(1L), "Network", ScreenshotCategory.NETWORK_TECH),
        EvalQuery("Q28", "wifi SSID and password", setOf(1L), "Network", ScreenshotCategory.NETWORK_TECH),

        // Category 4: Technical Identifiers & IP Addresses (8 queries)
        EvalQuery("Q29", "192.168.1.1", setOf(1L), "Identifier", isExactImportant = true),
        EvalQuery("Q30", "192.168.1.142", setOf(1L), "Identifier", isExactImportant = true),
        EvalQuery("Q31", "HTTP 404", setOf(6L), "Identifier", isExactImportant = true),
        EvalQuery("Q32", "404 Not Found", setOf(6L), "Identifier", isExactImportant = true),
        EvalQuery("Q33", "pi_9381729", setOf(6L), "Identifier", isExactImportant = true),
        EvalQuery("Q34", "api.stripe.com payment", setOf(6L), "Identifier", isExactImportant = true),
        EvalQuery("Q35", "resource_missing Stripe", setOf(6L), "Identifier"),
        EvalQuery("Q36", "NullPointerException", setOf(3L), "Identifier", isExactImportant = true),

        // Category 5: Hardware & Benchmark Comparisons (7 queries)
        EvalQuery("Q37", "RTX 4070 Super benchmark", setOf(4L), "Shopping", ScreenshotCategory.SHOPPING_PRICES),
        EvalQuery("Q38", "graphics card comparison 4070 vs 7800", setOf(4L), "Shopping", ScreenshotCategory.SHOPPING_PRICES),
        EvalQuery("Q39", "Cyberpunk 2077 FPS benchmark", setOf(4L), "Shopping", ScreenshotCategory.SHOPPING_PRICES),
        EvalQuery("Q40", "Radeon RX 7800 XT price", setOf(4L), "Shopping", ScreenshotCategory.SHOPPING_PRICES),
        EvalQuery("Q41", "12GB GDDR6X MSRP $599", setOf(4L), "Shopping", ScreenshotCategory.SHOPPING_PRICES, isExactImportant = true),
        EvalQuery("Q42", "GPU 94 FPS average", setOf(4L), "Shopping", ScreenshotCategory.SHOPPING_PRICES),
        EvalQuery("Q43", "graphics card with 16GB VRAM", setOf(4L), "Shopping", ScreenshotCategory.SHOPPING_PRICES),

        // Category 6: Study Notes & Plant Physiology (7 queries)
        EvalQuery("Q44", "notes about plants growing toward light", setOf(5L), "Notes", ScreenshotCategory.STUDY_NOTES),
        EvalQuery("Q45", "Biology 201 lecture notes", setOf(5L), "Notes", ScreenshotCategory.STUDY_NOTES),
        EvalQuery("Q46", "Phototropism auxin hormone", setOf(5L), "Notes", ScreenshotCategory.STUDY_NOTES),
        EvalQuery("Q47", "unilateral light stimulus shoot", setOf(5L), "Notes", ScreenshotCategory.STUDY_NOTES),
        EvalQuery("Q48", "plant stem elongation shaded side", setOf(5L), "Notes", ScreenshotCategory.STUDY_NOTES),
        EvalQuery("Q49", "Biology plant physiology notes", setOf(5L), "Notes", ScreenshotCategory.STUDY_NOTES),
        EvalQuery("Q50", "hormone redistribution to shaded side", setOf(5L), "Notes", ScreenshotCategory.STUDY_NOTES),

        // Category 7: Finance & Apartment Lease (5 queries)
        EvalQuery("Q51", "apartment lease rent contract", setOf(7L), "Finance", ScreenshotCategory.FINANCE_RECEIPTS),
        EvalQuery("Q52", "Monthly Rent $1,450", setOf(7L), "Finance", ScreenshotCategory.FINANCE_RECEIPTS, isExactImportant = true),
        EvalQuery("Q53", "Jordan Miller 742 Evergreen Terrace", setOf(7L), "Finance", ScreenshotCategory.FINANCE_RECEIPTS, isExactImportant = true),
        EvalQuery("Q54", "building access code #4829", setOf(7L), "Finance", ScreenshotCategory.FINANCE_RECEIPTS, isExactImportant = true),
        EvalQuery("Q55", "security deposit held in escrow", setOf(7L), "Finance", ScreenshotCategory.FINANCE_RECEIPTS),

        // Category 8: Date-Constrained Queries (5 queries)
        EvalQuery("Q56", "The Python error I saw yesterday", setOf(3L), "Date", ScreenshotCategory.CODE_ERROR, hasDateConstraint = true),
        EvalQuery("Q57", "chickpea recipe yesterday", setOf(2L, 8L), "Date", ScreenshotCategory.RECIPES, hasDateConstraint = true),
        EvalQuery("Q58", "wifi password from today", setOf(1L), "Date", ScreenshotCategory.NETWORK_TECH, hasDateConstraint = true),
        EvalQuery("Q59", "notes saved this week", setOf(5L), "Date", ScreenshotCategory.STUDY_NOTES, hasDateConstraint = true),
        EvalQuery("Q60", "screenshot from past week", setOf(1L, 2L, 3L, 4L, 5L, 8L), "Date", hasDateConstraint = true),

        // Category 9: Negative & Disambiguation Queries (5 queries)
        EvalQuery("Q61", "error not stripe", setOf(3L), "Negative"),
        EvalQuery("Q62", "recipe without onion", setOf(2L, 8L), "Negative"),
        EvalQuery("Q63", "benchmark exclude cyberpunk", setOf(4L), "Negative"),
        EvalQuery("Q64", "network without dns", setOf(1L), "Negative"),
        EvalQuery("Q65", "lease not deposit", setOf(7L), "Negative")
    )
}
