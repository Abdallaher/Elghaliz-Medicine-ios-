package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.model.Category
import com.example.data.model.DownloadTaskState
import com.example.data.model.DownloadTaskStatus
import com.example.data.model.Lecture
import com.example.data.model.OfflineDownload
import com.example.data.model.SessionPreferences
import com.example.data.model.TimestampedLectureNote
import com.example.ui.components.ELGHALIZ_PLAYER_SPEEDS
import com.example.ui.components.ElghalizVideoViewport
import com.example.ui.components.formatDurationHoursMinutesSeconds
import com.example.ui.components.rememberElghalizMedia3PlayerState
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DeepEmerald = Color(0xFF0F3D2E)
private val MidEmerald = Color(0xFF1F6B52)
private val SoftGold = Color(0xFFC9A24D)
private val SoftMint = Color(0xFFE9F0E8)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoursePlayerScreen(
    category: Category,
    allLectures: List<Lecture>,
    downloadsState: UiState<List<OfflineDownload>>,
    downloadingLectureIds: Set<String>,
    deletingLectureIds: Set<String>,
    downloadProgressMap: Map<String, Int>,
    downloadStatesMap: Map<String, DownloadTaskState>,
    isAdminMode: Boolean,
    userEmail: String,
    bannerMessage: String?,
    onDismissBanner: () -> Unit,
    onBackClick: () -> Unit,
    onDeleteLecture: (String) -> Unit,
    onDownloadInAppOnly: (Lecture) -> Unit,
    onPauseDownload: (Lecture) -> Unit,
    onCancelDownload: (Lecture) -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val courseLectures = remember(allLectures, category.id) {
        allLectures.filter { it.categoryId == category.id }
    }

    val downloadedIds = remember(downloadsState) {
        ((downloadsState as? UiState.Success)?.data ?: emptyList())
            .map { it.lectureId }
            .toSet()
    }

    var playbackPositions by remember(category.id) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var completedIds by remember(category.id) { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(category.id) {
        val (posMap, compSet) = withContext(Dispatchers.IO) {
            SessionPreferences.getAllPlaybackPositionsAndCompletions(context)
        }
        playbackPositions = posMap
        completedIds = compSet
    }

    // عدم التشغيل التلقائي عند الدخول للكورس: يبدأ التشغيل فقط عند الضغط على زر «تشغيل» بجانب المحاضرة
    var selectedLectureId by rememberSaveable(category.id) {
        mutableStateOf<String?>(null)
    }

    // إذا تم حذف المحاضرة النشطة حالياً، نُفرّغ الاختيار بدلاً من تشغيل محاضرة أخرى تلقائياً
    LaunchedEffect(courseLectures) {
        val currentId = selectedLectureId
        if (currentId != null && courseLectures.none { it.id == currentId }) {
            selectedLectureId = null
        }
    }

    val activeLecture = remember(courseLectures, selectedLectureId) {
        val currentId = selectedLectureId ?: return@remember null
        courseLectures.firstOrNull { it.id == currentId }
    }

    var showSearchBar by rememberSaveable(category.id) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(category.id) { mutableStateOf("") }
    var activeSubTab by rememberSaveable(category.id) { mutableIntStateOf(0) } // 0 = محاضرات الكورس, 1 = ملاحظاتي

    val filteredCourseLectures = remember(courseLectures, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) {
            courseLectures
        } else {
            courseLectures.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.description.contains(q, ignoreCase = true) ||
                    it.doctorName.contains(q, ignoreCase = true)
            }
        }
    }

    val isActiveDownloaded = activeLecture != null && (
        downloadedIds.contains(activeLecture.id) ||
            downloadStatesMap[activeLecture.id]?.status == DownloadTaskStatus.COMPLETED
        )

    val playerState = rememberElghalizMedia3PlayerState(
        lecture = activeLecture,
        isDownloadedInApp = isActiveDownloaded,
        onPlaybackUpdated = { lecId, sec, _, isCompleted ->
            playbackPositions = playbackPositions + (lecId to sec)
            if (isCompleted) {
                completedIds = completedIds + lecId
            }
        }
    )

    // Lecture Notes state for currently playing lecture
    var currentNotes by remember(activeLecture?.id) {
        mutableStateOf<List<TimestampedLectureNote>>(emptyList())
    }
    LaunchedEffect(activeLecture?.id) {
        val lec = activeLecture
        currentNotes = if (lec != null) {
            withContext(Dispatchers.IO) { SessionPreferences.getLectureNotes(context, lec.id) }
        } else {
            emptyList()
        }
    }
    var newNoteText by remember(activeLecture?.id) { mutableStateOf("") }

    BackHandler {
        playerState.persistCurrentPosition()
        if (playerState.isScreenLocked) {
            playerState.unlockScreen()
        } else if (playerState.isFullscreenLandscape) {
            playerState.exitFullscreen()
        } else {
            onBackClick()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (playerState.isFullscreenLandscape) Color.Black else MaterialTheme.colorScheme.background
            )
            .let { mod ->
                if (playerState.isFullscreenLandscape) {
                    mod.zIndex(100f).testTag("fullscreen_landscape_video_container")
                } else {
                    mod.windowInsetsPadding(WindowInsets.safeDrawing)
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Course Player Top Bar (Hidden only in Fullscreen Landscape)
            if (!playerState.isFullscreenLandscape) {
                Surface(
                    color = DeepEmerald,
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        playerState.persistCurrentPosition()
                                        onBackClick()
                                    },
                                    modifier = Modifier.testTag("course_player_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "العودة لقائمة الكورسات",
                                        tint = SoftGold
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${courseLectures.size} محاضرة في الكورس",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.78f),
                                        maxLines = 1
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    showSearchBar = !showSearchBar
                                    if (!showSearchBar) searchQuery = ""
                                },
                                modifier = Modifier.testTag("course_player_search_button")
                            ) {
                                Icon(
                                    imageVector = if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "بحث داخل محاضرات الكورس",
                                    tint = SoftGold
                                )
                            }
                        }

                        AnimatedVisibility(visible = showSearchBar) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        text = "ابحث داخل محاضرات ${category.name}...",
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = SoftGold
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "مسح البحث",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = SoftGold,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                                    cursorColor = SoftGold
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                                    .testTag("course_player_search_input")
                            )
                        }

                        if (!bannerMessage.isNullOrBlank()) {
                            Surface(
                                color = Color(0x33C9A24D),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                                onClick = onDismissBanner
                            ) {
                                Text(
                                    text = bannerMessage,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Single-Tree 16:9 Media3 Video Player (appears once the student taps "تشغيل" on any lecture)
            if (activeLecture != null) {
                Surface(
                    modifier = if (playerState.isFullscreenLandscape) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    },
                    shape = RectangleShape,
                    color = Color.Black
                ) {
                    ElghalizVideoViewport(
                        state = playerState,
                        lecture = activeLecture,
                        userEmail = userEmail,
                        isPreparingVideo = false,
                        showOverlaySpeedRow = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (!playerState.isFullscreenLandscape && courseLectures.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color(0xFF0A2A20)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = SoftGold,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "لا توجد محاضرات مرفوعة في هذا الكورس بعد",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (!playerState.isFullscreenLandscape) {
                Surface(
                    color = if (isDarkTheme) Color(0xFF133629) else SoftMint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("course_player_idle_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(DeepEmerald),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = SoftGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "اضغط على زر «تشغيل» بجوار أي محاضرة للبدء",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else DeepEmerald
                        )
                    }
                }
            }

            // 3. Controls Bar + Two Tabs + Lectures/Notes List (Portrait Mode)
            if (!playerState.isFullscreenLandscape) {
                if (activeLecture != null) {
                    // Controls Bar directly below video
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = activeLecture.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Transport Row: -10 ث | Green Circular Play/Pause (#0F3D2E) | +10 ث
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { playerState.seekByDeltaSeconds(-10) },
                                    shape = RoundedCornerShape(50),
                                    color = if (isDarkTheme) Color(0xFF1A4636) else SoftMint,
                                    modifier = Modifier.testTag("course_player_rewind_10")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Replay10,
                                            contentDescription = "-10 ث",
                                            tint = if (isDarkTheme) SoftGold else DeepEmerald,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "-10 ث",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkTheme) Color.White else DeepEmerald
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(18.dp))

                                // Green Circular Play/Pause Button (#0F3D2E)
                                IconButton(
                                    onClick = { playerState.togglePlayPause() },
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(DeepEmerald)
                                        .testTag("course_player_play_pause")
                                ) {
                                    Icon(
                                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playerState.isPlaying) "إيقاف مؤقت" else "تشغيل",
                                        tint = SoftGold,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(18.dp))

                                Surface(
                                    onClick = { playerState.seekByDeltaSeconds(10) },
                                    shape = RoundedCornerShape(50),
                                    color = if (isDarkTheme) Color(0xFF1A4636) else SoftMint,
                                    modifier = Modifier.testTag("course_player_forward_10")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "+10 ث",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkTheme) Color.White else DeepEmerald
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Forward10,
                                            contentDescription = "+10 ث",
                                            tint = if (isDarkTheme) SoftGold else DeepEmerald,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Course Name Green Badge + Speed Chips (1x, 1.25x, 1.5x, 2x)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = if (isDarkTheme) Color(0xFF1A4636) else SoftMint,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isDarkTheme) SoftGold else MidEmerald,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ELGHALIZ_PLAYER_SPEEDS.forEach { speedOption ->
                                        val isSelected = playerState.playbackSpeed == speedOption
                                        val label = when (speedOption) {
                                            1.0f -> "1x"
                                            2.0f -> "2x"
                                            else -> "${speedOption}x"
                                        }
                                        Surface(
                                            color = if (isSelected) DeepEmerald else (if (isDarkTheme) Color(0xFF193E30) else SoftMint),
                                            border = if (isSelected) BorderStroke(1.dp, SoftGold) else null,
                                            shape = RoundedCornerShape(50),
                                            onClick = { playerState.setSpeed(speedOption) }
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isSelected) Color.White else (if (isDarkTheme) Color.White else DeepEmerald),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Two Stylish Tabs: "محاضرات الكورس" & "ملاحظاتي"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val tab0Selected = activeSubTab == 0
                    Surface(
                        onClick = { activeSubTab = 0 },
                        shape = RoundedCornerShape(14.dp),
                        color = if (tab0Selected) DeepEmerald else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (tab0Selected) SoftGold else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("course_tab_lectures")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "محاضرات الكورس (${filteredCourseLectures.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (tab0Selected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    val tab1Selected = activeSubTab == 1
                    Surface(
                        onClick = { activeSubTab = 1 },
                        shape = RoundedCornerShape(14.dp),
                        color = if (tab1Selected) DeepEmerald else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (tab1Selected) SoftGold else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("course_tab_notes")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "ملاحظاتي (${currentNotes.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (tab1Selected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 5. Tab Content Area
                if (activeSubTab == 0) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .testTag("course_lectures_list"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (filteredCourseLectures.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Text(
                                        text = if (searchQuery.isNotBlank()) {
                                            "لا توجد محاضرات مطابقة لبحث «$searchQuery» داخل هذا الكورس"
                                        } else {
                                            "لا توجد محاضرات مرفوعة في هذا الكورس حتى الآن"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp)
                                    )
                                }
                            }
                        } else {
                            items(filteredCourseLectures, key = { it.id }) { lecture ->
                                val isCurrentPlaying = activeLecture?.id == lecture.id
                                val isCompleted = completedIds.contains(lecture.id)
                                val isInProgress = !isCompleted && (isCurrentPlaying || (playbackPositions[lecture.id] ?: 0) > 0)
                                val taskState = downloadStatesMap[lecture.id]

                                CourseLectureCard(
                                    lecture = lecture,
                                    isCurrentlyPlaying = isCurrentPlaying,
                                    isPlayerActivelyPlaying = isCurrentPlaying && playerState.isPlaying,
                                    isCompleted = isCompleted,
                                    isInProgress = isInProgress,
                                    isDownloadedInApp = downloadedIds.contains(lecture.id) || taskState?.status == DownloadTaskStatus.COMPLETED,
                                    isDownloadingNow = downloadingLectureIds.contains(lecture.id) || taskState?.status == DownloadTaskStatus.DOWNLOADING,
                                    isDeletingNow = deletingLectureIds.contains(lecture.id),
                                    isPausedNow = taskState?.status == DownloadTaskStatus.PAUSED,
                                    downloadProgressPercent = taskState?.progressPercent ?: downloadProgressMap[lecture.id] ?: 0,
                                    isAdminMode = isAdminMode,
                                    isDarkTheme = isDarkTheme,
                                    onSelectAndPlay = {
                                        if (selectedLectureId != lecture.id) {
                                            playerState.persistCurrentPosition()
                                            selectedLectureId = lecture.id
                                        } else {
                                            playerState.togglePlayPause()
                                        }
                                    },
                                    onDownloadInAppClick = { onDownloadInAppOnly(lecture) },
                                    onPauseDownloadClick = { onPauseDownload(lecture) },
                                    onCancelDownloadClick = { onCancelDownload(lecture) },
                                    onDeleteClick = { onDeleteLecture(lecture.id) }
                                )
                            }
                        }
                    }
                } else {
                    // Tab 1: "ملاحظاتي" (Timestamped Notes)
                    CourseNotesTabContent(
                        activeLecture = activeLecture,
                        currentSecond = playerState.currentSecond,
                        notes = currentNotes,
                        noteInput = newNoteText,
                        onNoteInputChange = { newNoteText = it },
                        onAddNote = {
                            val lec = activeLecture ?: return@CourseNotesTabContent
                            if (newNoteText.isNotBlank()) {
                                currentNotes = SessionPreferences.addLectureNote(
                                    context = context,
                                    lectureId = lec.id,
                                    second = playerState.currentSecond,
                                    text = newNoteText
                                )
                                newNoteText = ""
                            }
                        },
                        onSeekToNote = { sec ->
                            playerState.seekToSecond(sec)
                        },
                        onDeleteNote = { noteId ->
                            val lec = activeLecture ?: return@CourseNotesTabContent
                            currentNotes = SessionPreferences.deleteLectureNote(context, lec.id, noteId)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseLectureCard(
    lecture: Lecture,
    isCurrentlyPlaying: Boolean,
    isPlayerActivelyPlaying: Boolean,
    isCompleted: Boolean,
    isInProgress: Boolean,
    isDownloadedInApp: Boolean,
    isDownloadingNow: Boolean,
    isDeletingNow: Boolean,
    isPausedNow: Boolean,
    downloadProgressPercent: Int,
    isAdminMode: Boolean,
    isDarkTheme: Boolean,
    onSelectAndPlay: () -> Unit,
    onDownloadInAppClick: () -> Unit,
    onPauseDownloadClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "تأكيد حذف المحاضرة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من حذف محاضرة «${lecture.title}»؟",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteClick()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_delete_lecture_${lecture.id}")
                ) {
                    Text(
                        text = "حذف نهائي",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_delete_lecture_${lecture.id}")
                ) {
                    Text("إلغاء")
                }
            }
        )
    }

    val cardBg = when {
        isCurrentlyPlaying && isDarkTheme -> Color(0xFF174534)
        isCurrentlyPlaying -> SoftMint
        else -> MaterialTheme.colorScheme.surface
    }
    val statusDotColor = when {
        isCompleted -> Color(0xFF10B981)
        isInProgress -> SoftGold
        else -> Color(0xFF9CA3AF)
    }
    val statusDotLabel = when {
        isCompleted -> "مكتملة"
        isInProgress -> "قيد المشاهدة"
        else -> "لم تبدأ"
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .let { mod ->
                if (isCurrentlyPlaying) {
                    mod.border(1.8.dp, SoftGold, RoundedCornerShape(22.dp))
                } else {
                    mod
                }
            }
            .testTag("lecture_card_${lecture.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardBg),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isCurrentlyPlaying) 4.dp else 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusDotLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrentlyPlaying) {
                            if (isDarkTheme) SoftGold else MidEmerald
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.SemiBold
                    )

                    if (isCurrentlyPlaying) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = SoftGold,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "قيد التشغيل الآن",
                                style = MaterialTheme.typography.labelSmall,
                                color = DeepEmerald,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (isAdminMode) {
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        enabled = !isDeletingNow,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("delete_lecture_button_${lecture.id}")
                    ) {
                        if (isDeletingNow) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "حذف المحاضرة",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lecture Title & Metadata + Explicit Play Button right next to each lecture
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lecture.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Duration & Size: e.g. 01:28:25 · 330 MB
                    Text(
                        text = "${formatLectureDuration(lecture.durationSeconds)} · ${formatLectureSize(lecture.fileSizeMb)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onSelectAndPlay,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlayerActivelyPlaying) SoftGold else DeepEmerald,
                        contentColor = if (isPlayerActivelyPlaying) DeepEmerald else Color.White
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("play_lecture_btn_${lecture.id}")
                ) {
                    Icon(
                        imageVector = if (isPlayerActivelyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlayerActivelyPlaying) "إيقاف مؤقت للمحاضرة" else "تشغيل المحاضرة",
                        tint = if (isPlayerActivelyPlaying) DeepEmerald else SoftGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPlayerActivelyPlaying) "إيقاف" else "تشغيل",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // In-App Download Button Row (supports Pause / Resume / Cancel / .elgdrm DRM)
            if (isPausedNow) {
                Button(
                    onClick = onDownloadInAppClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("download_in_app_btn_${lecture.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0D6E52),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "▶️ استمرار (من ${downloadProgressPercent.coerceIn(0, 99)}%)",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onDownloadInAppClick,
                    enabled = !isDownloadedInApp && !isDownloadingNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("download_in_app_btn_${lecture.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDarkTheme) Color(0xFF1D4E3B) else Color(0xFFF3EFE4),
                        contentColor = if (isDarkTheme) Color.White else DeepEmerald,
                        disabledContainerColor = if (isDarkTheme) Color(0xFF163C2D) else SoftMint.copy(alpha = 0.85f),
                        disabledContentColor = if (isDarkTheme) SoftGold else MidEmerald
                    )
                ) {
                    if (isDownloadingNow) {
                        CircularProgressIndicator(
                            color = if (isDarkTheme) SoftGold else DeepEmerald,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "جاري التنزيل (${downloadProgressPercent.coerceIn(0, 99)}%)",
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = if (isDownloadedInApp) Icons.Default.CheckCircle else Icons.Default.DownloadForOffline,
                            contentDescription = null,
                            tint = if (isDownloadedInApp) {
                                if (isDarkTheme) SoftGold else MidEmerald
                            } else {
                                if (isDarkTheme) SoftGold else DeepEmerald
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDownloadedInApp) "تم التنزيل داخل التطبيق ✓" else "تنزيل داخل التطبيق",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isDownloadingNow || isPausedNow) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isPausedNow) {
                                    "متوقف مؤقتاً (${downloadProgressPercent.coerceIn(0, 99)}%)"
                                } else {
                                    "جاري التنزيل والتشفير..."
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${downloadProgressPercent.coerceIn(0, 100)}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (downloadProgressPercent.coerceIn(0, 100)) / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = SoftGold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isDownloadingNow) {
                                OutlinedButton(
                                    onClick = onPauseDownloadClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pause_download_btn_${lecture.id}"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("إيقاف مؤقت", fontWeight = FontWeight.Bold)
                                }
                            } else if (isPausedNow) {
                                Button(
                                    onClick = onDownloadInAppClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("resume_download_btn_${lecture.id}"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D6E52))
                                ) {
                                    Text("استمرار", fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = onCancelDownloadClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cancel_download_btn_${lecture.id}"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إلغاء")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseNotesTabContent(
    activeLecture: Lecture?,
    currentSecond: Int,
    notes: List<TimestampedLectureNote>,
    noteInput: String,
    onNoteInputChange: (String) -> Unit,
    onAddNote: () -> Unit,
    onSeekToNote: (Int) -> Unit,
    onDeleteNote: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.testTag("course_notes_list"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (activeLecture == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "اختر محاضرة لإضافة ملاحظات مرجعية عليها",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp)
                    )
                }
            }
        } else {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "إضافة ملاحظة مرجعية",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = SoftMint,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "عند ${formatDurationHoursMinutesSeconds(currentSecond)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = DeepEmerald,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = onNoteInputChange,
                            placeholder = { Text("اكتب ملاحظتك الطبية عند هذه النقطة...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("course_note_input"),
                            shape = RoundedCornerShape(14.dp),
                            maxLines = 3
                        )

                        Button(
                            onClick = onAddNote,
                            enabled = noteInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("add_course_note_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepEmerald,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = SoftGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "حفظ الملاحظة عند ${formatDurationHoursMinutesSeconds(currentSecond)}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (notes.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = SoftGold,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "لا توجد ملاحظات مسجلة لهذه المحاضرة بعد",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeekToNote(note.second) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                color = DeepEmerald,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.clickable { onSeekToNote(note.second) }
                            ) {
                                Text(
                                    text = formatDurationHoursMinutesSeconds(note.second),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SoftGold,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = note.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(onClick = { onDeleteNote(note.id) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "حذف الملاحظة",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
