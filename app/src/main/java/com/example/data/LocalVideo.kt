package com.example.data

data class LocalVideo(
    val uriString: String, // Playable content:// URI string from SAF
    val name: String,
    val size: Long,
    val mimeType: String
)
