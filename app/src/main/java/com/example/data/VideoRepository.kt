package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(private val watchedVideoDao: WatchedVideoDao) {

    suspend fun getWatchedUris(): Set<String> = withContext(Dispatchers.IO) {
        watchedVideoDao.getAllWatchedUris().toSet()
    }

    suspend fun addToWatched(uriString: String) = withContext(Dispatchers.IO) {
        watchedVideoDao.insertWatched(WatchedVideo(videoUri = uriString))
    }

    suspend fun removeFromWatched(uriString: String) = withContext(Dispatchers.IO) {
        watchedVideoDao.deleteWatchedByUri(uriString)
    }

    suspend fun resetWatchedUris(uriStrings: List<String>) = withContext(Dispatchers.IO) {
        watchedVideoDao.deleteWatchedUris(uriStrings)
    }

    suspend fun clearAllWatched() = withContext(Dispatchers.IO) {
        watchedVideoDao.clearAll()
    }

    /**
     * Scans the given SAF tree URI for video files (mp4, mkv, webm, 3gp, ts, avi, etc.)
     */
    suspend fun scanVideosInFolder(context: Context, treeUriString: String): List<LocalVideo> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<LocalVideo>()
        val contentResolver = context.contentResolver
        val treeUri = Uri.parse(treeUriString)

        try {
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )

            // Query only the immediate children of the tree directory
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val docIdIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val displayNameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeTypeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(docIdIndex)
                    val displayName = cursor.getString(displayNameIndex) ?: "Unknown Video"
                    val mimeType = cursor.getString(mimeTypeIndex) ?: ""
                    val size = cursor.getLong(sizeIndex)

                    val isVideo = mimeType.startsWith("video/") ||
                            displayName.endsWith(".mp4", ignoreCase = true) ||
                            displayName.endsWith(".mkv", ignoreCase = true) ||
                            displayName.endsWith(".webm", ignoreCase = true) ||
                            displayName.endsWith(".3gp", ignoreCase = true)

                    if (isVideo) {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        videos.add(
                            LocalVideo(
                                uriString = fileUri.toString(),
                                name = displayName,
                                size = size,
                                mimeType = mimeType
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        videos
    }

    /**
     * Deletes a video file from local SAF folder or device storage
     */
    suspend fun deleteVideoFile(context: Context, uriString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
                // Also remove it from Room watched tracking, just in case
                watchedVideoDao.deleteWatchedByUri(uriString)
                true
            } else {
                if (uri.scheme == "file") {
                    val file = java.io.File(uri.path ?: "")
                    if (file.exists() && file.delete()) {
                        watchedVideoDao.deleteWatchedByUri(uriString)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
