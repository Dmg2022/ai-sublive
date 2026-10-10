package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError

@Composable
fun PermissionBanner(
    hasAudioPermission: Boolean,
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestAudio: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allGranted = hasAudioPermission && hasOverlayPermission && hasNotificationPermission

    if (allGranted) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("permission_banner_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = RoseError,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cần cấp quyền để hoạt động",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Hệ thống Android yêu cầu cấp quyền thu âm thanh, vẽ cửa sổ nổi và thông báo để LiveSub hoạt động.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionItem(
                title = "Thu âm thanh (RECORD_AUDIO)",
                isGranted = hasAudioPermission,
                icon = Icons.Default.Mic,
                onRequest = onRequestAudio
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionItem(
                title = "Cửa sổ nổi (SYSTEM_ALERT_WINDOW)",
                isGranted = hasOverlayPermission,
                icon = Icons.Default.Layers,
                onRequest = onRequestOverlay
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionItem(
                title = "Thông báo hệ thống (POST_NOTIFICATIONS)",
                isGranted = hasNotificationPermission,
                icon = Icons.Default.Notifications,
                onRequest = onRequestNotification
            )
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    isGranted: Boolean,
    icon: ImageVector,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Đã cấp",
                tint = EmeraldSuccess,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.height(30.dp)
            ) {
                Text(text = "Cấp quyền", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
