package com.example.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SrtCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

class SrtExporter(private val context: Context) {

    private val cues = mutableListOf<SrtCue>()
    private var sessionStartTimeMs: Long = 0L
    private var currentCueIndex: Int = 1
    private var lastCueStartTimeMs: Long = 0L

    fun startSession() {
        cues.clear()
        sessionStartTimeMs = System.currentTimeMillis()
        currentCueIndex = 1
        lastCueStartTimeMs = 0L
    }

    fun addSubtitle(original: String, translated: String) {
        val now = System.currentTimeMillis()
        val relStart = if (lastCueStartTimeMs == 0L) {
            (now - sessionStartTimeMs).coerceAtLeast(0L)
        } else {
            (lastCueStartTimeMs - sessionStartTimeMs).coerceAtLeast(0L)
        }
        val relEnd = (now - sessionStartTimeMs + 2500L).coerceAtLeast(relStart + 1000L)
        lastCueStartTimeMs = now

        val content = if (original.isNotBlank() && original != translated) {
            "$translated\n($original)"
        } else {
            translated
        }

        cues.add(SrtCue(currentCueIndex++, relStart, relEnd, content))
    }

    fun finishAndExport(): File? {
        if (cues.isEmpty()) return null

        return try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val liveSubDir = File(downloadDir, "LiveSub").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val srtFile = File(liveSubDir, "LiveSub_$timeStamp.srt")

            FileWriter(srtFile).use { writer ->
                for (cue in cues) {
                    writer.write("${cue.index}\n")
                    writer.write("${formatSrtTime(cue.startMs)} --> ${formatSrtTime(cue.endMs)}\n")
                    writer.write("${cue.text}\n\n")
                }
            }
            Log.d("SrtExporter", "Exported SRT to: ${srtFile.absolutePath}")
            srtFile
        } catch (e: Exception) {
            Log.e("SrtExporter", "Failed to export SRT: ${e.message}", e)
            null
        }
    }

    private fun formatSrtTime(millis: Long): String {
        val totalSec = millis / 1000
        val ms = millis % 1000
        val sec = totalSec % 60
        val min = (totalSec / 60) % 60
        val hr = totalSec / 3600
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hr, min, sec, ms)
    }

    companion object {
        fun listExportedFiles(): List<File> {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val liveSubDir = File(downloadDir, "LiveSub")
            if (!liveSubDir.exists() || !liveSubDir.isDirectory) return emptyList()
            return liveSubDir.listFiles { file -> file.extension.equals("srt", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        }
    }
}
