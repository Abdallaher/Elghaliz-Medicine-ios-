package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.model.Meeting
import com.example.data.model.MeetingMessage
import com.example.ui.components.ElghalizModalDialog
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.delay
import kotlin.math.sin

private data class MedicalSlideDeckItem(
    val slideTitle: String,
    val subtitle: String,
    val keyPoints: List<String>
)

private val BUILT_IN_MEDICAL_SLIDES = listOf(
    MedicalSlideDeckItem(
        slideTitle = "الشريحة 1: التقييم السريري الأولي وقراءة تخطيط القلب (ECG)",
        subtitle = "Clinical Assessment & 12-Lead ECG Interpretation",
        keyPoints = listOf(
            "فحص انتظام النبض ومعدل ضربات القلب (Rate & Rhythm Analysis)",
            "تحليل محور القلب الكهربائي وموجة P وفترة PR",
            "تشخيص ارتفاع المقطع ST الحاد في الاحتشاء القلبي (STEMI)"
        )
    ),
    MedicalSlideDeckItem(
        slideTitle = "الشريحة 2: بروتوكول الطوارئ الباطنية والإنعاش القلبي المتقدم",
        subtitle = "Emergency Stabilization & Hemodynamic Monitoring",
        keyPoints = listOf(
            "تأمين مجرى الهواء وإعطاء الأكسجين حسب نسبة التشبع SpO2",
            "سحب إنزيمات القلب Troponin I/T مع المراقبة المستمرة",
            "التدخل الدوائي العاجل ومضادات التجلط وفق الدليل السريري"
        )
    ),
    MedicalSlideDeckItem(
        slideTitle = "الشريحة 3: مناقشة الحالات السريرية وأسئلة الطلاب",
        subtitle = "Interactive Case Discussion & Differential Diagnosis",
        keyPoints = listOf(
            "مقارنة الأعراض السريرية بين الذبحة الصدرية والتهاب التامور",
            "تفسير صور الأشعة المقطعية وفحوصات الإيكو القلبي",
            "استقبال أسئلة الطلاب المباشرة عبر صندوق الدردشة ورفع اليد"
        )
    )
)

