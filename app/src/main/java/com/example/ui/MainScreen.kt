package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppSettings
import com.example.model.PipelineStatus
import com.example.model.SubtitleState
import com.example.model.SupportedLanguages
import com.example.ui.components.PermissionBanner
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.RoseError
import com.example.ui.theme.VioletSecondary

@Composable
fun MainScreen(
    isRunning: Boolean,
    subtitleState: SubtitleState,
    settings: AppSettings,
    hasAudioPermission: Boolean,
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    hasModelDownloaded: Boolean,
    onRequestAudio: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val sourceLang = SupportedLanguages.getSourceLang(settings.sourceLanguage)
    val targetLang = SupportedLanguages.getTargetLang(settings.targetLanguage)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.testTag("settings_nav_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Cài đặt",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Permission Banner (if not all granted)
        PermissionBanner(
            hasAudioPermission = hasAudioPermission,
            hasOverlayPermission = hasOverlayPermission,
            hasNotificationPermission = hasNotificationPermission,
            onRequestAudio = onRequestAudio,
            onRequestOverlay = onRequestOverlay,
            onRequestNotification = onRequestNotification
        )

        if (!hasAudioPermission || !hasOverlayPermission || !hasNotificationPermission) {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Active Language Translation Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToModels() }
                .testTag("language_select_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = sourceLang.flag, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = sourceLang.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = IndigoPrimary,
                    modifier = Modifier.size(18.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = targetLang.flag, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = targetLang.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Main Hero Control Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("main_status_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Badge
                StatusPill(status = subtitleState.status, isRunning = isRunning)

                Spacer(modifier = Modifier.height(20.dp))

                // Waveform Audio Meter
                WaveformVisualizer(
                    rms = subtitleState.audioRms,
                    isListening = isRunning && subtitleState.status == PipelineStatus.LISTENING,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Main Start / Stop Button
                val canStart = hasAudioPermission && hasOverlayPermission && hasNotificationPermission

                Button(
                    onClick = {
                        if (isRunning) {
                            onStopService()
                        } else {
                            if (!hasModelDownloaded) {
                                onNavigateToModels()
                            } else {
                                onStartService()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_stop_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) RoseError else IndigoPrimary
                    ),
                    enabled = canStart
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isRunning) {
                            stringResource(R.string.btn_stop_service)
                        } else if (!hasModelDownloaded) {
                            "Cần tải mô hình nhận dạng"
                        } else {
                            stringResource(R.string.btn_start_service)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!hasModelDownloaded && !isRunning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nhấn vào đây để tải mô hình Vosk offline (${sourceLang.sizeMb} MB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberWarning,
                        modifier = Modifier.clickable { onNavigateToModels() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Live Subtitle Preview Card
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("live_subtitle_preview_card"),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Xem trước phụ đề trên màn hình",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (isRunning) {
                        Text(
                            text = if (subtitleState.isFinal) "Final" else "Partial…",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (subtitleState.isFinal) EmeraldSuccess else AmberWarning
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF12141A))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = when {
                                !isRunning -> "LiveSub chưa chạy. Nhấn 'Bắt đầu' để nghe."
                                subtitleState.errorMessage != null -> subtitleState.errorMessage
                                subtitleState.translatedText.isNotBlank() -> subtitleState.translatedText
                                subtitleState.originalText.isNotBlank() -> subtitleState.originalText
                                else -> "Đang nghe âm thanh từ các ứng dụng…"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = if (subtitleState.errorMessage != null) RoseError else Color.White
                        )

                        if (settings.showOriginalText && subtitleState.originalText.isNotBlank() &&
                            subtitleState.originalText != subtitleState.translatedText && isRunning
                        ) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "(${subtitleState.originalText})",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Android Ecosystem Limitations Note
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.limitations_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.limitations_drm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.limitations_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StatusPill(
    status: PipelineStatus,
    isRunning: Boolean
) {
    val (dotColor, text) = when {
        !isRunning -> Pair(MaterialTheme.colorScheme.outline, "Chưa kích hoạt")
        status == PipelineStatus.INITIALIZING -> Pair(IndigoPrimary, "Đang tải mô hình…")
        status == PipelineStatus.LISTENING -> Pair(EmeraldSuccess, "Đang lắng nghe âm thanh")
        status == PipelineStatus.TRANSLATING -> Pair(CyanAccent, "Đang nhận diện & dịch…")
        status == PipelineStatus.PAUSED -> Pair(AmberWarning, "Đang tạm dừng")
        status == PipelineStatus.SILENCE -> Pair(AmberWarning, "Im lặng")
        status == PipelineStatus.ERROR -> Pair(RoseError, "Lỗi")
        else -> Pair(EmeraldSuccess, "Sẵn sàng")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = dotColor.copy(alpha = 0.15f),
                shape = CircleShape
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = dotColor
        )
    }
}
