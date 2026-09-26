package com.example.loopmuse.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SongMetaEntity::class, AlarmEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songMetaDao(): SongMetaDao
    abstract fun alarmDao(): AlarmDao

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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "loopmuse_database"
                )
                .addMigrations(migrationFrom(1), migrationFrom(2), migrationFrom(3))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
