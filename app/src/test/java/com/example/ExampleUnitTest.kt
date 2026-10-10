package com.example

import com.example.capture.VadDetector
import com.example.data.AppSettings
import com.example.data.SrtCue
import com.example.model.SupportedLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun vadDetector_correctlyCalculatesRmsAndDetectsSpeech() {
        val vad = VadDetector(sensitivityThresholdRms = 150f)

        // Silent buffer (zeros)
        val silence = ShortArray(1600) { 0 }
        val (isVoiceSilence, silenceRms) = vad.isSpeech(silence, silence.size)
        assertEquals(0f, silenceRms, 0.001f)
        assertFalse(isVoiceSilence)

        // Loud sine wave buffer (~2000 amplitude)
        val loudBuffer = ShortArray(1600) { 2000 }
        val (isVoiceLoud, loudRms) = vad.isSpeech(loudBuffer, loudBuffer.size)
        assertTrue(loudRms > 150f)
        assertTrue(isVoiceLoud)
    }

    @Test
    fun supportedLanguages_containsCoreModels() {
        val english = SupportedLanguages.getSourceLang("en")
        assertEquals("English (Tiếng Anh)", english.displayName)
        assertEquals("vosk-model-small-en-us-0.15", english.modelDirName)
        assertTrue(english.sizeMb > 0)

        val vietnamese = SupportedLanguages.getSourceLang("vi")
        assertEquals("Tiếng Việt", vietnamese.displayName)

        val target = SupportedLanguages.getTargetLang("vi")
        assertEquals("Tiếng Việt", target.displayName)
    }

    @Test
    fun appSettings_defaultsAreValid() {
        val settings = AppSettings()
        assertEquals("en", settings.sourceLanguage)
        assertEquals("vi", settings.targetLanguage)
        assertTrue(settings.fontSizeSp >= 14f)
        assertTrue(settings.backgroundOpacity in 0.1f..1.0f)
        assertTrue(settings.showOriginalText)
    }

    @Test
    fun srtCue_creationIsValid() {
        val cue = SrtCue(1, 1000L, 3500L, "Xin chào thế giới")
        assertEquals(1, cue.index)
        assertEquals(1000L, cue.startMs)
        assertEquals(3500L, cue.endMs)
        assertEquals("Xin chào thế giới", cue.text)
    }

    @Test
    fun offlinePhraseDictionary_translatesCommonPhrases() {
        val hello = com.example.translation.OfflinePhraseDictionary.translate("Hello", "en", "vi")
        assertEquals("Xin chào", hello)

        val thanks = com.example.translation.OfflinePhraseDictionary.translate("Thank you!", "en", "vi")
        assertEquals("Cảm ơn bạn", thanks)
    }
}
