package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.capture.AudioCaptureSource
import com.example.data.AppSettings
import com.example.data.SettingsRepository
import com.example.data.SrtExporter
import com.example.engine.SpeechRecognizerEngine
import com.example.engine.VoskEngine
import com.example.manager.ModelManager
import com.example.model.PipelineStatus
import com.example.model.SubtitleState
import com.example.model.SupportedLanguages
import com.example.overlay.OverlayController
import com.example.pipeline.SubtitlePipeline
import com.example.translation.TranslationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class CaptureService : Service() {

    companion object {
        private const val TAG = "CaptureService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "livesub_channel"

        const val ACTION_START = "com.example.livesub.ACTION_START"
        const val ACTION_STOP = "com.example.livesub.ACTION_STOP"
        const val ACTION_TOGGLE_PAUSE = "com.example.livesub.ACTION_TOGGLE_PAUSE"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _currentSubtitleState = MutableStateFlow(SubtitleState())
        val currentSubtitleState = _currentSubtitleState.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaProjection: MediaProjection? = null
    private var pipeline: SubtitlePipeline? = null
    private var overlayController: OverlayController? = null

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var modelManager: ModelManager
    private lateinit var translationManager: TranslationManager
    private lateinit var srtExporter: SrtExporter

    private var isPaused = false
    private var currentSettings = AppSettings()

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        modelManager = ModelManager(applicationContext)
        translationManager = TranslationManager()
        srtExporter = SrtExporter(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    startForegroundWithProjection(resultCode, resultData)
                } else {
                    Log.e(TAG, "Missing MediaProjection resultCode or data in start command")
                    stopSelf()
                }
            }
            ACTION_TOGGLE_PAUSE -> {
                togglePause()
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithProjection(resultCode: Int, resultData: Intent) {
        // Step 1: Start foreground service FIRST before acquiring MediaProjection (mandatory in Android 14+)
        val notification = buildNotification(isPaused = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _isRunning.value = true

        // Step 2: Acquire MediaProjection token
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            Log.e(TAG, "Failed to obtain MediaProjection from token")
            stopSelf()
            return
        }
        mediaProjection = projection

        // Step 3: Register MediaProjection callback to handle user/system revocation
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "MediaProjection revoked or stopped by system/user")
                stopSelf()
            }
        }, null)

        // Step 4: Initialize Overlay, Settings & Pipeline
        serviceScope.launch {
            currentSettings = settingsRepository.settingsFlow.first()

            // Initialize Overlay
            overlayController = OverlayController(
                context = applicationContext,
                onPositionChanged = { x, y ->
                    serviceScope.launch {
                        settingsRepository.updateOverlayPosition(x, y)
                    }
                },
                onTogglePause = {
                    togglePause()
                },
                onCloseRequested = {
                    stopSelf()
                },
                onSettingsChanged = { updated ->
                    currentSettings = updated
                    serviceScope.launch {
                        settingsRepository.updateFontSize(updated.fontSizeSp)
                        settingsRepository.updateShowOriginalText(updated.showOriginalText)
                        settingsRepository.updateClickThrough(updated.clickThroughEnabled)
                    }
                }
            ).apply {
                createOverlay(currentSettings)
            }

            // Listen for continuous settings updates
            launch {
                settingsRepository.settingsFlow.collect { settings ->
                    currentSettings = settings
                    overlayController?.applySettings(settings)
                    pipeline?.updateSettings(settings)
                }
            }

            // Step 5: Check Vosk model
            val srcLangInfo = SupportedLanguages.getSourceLang(currentSettings.sourceLanguage)
            val modelPath = modelManager.getVoskModelPath(srcLangInfo)

            if (modelPath == null) {
                Log.e(TAG, "Vosk model not found for ${srcLangInfo.displayName}")
                _currentSubtitleState.value = SubtitleState(
                    status = PipelineStatus.ERROR,
                    errorMessage = "Chưa tải mô hình nhận dạng cho ${srcLangInfo.displayName}. Vui lòng tải trong mục Mô hình."
                )
                overlayController?.updateSubtitles(_currentSubtitleState.value)
                return@launch
            }

            // Step 6: Prepare SpeechRecognizerEngine & Translation
            val recognizerEngine: SpeechRecognizerEngine = VoskEngine()
            try {
                _currentSubtitleState.value = SubtitleState(status = PipelineStatus.INITIALIZING)
                overlayController?.updateSubtitles(_currentSubtitleState.value)

                recognizerEngine.start(currentSettings.sourceLanguage, modelPath)

                val targetLangInfo = SupportedLanguages.getTargetLang(currentSettings.targetLanguage)
                translationManager.prepareTranslator(
                    sourceLangCode = srcLangInfo.mlKitCode,
                    targetLangCode = targetLangInfo.mlKitCode,
                    wifiOnly = currentSettings.wifiOnlyDownload
                )

                // Step 7: Create and start AudioCaptureSource & Pipeline
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val captureSource = AudioCaptureSource(projection)
                    if (captureSource.initialize()) {
                        pipeline = SubtitlePipeline(
                            context = applicationContext,
                            audioCaptureSource = captureSource,
                            recognizerEngine = recognizerEngine,
                            translationManager = translationManager,
                            srtExporter = srtExporter,
                            coroutineScope = serviceScope
                        ).apply {
                            updateSettings(currentSettings)
                            start()
                        }

                        // Pipe state to overlay and companion state
                        launch {
                            pipeline?.subtitleState?.collect { state ->
                                _currentSubtitleState.value = state
                                overlayController?.updateSubtitles(state)
                            }
                        }
                    } else {
                        Log.e(TAG, "AudioCaptureSource failed to initialize")
                        _currentSubtitleState.value = SubtitleState(
                            status = PipelineStatus.ERROR,
                            errorMessage = "Không thể khởi tạo thu âm thanh từ hệ thống."
                        )
                        overlayController?.updateSubtitles(_currentSubtitleState.value)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing pipeline: ${e.message}", e)
                _currentSubtitleState.value = SubtitleState(
                    status = PipelineStatus.ERROR,
                    errorMessage = "Lỗi khởi tạo: ${e.message}"
                )
                overlayController?.updateSubtitles(_currentSubtitleState.value)
            }
        }
    }

    private fun togglePause() {
        isPaused = !isPaused
        if (isPaused) {
            pipeline?.pause()
        } else {
            pipeline?.resume()
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(isPaused))
    }

    private fun buildNotification(isPaused: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, CaptureService::class.java).apply {
            action = ACTION_TOGGLE_PAUSE
        }
        val pendingPause = PendingIntent.getService(
            this, 1, pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseActionTitle = if (isPaused) getString(R.string.notif_action_resume) else getString(R.string.notif_action_pause)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title_running))
            .setContentText(if (isPaused) getString(R.string.status_paused) else getString(R.string.notif_text_running))
            .setSmallIcon(R.drawable.ic_stat_live)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, pauseActionTitle, pendingPause)
            .addAction(0, getString(R.string.notif_action_stop), pendingStop)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "CaptureService destroying...")

        _isRunning.value = false
        _currentSubtitleState.value = SubtitleState(status = PipelineStatus.IDLE)

        pipeline?.stop()
        pipeline = null

        overlayController?.destroy()
        overlayController = null

        mediaProjection?.stop()
        mediaProjection = null

        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
