package com.example.loopmuse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongMetaDao {
    @Query("SELECT * FROM song_metadata")
    fun getAllMetadata(): Flow<List<SongMetaEntity>>

    @Query("SELECT * FROM song_metadata WHERE isLiked = 1")
    fun getLikedSongs(): Flow<List<SongMetaEntity>>

    @Query("SELECT * FROM song_metadata WHERE fingerprintId = :fingerprintId")
    suspend fun getMetadataById(fingerprintId: String): SongMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(meta: SongMetaEntity)

    @Query("UPDATE song_metadata SET isLiked = :isLiked, lastUpdated = :updatedAt WHERE fingerprintId = :fingerprintId")
    suspend fun updateLikeStatus(fingerprintId: String, isLiked: Boolean, updatedAt: Long)

    @Query("DELETE FROM song_metadata")
    suspend fun deleteAll()
}
