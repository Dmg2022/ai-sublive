package com.example.model

import com.google.mlkit.nl.translate.TranslateLanguage

data class VoskLanguageInfo(
    val code: String,
    val displayName: String,
    val flag: String,
    val modelDirName: String,
    val downloadUrl: String,
    val sizeMb: Int,
    val isDownloaded: Boolean = false,
    val downloadProgress: Int = 0,
    val isDownloading: Boolean = false,
    val mlKitCode: String
)

object SupportedLanguages {
    val SOURCE_LANGUAGES = listOf(
        VoskLanguageInfo(
            code = "en",
            displayName = "English (Tiếng Anh)",
            flag = "🇺🇸",
            modelDirName = "vosk-model-small-en-us-0.15",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            sizeMb = 40,
            mlKitCode = TranslateLanguage.ENGLISH
        ),
        VoskLanguageInfo(
            code = "vi",
            displayName = "Tiếng Việt",
            flag = "🇻🇳",
            modelDirName = "vosk-model-small-vn-0.4",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-vn-0.4.zip",
            sizeMb = 36,
            mlKitCode = TranslateLanguage.VIETNAMESE
        ),
        VoskLanguageInfo(
            code = "ja",
            displayName = "Japanese (日本語)",
            flag = "🇯🇵",
            modelDirName = "vosk-model-small-ja-0.22",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
            sizeMb = 49,
            mlKitCode = TranslateLanguage.JAPANESE
        ),
        VoskLanguageInfo(
            code = "zh",
            displayName = "Chinese (中文)",
            flag = "🇨🇳",
            modelDirName = "vosk-model-small-cn-0.22",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
            sizeMb = 42,
            mlKitCode = TranslateLanguage.CHINESE
        ),
        VoskLanguageInfo(
            code = "ko",
            displayName = "Korean (한국어)",
            flag = "🇰🇷",
            modelDirName = "vosk-model-small-ko-0.22",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
            sizeMb = 45,
            mlKitCode = TranslateLanguage.KOREAN
        ),
        VoskLanguageInfo(
            code = "fr",
            displayName = "French (Français)",
            flag = "🇫🇷",
            modelDirName = "vosk-model-small-fr-0.22",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
            sizeMb = 41,
            mlKitCode = TranslateLanguage.FRENCH
        ),
        VoskLanguageInfo(
            code = "es",
            displayName = "Spanish (Español)",
            flag = "🇪🇸",
            modelDirName = "vosk-model-small-es-0.42",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
            sizeMb = 39,
            mlKitCode = TranslateLanguage.SPANISH
        )
    )

    data class TargetLanguage(
        val code: String,
        val displayName: String,
        val flag: String,
        val mlKitCode: String
    )

    val TARGET_LANGUAGES = listOf(
        TargetLanguage("vi", "Tiếng Việt", "🇻🇳", TranslateLanguage.VIETNAMESE),
        TargetLanguage("en", "English", "🇺🇸", TranslateLanguage.ENGLISH),
        TargetLanguage("ja", "日本語", "🇯🇵", TranslateLanguage.JAPANESE),
        TargetLanguage("zh", "中文", "🇨🇳", TranslateLanguage.CHINESE),
        TargetLanguage("ko", "한국어", "🇰🇷", TranslateLanguage.KOREAN),
        TargetLanguage("fr", "Français", "🇫🇷", TranslateLanguage.FRENCH),
        TargetLanguage("es", "Español", "🇪🇸", TranslateLanguage.SPANISH)
    )

    fun getSourceLang(code: String): VoskLanguageInfo {
        return SOURCE_LANGUAGES.find { it.code == code } ?: SOURCE_LANGUAGES.first()
    }

    fun getTargetLang(code: String): TargetLanguage {
        return TARGET_LANGUAGES.find { it.code == code } ?: TARGET_LANGUAGES.first()
    }
}
