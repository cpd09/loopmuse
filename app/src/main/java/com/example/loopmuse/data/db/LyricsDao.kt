package com.example.loopmuse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics")
    fun getAll(): Flow<List<LyricsEntity>>

    @Query("SELECT * FROM lyrics WHERE fingerprintId = :fingerprintId")
    suspend fun getById(fingerprintId: String): LyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(lyrics: LyricsEntity)

    @Query("DELETE FROM lyrics")
    suspend fun deleteAll()
}
