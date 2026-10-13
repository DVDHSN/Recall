package com.example.core.indexing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SyntheticScreenshotGenerator(private val context: Context) {

    data class SampleTemplate(
        val filename: String,
        val title: String,
        val subtitle: String,
        val bodyLines: List<String>,
        val headerColor: Int,
        val accentColor: Int
    )

    private val samples = listOf(
        SampleTemplate(
            filename = "Screenshot_20261012_Wifi_Details.png",
            title = "University Guest Wi-Fi",
            subtitle = "Network Configuration & Security",
            bodyLines = listOf(
                "SSID: Campus_HighSpeed_5G",
                "Security: WPA3-Personal",
                "Password: BlueRiver#9281",
                "IP Assignment: DHCP",
                "IPv4 Address: 192.168.1.142",
                "Default Gateway: 192.168.1.1",
                "DNS Server: 1.1.1.1, 8.8.8.8",
                "Valid until end of semester."
            ),
            headerColor = Color.rgb(24, 76, 120),
            accentColor = Color.rgb(65, 145, 210)
        ),
        SampleTemplate(
            filename = "Screenshot_20261011_Chickpea_Recipe.png",
            title = "Creamy Coconut Chickpea Curry",
            subtitle = "30-Minute Weeknight Dinner",
            bodyLines = listOf(
                "Ingredients:",
                "- 2 cans (15 oz) chickpeas (garbanzo beans), rinsed",
                "- 1 can (14 oz) full-fat coconut milk",
                "- 1 diced yellow onion & 3 cloves garlic",
                "- 1 tbsp ginger paste, 2 tbsp curry powder",
                "- 1 can crushed tomatoes & 2 cups baby spinach",
                "Instructions:",
                "1. Sauté aromatics in coconut oil until fragrant.",
                "2. Simmer chickpeas with coconut milk and spices for 15 min.",
                "3. Fold in baby spinach and squeeze fresh lime juice."
            ),
            headerColor = Color.rgb(180, 83, 9),
            accentColor = Color.rgb(245, 158, 11)
        ),
        SampleTemplate(
            filename = "Screenshot_20261010_Python_NPE_Error.png",
            title = "Crash Stacktrace & Exception",
            subtitle = "Terminal Output - backend_service.py",
            bodyLines = listOf(
                "Traceback (most recent call last):",
                "  File \"/srv/app/user_controller.py\", line 142, in get_account",
                "    profile = auth_service.resolve_profile(user_id)",
                "  File \"/srv/app/auth_service.py\", line 88, in resolve_profile",
                "    return self.cache[user_id].profile",
                "AttributeError: 'NoneType' object has no attribute 'profile'",
                "java.lang.NullPointerException equivalent in Python runtime.",
                "Error Code: 500 Internal Server Error",
                "Fix: Check if user_id exists before referencing cache."
            ),
            headerColor = Color.rgb(153, 27, 27),
            accentColor = Color.rgb(239, 68, 68)
        ),
        SampleTemplate(
            filename = "Screenshot_20261009_GPU_Benchmark.png",
            title = "Graphics Card Comparison 2026",
            subtitle = "Hardware Unboxed 1440p Rasterization",
            bodyLines = listOf(
                "GeForce RTX 4070 Super (12GB GDDR6X) - $599",
                "  Cyberpunk 2077: 94 FPS average (DLSS 3 Enabled)",
                "  Power Consumption: 220W TGP",
                "Radeon RX 7800 XT (16GB GDDR6) - $499",
                "  Cyberpunk 2077: 88 FPS average (FSR 3.1)",
                "  Power Consumption: 263W TBP",
                "Verdict: RTX 4070 Super wins in ray tracing and efficiency."
            ),
            headerColor = Color.rgb(79, 70, 229),
            accentColor = Color.rgb(99, 102, 241)
        ),
        SampleTemplate(
            filename = "Screenshot_20261008_Biology_Notes.png",
            title = "Biology 201 - Plant Physiology",
            subtitle = "Lecture 14: Phototropism & Hormonal Signals",
            bodyLines = listOf(
                "Key Concept: Phototropism",
                "- Plants grow toward unilateral light stimulus.",
                "- Mechanism: Auxin (IAA) hormone synthesized in shoot apex.",
                "- Light detection causes auxin to redistribute to the shaded side.",
                "- Higher auxin concentration on shaded side causes cell elongation.",
                "- Differential elongation bends the stem toward the light source.",
                "Contrast with gravitropism (roots respond to gravity vector)."
            ),
            headerColor = Color.rgb(22, 101, 52),
            accentColor = Color.rgb(34, 197, 94)
        ),
        SampleTemplate(
            filename = "Screenshot_20261007_HTTP_404_Error.png",
            title = "REST API Response",
            subtitle = "Postman Console - Payment Gateway",
            bodyLines = listOf(
                "GET https://api.stripe.com/v1/payment_intents/pi_9381729",
                "Status: HTTP 404 Not Found",
                "Response Body:",
                "{",
                "  \"error\": {",
                "    \"code\": \"resource_missing\",",
                "    \"message\": \"No such payment_intent: pi_9381729.\",",
                "    \"param\": \"id\",",
                "    \"type\": \"invalid_request_error\"",
                "  }",
                "}"
            ),
            headerColor = Color.rgb(185, 28, 28),
            accentColor = Color.rgb(248, 113, 113)
        ),
        SampleTemplate(
            filename = "Screenshot_20261006_Apartment_Lease.png",
            title = "Apartment Lease Summary",
            subtitle = "Metro Residential Property Management",
            bodyLines = listOf(
                "Tenant: Jordan Miller",
                "Address: 742 Evergreen Terrace, Apt 3B, Springfield",
                "Monthly Rent: $1,450.00 (Due 1st of every month)",
                "Security Deposit: $1,450.00 held in escrow",
                "Lease Term: Nov 1, 2026 - Oct 31, 2027",
                "Building Access Code: #4829",
                "Emergency Maintenance Contact: (555) 019-2834"
            ),
            headerColor = Color.rgb(30, 41, 59),
            accentColor = Color.rgb(71, 85, 105)
        ),
        SampleTemplate(
            filename = "Screenshot_20261005_Transformers_AI.png",
            title = "Deep Learning Cheatsheet",
            subtitle = "Transformer Self-Attention Architecture",
            bodyLines = listOf(
                "Attention(Q, K, V) = softmax((Q * K^T) / sqrt(d_k)) * V",
                "- Q: Queries, K: Keys, V: Values",
                "- Multi-Head Attention allows joint attendance to info",
                "  from different representation subspaces at different positions.",
                "- MiniLM uses Knowledge Distillation from BERT-large.",
                "- Generates 384-dimensional dense semantic sentence embeddings.",
                "- Cosine similarity compares dot products of normalized vectors."
            ),
            headerColor = Color.rgb(88, 28, 135),
            accentColor = Color.rgb(168, 85, 247)
        ),
        SampleTemplate(
            filename = "Photo_20261013_Red_Ferrari_Night.jpg",
            title = "A Red Ferrari at Night",
            subtitle = "Exotic Sports Car Exhibition",
            bodyLines = listOf(
                "Automotive Spotlight:",
                "- Vehicle: Ferrari SF90 Stradale in Rosso Corsa red",
                "- Setting: Urban neon street lights at midnight",
                "- Twin-turbocharged V8 hybrid powertrain",
                "- High-contrast visual color silhouette on dark backdrop."
            ),
            headerColor = Color.rgb(185, 28, 28),
            accentColor = Color.rgb(239, 68, 68)
        ),
        SampleTemplate(
            filename = "Photo_20261012_Golden_Sunset_Ocean.jpg",
            title = "A Golden Sunset Over the Ocean",
            subtitle = "Pacific Coastline Golden Hour",
            bodyLines = listOf(
                "Nature Photography:",
                "- Warm golden and orange twilight sky reflecting on ocean waters",
                "- Low horizon with calm deep blue ocean swells",
                "- Captured during autumn equinox at 6:42 PM",
                "- High chromatic saturation across upper and lower visual field."
            ),
            headerColor = Color.rgb(217, 119, 6),
            accentColor = Color.rgb(245, 158, 11)
        ),
        SampleTemplate(
            filename = "Photo_20261011_Cat_Sleeping_Sofa.jpg",
            title = "A Cat Sleeping on a Sofa",
            subtitle = "Domestic Feline Lounging",
            bodyLines = listOf(
                "Pet Moments:",
                "- Ginger tabby cat peacefully curled up sleeping",
                "- Soft textured gray fabric sofa background",
                "- Focal center subject with distinctive fur texture",
                "- Relaxed ambient indoor natural lighting."
            ),
            headerColor = Color.rgb(120, 53, 15),
            accentColor = Color.rgb(217, 119, 6)
        ),
        SampleTemplate(
            filename = "Diagram_20261010_Science_Cell_Structure.png",
            title = "A Blue and White School Science Diagram",
            subtitle = "Cell Biology & Organelles",
            bodyLines = listOf(
                "Educational Diagram:",
                "- High contrast clean white paper background",
                "- Crisp blue circular cell membrane & organelle boundaries",
                "- Labels: Nucleus, Mitochondria, Endoplasmic Reticulum",
                "- High spatial line gradients and geometric structure."
            ),
            headerColor = Color.rgb(29, 78, 216),
            accentColor = Color.rgb(59, 130, 246)
        ),
        SampleTemplate(
            filename = "Photo_20261009_Product_Box_Green_Label.jpg",
            title = "A Product Box with a Green Label",
            subtitle = "Eco-Friendly Packaging",
            bodyLines = listOf(
                "Product Packaging:",
                "- Minimalist recycled cardboard product box",
                "- Prominent emerald green organic certification label",
                "- Crisp rectangular geometric packaging contours",
                "- Distinctive central green chromaticity signature."
            ),
            headerColor = Color.rgb(21, 128, 61),
            accentColor = Color.rgb(34, 197, 94)
        ),
        SampleTemplate(
            filename = "Photo_20261008_Mountain_Above.jpg",
            title = "A Photo of a Mountain from Above",
            subtitle = "Aerial Mountain Range Vista",
            bodyLines = listOf(
                "Geographic Scenery:",
                "- High altitude aerial view overlooking jagged mountain ridge",
                "- Rocky granite texture and snow-capped peaks",
                "- Dramatic elevation gradients and terrain shadows",
                "- Wide angle landscape perspective from aircraft."
            ),
            headerColor = Color.rgb(71, 85, 105),
            accentColor = Color.rgb(100, 116, 139)
        )
    )

    suspend fun generateSampleScreenshots(): Int = withContext(Dispatchers.IO) {
        var createdCount = 0
        val screenshotsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Screenshots")
        if (!screenshotsDir.exists()) {
            screenshotsDir.mkdirs()
        }

        for (sample in samples) {
            val bitmap = renderScreenshotBitmap(sample)
            // Try saving to MediaStore first, fallback to external files
            val uri = saveToMediaStoreOrFile(bitmap, sample.filename, screenshotsDir)
            if (uri != null) {
                createdCount++
            }
            bitmap.recycle()
        }
        Log.i("SyntheticScreenshot", "Generated $createdCount sample screenshots for Recall testing")
        createdCount
    }

    private fun renderScreenshotBitmap(sample: SampleTemplate): Bitmap {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.rgb(245, 247, 250))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Status Bar
        paint.color = Color.rgb(230, 233, 238)
        canvas.drawRect(0f, 0f, width.toFloat(), 72f, paint)

        paint.color = Color.rgb(70, 75, 85)
        paint.textSize = 28f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("9:41", 48f, 48f, paint)
        canvas.drawText("100% 5G", width - 180f, 48f, paint)

        // Header Card
        paint.color = sample.headerColor
        val headerRect = RectF(48f, 120f, width - 48f, 360f)
        canvas.drawRoundRect(headerRect, 32f, 32f, paint)

        // Accent indicator
        paint.color = sample.accentColor
        canvas.drawCircle(96f, 180f, 14f, paint)

        // Header Title
        paint.color = Color.WHITE
        paint.textSize = 46f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(sample.title, 130f, 196f, paint)

        // Header Subtitle
        paint.color = Color.rgb(220, 230, 242)
        paint.textSize = 30f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(sample.subtitle, 96f, 260f, paint)

        // Category Tag
        paint.color = sample.accentColor
        val tagRect = RectF(96f, 290f, 320f, 335f)
        canvas.drawRoundRect(tagRect, 12f, 12f, paint)
        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("RECALL ARCHIVE", 116f, 322f, paint)

        // Content Card
        paint.color = Color.WHITE
        val contentRect = RectF(48f, 390f, width - 48f, 1780f)
        canvas.drawRoundRect(contentRect, 28f, 28f, paint)

        // Content Card Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.rgb(226, 232, 240)
        canvas.drawRoundRect(contentRect, 28f, 28f, paint)
        paint.style = Paint.Style.FILL

        // Content Text
        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = 32f
        paint.typeface = Typeface.MONOSPACE
        var yPos = 470f

        for (line in sample.bodyLines) {
            if (line.startsWith("Traceback") || line.startsWith("Ingredients:") || line.startsWith("Instructions:") || line.startsWith("Key Concept:")) {
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.color = sample.headerColor
            } else if (line.contains("Password:") || line.contains("Error") || line.contains("Rent:") || line.contains("192.168.1.1")) {
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.color = sample.accentColor
            } else {
                paint.typeface = Typeface.DEFAULT
                paint.color = Color.rgb(51, 65, 85)
            }

            canvas.drawText(line, 96f, yPos, paint)
            yPos += 58f
        }

        // Footer watermarked timestamp
        paint.color = Color.rgb(148, 163, 184)
        paint.textSize = 24f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Captured at 2026-10-12 • Device: SM-A536B (Galaxy A53 5G)", 96f, 1730f, paint)

        return bitmap
    }

    private fun saveToMediaStoreOrFile(bitmap: Bitmap, filename: String, fallbackDir: File): Uri? {
        // Attempt MediaStore insertion
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }
                return uri
            }
        } catch (e: Exception) {
            Log.w("SyntheticScreenshot", "MediaStore insertion failed, using internal app directory: ${e.message}")
        }

        // Fallback to internal app storage
        try {
            val file = File(fallbackDir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            return Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e("SyntheticScreenshot", "Fallback file write failed", e)
            return null
        }
    }
}
