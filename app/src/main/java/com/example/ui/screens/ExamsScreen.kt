package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.Category
import com.example.data.model.CourseRequest
import com.example.data.model.ExamItem
import com.example.ui.components.ElghalizModalDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val EmeraldDeep = Color(0xFF0F3D2E)
private val EmeraldMid = Color(0xFF1F6B52)
private val GoldAccent = Color(0xFFC9A24D)
private val MintSoft = Color(0xFFE9F0E8)
private val CreamCardBorder = Color(0xFFEFE8D8)
private val DangerRed = Color(0xFFB04A44)
private val DangerSoftBg = Color(0xFFFBEDEA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    isAdminMode: Boolean,
    categoriesState: com.example.ui.viewmodel.UiState<List<Category>>,
    examsState: com.example.ui.viewmodel.UiState<List<ExamItem>>,
    myCourseRequestsList: List<CourseRequest> = emptyList(),
    isUploadingExam: Boolean = false,
    examUploadProgress: Int = 0,
    downloadingExamIds: Set<String> = emptySet(),
    doctorDisplayName: String = "",
    onUploadExamPdf: (
        title: String,
        description: String,
        categoryId: String,
        categoryName: String,
        pdfFileName: String,
        pdfSourceUri: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        scheduleLabel: String
    ) -> Unit,
    onDownloadExamPdfToPhone: (ExamItem, (File) -> Unit) -> Unit,
    onDeleteExam: (String) -> Unit
) {
    val categories = (categoriesState as? com.example.ui.viewmodel.UiState.Success)?.data ?: emptyList()
    val allExams = (examsState as? com.example.ui.viewmodel.UiState.Success)?.data ?: emptyList()
    val approvedIds = remember(myCourseRequestsList) {
        myCourseRequestsList.filter { it.status == "approved" }.map { it.categoryId }.toSet()
    }
    ExamsScreen(
        categories = categories,
        allExams = allExams,
        isAdminMode = isAdminMode,
        downloadingExamIds = downloadingExamIds,
        isUploadingExam = isUploadingExam,
        examUploadProgress = examUploadProgress,
        approvedCourseIds = approvedIds,
        studentCourseRequests = myCourseRequestsList,
        onUploadExam = { title, desc, catId, catName, uri, _, startMs, endMs ->
            val fileName = "${title.trim().ifBlank { "exam" }.replace(" ", "_")}.pdf"
            onUploadExamPdf(title, desc, catId, catName, fileName, uri, startMs, endMs, "")
        },
        onCreateCategoryInline = { _, _, _ -> },
        onDownloadExamPdf = { exam ->
            onDownloadExamPdfToPhone(exam) { _ -> }
        },
        onPreviewExamPdf = { exam, onReady ->
            onDownloadExamPdfToPhone(exam, onReady)
        },
        onDeleteExam = onDeleteExam
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    categories: List<Category>,
    allExams: List<ExamItem>,
    isAdminMode: Boolean,
    downloadingExamIds: Set<String> = emptySet(),
    isUploadingExam: Boolean = false,
    examUploadProgress: Int = 0,
    approvedCourseIds: Set<String> = emptySet(),
    studentCourseRequests: List<CourseRequest> = emptyList(),
    listState: LazyListState = rememberLazyListState(),
    onUploadExam: (
        title: String,
        description: String,
        categoryId: String,
        categoryName: String,
        pdfSourceUri: String,
        fileSizeKb: Long,
        startTimeMillis: Long,
        endTimeMillis: Long
    ) -> Unit,
    onCreateCategoryInline: (name: String, description: String, onCreated: (String) -> Unit) -> Unit,
    onDownloadExamPdf: (ExamItem) -> Unit,
    onPreviewExamPdf: (ExamItem, (File) -> Unit) -> Unit = { _, _ -> },
    onExportExamPdfToUri: (File, Uri) -> Unit = { _, _ -> },
    onDeleteExam: (String) -> Unit,
    onRequestCourseEnrollment: (Category) -> Unit = {},
    onRefreshExams: () -> Unit = {}
) {
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var showUploadExamModal by remember { mutableStateOf(false) }
    var showInteractiveMcqModal by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var previewingExamItem by remember { mutableStateOf<ExamItem?>(null) }
    var previewingPdfFile by remember { mutableStateOf<File?>(null) }
    var pendingSaveAsPdfFile by remember { mutableStateOf<File?>(null) }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri: Uri? ->
        val sourceFile = pendingSaveAsPdfFile
        if (targetUri != null && sourceFile != null) {
            onExportExamPdfToUri(sourceFile, targetUri)
        }
        pendingSaveAsPdfFile = null
    }

    LaunchedEffect(categories, selectedCategoryFilter) {
        if (categories.isNotEmpty() &&
            (selectedCategoryFilter == null || categories.none { it.id == selectedCategoryFilter })
        ) {
            selectedCategoryFilter = categories.first().id
        }
    }

    // تحديث عداد الوقت كل ثانية لعرض العد التنازلي وإخفاء المنتهي تلقائياً للطالب
    LaunchedEffect(Unit) {
        if (!android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
            while (true) {
                delay(1_000L)
                nowMillis = System.currentTimeMillis()
            }
        }
    }

    // إخفاء الاختبارات المنتهية تلقائياً للطالب مع إبقائها ظاهرة للدكتور
    val visibleExams = remember(allExams, selectedCategoryFilter, isAdminMode, nowMillis) {
        val byCourse = if (selectedCategoryFilter == null) {
            allExams
        } else {
            allExams.filter { it.categoryId == selectedCategoryFilter }
        }
        if (isAdminMode) {
            byCourse
        } else {
            byCourse.filter { !it.isExpired(nowMillis) }
        }
    }

    val activeCategory = categories.firstOrNull { it.id == selectedCategoryFilter } ?: categories.firstOrNull()
    val isCurrentCourseApprovedForStudent = isAdminMode ||
        (activeCategory != null && approvedCourseIds.contains(activeCategory.id))
    val currentCourseEnrollmentReq = if (!isAdminMode && activeCategory != null) {
        studentCourseRequests.firstOrNull { it.categoryId == activeCategory.id }
    } else {
        null
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("exams_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "exams_header_section") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.exams_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { showInteractiveMcqModal = true },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, GoldAccent),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = GoldAccent.copy(alpha = 0.14f),
                            contentColor = EmeraldDeep
                        ),
                        modifier = Modifier.testTag("open_interactive_mcq_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Quiz,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اختبار MCQ تفاعلي", fontWeight = FontWeight.ExtraBold)
                    }
                }

                if (isAdminMode) {
                    Button(
                        onClick = { showUploadExamModal = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("open_upload_exam_dialog_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldAccent,
                            contentColor = EmeraldDeep
                        )
                    ) {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.upload_exam_btn),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                if (categories.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories, key = { it.id }) { cat ->
                            val isSelected = selectedCategoryFilter == cat.id
                            val isEnrolled = isAdminMode || approvedCourseIds.contains(cat.id)
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategoryFilter = cat.id },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldAccent,
                                    selectedLabelColor = EmeraldDeep,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = CreamCardBorder,
                                    selectedBorderColor = GoldAccent
                                ),
                                label = {
                                    Text(
                                        text = cat.name,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                    )
                                },
                                leadingIcon = if (!isEnrolled) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        if (isAdminMode && isUploadingExam) {
            item(key = "active_exam_upload") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.5.dp, GoldAccent),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "جاري رفع ملف الاختبار PDF...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${examUploadProgress.coerceIn(0, 100)}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = GoldAccent,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (examUploadProgress.coerceIn(0, 100)) / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50)),
                            color = GoldAccent
                        )
                    }
                }
            }
        }

        if (!isCurrentCourseApprovedForStudent && activeCategory != null) {
            item(key = "exam_course_gate_${activeCategory.id}") {
                ExamCourseEnrollmentGateCard(
                    category = activeCategory,
                    existingRequest = currentCourseEnrollmentReq,
                    onRequestEnrollment = { onRequestCourseEnrollment(activeCategory) },
                    onRefreshStatus = onRefreshExams
                )
            }
        } else if (visibleExams.isEmpty()) {
            item(key = "empty_exams_state") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, CreamCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = MintSoft,
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = EmeraldDeep,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.empty_exams_message),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(visibleExams, key = { it.id }) { exam ->
                ExamItemCard(
                    exam = exam,
                    nowMillis = nowMillis,
                    isAdminMode = isAdminMode,
                    isDownloading = downloadingExamIds.contains(exam.id),
                    onPreviewClick = {
                        onPreviewExamPdf(exam) { downloadedFile ->
                            previewingExamItem = exam
                            previewingPdfFile = downloadedFile
                        }
                    },
                    onSaveAsClick = {
                        onPreviewExamPdf(exam) { downloadedFile ->
                            pendingSaveAsPdfFile = downloadedFile
                            val cleanTitle = exam.title.replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "_").trim().ifBlank { "exam" }
                            runCatching {
                                saveDocumentLauncher.launch("${cleanTitle}.pdf")
                            }
                        }
                    },
                    onDownloadClick = { onDownloadExamPdf(exam) },
                    onDeleteClick = { onDeleteExam(exam.id) }
                )
            }
        }
    }

    if (showUploadExamModal) {
        UploadExamModal(
            categories = categories,
            onDismiss = { showUploadExamModal = false },
            onCreateCategoryInline = onCreateCategoryInline,
            onConfirmUpload = { title, desc, catId, catName, pdfUri, sizeKb, startMillis, endMillis ->
                onUploadExam(title, desc, catId, catName, pdfUri, sizeKb, startMillis, endMillis)
                showUploadExamModal = false
            }
        )
    }

    if (showInteractiveMcqModal) {
        InteractiveMcqQuizModal(
            courseName = activeCategory?.name ?: "Pharmacology",
            onDismiss = { showInteractiveMcqModal = false }
        )
    }

    val currentPreviewFile = previewingPdfFile
    val currentPreviewExam = previewingExamItem
    if (currentPreviewFile != null && currentPreviewExam != null) {
        InAppPdfPreviewModal(
            exam = currentPreviewExam,
            pdfFile = currentPreviewFile,
            onDismiss = {
                previewingPdfFile = null
                previewingExamItem = null
            },
            onSaveToFolderClick = {
                pendingSaveAsPdfFile = currentPreviewFile
                val cleanTitle = currentPreviewExam.title.replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "_").trim().ifBlank { "exam" }
                runCatching {
                    saveDocumentLauncher.launch("${cleanTitle}.pdf")
                }
            },
            onQuickSaveToDownloadsClick = {
                onDownloadExamPdf(currentPreviewExam)
            }
        )
    }
}

