package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watched_videos")
data class WatchedVideo(
    @PrimaryKey val videoUri: String,
    val watchedAt: Long = System.currentTimeMillis()
)
