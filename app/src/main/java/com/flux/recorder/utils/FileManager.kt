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
    fun getAllRecordings(): List<File> {
        val dir = getRecordingsDirectory()
        return dir.listFiles { file ->
            file.isFile && file.extension == "mp4"
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    /**
     * Delete a recording file
     */
    fun deleteRecording(file: File): Boolean {
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