@Composable
private fun InteractiveMcqQuizModal(
    courseName: String,
    onDismiss: () -> Unit
) {
    data class McqQuestion(
        val id: Int,
        val question: String,
        val options: List<String>,
        val correctIndex: Int
    )

    val questions = remember(courseName) {
        listOf(
            McqQuestion(
                id = 1,
                question = "1. Which receptor is primarily responsible for bronchodilation in smooth muscle?",
                options = listOf("Alpha-1 adrenergic", "Beta-1 adrenergic", "Beta-2 adrenergic", "Muscarinic M3"),
                correctIndex = 2
            ),
            McqQuestion(
                id = 2,
                question = "2. First-pass hepatic metabolism primarily affects drugs administered via which route?",
                options = listOf("Intravenous (IV)", "Sublingual", "Oral (PO)", "Inhalation"),
                correctIndex = 2
            ),
            McqQuestion(
                id = 3,
                question = "3. What is the specific antidote for Acetaminophen (Paracetamol) toxicity?",
                options = listOf("Naloxone", "N-acetylcysteine (NAC)", "Flumazenil", "Atropine"),
                correctIndex = 1
            )
        )
    }

    val selectedAnswers = remember { mutableStateMapOf<Int, Int>() }
    var isSubmitted by remember { mutableStateOf(false) }

    val score = questions.count { q -> selectedAnswers[q.id] == q.correctIndex }

    ElghalizModalDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "اختبار تفاعلي سريع (MCQ) — $courseName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = EmeraldDeep
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isSubmitted) {
                    Surface(
                        color = if (score >= 2) MintSoft else DangerSoftBg,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "نتيجتك الفورية: $score من ${questions.size} (${(score * 100) / questions.size}%)",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (score >= 2) EmeraldDeep else DangerRed,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                questions.forEach { q ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = q.question,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            q.options.forEachIndexed { idx, opt ->
                                val isChosen = selectedAnswers[q.id] == idx
                                val isCorrect = q.correctIndex == idx
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = isChosen,
                                        onClick = {
                                            if (!isSubmitted) {
                                                selectedAnswers[q.id] = idx
                                            }
                                        }
                                    )
                                    Text(
                                        text = opt + if (isSubmitted && isCorrect) " ✓" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            isSubmitted && isCorrect -> EmeraldDeep
                                            isSubmitted && isChosen && !isCorrect -> DangerRed
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = if (isSubmitted && isCorrect) FontWeight.ExtraBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isSubmitted) {
                        isSubmitted = true
                    } else {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldDeep,
                    contentColor = Color.White
                )
            ) {
                Text(if (!isSubmitted) "تصحيح فوري وإظهار النتيجة" else "إغلاق")
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
private fun ExamCourseEnrollmentGateCard(
    category: Category,
    existingRequest: CourseRequest?,
    onRequestEnrollment: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    val isPending = existingRequest?.status?.equals("pending", ignoreCase = true) == true
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exam_course_enrollment_gate_card"),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = GoldAccent.copy(alpha = 0.2f),
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPending) {
                            Icons.Default.HourglassEmpty
                        } else {
                            Icons.Default.Lock
                        },
                        contentDescription = null,
                        tint = EmeraldDeep,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Text(
                text = "اختبارات كورس: ${category.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (isPending) {
                Text(
                    text = "طلب انضمامك لكورس (${category.name}) قيد المراجعة حالياً. ستفتح لك اختبارات هذا الكورس فور موافقة الدكتور.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    onClick = onRefreshStatus,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تحديث حالة القبول")
                }
            } else {
                Text(
                    text = "لدخول وتحميل اختبارات كورس (${category.name})، يجب أولاً الانضمام للكورس وموافقة المسؤول.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRequestEnrollment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("exam_request_course_enrollment_btn"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("طلب الانضمام لكورس ${category.name}")
                }
            }
        }
    }
}

