package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WatchedVideoDao {
    @Query("SELECT videoUri FROM watched_videos")
    suspend fun getAllWatchedUris(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatched(watchedVideo: WatchedVideo)

    @Query("DELETE FROM watched_videos WHERE videoUri = :uri")
    suspend fun deleteWatchedByUri(uri: String)

    @Query("DELETE FROM watched_videos WHERE videoUri IN (:uris)")
    suspend fun deleteWatchedUris(uris: List<String>)

    @Query("DELETE FROM watched_videos")
    suspend fun clearAll()
}
