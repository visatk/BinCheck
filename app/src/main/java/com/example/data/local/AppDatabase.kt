package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ValidationHistoryEntity::class,
        BatchQueueItemEntity::class,
        BinCacheEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun validationHistoryDao(): ValidationHistoryDao
    abstract fun binCacheDao(): BinCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bin_cache` (
                        `bin` TEXT NOT NULL,
                        `scheme` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `brand` TEXT NOT NULL,
                        `bankName` TEXT NOT NULL,
                        `bankPhone` TEXT NOT NULL,
                        `bankUrl` TEXT NOT NULL,
                        `countryName` TEXT NOT NULL,
                        `countryCode` TEXT NOT NULL,
                        `countryEmoji` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `isPrepaid` INTEGER,
                        `source` TEXT NOT NULL,
                        `isNetworkVerified` INTEGER NOT NULL,
                        `fetchedAtMillis` INTEGER NOT NULL,
                        `expiresAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`bin`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bin_cache_expiresAtMillis` ON `bin_cache` (`expiresAtMillis`)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bincheck_local.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