@Composable
private fun ExamItemCard(
    exam: ExamItem,
    nowMillis: Long,
    isAdminMode: Boolean,
    isDownloading: Boolean,
    onPreviewClick: () -> Unit,
    onSaveAsClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isAvailableNow = exam.isCurrentlyOpen(nowMillis)
    val isUpcoming = exam.isUpcoming(nowMillis)
    val isExpired = exam.isExpired(nowMillis)

    val statusText: String
    val statusBgColor: Color
    val statusTextColor: Color

    when {
        isAvailableNow -> {
            statusText = stringResource(R.string.exam_status_open)
            statusBgColor = MintSoft
            statusTextColor = EmeraldDeep
        }
        isUpcoming -> {
            statusText = stringResource(R.string.exam_status_upcoming)
            statusBgColor = GoldAccent.copy(alpha = 0.22f)
            statusTextColor = EmeraldDeep
        }
        else -> {
            statusText = stringResource(R.string.exam_status_expired)
            statusBgColor = DangerSoftBg
            statusTextColor = DangerRed
        }
    }

    // عداد تنازلي للوقت المتبقي
    val countdownText = remember(exam, nowMillis) {
        when {
            isAvailableNow -> {
                val remSec = ((exam.endTimeMillis - nowMillis) / 1000L).coerceAtLeast(0L)
                val hrs = remSec / 3600L
                val mins = (remSec % 3600L) / 60L
                val secs = remSec % 60L
                "⏳ متبقي على الإغلاق: %02d:%02d:%02d".format(hrs, mins, secs)
            }
            isUpcoming -> {
                val remSec = ((exam.startTimeMillis - nowMillis) / 1000L).coerceAtLeast(0L)
                val hrs = remSec / 3600L
                val mins = (remSec % 3600L) / 60L
                val secs = remSec % 60L
                "⏳ يبدأ بعد: %02d:%02d:%02d".format(hrs, mins, secs)
            }
            else -> "انتهى وقت الاختبار"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exam_card_${exam.id}"),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, CreamCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MintSoft,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = EmeraldDeep,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = GoldAccent.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = exam.categoryName,
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldDeep,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }

                        Surface(
                            color = statusBgColor,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusTextColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = exam.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isAdminMode) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.testTag("delete_exam_btn_${exam.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_exam_btn),
                            tint = DangerRed
                        )
                    }
                }
            }

            if (exam.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = exam.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // شريط العد التنازلي
            Surface(
                color = if (isAvailableNow) GoldAccent.copy(alpha = 0.18f) else MintSoft.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = EmeraldDeep,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = countdownText,
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldDeep,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = EmeraldDeep,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${stringResource(R.string.exam_starts_at)}: ${formatDateTime12HrArabic(exam.startTimeMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${stringResource(R.string.exam_ends_at)}: ${formatDateTime12HrArabic(exam.endTimeMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "د. ${exam.doctorName.ifBlank { "Abdallah.surour" }} • حجم الملف: ${exam.fileSizeKb} KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val canAccessPdf = isAvailableNow || isAdminMode

            if (canAccessPdf) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onPreviewClick,
                            enabled = !isDownloading,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("preview_exam_pdf_btn_${exam.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldDeep,
                                contentColor = Color.White
                            )
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "معاينة الاختبار",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onSaveAsClick,
                            enabled = !isDownloading,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("save_as_exam_pdf_btn_${exam.id}"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "حفظ في مجلد...",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Button(
                        onClick = onDownloadClick,
                        enabled = !isDownloading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("download_exam_pdf_btn_${exam.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldAccent,
                            contentColor = EmeraldDeep
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDownloading) {
                                "جاري تجهيز وتنزيل ملف PDF..."
                            } else {
                                stringResource(R.string.download_exam_pdf_btn)
                            },
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            } else {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("download_exam_pdf_btn_${exam.id}"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isUpcoming) {
                            "يُفتح تلقائياً في الموعد المحدد أعلاه"
                        } else {
                            "انتهى وقت الاختبار ولم يعد متاحاً للتحميل"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun InAppPdfPreviewModal(
    exam: ExamItem,
    pdfFile: File,
    onDismiss: () -> Unit,
    onSaveToFolderClick: () -> Unit,
    onQuickSaveToDownloadsClick: () -> Unit
) {
    var renderedPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isRendering by remember { mutableStateOf(true) }
    var renderError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pdfFile.absolutePath) {
        isRendering = true
        renderError = null
        val bitmaps = withContext(Dispatchers.IO) {
            runCatching {
                val pages = mutableListOf<Bitmap>()
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val count = renderer.pageCount.coerceAtMost(25)
                        for (i in 0 until count) {
                            renderer.openPage(i).use { page ->
                                val width = (page.width * 2).coerceIn(600, 1600)
                                val height = (page.height * 2).coerceIn(800, 2200)
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pages.add(bmp)
                            }
                        }
                    }
                }
                pages
            }.getOrElse { emptyList() }
        }
        renderedPages = bitmaps
        if (bitmaps.isEmpty()) {
            renderError = "ملف الاختبار جاهز للتحميل والحفظ في جهازك."
        }
        isRendering = false
    }

    DisposableEffect(Unit) {
        onDispose {
            renderedPages.forEach { bmp ->
                if (!bmp.isRecycled) {
                    runCatching { bmp.recycle() }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .testTag("in_app_pdf_preview_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = EmeraldDeep,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exam.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${exam.categoryName} • معاينة مباشرة داخل التطبيق",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_pdf_preview_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق المعاينة",
                                tint = Color.White
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isRendering -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator()
                                Text("جاري فتح صفحات ملف الـ PDF...")
                            }
                        }

                        renderedPages.isNotEmpty() -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(renderedPages) { index, bmp ->
                                    ElevatedCard(
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "صفحة ${index + 1}",
                                                modifier = Modifier.fillMaxWidth(),
                                                contentScale = ContentScale.FillWidth
                                            )
                                            Text(
                                                text = "صفحة ${index + 1} من ${renderedPages.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            Text(
                                text = renderError ?: "ملف الاختبار جاهز للحفظ.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSaveToFolderClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("modal_save_as_pdf_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep)
                        ) {
                            Icon(imageVector = Icons.Default.SaveAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اختيار مكان الحفظ", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onQuickSaveToDownloadsClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حفظ في التنزيلات", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadExamModal(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onCreateCategoryInline: (name: String, desc: String, onCreated: (String) -> Unit) -> Unit,
    onConfirmUpload: (
        title: String,
        description: String,
        categoryId: String,
        categoryName: String,
        pdfSourceUri: String,
        fileSizeKb: Long,
        startTimeMillis: Long,
        endTimeMillis: Long
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var selectedCategoryName by remember { mutableStateOf(categories.firstOrNull()?.name ?: "") }
    var isCreatingInlineCategory by remember { mutableStateOf(categories.isEmpty()) }
    var inlineCategoryName by remember { mutableStateOf("") }

    var startTimeMillis by remember {
        mutableLongStateOf(System.currentTimeMillis() - 60_000L)
    }
    var endTimeMillis by remember {
        mutableLongStateOf(System.currentTimeMillis() + 2L * 3600_000L)
    }
    var selectedDurationMinutes by remember { mutableIntStateOf(120) }

    var pickedLocalPdfUri by remember { mutableStateOf<String?>(null) }
    var pickedPdfName by remember {
        mutableStateOf("لم يتم اختيار ملف PDF بعد — اختر ملف الاختبار من الهاتف أو أنشئ نموذجاً تجريبياً")
    }
    var pickedSizeKb by remember { mutableLongStateOf(240L) }
    var isCopyingPdf by remember { mutableStateOf(false) }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isCopyingPdf = true
            scope.launch {
                val info = copyPickedPdfToInternalFile(context, uri)
                isCopyingPdf = false
                if (info != null) {
                    pickedLocalPdfUri = info.first
                    pickedPdfName = "تم اختيار: ${info.second} (${info.third} KB)"
                    pickedSizeKb = info.third
                    if (title.isBlank()) {
                        title = info.second.substringBeforeLast(".")
                    }
                } else {
                    pickedPdfName = "تعذر قراءة ملف PDF المختار."
                }
            }
        }
    }

    fun pickDateTime12Hr(initialMillis: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val chosenCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onPicked(chosenCal.timeInMillis)
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    false
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    ElghalizModalDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "رفع اختبار PDF جديد وتحديد موعده",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "اختر الكورس الدراسي:",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldDeep,
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
                } else {
                    OutlinedTextField(
                        value = inlineCategoryName,
                        onValueChange = { inlineCategoryName = it },
                        label = { Text("اسم الكورس الجديد") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الاختبار (مثال: اختبار الفارماكولوجي النصفي)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("تعليمات الاختبار للطلاب (اختياري)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_desc_input")
                )

                Text(
                    text = "تحديد موعد فتح وإغلاق الاختبار (نظام 12 ساعة ص / م):",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldDeep,
                    fontWeight = FontWeight.Bold
                )

                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "بداية فتح الاختبار:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatDateTime12HrArabic(startTimeMillis),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldDeep
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    pickDateTime12Hr(startTimeMillis) { picked ->
                                        startTimeMillis = picked
                                        endTimeMillis = picked + selectedDurationMinutes * 60_000L
                                    }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تحديد البداية")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "نهاية وإغلاق الاختبار:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatDateTime12HrArabic(endTimeMillis),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DangerRed
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    pickDateTime12Hr(endTimeMillis) { picked ->
                                        if (picked > startTimeMillis) {
                                            endTimeMillis = picked
                                        } else {
                                            endTimeMillis = startTimeMillis + 30 * 60_000L
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تحديد النهاية")
                            }
                        }
                    }
                }

                Text(
                    text = "أو اختر مدة سريعة من وقت البداية:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val durationOptions = listOf(
                    30 to "30 دقيقة",
                    60 to "ساعة واحدة",
                    120 to "ساعتان",
                    1440 to "24 ساعة"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(durationOptions, key = { it.first }) { (mins, label) ->
                        FilterChip(
                            selected = selectedDurationMinutes == mins,
                            onClick = {
                                selectedDurationMinutes = mins
                                endTimeMillis = startTimeMillis + mins * 60_000L
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            runCatching {
                                openPdfLauncher.launch(arrayOf("application/pdf"))
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_exam_pdf_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep)
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اختيار ملف PDF")
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val sampleDir = File(context.filesDir, "uploaded_exams").apply { if (!exists()) mkdirs() }
                                val sampleFile = File(sampleDir, "elghaliz_exam_${System.currentTimeMillis()}.pdf")
                                val minimalPdf = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/MediaBox[0 0 595 842]/Parent 2 0 R>>endobj\nxref\n0 4\n0000000000 65535 f \n0000000009 00000 n \n0000000052 00000 n \n0000000101 00000 n \ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n160\n%%EOF\n"
                                sampleFile.writeBytes(minimalPdf.toByteArray(Charsets.US_ASCII))
                                pickedLocalPdfUri = Uri.fromFile(sampleFile).toString()
                                pickedSizeKb = (sampleFile.length() / 1024L).coerceAtLeast(12L)
                                pickedPdfName = "تم تجهيز ملف PDF: ${sampleFile.name} ($pickedSizeKb KB)"
                                if (title.isBlank()) {
                                    title = "اختبار طبي تقييمي"
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("generate_sample_exam_pdf_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("توليد نموذج PDF")
                    }
                }

                Surface(
                    color = if (pickedLocalPdfUri != null) {
                        MintSoft
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
                        if (isCopyingPdf) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else if (pickedLocalPdfUri != null) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldDeep,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = pickedPdfName,
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldDeep
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosenPdfUri = pickedLocalPdfUri ?: return@Button
                    val safeEnd = if (endTimeMillis > startTimeMillis) {
                        endTimeMillis
                    } else {
                        startTimeMillis + selectedDurationMinutes * 60_000L
                    }
                    if (inlineCategoryName.isNotBlank() && selectedCategoryId.isBlank()) {
                        val catTitle = inlineCategoryName.trim()
                        onCreateCategoryInline(catTitle, "كورس دراسي") { newId ->
                            onConfirmUpload(
                                title,
                                description,
                                newId,
                                catTitle,
                                chosenPdfUri,
                                pickedSizeKb,
                                startTimeMillis,
                                safeEnd
                            )
                        }
                    } else {
                        val effectiveCatId = selectedCategoryId.ifBlank {
                            categories.firstOrNull()?.id ?: "cat_pharma"
                        }
                        val effectiveCatName = selectedCategoryName.ifBlank {
                            categories.firstOrNull()?.name ?: "Pharmacology"
                        }
                        onConfirmUpload(
                            title,
                            description,
                            effectiveCatId,
                            effectiveCatName,
                            chosenPdfUri,
                            pickedSizeKb,
                            startTimeMillis,
                            safeEnd
                        )
                    }
                },
                enabled = !isCopyingPdf && pickedLocalPdfUri != null && title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    contentColor = EmeraldDeep
                ),
                modifier = Modifier.testTag("confirm_upload_exam_button")
            ) {
                Text("حفظ ورفع الاختبار", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

private fun formatDateTime12HrArabic(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale("ar"))
    return sdf.format(Date(millis))
}

private suspend fun copyPickedPdfToInternalFile(
    context: Context,
    sourceUri: Uri
): Triple<String, String, Long>? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        var displayName = "exam_${System.currentTimeMillis()}.pdf"
        runCatching {
            resolver.query(
                sourceUri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) displayName = name
                    }
                }
            }
        }
        val dir = File(context.filesDir, "uploaded_exams").apply { if (!exists()) mkdirs() }
        val targetFile = File(dir, "exam_${System.currentTimeMillis()}.pdf")
        resolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output, 16 * 1024)
            }
        }
        if (!targetFile.exists() || targetFile.length() == 0L) return@runCatching null
        val sizeKb = (targetFile.length() / 1024L).coerceAtLeast(1L)
        Triple(Uri.fromFile(targetFile).toString(), displayName, sizeKb)
    }.getOrNull()
}

private suspend fun generateSampleMedicalExamPdf(
    context: Context,
    examTitle: String
): File? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.filesDir, "uploaded_exams").apply { if (!exists()) mkdirs() }
        val file = File(dir, "elghaliz_exam_${System.currentTimeMillis()}.pdf")
        
        val writtenNative = runCatching {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val paint = Paint().apply {
                textSize = 18f
                isFakeBoldText = true
            }
            canvas.drawText("Elghaliz Medicine - Official Exam", 60f, 80f, paint)
            paint.textSize = 14f
            paint.isFakeBoldText = false
            canvas.drawText("Exam Title: $examTitle", 60f, 120f, paint)
            canvas.drawText("Q1: Explain the mechanism of action of Beta-Blockers in detail.", 60f, 170f, paint)
            canvas.drawText("Q2: Compare Pharmacokinetics and Pharmacodynamics with clinical examples.", 60f, 220f, paint)
            canvas.drawText("Q3: Describe the adverse effects and contraindications of ACE Inhibitors.", 60f, 270f, paint)
            pdfDocument.finishPage(page)
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            true
        }.getOrDefault(false)

        if (!writtenNative || !file.exists() || file.length() == 0L) {
            val minimalPdf = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/MediaBox[0 0 595 842]/Parent 2 0 R>>endobj\nxref\n0 4\n0000000000 65535 f \n0000000009 00000 n \n0000000052 00000 n \n0000000101 00000 n \ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n160\n%%EOF\n"
            file.writeBytes(minimalPdf.toByteArray(Charsets.US_ASCII))
        }
        file
    }.getOrNull()
}
