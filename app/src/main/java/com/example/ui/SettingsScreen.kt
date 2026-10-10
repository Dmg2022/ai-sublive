package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppSettings
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.IndigoPrimary
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateFontSize: (Float) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onUpdateShowOriginal: (Boolean) -> Unit,
    onUpdateTranslatePartial: (Boolean) -> Unit,
    onUpdateVadSensitivity: (Float) -> Unit,
    onUpdateWifiOnly: (Boolean) -> Unit,
    onUpdateSaveSrt: (Boolean) -> Unit,
    onUpdateClickThrough: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Cài đặt & Tùy biến",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Điều chỉnh kích thước phụ đề nổi, hiệu năng VAD và các tùy chọn lưu trữ.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Section: Floating Window Appearance
        SectionHeader(icon = Icons.Default.Layers, title = "Giao diện Cửa sổ Nổi")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Font Size Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.pref_font_size),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${settings.fontSizeSp.roundToInt()} sp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IndigoPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = settings.fontSizeSp,
                    onValueChange = onUpdateFontSize,
                    valueRange = 14f..36f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        thumbColor = IndigoPrimary,
                        activeTrackColor = IndigoPrimary
                    ),
                    modifier = Modifier.testTag("font_size_slider")
                )

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )

                // Background Opacity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.pref_bg_opacity),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${(settings.backgroundOpacity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = settings.backgroundOpacity,
                    onValueChange = onUpdateOpacity,
                    valueRange = 0.2f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanAccent,
                        activeTrackColor = CyanAccent
                    ),
                    modifier = Modifier.testTag("bg_opacity_slider")
                )

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )

                // Show original text switch
                SettingSwitchRow(
                    title = stringResource(R.string.pref_show_original),
                    description = "Hiển thị dòng chữ ngoại ngữ gốc nhỏ phía dưới bản dịch",
                    checked = settings.showOriginalText,
                    onCheckedChange = onUpdateShowOriginal,
                    testTag = "show_original_switch"
                )

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )

                // Click-through mode switch
                SettingSwitchRow(
                    title = stringResource(R.string.pref_click_through),
                    description = stringResource(R.string.pref_click_through_sub),
                    checked = settings.clickThroughEnabled,
                    onCheckedChange = onUpdateClickThrough,
                    testTag = "click_through_switch"
                )
            }
        }

        // Section: Engine & Performance
        SectionHeader(icon = Icons.Default.Speed, title = "Hiệu năng & Dịch")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Translate partial results
                SettingSwitchRow(
                    title = stringResource(R.string.pref_translate_partial),
                    description = stringResource(R.string.pref_translate_partial_sub),
                    checked = settings.translatePartial,
                    onCheckedChange = onUpdateTranslatePartial,
                    testTag = "translate_partial_switch"
                )

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )

                // VAD Sensitivity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.pref_vad_sensitivity),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.pref_vad_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${settings.vadSensitivityRms.roundToInt()} RMS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IndigoPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = settings.vadSensitivityRms,
                    onValueChange = onUpdateVadSensitivity,
                    valueRange = 80f..400f,
                    colors = SliderDefaults.colors(
                        thumbColor = IndigoPrimary,
                        activeTrackColor = IndigoPrimary
                    ),
                    modifier = Modifier.testTag("vad_slider")
                )
            }
        }

        // Section: Storage & Network
        SectionHeader(icon = Icons.Default.FileDownload, title = "Mạng & Lưu trữ")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingSwitchRow(
                    title = stringResource(R.string.pref_wifi_only),
                    description = "Tránh tiêu tốn 3G/4G/5G khi tải mô hình nhận dạng giọng nói",
                    checked = settings.wifiOnlyDownload,
                    onCheckedChange = onUpdateWifiOnly,
                    testTag = "wifi_only_switch"
                )

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )

                SettingSwitchRow(
                    title = stringResource(R.string.pref_save_srt),
                    description = stringResource(R.string.pref_save_srt_sub),
                    checked = settings.saveSrtHistory,
                    onCheckedChange = onUpdateSaveSrt,
                    testTag = "save_srt_switch"
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = IndigoPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
