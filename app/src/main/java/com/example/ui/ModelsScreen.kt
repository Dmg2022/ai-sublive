package com.example.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.SupportedLanguages
import com.example.model.VoskLanguageInfo
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.RoseError

@Composable
fun ModelsScreen(
    currentSourceLang: String,
    currentTargetLang: String,
    downloadProgressMap: Map<String, Int>,
    isModelDownloaded: (VoskLanguageInfo) -> Boolean,
    isWifiConnected: Boolean,
    wifiOnlySetting: Boolean,
    onSelectSourceLang: (String) -> Unit,
    onSelectTargetLang: (String) -> Unit,
    onDownloadModel: (VoskLanguageInfo) -> Unit,
    onDeleteModel: (VoskLanguageInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var modelToDelete by remember { mutableStateOf<VoskLanguageInfo?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Quản lý Mô hình Ngoại tuyến",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Tải một lần để nhận dạng giọng nói và dịch hoàn toàn offline, không tốn dữ liệu mạng khi xem video.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Wi-Fi Status Bar
            if (wifiOnlySetting && !isWifiConnected) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = RoseError,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Đang bật chế độ 'Chỉ tải qua Wi-Fi'. Hãy kết nối Wi-Fi để tải mô hình.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Section: STT Source Models (Vosk)
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = IndigoPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ngôn ngữ nói (Mô hình Vosk)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(SupportedLanguages.SOURCE_LANGUAGES) { lang ->
            val isDownloaded = isModelDownloaded(lang)
            val isSelected = currentSourceLang == lang.code
            val progress = downloadProgressMap[lang.code]
            val isDownloading = progress != null

            VoskModelCard(
                lang = lang,
                isSelected = isSelected,
                isDownloaded = isDownloaded,
                isDownloading = isDownloading,
                downloadProgress = progress ?: 0,
                onSelect = { onSelectSourceLang(lang.code) },
                onDownload = { onDownloadModel(lang) },
                onDelete = { modelToDelete = lang }
            )
        }

        // Section: Target Translation Languages (ML Kit)
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ngôn ngữ dịch đích (ML Kit)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(SupportedLanguages.TARGET_LANGUAGES) { target ->
            val isSelected = currentTargetLang == target.code

            TargetLanguageCard(
                target = target,
                isSelected = isSelected,
                onSelect = { onSelectTargetLang(target.code) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Delete Confirmation Dialog
    modelToDelete?.let { lang ->
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = { Text(text = "Xóa mô hình?") },
            text = {
                Text(text = "Bạn có chắc muốn xóa mô hình ${lang.displayName} (${lang.sizeMb} MB) khỏi máy?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteModel(lang)
                        modelToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                ) {
                    Text(text = stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text(text = stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun VoskModelCard(
    lang: VoskLanguageInfo,
    isSelected: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Int,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vosk_model_card_${lang.code}")
            .clickable(enabled = isDownloaded) { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                IndigoPrimary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, IndigoPrimary) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = lang.flag, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = lang.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(IndigoPrimary, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Đang chọn",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = androidx.compose.ui.graphics.Color.White,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Kích thước: ~${lang.sizeMb} MB • Vosk Offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action buttons
                when {
                    isDownloading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 2.5.dp,
                                color = IndigoPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$downloadProgress%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                        }
                    }
                    isDownloaded -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Đã tải",
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                            IconButton(onClick = onDelete) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Xóa",
                                    tint = RoseError.copy(alpha = 0.8f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    else -> {
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Tải", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (isDownloading) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = IndigoPrimary
                )
            }
        }
    }
}

@Composable
private fun TargetLanguageCard(
    target: SupportedLanguages.TargetLanguage,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("target_lang_card_${target.code}")
            .clickable { onSelect() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = target.flag, fontSize = 22.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = target.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Icon(
                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
            )
        }
    }
}
