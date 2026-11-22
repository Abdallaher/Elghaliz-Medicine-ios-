package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.model.Lecture

@Composable
fun MedicalVideoPlayerDialog(
    lecture: Lecture,
    isPreparingVideo: Boolean,
    preparingVideoProgress: Int = 0,
    isDownloadedInApp: Boolean,
    isDownloadingNow: Boolean,
    downloadProgressPercent: Int = 0,
    userEmail: String = "",
    onDownloadInAppClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val playerState = rememberElghalizMedia3PlayerState(
        lecture = lecture,
        isDownloadedInApp = isDownloadedInApp
    )

    BackHandler {
        playerState.persistCurrentPosition()
        if (playerState.isScreenLocked) {
            playerState.unlockScreen()
        } else if (playerState.isFullscreenLandscape) {
            playerState.exitFullscreen()
        } else {
            onDismiss()
        }
    }

    val backdropInteraction = remember { MutableInteractionSource() }
    val cleanDescription = remember(lecture.description) {
        val trimmed = lecture.description.trim()
        if (trimmed == "محاضرة مرئية مرفوعة مباشرة إلى مخزن الـ 5 تيرابايت المركزي" ||
            trimmed == "محاضرة طبية مرفوعة لتطبيق Elghaliz Medicine بالجودة الأصلية الكاملة" ||
            trimmed == "محاضرة مرئية"
        ) {
            ""
        } else {
            trimmed
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (playerState.isFullscreenLandscape) 100f else 90f)
            .let {
                if (playerState.isFullscreenLandscape) {
                    it.background(Color.Black).testTag("fullscreen_landscape_video_container")
                } else {
                    it
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (!playerState.isFullscreenLandscape) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .clickable(
                        interactionSource = backdropInteraction,
                        indication = null,
                        onClick = {
                            playerState.persistCurrentPosition()
                            onDismiss()
                        }
                    )
            )
        }

        ElevatedCard(
            modifier = if (playerState.isFullscreenLandscape) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            },
            shape = if (playerState.isFullscreenLandscape) RectangleShape else RoundedCornerShape(26.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (playerState.isFullscreenLandscape) Color.Black else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = if (playerState.isFullscreenLandscape) 0.dp else 8.dp
            )
        ) {
            Column(
                modifier = if (playerState.isFullscreenLandscape) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                }
            ) {
                if (!playerState.isFullscreenLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFFE9F0E8),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = lecture.categoryName.ifBlank { "محاضرة طبية" },
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF1F6B52),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                playerState.persistCurrentPosition()
                                onDismiss()
                            },
                            modifier = Modifier.testTag("close_player_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق المشغل",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                Card(
                    modifier = if (playerState.isFullscreenLandscape) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    },
                    shape = if (playerState.isFullscreenLandscape) RectangleShape else RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (playerState.isFullscreenLandscape) 0.dp else 6.dp
                    )
                ) {
                    ElghalizVideoViewport(
                        state = playerState,
                        lecture = lecture,
                        userEmail = userEmail,
                        isPreparingVideo = isPreparingVideo,
                        showOverlaySpeedRow = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (!playerState.isFullscreenLandscape) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = lecture.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "د. ${lecture.doctorName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF1F6B52),
                            fontWeight = FontWeight.Bold
                        )

                        if (cleanDescription.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = cleanDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onDownloadInAppClick,
                            enabled = !isDownloadedInApp && !isDownloadingNow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("player_download_in_app_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFC9A24D),
                                contentColor = Color(0xFF0F3D2E)
                            )
                        ) {
                            if (isDownloadingNow) {
                                CircularProgressIndicator(
                                    color = Color(0xFF0F3D2E),
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "جاري التنزيل (${downloadProgressPercent.coerceIn(0, 99)}%)",
                                    color = Color(0xFF0F3D2E),
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = if (isDownloadedInApp) Icons.Default.CheckCircle else Icons.Default.DownloadForOffline,
                                    contentDescription = null,
                                    tint = Color(0xFF0F3D2E),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isDownloadedInApp) {
                                        "تم التنزيل للمشاهدة بدون إنترنت ✓"
                                    } else {
                                        "تنزيل داخل التطبيق"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF0F3D2E),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (isDownloadingNow) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (downloadProgressPercent.coerceIn(0, 100)) / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = Color(0xFFC9A24D)
                            )
                        }
                    }
                }
            }
        }
    }
}
