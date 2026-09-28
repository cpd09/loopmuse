package com.example.loopmuse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscoveryReactionDao {
    @Query("SELECT * FROM discovery_reactions ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<DiscoveryReactionEntity>>

    @Query("SELECT * FROM discovery_reactions")
    suspend fun getAllOnce(): List<DiscoveryReactionEntity>

    @Query("SELECT * FROM discovery_reactions WHERE songKey = :songKey")
    suspend fun getByKey(songKey: String): DiscoveryReactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(reaction: DiscoveryReactionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecommendations(recommendations: List<DiscoveryReactionEntity>)

    @Query("UPDATE discovery_reactions SET isLiked = CASE WHEN isLiked = 1 THEN 0 ELSE 1 END, updatedAt = :now WHERE songKey = :songKey")
    suspend fun toggleLike(songKey: String, now: Long)

    @Query("UPDATE discovery_reactions SET isRead = :isRead, updatedAt = :now WHERE songKey = :songKey")
    suspend fun setRead(songKey: String, isRead: Boolean, now: Long)

    @Query("UPDATE discovery_reactions SET isHidden = 1, updatedAt = :now WHERE songKey = :songKey")
    suspend fun hideRecommendation(songKey: String, now: Long)

    @Query("DELETE FROM discovery_reactions")
    suspend fun deleteAll()
}
