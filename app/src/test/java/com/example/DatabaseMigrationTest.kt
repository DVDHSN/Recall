package com.example

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.RecallDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DatabaseMigrationTest {

    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE screenshots (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            mediaStoreId INTEGER NOT NULL,
                            contentUri TEXT NOT NULL,
                            displayName TEXT NOT NULL,
                            relativePath TEXT NOT NULL,
                            mimeType TEXT NOT NULL,
                            width INTEGER NOT NULL,
                            height INTEGER NOT NULL,
                            sizeBytes INTEGER NOT NULL,
                            dateAdded INTEGER NOT NULL,
                            dateModified INTEGER NOT NULL,
                            dateCaptured INTEGER NOT NULL DEFAULT 0,
                            contentFingerprint TEXT NOT NULL DEFAULT '',
                            ocrText TEXT NOT NULL DEFAULT '',
                            indexingStatus TEXT NOT NULL,
                            lastIndexedAt INTEGER NOT NULL DEFAULT 0,
                            embeddingModelVersion TEXT NOT NULL DEFAULT '',
                            embeddingDimension INTEGER NOT NULL DEFAULT 0,
                            embeddingBlob BLOB,
                            ocrError TEXT,
                            embeddingError TEXT
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE UNIQUE INDEX index_screenshots_mediaStoreId ON screenshots(mediaStoreId)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
        db = helper.writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
        helper.close()
    }

    @Test
    fun testMigrationFromV1ToV2AndV2ToV3PreservesData() {
        // 1. Insert test record in schema v1
        db.execSQL(
            """
            INSERT INTO screenshots (
                mediaStoreId, contentUri, displayName, relativePath, mimeType,
                width, height, sizeBytes, dateAdded, dateModified, indexingStatus
            ) VALUES (
                101, 'content://media/101', 'Test_Screenshot.png', 'Pictures/Screenshots', 'image/png',
                1080, 1920, 50000, 1000, 1000, 'INDEXED'
            )
            """.trimIndent()
        )

        // 2. Execute MIGRATION_1_2
        RecallDatabase.MIGRATION_1_2.migrate(db)

        // Verify v2 columns
        var cursor = db.query("SELECT contentHash, perceptualHash, categoryLabels, classificationConfidence FROM screenshots WHERE mediaStoreId = 101")
        cursor.use {
            assertTrue("Row must exist after migration 1->2", it.moveToFirst())
            assertEquals("", it.getString(0)) // contentHash
            assertEquals(0L, it.getLong(1)) // perceptualHash
            assertEquals("", it.getString(2)) // categoryLabels
            assertEquals(0.0, it.getDouble(3), 1e-4) // classificationConfidence
        }

        // 3. Execute MIGRATION_2_3
        RecallDatabase.MIGRATION_2_3.migrate(db)

        // Verify v3 columns
        cursor = db.query("SELECT bucketName, imageSource, displayName FROM screenshots WHERE mediaStoreId = 101")
        cursor.use {
            assertTrue("Row must exist after migration 2->3", it.moveToFirst())
            assertEquals("", it.getString(0)) // bucketName
            assertEquals("OTHER", it.getString(1)) // imageSource
            assertEquals("Test_Screenshot.png", it.getString(2)) // displayName preserved intact
        }

        // 4. Execute MIGRATION_3_4
        RecallDatabase.MIGRATION_3_4.migrate(db)

        // Verify v4 columns
        cursor = db.query("SELECT visualModelVersion, visualDimension, visualEmbeddingStatus, displayName FROM screenshots WHERE mediaStoreId = 101")
        cursor.use {
            assertTrue("Row must exist after migration 3->4", it.moveToFirst())
            assertEquals("", it.getString(0)) // visualModelVersion
            assertEquals(0, it.getInt(1)) // visualDimension
            assertEquals("PENDING", it.getString(2)) // visualEmbeddingStatus
            assertEquals("Test_Screenshot.png", it.getString(3)) // displayName preserved intact
        }
    }
}
