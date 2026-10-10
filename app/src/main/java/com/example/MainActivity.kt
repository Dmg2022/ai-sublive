package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AppSettings
import com.example.data.SettingsRepository
import com.example.manager.ModelManager
import com.example.model.PipelineStatus
import com.example.model.SupportedLanguages
import com.example.model.VoskLanguageInfo
import com.example.service.CaptureService
import com.example.ui.HelpDialog
import com.example.ui.HistoryScreen
import com.example.ui.MainScreen
import com.example.ui.ModelsScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.LiveSubTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepo = SettingsRepository(applicationContext)
        val modelManager = ModelManager(applicationContext)

        setContent {
            LiveSubTheme {
                LiveSubApp(
                    settingsRepo = settingsRepo,
                    modelManager = modelManager
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSubApp(
    settingsRepo: SettingsRepository,
    modelManager: ModelManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showHelpDialog by remember { mutableStateOf(false) }

    // Service & Pipeline states
    val isRunning by CaptureService.isRunning.collectAsState()
    val subtitleState by CaptureService.currentSubtitleState.collectAsState()
    val settings by settingsRepo.settingsFlow.collectAsState(initial = AppSettings())
    val downloadProgressMap by modelManager.downloadProgressMap.collectAsState()

    // Permission states
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    // Refresh permissions on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAudioPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                hasOverlayPermission = Settings.canDrawOverlays(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Permission Launchers
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("LiveSub cần quyền thu âm thanh để nhận diện giọng nói.")
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    // MediaProjection Launcher
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(context, CaptureService::class.java).apply {
                action = CaptureService.ACTION_START
                putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            Toast.makeText(context, "LiveSub đang chạy và lắng nghe âm thanh…", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Bạn đã từ chối quyền thu âm thanh hệ thống.")
            }
        }
    }

    val currentSrcLangInfo = SupportedLanguages.getSourceLang(settings.sourceLanguage)
    val hasSourceModelDownloaded = modelManager.isVoskModelDownloaded(currentSrcLangInfo)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> stringResource(R.string.app_name)
                            1 -> stringResource(R.string.tab_models)
                            2 -> stringResource(R.string.tab_settings)
                            else -> stringResource(R.string.tab_history)
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.testTag("help_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Hướng dẫn"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_home)) },
                    modifier = Modifier.testTag("tab_home")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Language, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_models)) },
                    modifier = Modifier.testTag("tab_models")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) },
                    modifier = Modifier.testTag("tab_settings")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Description, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_history)) },
                    modifier = Modifier.testTag("tab_history")
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> {
                MainScreen(
                    isRunning = isRunning,
                    subtitleState = subtitleState,
                    settings = settings,
                    hasAudioPermission = hasAudioPermission,
                    hasOverlayPermission = hasOverlayPermission,
                    hasNotificationPermission = hasNotificationPermission,
                    hasModelDownloaded = hasSourceModelDownloaded,
                    onRequestAudio = {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onRequestOverlay = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onStartService = {
                        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                    },
                    onStopService = {
                        val stopIntent = Intent(context, CaptureService::class.java).apply {
                            action = CaptureService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                    },
                    onNavigateToModels = { selectedTab = 1 },
                    onNavigateToSettings = { selectedTab = 2 },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            1 -> {
                ModelsScreen(
                    currentSourceLang = settings.sourceLanguage,
                    currentTargetLang = settings.targetLanguage,
                    downloadProgressMap = downloadProgressMap,
                    isModelDownloaded = { modelManager.isVoskModelDownloaded(it) },
                    isWifiConnected = modelManager.isWifiConnected(),
                    wifiOnlySetting = settings.wifiOnlyDownload,
                    onSelectSourceLang = { langCode ->
                        scope.launch { settingsRepo.updateSourceLanguage(langCode) }
                    },
                    onSelectTargetLang = { langCode ->
                        scope.launch { settingsRepo.updateTargetLanguage(langCode) }
                    },
                    onDownloadModel = { lang ->
                        scope.launch {
                            val result = modelManager.downloadVoskModel(lang, settings.wifiOnlyDownload)
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Đã tải xong mô hình ${lang.displayName}!")
                            } else {
                                snackbarHostState.showSnackbar("Lỗi tải: ${result.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    onDeleteModel = { lang ->
                        modelManager.deleteVoskModel(lang)
                        scope.launch {
                            snackbarHostState.showSnackbar("Đã xóa mô hình ${lang.displayName}.")
                        }
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            2 -> {
                SettingsScreen(
                    settings = settings,
                    onUpdateFontSize = { scope.launch { settingsRepo.updateFontSize(it) } },
                    onUpdateOpacity = { scope.launch { settingsRepo.updateBackgroundOpacity(it) } },
                    onUpdateShowOriginal = { scope.launch { settingsRepo.updateShowOriginalText(it) } },
                    onUpdateTranslatePartial = { scope.launch { settingsRepo.updateTranslatePartial(it) } },
                    onUpdateVadSensitivity = { scope.launch { settingsRepo.updateVadSensitivity(it) } },
                    onUpdateWifiOnly = { scope.launch { settingsRepo.updateWifiOnly(it) } },
                    onUpdateSaveSrt = { scope.launch { settingsRepo.updateSaveSrt(it) } },
                    onUpdateClickThrough = { scope.launch { settingsRepo.updateClickThrough(it) } },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            3 -> {
                HistoryScreen(modifier = Modifier.padding(innerPadding))
            }
        }

        if (showHelpDialog) {
            HelpDialog(onDismiss = { showHelpDialog = false })
        }
    }
}
