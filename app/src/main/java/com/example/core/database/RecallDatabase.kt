package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ScreenshotEntity::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RecallDatabase : RoomDatabase() {
    abstract fun screenshotDao(): ScreenshotDao

    companion object {
        @Volatile
        private var INSTANCE: RecallDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE screenshots ADD COLUMN contentHash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN perceptualHash INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN visualBlob BLOB")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN categoryLabels TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN classificationConfidence REAL NOT NULL DEFAULT 0.0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_screenshots_contentHash ON screenshots(contentHash)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_screenshots_perceptualHash ON screenshots(perceptualHash)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE screenshots ADD COLUMN bucketName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN imageSource TEXT NOT NULL DEFAULT 'OTHER'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_screenshots_imageSource ON screenshots(imageSource)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_screenshots_bucketName ON screenshots(bucketName)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE screenshots ADD COLUMN visualModelVersion TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN visualDimension INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN visualEmbeddingStatus TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_screenshots_visualEmbeddingStatus ON screenshots(visualEmbeddingStatus)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE screenshots ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE screenshots ADD COLUMN lastAttemptAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): RecallDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecallDatabase::class.java,
                    "recall_screenshots.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
