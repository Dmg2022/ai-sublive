package com.example.manager

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.model.SupportedLanguages
import com.example.model.VoskLanguageInfo
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class ModelManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "ModelManager"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val modelsDir: File
        get() {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            return File(base, "vosk_models").apply { mkdirs() }
        }

    // Active downloading states: map of langCode to progress percentage (0..100)
    private val _downloadProgressMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Int>> = _downloadProgressMap.asStateFlow()

    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun isVoskModelDownloaded(langInfo: VoskLanguageInfo): Boolean {
        val targetDir = File(modelsDir, langInfo.modelDirName)
        if (!targetDir.exists() || !targetDir.isDirectory) return false
        return findModelDirectory(targetDir) != null
    }

    fun getVoskModelPath(langInfo: VoskLanguageInfo): String? {
        val targetDir = File(modelsDir, langInfo.modelDirName)
        if (!targetDir.exists() || !targetDir.isDirectory) return null
        return findModelDirectory(targetDir)?.absolutePath
    }

    private fun findModelDirectory(dir: File): File? {
        if (File(dir, "conf").exists() || File(dir, "am").exists() || File(dir, "model.conf").exists()) {
            return dir
        }
        val subdirs = dir.listFiles { f -> f.isDirectory } ?: return null
        for (sub in subdirs) {
            val found = findModelDirectory(sub)
            if (found != null) return found
        }
        val files = dir.listFiles() ?: return null
        return if (files.isNotEmpty()) dir else null
    }

    suspend fun downloadVoskModel(
        langInfo: VoskLanguageInfo,
        wifiOnly: Boolean,
        onProgress: ((Int) -> Unit)? = null
    ): Result<String> = withContext(ioDispatcher) {
        if (wifiOnly && !isWifiConnected()) {
            return@withContext Result.failure(
                IllegalStateException("Yêu cầu kết nối Wi-Fi để tải mô hình theo cài đặt.")
            )
        }

        val tempZip = File(modelsDir, "${langInfo.modelDirName}.zip")
        val targetDir = File(modelsDir, langInfo.modelDirName)

        try {
            updateProgress(langInfo.code, 0)
            onProgress?.invoke(0)

            Log.i(TAG, "Starting download for ${langInfo.displayName} from ${langInfo.downloadUrl}")
            val request = Request.Builder().url(langInfo.downloadUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Tải thất bại (Mã phản hồi: ${response.code})")
                )
            }

            val body = response.body ?: return@withContext Result.failure(
                Exception("Không có dữ liệu trả về từ máy chủ")
            )
            val contentLength = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(tempZip).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastReported = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val percent = ((totalRead * 100) / contentLength).toInt().coerceIn(0, 95)
                            if (percent != lastReported) {
                                lastReported = percent
                                updateProgress(langInfo.code, percent)
                                onProgress?.invoke(percent)
                            }
                        }
                    }
                }
            }

            // Unpack ZIP
            Log.i(TAG, "Extracting model zip to ${modelsDir.absolutePath}")
            updateProgress(langInfo.code, 96)
            unzip(tempZip, modelsDir)
            tempZip.delete()

            updateProgress(langInfo.code, 100)
            onProgress?.invoke(100)
            removeProgress(langInfo.code)

            Log.i(TAG, "Model extracted successfully: ${targetDir.absolutePath}")
            Result.success(targetDir.absolutePath)
        } catch (e: Exception) {
            tempZip.delete()
            targetDir.deleteRecursively()
            removeProgress(langInfo.code)
            Log.e(TAG, "Error downloading Vosk model: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun deleteVoskModel(langInfo: VoskLanguageInfo): Boolean {
        val targetDir = File(modelsDir, langInfo.modelDirName)
        return targetDir.deleteRecursively()
    }

    suspend fun isMlKitModelDownloaded(languageCode: String): Boolean {
        return try {
            val model = TranslateRemoteModel.Builder(languageCode).build()
            RemoteModelManager.getInstance().isModelDownloaded(model).await()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadMlKitModel(languageCode: String, wifiOnly: Boolean): Result<Unit> {
        return try {
            val model = TranslateRemoteModel.Builder(languageCode).build()
            val conditionsBuilder = DownloadConditions.Builder()
            if (wifiOnly) {
                conditionsBuilder.requireWifi()
            }
            val conditions = conditionsBuilder.build()
            RemoteModelManager.getInstance().download(model, conditions).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download ML Kit model for $languageCode: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun updateProgress(langCode: String, progress: Int) {
        val updated = _downloadProgressMap.value.toMutableMap()
        updated[langCode] = progress
        _downloadProgressMap.value = updated
    }

    private fun removeProgress(langCode: String) {
        val updated = _downloadProgressMap.value.toMutableMap()
        updated.remove(langCode)
        _downloadProgressMap.value = updated
    }

    private fun unzip(zipFile: File, targetDirectory: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDirectory, entry.name)
                // Security check against Zip Slip path traversal vulnerability
                if (!newFile.canonicalPath.startsWith(targetDirectory.canonicalPath)) {
                    throw SecurityException("Zip entry attempted directory traversal: ${entry.name}")
                }
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
