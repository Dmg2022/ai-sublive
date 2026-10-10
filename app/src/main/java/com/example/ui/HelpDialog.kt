package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.IndigoPrimary

@Composable
fun HelpDialog(
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = IndigoPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hướng dẫn sử dụng LiveSub",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = "Các bước bắt đầu:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                HelpStepItem(number = "1", title = "Tải mô hình ngôn ngữ", desc = "Vào tab 'Mô hình' và tải mô hình tiếng nói (ví dụ: English ~40MB).")
                HelpStepItem(number = "2", title = "Cấp đủ 3 quyền", desc = "Cấp quyền Thu âm, Cửa sổ nổi và Thông báo.")
                HelpStepItem(number = "3", title = "Nhấn 'Bắt đầu LiveSub'", desc = "Khi hộp thoại hệ thống hiện lên hỏi bắt âm thanh/màn hình, chọn 'Bắt đầu ngay' (Start now).")
                HelpStepItem(number = "4", title = "Mở app video/podcast bất kỳ", desc = "Mở YouTube, TikTok, trình duyệt... Phụ đề tiếng Việt sẽ tự động nổi lên trên video!")
                HelpStepItem(number = "5", title = "Thao tác trên cửa sổ nổi", desc = "• Kéo thả để đổi vị trí.\n• Chạm giữ để mở menu nhanh (đổi cỡ chữ, độ mờ, ẩn câu gốc, xuyên chạm).")

                Spacer(modifier = Modifier.height(14.dp))

                // Limitations Notice
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Lưu ý & Giới hạn hệ điều hành Android:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = AmberWarning
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• DRM & Cuộc gọi: Các ứng dụng bảo vệ bản quyền DRM (như Netflix) hoặc cuộc gọi (SIM, Zalo, Telegram) bị Android cấm bắt âm thanh ở cấp độ bảo mật hệ thống.\n• Vosk Offline: Mô hình nhỏ được tối ưu để chạy mượt, tiết kiệm pin mà không cần mạng. Với video có nhạc nền quá lớn, bạn có thể tăng âm lượng hoặc chỉnh lại độ nhạy VAD trong Cài đặt.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(R.string.btn_close))
            }
        }
    )
}

@Composable
private fun HelpStepItem(number: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = IndigoPrimary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
