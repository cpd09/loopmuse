package com.example.loopmuse.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SongMetaEntity::class, AlarmEntity::class, LyricsEntity::class, DiscoveryReactionEntity::class], version = 11, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songMetaDao(): SongMetaDao
    abstract fun alarmDao(): AlarmDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun discoveryReactionDao(): DiscoveryReactionDao

    companion object {
        // The shipped versions 1 and 2 contained song_metadata only. Version 4 added
        // alarms; version 3 is handled too in case an intermediate beta was installed.
        private fun migrationFrom(oldVersion: Int) = object : Migration(oldVersion, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val songColumns = mutableSetOf<String>()
                db.query("PRAGMA table_info(`song_metadata`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) songColumns.add(cursor.getString(nameIndex))
                }
                db.execSQL("CREATE TABLE IF NOT EXISTS `song_metadata_new` (`fingerprintId` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `isLiked` INTEGER NOT NULL, `vibeTags` TEXT NOT NULL, `occasionTags` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`fingerprintId`))")
                if (songColumns.isNotEmpty()) {
                    fun source(column: String, fallback: String) = if (column in songColumns) "`$column`" else fallback
                    db.execSQL("INSERT INTO `song_metadata_new` SELECT ${source("fingerprintId", "''")}, ${source("title", "''")}, ${source("artist", "''")}, ${source("isLiked", "0")}, ${source("vibeTags", "''")}, ${source("occasionTags", "''")}, ${source("lastUpdated", "0")} FROM `song_metadata`")
                    db.execSQL("DROP TABLE `song_metadata`")
                }
                db.execSQL("ALTER TABLE `song_metadata_new` RENAME TO `song_metadata`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `alarms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `repeatDays` TEXT NOT NULL, `isOneTime` INTEGER NOT NULL, `songFingerprintId` TEXT, `songTitle` TEXT, `songPath` TEXT, `startPositionMs` INTEGER NOT NULL, `targetVolume` REAL NOT NULL, `useFadeIn` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `alarms` ADD COLUMN `endPositionMs` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `alarms` ADD COLUMN `respectPhoneSoundMode` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `alarms` ADD COLUMN `label` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `alarms` ADD COLUMN `soundMode` TEXT NOT NULL DEFAULT 'LEGACY'")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `lyrics` (`fingerprintId` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `plainLyrics` TEXT NOT NULL, `syncedLyrics` TEXT NOT NULL, `source` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`fingerprintId`))")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `discovery_reactions` (`songKey` TEXT NOT NULL, `artist` TEXT NOT NULL, `title` TEXT NOT NULL, `isLiked` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`songKey`))")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `discovery_reactions` ADD COLUMN `isRead` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `discovery_reactions` ADD COLUMN `recommendedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `discovery_reactions` ADD COLUMN `batchId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `discovery_reactions` ADD COLUMN `listenCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `discovery_reactions` SET `isRead` = 1")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `discovery_reactions` ADD COLUMN `isHidden` INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "loopmuse_database"
                )
                .addMigrations(migrationFrom(1), migrationFrom(2), migrationFrom(3), MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
