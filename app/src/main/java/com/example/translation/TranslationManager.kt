package com.example.translation

import android.util.Log
import android.util.LruCache
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TranslationManager(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "TranslationManager"
        private const val PARTIAL_RATE_LIMIT_MS = 1000L // Max 1 partial translation per second
    }

    private var currentTranslator: Translator? = null
    private var currentSourceLang: String = ""
    private var currentTargetLang: String = ""

    // In-memory LRU cache: key = "$sourceLang->$targetLang:$text"
    private val translationCache = LruCache<String, String>(200)

    // Job tracking for cancellation of obsolete translation tasks
    private var inFlightTranslationJob: Job? = null
    private var lastPartialTranslateTimeMs = 0L

    suspend fun prepareTranslator(
        sourceLangCode: String,
        targetLangCode: String,
        wifiOnly: Boolean
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            // Close old translator if source/target languages changed
            if (currentTranslator != null &&
                (sourceLangCode != currentSourceLang || targetLangCode != currentTargetLang)
            ) {
                close()
            }

            currentSourceLang = sourceLangCode
            currentTargetLang = targetLangCode

            // ML Kit throws exception if source and target are the same; in that case no translation is needed
            if (sourceLangCode == targetLangCode) {
                Log.i(TAG, "Source and target languages are identical ($sourceLangCode), skipping translation")
                return@withContext Result.success(Unit)
            }

            if (currentTranslator == null) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLangCode)
                    .setTargetLanguage(targetLangCode)
                    .build()

                val translator = Translation.getClient(options)
                val conditionsBuilder = DownloadConditions.Builder()
                if (wifiOnly) {
                    conditionsBuilder.requireWifi()
                }
                try {
                    translator.downloadModelIfNeeded(conditionsBuilder.build()).await()
                } catch (e: Throwable) {
                    Log.w(TAG, "ML Kit model download non-fatal issue: ${e.message}")
                }

                currentTranslator = translator
                Log.i(TAG, "ML Kit Translator ready: $sourceLangCode -> $targetLangCode")
            }
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "Error preparing ML Kit translator: ${e.message}", e)
            Result.failure(Exception(e.message))
        }
    }

    suspend fun translate(
        text: String,
        isFinal: Boolean,
        allowPartial: Boolean
    ): String? = withContext(ioDispatcher) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext ""

        // If source and target languages are identical, return text directly
        if (currentSourceLang == currentTargetLang) {
            return@withContext trimmed
        }

        // Check if partial translation is permitted and rate limited
        if (!isFinal) {
            if (!allowPartial) return@withContext null
            val now = System.currentTimeMillis()
            if (now - lastPartialTranslateTimeMs < PARTIAL_RATE_LIMIT_MS) {
                // Rate limited: skip this partial translation to save CPU/battery
                return@withContext null
            }
            lastPartialTranslateTimeMs = now
        }

        // Check cache first
        val cacheKey = "$currentSourceLang->$currentTargetLang:$trimmed"
        val cached = translationCache.get(cacheKey)
        if (cached != null) {
            return@withContext cached
        }

        // Check instant offline dictionary
        val dictFallback = OfflinePhraseDictionary.translate(trimmed, currentSourceLang, currentTargetLang)
        if (dictFallback != null) {
            translationCache.put(cacheKey, dictFallback)
            return@withContext dictFallback
        }

        val translator = currentTranslator ?: return@withContext dictFallback ?: trimmed

        try {
            // Cancel any previous in-flight translation job
            inFlightTranslationJob?.cancel()

            val job = coroutineContext[Job]
            inFlightTranslationJob = job

            val translated = translator.translate(trimmed).await()
            if (translated.isNotBlank()) {
                translationCache.put(cacheKey, translated)
                translated
            } else {
                dictFallback ?: trimmed
            }
        } catch (e: CancellationException) {
            // Normal cancellation when newer audio/text arrives
            null
        } catch (e: Throwable) {
            Log.w(TAG, "Translation error for '$trimmed': ${e.message}")
            dictFallback ?: trimmed
        }
    }

    fun close() {
        inFlightTranslationJob?.cancel()
        inFlightTranslationJob = null
        try {
            currentTranslator?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing translator: ${e.message}")
        } finally {
            currentTranslator = null
        }
    }
}
