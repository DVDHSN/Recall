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
    version = 2,
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

        fun getInstance(context: Context): RecallDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecallDatabase::class.java,
                    "recall_screenshots.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(true)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