@Composable
fun LiveMeetingScreen(
    isAdminMode: Boolean,
    meetingsState: UiState<List<Meeting>>,
    activeLiveRoom: Meeting?,
    activeRoomMessages: List<MeetingMessage>,
    onStartLiveMeeting: (title: String, initialPresentationMode: String) -> Unit,
    onEnterMeetingRoom: (Meeting) -> Unit,
    onLeaveMeetingRoom: () -> Unit,
    onUpdateDoctorControls: (
        meeting: Meeting,
        isRecording: Boolean,
        isScreenSharing: Boolean,
        presentationMode: String,
        currentSlideIndex: Long,
        studentsMuted: Boolean
    ) -> Unit,
    onToggleHandRaise: (Meeting, isRaising: Boolean) -> Unit,
    onSendChatMessage: (meetingId: String, text: String) -> Unit,
    onEndMeetingAndSave: (Meeting, wasRecorded: Boolean) -> Unit
) {
    var showStartMeetingDialog by remember { mutableStateOf(false) }
    val allMeetings = (meetingsState as? UiState.Success)?.data ?: emptyList()
    val liveMeetings = remember(allMeetings) { allMeetings.filter { it.status == "live" } }
    val endedMeetings = remember(allMeetings) { allMeetings.filter { it.status == "ended" } }

    if (activeLiveRoom != null) {
        ActiveNativeLiveMeetingRoomView(
            meeting = activeLiveRoom,
            messages = activeRoomMessages,
            isAdminMode = isAdminMode,
            onLeaveRoom = onLeaveMeetingRoom,
            onUpdateDoctorControls = { rec, share, mode, slideIdx, muted ->
                onUpdateDoctorControls(activeLiveRoom, rec, share, mode, slideIdx, muted)
            },
            onToggleHandRaise = { isRaising ->
                onToggleHandRaise(activeLiveRoom, isRaising)
            },
            onSendChatMessage = { msgText ->
                onSendChatMessage(activeLiveRoom.id, msgText)
            },
            onEndMeeting = { wasRecorded ->
                onEndMeetingAndSave(activeLiveRoom, wasRecorded)
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("live_meetings_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Card for Built-in Native Live Meetings (No Google Meet)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoCall,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "غرفة الاجتماعات والبث المباشر الداخلية",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "غرفة بث واجتماع مدمجة 100% داخل التطبيق بالكاميرا والمايكروفون وعرض السلايدات والدردشة ورفع اليد والمسجل الداخلي المباشر.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isAdminMode) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showStartMeetingDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("start_live_meet_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(imageVector = Icons.Default.VideoCall, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "بدء اجتماع مباشر داخلي (Start Native Live)",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Active Live Meetings Section
        if (liveMeetings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لا يوجد بث مباشر جارٍ في هذه اللحظة",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isAdminMode) {
                                "اضغط على زر \"بدء اجتماع مباشر داخلي\" أعلاه لفتح غرفة البث المدمجة بالكاميرا والسلايدات للطلاب."
                            } else {
                                "عندما يبدأ الدكتور البث المباشر الداخلي، سيظهر لك زر \"الانضمام لغرفة البث المباشر\" هنا فوراً."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "غرف البث المباشر النشطة الآن (${liveMeetings.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            items(liveMeetings, key = { it.id }) { meeting ->
                NativeLiveMeetingCard(
                    meeting = meeting,
                    isAdminMode = isAdminMode,
                    onJoinClick = { onEnterMeetingRoom(meeting) }
                )
            }
        }

        if (endedMeetings.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "جلسات بث سابقة منتهية",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(endedMeetings.take(5), key = { it.id }) { ended ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ended.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "بإشراف: د. ${ended.hostName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "منتهي",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStartMeetingDialog) {
        StartNativeLiveMeetingModal(
            onDismiss = { showStartMeetingDialog = false },
            onConfirm = { title, initialMode ->
                onStartLiveMeeting(title, initialMode)
                showStartMeetingDialog = false
            }
        )
    }
    }
}

@Composable
private fun NativeLiveMeetingCard(
    meeting: Meeting,
    isAdminMode: Boolean,
    onJoinClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_meeting_card_${meeting.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFFD63B4B),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (meeting.isRecording) "بث داخلي مباشر • جاري التسجيل" else "غرفة بث داخلي نشطة",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = "${meeting.participantCount} متصل • ✋ ${meeting.raisedHandsCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = meeting.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "المحاضر: د. ${meeting.hostName} • الوضع: ${if (meeting.presentationMode == "slides") "عرض السلايدات الطبية" else "كاميرا الدكتور المباشرة"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onJoinClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("join_live_meeting_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.Videocam, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAdminMode) "إدارة غرفة البث المباشر الداخلية" else "الانضمام للاجتماع المباشر الداخلي",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ActiveNativeLiveMeetingRoomView(
    meeting: Meeting,
    messages: List<MeetingMessage>,
    isAdminMode: Boolean,
    onLeaveRoom: () -> Unit,
    onUpdateDoctorControls: (
        isRecording: Boolean,
        isScreenSharing: Boolean,
        presentationMode: String,
        currentSlideIndex: Long,
        studentsMuted: Boolean
    ) -> Unit,
    onToggleHandRaise: (isRaising: Boolean) -> Unit,
    onSendChatMessage: (String) -> Unit,
    onEndMeeting: (wasRecorded: Boolean) -> Unit
) {
    BackHandler(onBack = onLeaveRoom)

    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            runCatching {
                permissionsLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            }
        }
    }

    var micEnabled by remember { mutableStateOf(true) }
    var camEnabled by remember { mutableStateOf(true) }
    var isRecording by remember(meeting.isRecording) { mutableStateOf(meeting.isRecording) }
    var isScreenSharing by remember(meeting.isScreenSharing) { mutableStateOf(meeting.isScreenSharing) }
    var presentationMode by remember(meeting.presentationMode) { mutableStateOf(meeting.presentationMode) }
    var currentSlideIndex by remember(meeting.currentSlideIndex) {
        mutableIntStateOf(meeting.currentSlideIndex.toInt().coerceIn(0, BUILT_IN_MEDICAL_SLIDES.lastIndex))
    }
    var studentsMuted by remember(meeting.studentsMuted) { mutableStateOf(meeting.studentsMuted) }
    var handRaised by remember { mutableStateOf(false) }
    var hadRecordedSession by remember { mutableStateOf(meeting.isRecording) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var chatInput by remember { mutableStateOf("") }

    // If Doctor mutes all students, force student mic off
    val effectiveStudentMic = if (!isAdminMode && studentsMuted) false else micEnabled

    LaunchedEffect(isRecording) {
        while (true) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                delay(1000L)
            }
            elapsedSeconds++
            if (isRecording) {
                recordingSeconds++
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("native_live_meeting_room"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Room Header Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = meeting.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "د. ${meeting.hostName} • ${meeting.participantCount} متصل • ✋ ${meeting.raisedHandsCount} رافع يده • ${"%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isRecording) {
                        Surface(
                            color = Color(0xFFD63B4B),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("recording_active_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RadioButtonChecked,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "REC ${"%02d:%02d".format(recordingSeconds / 60, recordingSeconds % 60)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Main Native Video / Medical Slides Broadcast Stage
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(265.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF073B2C))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF074E39), Color(0xFF04241B))
                            )
                        )
                ) {
                    if (presentationMode == "slides" || isScreenSharing) {
                        // Built-in Medical Slides / Screen Presentation View
                        val currentSlide = BUILT_IN_MEDICAL_SLIDES[currentSlideIndex.coerceIn(0, BUILT_IN_MEDICAL_SLIDES.lastIndex)]
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color(0xFF1A9672),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Slideshow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "عرض السلايدات والشاشة الطبية المباشر",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White
                                        )
                                    }
                                }

                                Text(
                                    text = "${currentSlideIndex + 1} / ${BUILT_IN_MEDICAL_SLIDES.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFB7F0DA)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = currentSlide.slideTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentSlide.subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF80E5C3)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                currentSlide.keyPoints.forEach { bullet ->
                                    Text(
                                        text = "• $bullet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFEAF7F1),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }

                            if (isAdminMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            val prevIdx = (currentSlideIndex - 1).coerceAtLeast(0)
                                            currentSlideIndex = prevIdx
                                            onUpdateDoctorControls(isRecording, isScreenSharing, "slides", prevIdx.toLong(), studentsMuted)
                                        },
                                        enabled = currentSlideIndex > 0,
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text("السلايد السابق", style = MaterialTheme.typography.labelSmall)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            val nextIdx = (currentSlideIndex + 1).coerceAtMost(BUILT_IN_MEDICAL_SLIDES.lastIndex)
                                            currentSlideIndex = nextIdx
                                            onUpdateDoctorControls(isRecording, isScreenSharing, "slides", nextIdx.toLong(), studentsMuted)
                                        },
                                        enabled = currentSlideIndex < BUILT_IN_MEDICAL_SLIDES.lastIndex,
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("السلايد التالي", style = MaterialTheme.typography.labelSmall)
                                        Icon(imageVector = Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            } else {
                                Text(
                                    text = "يشرح الآن د. ${meeting.hostName} على السلايدات الطبية المباشرة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB7F0DA),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Live Native Camera Feed / Doctor Video Broadcast Stage
                        if (camEnabled && hasCameraPermission) {
                            NativeCameraPreviewSurface(
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Medical Telemetry + Doctor Camera Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (camEnabled && hasCameraPermission) {
                                        Color.Black.copy(alpha = 0.25f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .padding(18.dp)
                        ) {
                            // Animated Live Medical ECG Wave at Bottom of Video Stage
                            LiveMedicalWaveOverlay(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .align(Alignment.BottomCenter)
                            )

                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(78.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1A9672).copy(alpha = 0.88f))
                                        .border(2.dp, Color(0xFF80E5C3), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (camEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "البث المرئي المباشر: د. ${meeting.hostName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (studentsMuted) {
                                        "صوت الطلاب مكتوم حالياً من قِبل الدكتور لمنع التشويش"
                                    } else {
                                        "الكاميرا والمايكروفون متصلان داخل الغرفة المدمجة"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFB7F0DA),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Doctor / Student Controls Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isAdminMode) {
                        // Row 1: Doctor Presentation Mode Switcher (Camera vs Slides) & Mute Students
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val nextMode = if (presentationMode == "slides") "camera" else "slides"
                                    val nextShare = nextMode == "slides"
                                    presentationMode = nextMode
                                    isScreenSharing = nextShare
                                    onUpdateDoctorControls(isRecording, nextShare, nextMode, currentSlideIndex.toLong(), studentsMuted)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("toggle_slides_mode_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (presentationMode == "slides") Icons.Default.Videocam else Icons.Default.PresentToAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (presentationMode == "slides") "عرض كاميرا الدكتور" else "مشاركة الشاشة والسلايدات",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    val nextMuted = !studentsMuted
                                    studentsMuted = nextMuted
                                    onUpdateDoctorControls(isRecording, isScreenSharing, presentationMode, currentSlideIndex.toLong(), nextMuted)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("mute_all_students_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (studentsMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (studentsMuted) "إلغاء كتم الطلاب" else "كتم صوت الطلاب",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        // Row 2: Built-in Native Recorder Button
                        Button(
                            onClick = {
                                val nextRec = !isRecording
                                isRecording = nextRec
                                if (nextRec) hadRecordedSession = true
                                onUpdateDoctorControls(nextRec, isScreenSharing, presentationMode, currentSlideIndex.toLong(), studentsMuted)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("record_meeting_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) Color(0xFFD63B4B) else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.StopCircle else Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRecording) {
                                    "إيقاف المسجل الداخلي المؤقت (جاري التسجيل بالصوت والصورة)"
                                } else {
                                    "بدء التسجيل بالمسجل الداخلي (Built-in Recorder)"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        // Student Status Banner if Muted by Doctor + Raise Hand Button
                        if (studentsMuted) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "قام الدكتور بكتم مايكروفونات الطلاب حالياً أثناء الشرح. يمكنك طرح سؤالك في الدردشة أو رفع اليد.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(10.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Row 3: Mic, Camera, Raise Hand (for Student), and End/Leave Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isAdminMode || !studentsMuted) {
                                    micEnabled = !micEnabled
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (effectiveStudentMic) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.errorContainer
                                )
                                .testTag("toggle_mic_button")
                        ) {
                            Icon(
                                imageVector = if (effectiveStudentMic) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = "المايكروفون",
                                tint = if (effectiveStudentMic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }

                        IconButton(
                            onClick = { camEnabled = !camEnabled },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .testTag("toggle_camera_button")
                        ) {
                            Icon(
                                imageVector = if (camEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                contentDescription = "الكاميرا",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (!isAdminMode) {
                            OutlinedButton(
                                onClick = {
                                    val nextRaise = !handRaised
                                    handRaised = nextRaise
                                    onToggleHandRaise(nextRaise)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("raise_hand_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FrontHand,
                                    contentDescription = "رفع اليد",
                                    tint = if (handRaised) Color(0xFFD97706) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (handRaised) "خفّض اليد ✋" else "رفع اليد ✋")
                            }
                        }

                        if (isAdminMode) {
                            Button(
                                onClick = { onEndMeeting(hadRecordedSession || isRecording) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD63B4B)),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("end_and_save_meeting_button")
                            ) {
                                Icon(imageVector = Icons.Default.CallEnd, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (hadRecordedSession || isRecording) {
                                        "إنهاء وحفظ التسجيل"
                                    } else {
                                        "إنهاء البث"
                                    }
                                )
                            }
                        } else {
                            Button(
                                onClick = onLeaveRoom,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD63B4B)),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(imageVector = Icons.Default.CallEnd, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مغادرة")
                            }
                        }
                    }
                }
            }
        }

        // 4. Built-in Real-time Chat Box for Student Questions ("صندوق دردشة داخلي لأسئلة الطلاب")
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("in_room_chat_box"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "صندوق الدردشة الداخلي لأسئلة الطلاب (${messages.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (messages.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "لا توجد أسئلة بعد. اكتب سؤالك للدكتور هنا ليظهر فوراً في الغرفة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            messages.takeLast(8).forEach { msg ->
                                val isDoctorMsg = msg.senderRole == "admin"
                                Surface(
                                    color = when {
                                        msg.isHandRaise -> Color(0xFFFEF3C7)
                                        isDoctorMsg -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(
                                            text = if (isDoctorMsg) "د. ${msg.senderName} (المحاضر)" else "طالب: ${msg.senderName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("اكتب سؤالك أو تعليقك الطبي في الغرفة...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("live_chat_input")
                        )

                        Button(
                            onClick = {
                                val trimmed = chatInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    onSendChatMessage(trimmed)
                                    chatInput = ""
                                }
                            },
                            enabled = chatInput.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("send_live_chat_button")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال")
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun NativeCameraPreviewSurface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var cameraDevice by remember { mutableStateOf<CameraDevice?>(null) }
    var captureSession by remember { mutableStateOf<CameraCaptureSession?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { captureSession?.close() }
            runCatching { cameraDevice?.close() }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        openFrontOrBackCamera(ctx, surface) { dev, session ->
                            cameraDevice = dev
                            captureSession = session
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        runCatching { captureSession?.close() }
                        runCatching { cameraDevice?.close() }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        }
    )
}

@SuppressLint("MissingPermission")
private fun openFrontOrBackCamera(
    context: Context,
    surfaceTexture: SurfaceTexture,
    onOpened: (CameraDevice?, CameraCaptureSession?) -> Unit
) {
    runCatching {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            val chars = manager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        } ?: manager.cameraIdList.firstOrNull() ?: return

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                runCatching {
                    surfaceTexture.setDefaultBufferSize(640, 480)
                    val surface = Surface(surfaceTexture)
                    val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(surface)
                    }
                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(
                        listOf(surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                runCatching {
                                    session.setRepeatingRequest(requestBuilder.build(), null, null)
                                    onOpened(camera, session)
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                onOpened(camera, null)
                            }
                        },
                        null
                    )
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
                runCatching { camera.close() }
            }

            override fun onError(camera: CameraDevice, error: Int) {
                runCatching { camera.close() }
            }
        }, null)
    }
}

@Composable
private fun LiveMedicalWaveOverlay(modifier: Modifier = Modifier) {
    var phase by remember { mutableStateOf(0.5f) }
    LaunchedEffect(Unit) {
        repeat(8) {
            delay(40L)
            phase += 0.25f
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val path = Path()
        val steps = 80
        for (i in 0..steps) {
            val x = (w / steps) * i
            val angle = (i * 0.22f) + phase
            val y = midY + (sin(angle.toDouble()).toFloat() * (h * 0.28f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = Color(0xFF42E6B5).copy(alpha = 0.65f),
            style = Stroke(width = 3.dp.toPx())
        )
        drawLine(
            color = Color.White.copy(alpha = 0.15f),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
private fun StartNativeLiveMeetingModal(
    onDismiss: () -> Unit,
    onConfirm: (title: String, initialPresentationMode: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("camera") }

    ElghalizModalDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "بدء غرفة بث مباشر داخلية (بدون روابط خارجية)",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "تُفتح الغرفة مباشرة داخل شاشة تطبيق Elghaliz Medicine مع الكاميرا والمايكروفون والسلايدات والدردشة والمسجل الداخلي.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان المحاضرة المباشرة") },
                    placeholder = { Text("مثال: مراجعة سريرية لأمراض القلب") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("meeting_title_input")
                )

                Text(
                    text = "اختر وضع العرض الابتدائي داخل الغرفة:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedMode == "camera",
                        onClick = { selectedMode = "camera" },
                        label = { Text("كاميرا الدكتور المباشرة") }
                    )
                    FilterChip(
                        selected = selectedMode == "slides",
                        onClick = { selectedMode = "slides" },
                        label = { Text("عرض السلايدات الطبية") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, selectedMode) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("confirm_start_meeting_button")
            ) {
                Text("بدء البث الداخلي الآن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
