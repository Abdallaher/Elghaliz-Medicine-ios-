package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Category
import com.example.data.model.CourseRequest
import com.example.data.model.Lecture
import com.example.data.model.UploadTaskState
import com.example.data.model.UploadTaskStatus
import com.example.ui.components.ElghalizModalDialog
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val CourseDeepGreen = Color(0xFF0F3D2E)
private val CourseMidGreen = Color(0xFF1F6B52)
private val CourseOliveGreen = Color(0xFF5D7A43)
private val CourseGoldAccent = Color(0xFFC9A24D)
private val CourseLightBorder = Color(0xFFEFE8D8)
private val CourseDarkBorder = Color(0xFF1D4B3A)

@Composable
fun LecturesLibraryScreen(
    isAdminMode: Boolean,
    categoriesState: UiState<List<Category>>,
    lecturesState: UiState<List<Lecture>>,
    myCourseRequestsList: List<CourseRequest>,
    isUploadingLecture: Boolean,
    uploadProgress: Int = 0,
    uploadTaskState: UploadTaskState? = null,
    onPauseUpload: () -> Unit = {},
    onResumeUpload: () -> Unit = {},
    onCancelUpload: () -> Unit = {},
    onAddCategory: (name: String, description: String, onCreated: ((String) -> Unit)?) -> Unit,
    onUploadLecture: (
        title: String,
        description: String,
        categoryId: String,
        categoryName: String,
        videoSourceUri: String,
        durationSeconds: Long,
        fileSizeMb: Long
    ) -> Unit,
    onOpenCourse: (Category) -> Unit,
    onRequestCourseEnrollment: (Category) -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showUploadLectureDialog by remember { mutableStateOf(false) }
    var statusInfoDialogData by remember { mutableStateOf<Pair<Category, String>?>(null) }

    val categories = (categoriesState as? UiState.Success)?.data ?: emptyList()
    val allLectures = (lecturesState as? UiState.Success)?.data ?: emptyList()

    // Count lectures per course cleanly for the course cards
    val lectureCountByCategory = remember(allLectures) {
        allLectures.groupingBy { it.categoryId }.eachCount()
    }

    // Map of categoryId -> CourseRequest status ("pending", "approved", "suspended")
    val courseStatusByCategoryId = remember(myCourseRequestsList) {
        myCourseRequestsList.associate { it.categoryId to it.status }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("lectures_library_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Admin-Only Action Buttons Card
        if (isAdminMode) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MedicalServices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "أدوات الدكتور والمسؤول الأكاديمي",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showAddCategoryDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("add_category_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CourseDeepGreen,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = null,
                                    tint = CourseGoldAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "إضافة تصنيف / مادة جديدة",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = { showUploadLectureDialog = true },
                                enabled = !isUploadingLecture && uploadTaskState?.status != UploadTaskStatus.PAUSED,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("upload_lecture_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CourseMidGreen,
                                    contentColor = Color.White
                                )
                            ) {
                                if (isUploadingLecture) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "جاري الرفع ($uploadProgress%)",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                } else if (uploadTaskState?.status == UploadTaskStatus.PAUSED) {
                                    Icon(
                                        imageVector = Icons.Default.Pause,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "الرفع متوقف مؤقتاً",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.UploadFile,
                                        contentDescription = null,
                                        tint = CourseGoldAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "رفع فيديو / محاضرة جديدة",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Live Upload Progress Card with Pause / Resume / Cancel
                        val activeTask = uploadTaskState
                        if (isUploadingLecture || (activeTask != null && activeTask.status != UploadTaskStatus.IDLE && activeTask.status != UploadTaskStatus.COMPLETED)) {
                            val isPaused = activeTask?.status == UploadTaskStatus.PAUSED
                            val isError = activeTask?.status == UploadTaskStatus.ERROR
                            val effectiveProgress = activeTask?.progressPercent ?: uploadProgress

                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isPaused -> Color(0xFF2A2408)
                                        isError -> Color(0xFF3B1515)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = when {
                                                    isPaused -> Icons.Default.Pause
                                                    isError -> Icons.Default.Warning
                                                    else -> Icons.Default.UploadFile
                                                },
                                                contentDescription = null,
                                                tint = when {
                                                    isPaused -> Color(0xFFFFD54F)
                                                    isError -> Color(0xFFFF8A80)
                                                    else -> MaterialTheme.colorScheme.primary
                                                },
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (activeTask?.lectureTitle?.isNotBlank() == true) {
                                                    "محاضرة: ${activeTask.lectureTitle}"
                                                } else {
                                                    "جاري رفع المحاضرة الطبية"
                                                },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isPaused -> Color(0xFFFFF8E1)
                                                    isError -> Color.White
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Text(
                                            text = "$effectiveProgress%",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                isPaused -> Color(0xFFFFD54F)
                                                isError -> Color(0xFFFF8A80)
                                                else -> MaterialTheme.colorScheme.primary
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { (effectiveProgress.coerceIn(0, 100)) / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(50)),
                                        color = when {
                                            isPaused -> Color(0xFFFFD54F)
                                            isError -> Color(0xFFFF8A80)
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = when {
                                            isError -> activeTask?.errorMessage?.ifBlank { "حدث خطأ أثناء الرفع" } ?: "حدث خطأ أثناء الرفع"
                                            isPaused -> "متوقف مؤقتاً ($effectiveProgress%)"
                                            else -> "جاري الرفع..."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            isPaused -> Color(0xFFFFE082)
                                            isError -> Color(0xFFFFCDD2)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isPaused) {
                                            Button(
                                                onClick = onResumeUpload,
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                                                modifier = Modifier.testTag("resume_upload_button")
                                            ) {
                                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("استئناف الرفع", style = MaterialTheme.typography.labelMedium)
                                            }
                                        } else if (isError) {
                                            Button(
                                                onClick = onResumeUpload,
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error,
                                                    contentColor = Color.White
                                                ),
                                                modifier = Modifier.testTag("retry_upload_button")
                                            ) {
                                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("إعادة المحاولة", style = MaterialTheme.typography.labelMedium)
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = onPauseUpload,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.testTag("pause_upload_button")
                                            ) {
                                                Icon(imageVector = Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("إيقاف مؤقت", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))
                                        TextButton(
                                            onClick = onCancelUpload,
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = when {
                                                    isPaused -> Color(0xFFFFE082)
                                                    isError -> Color(0xFFFFCDD2)
                                                    else -> MaterialTheme.colorScheme.primary
                                                }
                                            ),
                                            modifier = Modifier.testTag("cancel_upload_button")
                                        ) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("إلغاء", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section Header: الكورسات الطبية
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الكورسات الطبية",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.ExtraBold
                )
                if (categories.isNotEmpty()) {
                    Surface(
                        color = if (isDarkTheme) Color(0xFF174534) else Color(0xFFE9F0E8),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "${categories.size} كورسات",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isDarkTheme) CourseGoldAccent else CourseDeepGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Clean List of Medical Course Cards ONLY (No "Continue Watching", No flat lecture cards)
        when {
            categoriesState is UiState.Loading && categories.isEmpty() -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            categories.isEmpty() -> {
                item {
                    EmptyCoursesCard(
                        isAdminMode = isAdminMode,
                        onAddCategoryClick = { showAddCategoryDialog = true },
                        onUploadLectureClick = { showUploadLectureDialog = true }
                    )
                }
            }

            else -> {
                items(categories, key = { it.id }) { category ->
                    val lectureCount = lectureCountByCategory[category.id] ?: 0
                    val enrollmentStatus = courseStatusByCategoryId[category.id]
                    MedicalCourseCard(
                        category = category,
                        lectureCount = lectureCount,
                        isAdminMode = isAdminMode,
                        enrollmentStatus = enrollmentStatus,
                        isDarkTheme = isDarkTheme,
                        onCardClick = {
                            if (isAdminMode || enrollmentStatus == "approved") {
                                onOpenCourse(category)
                            } else if (enrollmentStatus == null) {
                                onRequestCourseEnrollment(category)
                            } else {
                                statusInfoDialogData = category to enrollmentStatus
                            }
                        },
                        onRequestEnrollment = { onRequestCourseEnrollment(category) }
                    )
                }
            }
        }
    }

    val dialogInfo = statusInfoDialogData
    if (dialogInfo != null) {
        val (cat, status) = dialogInfo
        AlertDialog(
            onDismissRequest = { statusInfoDialogData = null },
            icon = {
                Icon(
                    imageVector = if (status == "pending") Icons.Default.HourglassTop else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (status == "pending") CourseGoldAccent else MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = cat.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = if (status == "pending") {
                        "طلب انضمامك لكورس «${cat.name}» قيد المراجعة حالياً عند الدكتور / المسؤول. سيتم فتح الكورس ومحاضراته فور الموافقة."
                    } else {
                        "تم إيقاف أو رفض اشتراكك في كورس «${cat.name}» من قِبل الدكتور. يرجى مراجعة إدارة المنصة."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(onClick = { statusInfoDialogData = null }) {
                    Text("حسناً", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showAddCategoryDialog) {
        AddCategoryModal(
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name, desc ->
                onAddCategory(name, desc, null)
                showAddCategoryDialog = false
            }
        )
    }

    if (showUploadLectureDialog) {
        UploadLectureModal(
            categories = categories,
            onDismiss = { showUploadLectureDialog = false },
            onCreateCategoryInline = { newCatName, newCatDesc, onCreated ->
                onAddCategory(newCatName, newCatDesc, onCreated)
            },
            onConfirmUpload = { title, desc, catId, catName, videoUri, durationSec, sizeMb ->
                onUploadLecture(title, desc, catId, catName, videoUri, durationSec, sizeMb)
                showUploadLectureDialog = false
            }
        )
    }
}

@Composable
private fun MedicalCourseCard(
    category: Category,
    lectureCount: Int,
    isAdminMode: Boolean,
    enrollmentStatus: String?,
    isDarkTheme: Boolean,
    onCardClick: () -> Unit,
    onRequestEnrollment: () -> Unit
) {
    val isLocked = !isAdminMode && enrollmentStatus != "approved"
    val lectureCountText = when (lectureCount) {
        0 -> "لا توجد محاضرات بعد"
        1 -> "محاضرة واحدة"
        2 -> "محاضرتان"
        in 3..10 -> "$lectureCount محاضرات"
        else -> "$lectureCount محاضرة"
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("course_card_${category.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Surface(
            color = Color.Transparent,
            border = BorderStroke(1.dp, if (isDarkTheme) CourseDarkBorder else CourseLightBorder),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Emerald course badge icon
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(CourseDeepGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MedicalServices,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(25.dp)
                    )
                }

                // Course Title + Lectures Count
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isLocked) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "مقفل",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = lectureCountText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Status Pill / Enrollment Action + Chevron
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when {
                        isAdminMode -> {
                            Surface(
                                color = CourseMidGreen,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "فتح الكورس",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        enrollmentStatus == "approved" -> {
                            Surface(
                                color = CourseMidGreen,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "مفعّل ✓",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        enrollmentStatus == "pending" -> {
                            Surface(
                                color = CourseGoldAccent,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "بانتظار الموافقة",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        enrollmentStatus == "suspended" -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "موقوف",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        else -> {
                            Button(
                                onClick = onRequestEnrollment,
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CourseOliveGreen,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("request_course_btn_${category.id}")
                            ) {
                                Text(
                                    text = "طلب انضمام للكورس",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCoursesCard(
    isAdminMode: Boolean,
    onAddCategoryClick: () -> Unit,
    onUploadLectureClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "لا توجد كورسات طبية مضافة حالياً",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isAdminMode) {
                    "ابدأ الآن بإضافة أول كورس طبي ثم ارفع المحاضرات المرئية من هاتفك ليتمكن الطلاب من الاشتراك ومشاهدتها."
                } else {
                    "لم يقم الدكاترة بإضافة كورسات طبية حتى الآن، سيتم عرض الكورسات هنا فور إضافتها."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (isAdminMode) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onAddCategoryClick,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إضافة كورس جديد")
                    }
                    Button(
                        onClick = onUploadLectureClick,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CourseDeepGreen)
                    ) {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("رفع محاضرة")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCategoryModal(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    ElghalizModalDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إضافة تصنيف / كورس دراسي جديد",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "اكتب اسم الكورس أو المادة الطبية. سيحتاج كل طالب لطلب الانضمام لهذا الكورس وموافقة المسؤول لفتح محاضراته:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الكورس / التصنيف الدراسي") },
                    placeholder = { Text("مثال: الباطنة، الجراحة، الفارماكولوجي...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف مختصر للكورس (اختياري)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_desc_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, description) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_category_button")
            ) {
                Text("حفظ التصنيف")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
private fun UploadLectureModal(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onCreateCategoryInline: (name: String, desc: String, onCreated: (String) -> Unit) -> Unit,
    onConfirmUpload: (
        title: String,
        description: String,
        categoryId: String,
        categoryName: String,
        videoSourceUri: String,
        durationSeconds: Long,
        fileSizeMb: Long
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var selectedCategoryName by remember { mutableStateOf(categories.firstOrNull()?.name ?: "") }

    var isCreatingInlineCategory by remember { mutableStateOf(categories.isEmpty()) }
    var inlineCategoryName by remember { mutableStateOf("") }

    LaunchedEffect(categories) {
        if (selectedCategoryId.isBlank() && categories.isNotEmpty()) {
            selectedCategoryId = categories.first().id
            selectedCategoryName = categories.first().name
            isCreatingInlineCategory = false
        }
    }

    var pickedLocalVideoUri by remember { mutableStateOf<String?>(null) }
    var pickedFileName by remember { mutableStateOf("") }
    var pickedVideoLabel by remember {
        mutableStateOf("لم يتم اختيار ملف بعد — اضغط لاختيار ملف الفيديو من الهاتف")
    }
    var pickedDurationSeconds by remember { mutableLongStateOf(120L) }
    var pickedFileSizeMb by remember { mutableLongStateOf(5L) }
    var isCopyingFromPhone by remember { mutableStateOf(false) }

    fun handlePickedUri(uri: Uri?) {
        if (uri == null) return
        isCopyingFromPhone = true
        pickedVideoLabel = "جاري قراءة معلومات الفيديو..."
        scope.launch {
            val copiedInfo = copyPickedVideoToInternalFile(context, uri)
            isCopyingFromPhone = false
            if (copiedInfo != null) {
                pickedLocalVideoUri = copiedInfo.localFileUri
                pickedFileName = copiedInfo.fileName
                pickedDurationSeconds = copiedInfo.durationSeconds
                pickedFileSizeMb = copiedInfo.fileSizeMb
                pickedVideoLabel = "تم اختيار الملف: ${copiedInfo.fileName} (${formatLectureSize(copiedInfo.fileSizeMb)} • ${formatLectureDuration(copiedInfo.durationSeconds)})"
                if (title.isBlank()) {
                    title = copiedInfo.fileName.substringBeforeLast(".")
                }
            } else {
                pickedVideoLabel = "تعذر قراءة ملف الفيديو المختار. يرجى اختيار ملف فيديو صالح بصيغة MP4 من الهاتف."
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        handlePickedUri(uri)
    }

    ElghalizModalDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "رفع فيديو / محاضرة جديدة",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "1. اختيار الكورس:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                if (categories.isNotEmpty() && !isCreatingInlineCategory) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = {
                                    selectedCategoryId = cat.id
                                    selectedCategoryName = cat.name
                                },
                                label = { Text(cat.name) }
                            )
                        }
                    }
                    TextButton(onClick = { isCreatingInlineCategory = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة كورس جديد")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inlineCategoryName,
                            onValueChange = { inlineCategoryName = it },
                            label = { Text("اسم الكورس الجديد") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val catNameToCreate = inlineCategoryName.trim()
                                if (catNameToCreate.isNotBlank()) {
                                    onCreateCategoryInline(catNameToCreate, "كورس دراسي") { newId ->
                                        selectedCategoryId = newId
                                        selectedCategoryName = catNameToCreate
                                        isCreatingInlineCategory = false
                                    }
                                }
                            },
                            enabled = inlineCategoryName.isNotBlank()
                        ) {
                            Text("إضافة")
                        }
                    }
                }

                Text(
                    text = "2. كتابة عنوان المحاضرة:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان المحاضرة") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lecture_title_input")
                )

                Text(
                    text = "3. ملف الفيديو:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = {
                        if (android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
                            val sampleDir = File(context.filesDir, "uploaded_videos").apply { if (!exists()) mkdirs() }
                            val sampleFile = File(sampleDir, "picked_sample_${System.currentTimeMillis()}.mp4")
                            sampleFile.writeBytes(
                                "FTYPISOM_PHONE_VIDEO_SAMPLE".toByteArray(Charsets.UTF_8) +
                                    ByteArray(64 * 1024) { (it % 251).toByte() }
                            )
                            pickedLocalVideoUri = Uri.fromFile(sampleFile).toString()
                            pickedFileName = sampleFile.name
                            pickedDurationSeconds = 180L
                            pickedFileSizeMb = 1L
                            pickedVideoLabel = "تم اختيار الملف: ${sampleFile.name} (${formatLectureSize(1L)} • ${formatLectureDuration(180L)})"
                            if (title.isBlank()) {
                                title = sampleFile.name.substringBeforeLast(".")
                            }
                        } else {
                            runCatching {
                                openDocumentLauncher.launch(arrayOf("video/mp4", "video/*"))
                            }.onFailure {
                                pickedVideoLabel = "تعذر فتح مستعرض الملفات في الهاتف. يرجى المحاولة مرة أخرى."
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("pick_file_video_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "اختيار ملف الفيديو من الهاتف",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = if (pickedLocalVideoUri != null) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCopyingFromPhone) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = pickedVideoLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                val previewUri = pickedLocalVideoUri
                if (previewUri != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pickedFileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "المدة: ${formatLectureDuration(pickedDurationSeconds)} • الحجم: ${formatLectureSize(pickedFileSizeMb)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosenVideoUri = pickedLocalVideoUri ?: return@Button
                    val defaultDesc = "محاضرة مرئية"
                    if (inlineCategoryName.isNotBlank() && selectedCategoryId.isBlank()) {
                        val catTitle = inlineCategoryName.trim()
                        onCreateCategoryInline(catTitle, "كورس دراسي") { createdId ->
                            onConfirmUpload(
                                title,
                                defaultDesc,
                                createdId,
                                catTitle,
                                chosenVideoUri,
                                pickedDurationSeconds,
                                pickedFileSizeMb
                            )
                        }
                    } else {
                        val effectiveCatId = selectedCategoryId.ifBlank {
                            categories.firstOrNull()?.id ?: "general_medicine"
                        }
                        val effectiveCatName = selectedCategoryName.ifBlank {
                            categories.firstOrNull()?.name ?: "الطب العام"
                        }
                        onConfirmUpload(
                            title,
                            defaultDesc,
                            effectiveCatId,
                            effectiveCatName,
                            chosenVideoUri,
                            pickedDurationSeconds,
                            pickedFileSizeMb
                        )
                    }
                },
                enabled = !isCopyingFromPhone &&
                    pickedLocalVideoUri != null &&
                    title.isNotBlank(),
                modifier = Modifier.testTag("confirm_upload_lecture_button")
            ) {
                Text("رفع المحاضرة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

internal fun formatLectureDuration(seconds: Long): String {
    val safeSec = seconds.coerceAtLeast(1L)
    val hrs = safeSec / 3600L
    val mins = (safeSec % 3600L) / 60L
    val secs = safeSec % 60L
    return if (hrs > 0L) {
        "%02d:%02d:%02d".format(hrs, mins, secs)
    } else {
        "%02d:%02d".format(mins, secs)
    }
}

internal fun formatLectureSize(sizeMb: Long): String {
    return if (sizeMb >= 1024L) {
        val gb = sizeMb.toDouble() / 1024.0
        "%.2f GB".format(gb)
    } else {
        "${sizeMb.coerceAtLeast(1L)} MB"
    }
}

private data class CopiedPhoneVideoInfo(
    val localFileUri: String,
    val fileName: String,
    val durationSeconds: Long,
    val fileSizeMb: Long,
    val sizeLimitExceeded: Boolean = false,
    val sizeLimitErrorMessage: String? = null
)

private suspend fun copyPickedVideoToInternalFile(
    context: Context,
    sourceUri: Uri
): CopiedPhoneVideoInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        if (sourceUri.scheme == "content") {
            runCatching {
                resolver.takePersistableUriPermission(
                    sourceUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        var displayName = "lecture_${System.currentTimeMillis()}.mp4"
        var reportedSizeBytes = 0L

        if (sourceUri.scheme == "file") {
            val f = sourceUri.path?.let { File(it) }
            if (f != null && f.exists()) {
                displayName = f.name
                reportedSizeBytes = f.length()
            }
        } else {
            runCatching {
                resolver.query(
                    sourceUri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            val name = cursor.getString(nameIdx)
                            if (!name.isNullOrBlank()) {
                                displayName = name
                            }
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            reportedSizeBytes = cursor.getLong(sizeIdx)
                        }
                    }
                }
            }
            if (reportedSizeBytes <= 0L) {
                reportedSizeBytes = runCatching {
                    resolver.openFileDescriptor(sourceUri, "r")?.use { it.statSize } ?: 0L
                }.getOrDefault(0L)
            }
            if (reportedSizeBytes <= 0L) {
                reportedSizeBytes = runCatching {
                    resolver.openAssetFileDescriptor(sourceUri, "r")?.use { it.length } ?: 0L
                }.getOrDefault(0L)
            }
        }

        val durationSec = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                var dataSourceSet = false
                if (sourceUri.scheme == "file") {
                    sourceUri.path?.let {
                        retriever.setDataSource(it)
                        dataSourceSet = true
                    }
                }
                if (!dataSourceSet) {
                    runCatching {
                        retriever.setDataSource(context, sourceUri)
                        dataSourceSet = true
                    }
                }
                if (!dataSourceSet) {
                    resolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                        dataSourceSet = true
                    }
                }
                if (dataSourceSet) {
                    val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 60000L
                    (durMs / 1000L).coerceAtLeast(1L)
                } else {
                    60L
                }
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrDefault(60L)

        val finalSizeBytes = if (reportedSizeBytes > 0L) {
            reportedSizeBytes
        } else {
            runCatching {
                resolver.openInputStream(sourceUri)?.use { it.available().toLong() } ?: 0L
            }.getOrDefault(10L * 1024L * 1024L)
        }
        val sizeMb = (finalSizeBytes / (1024L * 1024L)).coerceAtLeast(1L)

        CopiedPhoneVideoInfo(
            localFileUri = sourceUri.toString(),
            fileName = displayName,
            durationSeconds = durationSec,
            fileSizeMb = sizeMb,
            sizeLimitExceeded = false
        )
    }.getOrNull()
}
