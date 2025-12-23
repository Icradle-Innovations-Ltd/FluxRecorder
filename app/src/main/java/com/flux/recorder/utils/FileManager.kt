package com.flux.recorder.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages recording file creation, naming, and storage
 */
class FileManager(private val context: Context) {
    
    companion object {
        private const val RECORDINGS_DIR = "Recordings"
        private const val FILE_PREFIX = "FluxRec_"
        private const val FILE_EXTENSION = ".mp4"
    }
    
    /**
     * Get the recordings directory
     */
    fun getRecordingsDirectory(): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ use scoped storage
            File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), RECORDINGS_DIR)
        } else {
            // Legacy external storage
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), RECORDINGS_DIR)
        }
        
        if (!dir.exists()) {
            dir.mkdirs()
        }
        
        return dir
    }
    
    /**
     * Create a new recording file with timestamp-based name
     */
    fun createRecordingFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "$FILE_PREFIX$timestamp$FILE_EXTENSION"
        return File(getRecordingsDirectory(), fileName)
    }
    
    /**
     * Get all recordings sorted by date (newest first)
     */
    /**
     * Get all recordings sorted by date (newest first)
     * Queries MediaStore on Android 10+ or Public Dir on Legacy
     */
    fun getAllRecordings(): List<File> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val recordingList = mutableListOf<File>()
            val projection = arrayOf(
                android.provider.MediaStore.Video.Media.DATA
            )
            val selection = "${android.provider.MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("$FILE_PREFIX%") 
            val sortOrder = "${android.provider.MediaStore.Video.Media.DATE_ADDED} DESC"

            try {
                context.contentResolver.query(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val dataColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataColumn)
                        if (path != null) {
                            val file = File(path)
                            if (file.exists()) {
                                recordingList.add(file)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return recordingList
        } else {
            // Legacy external storage (Public Folder)
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "FluxRecorder")
            if (!publicDir.exists()) return emptyList()
            
            return publicDir.listFiles { file ->
                file.isFile && file.extension == "mp4" && file.name.startsWith(FILE_PREFIX)
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        }
    }
    
    /**
     * Delete a recording file
     */
    /**
     * Delete a recording file
     */
    fun deleteRecording(file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val projection = arrayOf(android.provider.MediaStore.Video.Media._ID)
                val selection = "${android.provider.MediaStore.Video.Media.DATA} = ?"
                val selectionArgs = arrayOf(file.absolutePath)
                
                val resolver = context.contentResolver
                val cursor = resolver.query(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
                        val id = it.getLong(idColumn)
                        val uri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        return resolver.delete(uri, null, null) > 0
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return file.delete()
    }
    
    /**
     * Get available storage space in bytes
     */
    fun getAvailableSpace(): Long {
        val dir = getRecordingsDirectory()
        return dir.usableSpace
    }
    
    /**
     * Copy the recorded file to the public Movies/FluxRecorder directory
     * This makes it visible in the user's File Manager and Gallery
     */
    fun copyToPublicGallery(privateFile: File): File? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (Scoped Storage)
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, privateFile.name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FluxRecorder")
                }
                
                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        privateFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    // We can't return a File object for a ContentUri easily, 
                    // but the copying is done. We return the original for scanning purposes if needed,
                    // or null to indicate we handled it via MediaStore.
                    // For consistency with legacy, let's return null and let the caller know 
                    // Scoped Storage handled it, OR we can return a dummy file pointing to the path if we wanted.
                    return null 
                }
            } else {
                // Legacy Storage
                val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "FluxRecorder")
                if (!publicDir.exists()) {
                    publicDir.mkdirs()
                }
                
                val publicFile = File(publicDir, privateFile.name)
                privateFile.copyTo(publicFile, overwrite = true)
                return publicFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Check if there's enough space for recording
     * @param estimatedDurationMinutes Estimated recording duration
     * @param bitrate Video bitrate in bps
     */
    fun hasEnoughSpace(estimatedDurationMinutes: Int, bitrate: Int): Boolean {
        val estimatedSizeBytes = (estimatedDurationMinutes * 60L * bitrate) / 8
        val requiredSpace = (estimatedSizeBytes * 1.2).toLong() // 20% buffer
        return getAvailableSpace() > requiredSpace
    }
}
