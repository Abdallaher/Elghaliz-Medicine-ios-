package com.example.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminEntry
import com.example.data.model.AppUpdateInfo
import com.example.data.model.Category
import com.example.data.model.CourseRequest
import com.example.data.model.DownloadTaskState
import com.example.data.model.DownloadTaskStatus
import com.example.data.model.Lecture
import com.example.data.model.OfflineDownload
import com.example.data.model.StudentRequest
import com.example.ui.viewmodel.UiState
import java.io.File

@Composable
fun DownloadsAndAdminScreen(
    isAdminMode: Boolean,
    isPrimaryAdmin: Boolean,
    downloadsState: UiState<List<OfflineDownload>>,
    lecturesState: UiState<List<Lecture>> = UiState.Success(emptyList()),
    downloadStatesMap: Map<String, DownloadTaskState> = emptyMap(),
    myCourseRequestsList: List<CourseRequest> = emptyList(),
    adminsList: List<AdminEntry>,
    studentRequestsList: List<StudentRequest>,
    courseRequestsList: List<CourseRequest>,
    categoriesState: UiState<List<Category>>,
    onPlayOfflineDownload: (OfflineDownload) -> Unit,
    onRemoveOfflineDownload: (String) -> Unit,
    onPauseDownload: (Lecture) -> Unit = {},
    onResumeDownload: (Lecture) -> Unit = {},
    onCancelDownload: (String, String) -> Unit = { _, _ -> },
    onApproveStudent: (StudentRequest) -> Unit,
    onSuspendStudent: (StudentRequest) -> Unit,
    onApproveCourseRequest: (CourseRequest) -> Unit,
    onRejectOrSuspendCourseRequest: (CourseRequest) -> Unit,
    onSetDirectStudentCourseAccess: (StudentRequest, Category, Boolean) -> Unit,
    onAddAdminEmail: (email: String, roleTitle: String) -> Unit,
    onRemoveAdminEntry: (AdminEntry) -> Unit,
    onDeleteCategory: (String) -> Unit,
    appUpdateInfo: AppUpdateInfo? = null,
    isPublishingAppUpdate: Boolean = false,
    appUpdatePublishProgress: Int = 0,
    onPublishAppUpdateApk: (
        apkSourceUri: String,
        latestVersionCode: Long,
        latestVersionName: String,
        releaseNotes: String,
        forceUpdate: Boolean
    ) -> Unit = { _, _, _, _, _ -> },
    usedStorageMb: Long = 0L,
    listState: LazyListState = rememberLazyListState(),
    onApproveAllPendingStudents: (List<StudentRequest>) -> Unit = {},
    onRejectAllPendingStudents: (List<StudentRequest>) -> Unit = {}
) {
    val context = LocalContext.current
    var newAdminEmail by remember { mutableStateOf("") }
    var newAdminRoleTitle by remember { mutableStateOf("") }
    var studentSearchQuery by remember { mutableStateOf("") }
    var selectedCourseFilterInAdmin by remember { mutableStateOf<String?>(null) }
    var selectedAdminSubTab by remember { mutableStateOf(0) }
    var studentLogFilter by remember { mutableStateOf("all") } // "all", "approved", "suspended"
    var lastActionResultMessage by remember { mutableStateOf<String?>(null) }

    // In-App APK Auto-Updater Publisher state (Admin / Primary Admin)
    var selectedApkUriString by remember { mutableStateOf("") }
    var selectedApkFileName by remember { mutableStateOf("") }
    var updateVersionCodeInput by remember(appUpdateInfo?.latestVersionCode) {
        mutableStateOf(((appUpdateInfo?.latestVersionCode ?: 1L) + 1L).toString())
    }
    var updateVersionNameInput by remember(appUpdateInfo?.latestVersionName) {
        mutableStateOf(
            if (appUpdateInfo != null && appUpdateInfo.latestVersionName.isNotBlank()) {
                "1.${appUpdateInfo.latestVersionCode + 1L}"
            } else {
                "1.1"
            }
        )
    }
    var updateReleaseNotesInput by remember {
        mutableStateOf("تحسين سرعة مشغل المحاضرات وإضافة مزايا جديدة مع الحفاظ على التنزيلات المشفرة")
    }
    var updateForceToggle by remember { mutableStateOf(false) }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedApkUriString = uri.toString()
            var resolvedName = uri.lastPathSegment?.substringAfterLast('/') ?: "ElghalizUpdate.apk"
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        resolvedName = cursor.getString(nameIdx) ?: resolvedName
                    }
                }
            }
            selectedApkFileName = resolvedName
        }
    }

    // Map of courseFolderName -> isExpanded (defaults to true so folders are easy to browse)
    val expandedFolderMap = remember { mutableStateMapOf<String, Boolean>() }

    val downloads = (downloadsState as? UiState.Success)?.data ?: emptyList()
    val allLectures = (lecturesState as? UiState.Success)?.data ?: emptyList()
    val lectureById = remember(allLectures) { allLectures.associateBy { it.id } }
    val categories = (categoriesState as? UiState.Success)?.data ?: emptyList()

    val pendingAppCount = remember(studentRequestsList) {
        studentRequestsList.count { it.status == "pending" }
    }
    val pendingCourseCount = remember(courseRequestsList) {
        courseRequestsList.count { it.status == "pending" }
    }

    // Pending student requests (status == "pending")
    val pendingStudentRequests = remember(studentRequestsList, studentSearchQuery) {
        val q = studentSearchQuery.trim().lowercase()
        studentRequestsList.filter { req ->
            req.status == "pending" && (
                q.isBlank() ||
                req.email.lowercase().contains(q) ||
                req.displayName.lowercase().contains(q)
            )
        }
    }

    // Processed students for the log (status == "approved" or "suspended")
    val processedStudents = remember(studentRequestsList, studentSearchQuery, studentLogFilter) {
        val q = studentSearchQuery.trim().lowercase()
        studentRequestsList.filter { req ->
            req.status != "pending" &&
            (studentLogFilter == "all" || req.status == studentLogFilter) &&
            (
                q.isBlank() ||
                req.email.lowercase().contains(q) ||
                req.displayName.lowercase().contains(q)
            )
        }
    }

    // Pending course join requests (status == "pending")
    val pendingCourseRequests = remember(courseRequestsList, studentSearchQuery, selectedCourseFilterInAdmin) {
        val q = studentSearchQuery.trim().lowercase()
        courseRequestsList.filter { req ->
            req.status == "pending" && (
                (q.isBlank() ||
                 req.studentEmail.lowercase().contains(q) ||
                 req.studentName.lowercase().contains(q) ||
                 req.categoryName.lowercase().contains(q)) &&
                (selectedCourseFilterInAdmin == null || req.categoryName == selectedCourseFilterInAdmin)
            )
        }
    }

    val pendingCourseRequestsGroupedByCourse = remember(pendingCourseRequests) {
        pendingCourseRequests.groupBy { it.categoryName.ifBlank { "كورس عام" } }
    }

    // Active / Paused Downloads in progress
    val activeOrPausedTasks = remember(downloadStatesMap) {
        downloadStatesMap.values.filter {
            it.status == DownloadTaskStatus.DOWNLOADING || it.status == DownloadTaskStatus.PAUSED
        }
    }

    // Group student downloads into Course Folders (including empty folders for enrolled courses)
    val enrolledCourseFolderNames = remember(categories, myCourseRequestsList, downloads, isAdminMode) {
        val approvedIds = myCourseRequestsList.filter { it.status == "approved" }.map { it.categoryId }.toSet()
        val fromEnrolled = if (isAdminMode) {
            categories.map { it.name }
        } else {
            categories.filter { approvedIds.contains(it.id) }.map { it.name }
        }
        val fromDownloads = downloads.map { it.categoryName.ifBlank { "محاضرات عامة" } }
        (fromDownloads + fromEnrolled).filter { it.isNotBlank() }.distinct()
    }

    val downloadsByCourseFolder = remember(downloads) {
        downloads.groupBy { it.categoryName.ifBlank { "محاضرات عامة" } }
    }

    // Lookup map of "${studentUid}_${categoryId}" -> CourseRequest status
    val courseStatusLookup = remember(courseRequestsList) {
        courseRequestsList.associate { "${it.userId}_${it.categoryId}" to it.status }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("downloads_and_admin_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =========================================================================
        // ADMIN MODE: 3-Tab Bar (الطلبات الجديدة / سجل الطلاب / إدارة المدراء)
        // =========================================================================
        if (isAdminMode) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    TabRow(
                        selectedTabIndex = selectedAdminSubTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        Tab(
                            selected = selectedAdminSubTab == 0,
                            onClick = { selectedAdminSubTab = 0 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("الطلبات الجديدة", fontWeight = FontWeight.Bold)
                                    val totalPending = pendingAppCount + pendingCourseCount
                                    if (totalPending > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFFD63B4B),
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                text = "$totalPending",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("admin_tab_pending_requests")
                        )
                        Tab(
                            selected = selectedAdminSubTab == 1,
                            onClick = { selectedAdminSubTab = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("سجل الطلاب", fontWeight = FontWeight.Bold)
                                    val processedCount = studentRequestsList.count { it.status != "pending" }
                                    if (processedCount > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                text = "$processedCount",
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("admin_tab_students_log")
                        )
                        Tab(
                            selected = selectedAdminSubTab == 2,
                            onClick = { selectedAdminSubTab = 2 },
                            text = {
                                Text("إدارة المدراء", fontWeight = FontWeight.Bold)
                            },
                            modifier = Modifier.testTag("admin_tab_manage_admins")
                        )
                    }
                }
            }

            // -------------------------------------------------------------------------
            // TAB 0: الطلبات الجديدة (Pending Requests Only)
            // -------------------------------------------------------------------------
            if (selectedAdminSubTab == 0) {
                // Search Input for Pending Requests
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "بحث فوري في الطلبات الجديدة المعلقة",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedTextField(
                                value = studentSearchQuery,
                                onValueChange = { studentSearchQuery = it },
                                label = { Text("ابحث بالبريد الإلكتروني أو اسم الطالب...") },
                                placeholder = { Text("student@gmail.com أو أحمد...") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (studentSearchQuery.isNotBlank()) {
                                        IconButton(onClick = { studentSearchQuery = "" }) {
                                            Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح البحث")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_student_requests_search_input")
                            )
                        }
                    }
                }

                // Recent Action Feedback Banner (if user just acted on a student)
                if (lastActionResultMessage != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = lastActionResultMessage ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { lastActionResultMessage = null }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "إغلاق",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Pending Student App Join Requests Panel
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pending_student_requests_panel"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.HowToReg,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "طلبات انضمام الطلاب للتطبيق (${pendingStudentRequests.size})",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "الطلبات المعلقة فقط — تختفي تلقائياً بمجرد القبول أو الرفض وتنتقل لسجل الطلاب.",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (pendingAppCount > 0) {
                                    Surface(
                                        color = Color(0xFFD63B4B),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = "$pendingAppCount بالانتظار",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (pendingStudentRequests.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onApproveAllPendingStudents(pendingStudentRequests) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("approve_all_pending_students_btn"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0F3D2E),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "قبول الكل (${pendingStudentRequests.size})",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onRejectAllPendingStudents(pendingStudentRequests) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("reject_all_pending_students_btn"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFB04A44)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Block,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "رفض الكل",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (pendingStudentRequests.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (studentSearchQuery.isNotBlank()) {
                                            "لا توجد طلبات معلقة مطابقة للبحث \"$studentSearchQuery\"."
                                        } else {
                                            "لا توجد طلبات انضمام طلاب معلقة حالياً (جميع الطلبات تمت مراجعتها ✓)."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pendingStudentRequests.forEach { req ->
                                        val studentUid = req.userId.ifBlank { req.id }
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("student_request_item_$studentUid")
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = req.displayName.ifBlank { "طالب طب" },
                                                            style = MaterialTheme.typography.titleMedium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = req.email,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    Surface(
                                                        color = Color(0xFFD97706).copy(alpha = 0.14f),
                                                        shape = RoundedCornerShape(50)
                                                    ) {
                                                        Text(
                                                            text = "قيد المراجعة (Pending)",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFFD97706),
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(10.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            onApproveStudent(req)
                                                            lastActionResultMessage = "تم قبول وتفعيل الطالب (${req.displayName.ifBlank { req.email }}) بنجاح: مفعّل بالتطبيق (Approved) — تم نقله إلى سجل الطلاب."
                                                        },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(44.dp)
                                                            .testTag("approve_student_button_$studentUid"),
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.primary
                                                        )
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "قبول وتفعيل (Approve)",
                                                            style = MaterialTheme.typography.labelMedium
                                                        )
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            onSuspendStudent(req)
                                                            lastActionResultMessage = "تم رفض/إيقاف الطالب (${req.displayName.ifBlank { req.email }}): موقوف / مرفوض (Suspended) — تم نقله إلى سجل الطلاب."
                                                        },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(44.dp)
                                                            .testTag("reject_suspend_student_button_$studentUid"),
                                                        shape = RoundedCornerShape(12.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Block,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "رفض / إيقاف (Suspend)",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Pending Course Enrollment Requests Panel
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("course_enrollment_requests_panel"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "طلبات الانضمام للكورسات (${pendingCourseRequests.size})",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "طلبات الطلاب المعلقة للاشتراك في كورسات محددة.",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (pendingCourseCount > 0) {
                                    Surface(
                                        color = Color(0xFFD63B4B),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = "$pendingCourseCount طلب كورس",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Course Filter Chips for Admin Course Requests
                            if (categories.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        FilterChip(
                                            selected = selectedCourseFilterInAdmin == null,
                                            onClick = { selectedCourseFilterInAdmin = null },
                                            label = { Text("كل الكورسات (${pendingCourseRequests.size})") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                    items(categories, key = { it.id }) { cat ->
                                        val countForCat = courseRequestsList.count { it.status == "pending" && it.categoryName == cat.name }
                                        FilterChip(
                                            selected = selectedCourseFilterInAdmin == cat.name,
                                            onClick = {
                                                selectedCourseFilterInAdmin =
                                                    if (selectedCourseFilterInAdmin == cat.name) null else cat.name
                                            },
                                            label = { Text("${cat.name} ($countForCat)") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (pendingCourseRequestsGroupedByCourse.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (studentSearchQuery.isNotBlank()) {
                                            "لا توجد طلبات كورس معلقة مطابقة للبحث \"$studentSearchQuery\"."
                                        } else {
                                            "لا توجد طلبات انضمام لكورسات معلقة حالياً ✓."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    pendingCourseRequestsGroupedByCourse.forEach { (courseName, requestsInCourse) ->
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.FolderSpecial,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "كورس: $courseName",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = RoundedCornerShape(50)
                                                    ) {
                                                        Text(
                                                            text = "${requestsInCourse.size} طلب معلق",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(10.dp))

                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    requestsInCourse.forEach { req ->
                                                        Surface(
                                                            shape = RoundedCornerShape(12.dp),
                                                            color = MaterialTheme.colorScheme.surface,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Column(modifier = Modifier.padding(12.dp)) {
                                                                Text(
                                                                    text = req.studentName.ifBlank { "طالب طب" },
                                                                    style = MaterialTheme.typography.titleSmall,
                                                                    color = MaterialTheme.colorScheme.onSurface,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                                Text(
                                                                    text = req.studentEmail,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )

                                                                Spacer(modifier = Modifier.height(8.dp))

                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                ) {
                                                                    Button(
                                                                        onClick = { onApproveCourseRequest(req) },
                                                                        modifier = Modifier
                                                                            .weight(1f)
                                                                            .height(38.dp)
                                                                            .testTag("approve_course_req_${req.id}"),
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        colors = ButtonDefaults.buttonColors(
                                                                            containerColor = MaterialTheme.colorScheme.primary
                                                                        )
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.CheckCircle,
                                                                            contentDescription = null,
                                                                            modifier = Modifier.size(14.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(4.dp))
                                                                        Text(
                                                                            text = "قبول الكورس",
                                                                            style = MaterialTheme.typography.labelMedium
                                                                        )
                                                                    }

                                                                    OutlinedButton(
                                                                        onClick = { onRejectOrSuspendCourseRequest(req) },
                                                                        modifier = Modifier
                                                                            .weight(1f)
                                                                            .height(38.dp)
                                                                            .testTag("reject_course_req_${req.id}"),
                                                                        shape = RoundedCornerShape(10.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Block,
                                                                            contentDescription = null,
                                                                            tint = MaterialTheme.colorScheme.error,
                                                                            modifier = Modifier.size(14.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(4.dp))
                                                                        Text(
                                                                            text = "رفض الكورس",
                                                                            style = MaterialTheme.typography.labelMedium,
                                                                            color = MaterialTheme.colorScheme.error
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------------------
            // TAB 1: سجل الطلاب (المسجلين والمرفوضين سابقاً مع البحث وتعديل الصلاحيات)
            // -------------------------------------------------------------------------
            if (selectedAdminSubTab == 1) {
                // Search Input for Students Log
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "بحث في سجل الطلاب (المعتمدين والموقوفين)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedTextField(
                                value = studentSearchQuery,
                                onValueChange = { studentSearchQuery = it },
                                label = { Text("ابحث بالبريد الإلكتروني أو الاسم...") },
                                placeholder = { Text("student@gmail.com أو أحمد...") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (studentSearchQuery.isNotBlank()) {
                                        IconButton(onClick = { studentSearchQuery = "" }) {
                                            Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح البحث")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_student_requests_search_input")
                            )

                            // Filter Chips: الكل / المعتمدون / الموقوفون
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val totalProcessed = studentRequestsList.count { it.status != "pending" }
                                val approvedCount = studentRequestsList.count { it.status == "approved" }
                                val suspendedCount = studentRequestsList.count { it.status == "suspended" }

                                FilterChip(
                                    selected = studentLogFilter == "all",
                                    onClick = { studentLogFilter = "all" },
                                    label = { Text("الكل ($totalProcessed)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = studentLogFilter == "approved",
                                    onClick = { studentLogFilter = "approved" },
                                    label = { Text("المعتمدون ($approvedCount)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = studentLogFilter == "suspended",
                                    onClick = { studentLogFilter = "suspended" },
                                    label = { Text("الموقوفون ($suspendedCount)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                // Processed Students List Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("processed_students_panel"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "سجل الطلاب (${processedStudents.size}):",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "يمكنك تعديل حالة أي طالب (إيقاف أو تفعيل) وتعديل الكورسات المصرح له بدخولها فورياً.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            if (processedStudents.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (studentSearchQuery.isNotBlank()) {
                                            "لا يوجد طلاب مطابقون للبحث \"$studentSearchQuery\" في السجل."
                                        } else {
                                            "سجل الطلاب فارغ حالياً — سيظهر هنا الطلاب الذين تم قبولهم أو إيقافهم."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    processedStudents.forEach { req ->
                                        val studentUid = req.userId.ifBlank { req.id }
                                        val isApproved = req.status == "approved"
                                        val statusLabel = if (isApproved) {
                                            "مفعّل بالتطبيق (Approved)"
                                        } else {
                                            "موقوف / مرفوض (Suspended)"
                                        }
                                        val statusColor = if (isApproved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("student_request_item_$studentUid")
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = req.displayName.ifBlank { "طالب طب" },
                                                            style = MaterialTheme.typography.titleMedium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = req.email,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    Surface(
                                                        color = statusColor.copy(alpha = 0.14f),
                                                        shape = RoundedCornerShape(50)
                                                    ) {
                                                        Text(
                                                            text = statusLabel,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = statusColor,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(10.dp))

                                                // Action Buttons to Toggle Status
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = { onApproveStudent(req) },
                                                        enabled = !isApproved,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(42.dp)
                                                            .testTag("approve_student_button_$studentUid"),
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.primary
                                                        )
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "قبول وتفعيل (Approve)",
                                                            style = MaterialTheme.typography.labelMedium
                                                        )
                                                    }

                                                    OutlinedButton(
                                                        onClick = { onSuspendStudent(req) },
                                                        enabled = isApproved,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(42.dp)
                                                            .testTag("reject_suspend_student_button_$studentUid"),
                                                        shape = RoundedCornerShape(12.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Block,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "رفض / إيقاف (Suspend)",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }

                                                // Enrolled Courses Chips (For Approved Students)
                                                if (categories.isNotEmpty() && isApproved) {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Text(
                                                        text = "الكورسات المسموح لهذا الطالب بدخولها (اضغط لتفعيل أو إلغاء أي كورس):",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        items(categories, key = { it.id }) { cat ->
                                                            val isEnrolled = courseStatusLookup["${studentUid}_${cat.id}"] == "approved"
                                                            FilterChip(
                                                                selected = isEnrolled,
                                                                onClick = {
                                                                    onSetDirectStudentCourseAccess(req, cat, !isEnrolled)
                                                                },
                                                                label = {
                                                                    Text(if (isEnrolled) "${cat.name} (مشترك ✓)" else "${cat.name} (غير مشترك)")
                                                                },
                                                                colors = FilterChipDefaults.filterChipColors(
                                                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                                    selectedLabelColor = Color.White
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------------------
            // TAB 2: إدارة المدراء والتصنيفات (Admins & Categories Management)
            // -------------------------------------------------------------------------
            if (selectedAdminSubTab == 2) {
                // Section 2A: Admins Management Panel
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admins_management_panel"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "لوحة إدارة المدراء (Admins Management)",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isPrimaryAdmin) {
                                            "أنت المدير الرئيسي: يمكنك إضافة إيميلات دكاترة ومشرفين آخرين أو حذفهم"
                                        } else {
                                            "حساب مسؤول معتمد: قائمة الأطباء والمشرفين المعتمدين في المنصة"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = newAdminEmail,
                                onValueChange = { newAdminEmail = it },
                                label = { Text("البريد الإلكتروني للدكتور / المشرف الجديد") },
                                placeholder = { Text("doctor@gmail.com") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Email, contentDescription = null)
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("new_admin_email_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = newAdminRoleTitle,
                                onValueChange = { newAdminRoleTitle = it },
                                label = { Text("التخصص أو المسمى الأكاديمي (اختياري)") },
                                placeholder = { Text("أستاذ الجراحة العامة") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    onAddAdminEmail(newAdminEmail, newAdminRoleTitle)
                                    newAdminEmail = ""
                                    newAdminRoleTitle = ""
                                },
                                enabled = newAdminEmail.contains("@"),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("add_admin_email_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("إضافة كمسؤول / دكتور معتمد")
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = "قائمة المدراء والأطباء المعتمدين (${adminsList.size}):",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            adminsList.forEach { entry ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VerifiedUser,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = entry.email,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = if (entry.isPrimary) "${entry.roleTitle} (المدير الأساسي)" else entry.roleTitle,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (!entry.isPrimary) {
                                            IconButton(onClick = { onRemoveAdminEntry(entry) }) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "حذف المسؤول",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 2B: Manage Categories Panel
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_categories_panel"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "إدارة التصنيفات والكورسات المضافة (${categories.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (categories.isEmpty()) {
                                Text(
                                    text = "لم تتم إضافة أي تصنيفات دراسية بعد. يمكنك إضافتها من تبويب الرئيسية.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                categories.forEach { cat ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = cat.name,
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                    if (cat.description.isNotBlank()) {
                                                        Text(
                                                            text = cat.description,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                            IconButton(onClick = { onDeleteCategory(cat.id) }) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "حذف التصنيف",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 2C: نشر تحديث جديد للتطبيق (APK) عبر مخزن 5TB Google Drive
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_apk_update_publisher_card"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF0F3D2E)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = Color(0xFFC9A24D),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "نشر تحديث جديد للتطبيق (APK)",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "يُرفع ملف APK إلى مخزن 5TB Google Drive ويُثبّت فوق النسخة الحالية للطلاب دون فقدان المحاضرات المحملة (.elgdrm).",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (appUpdateInfo != null && appUpdateInfo.apkDriveFileId.isNotBlank()) {
                                Surface(
                                    color = Color(0xFF0F3D2E).copy(alpha = 0.09f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "آخر تحديث منشور حالياً: الإصدار ${appUpdateInfo.latestVersionName} (رقم ${appUpdateInfo.latestVersionCode}) • ${if (appUpdateInfo.forceUpdate) "تحديث إجباري" else "تحديث اختياري"}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (appUpdateInfo.releaseNotes.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "الملاحظات: ${appUpdateInfo.releaseNotes}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
                                        val stubApk = File(context.cacheDir, "stub_release_update.apk").apply {
                                            writeBytes("PK\u0003\u0004ElghalizApkUpdate".toByteArray(Charsets.UTF_8))
                                        }
                                        selectedApkUriString = stubApk.absolutePath
                                        selectedApkFileName = stubApk.name
                                    } else {
                                        apkPickerLauncher.launch(
                                            arrayOf(
                                                "application/vnd.android.package-archive",
                                                "application/octet-stream",
                                                "*/*"
                                            )
                                        )
                                    }
                                },
                                enabled = !isPublishingAppUpdate,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("select_apk_file_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (selectedApkFileName.isNotBlank()) {
                                        "الملف المختار: $selectedApkFileName (تغيير الملف)"
                                    } else {
                                        "اختيار ملف التحديث (.apk) من الهاتف"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = updateVersionCodeInput,
                                    onValueChange = { input ->
                                        updateVersionCodeInput = input.filter { it.isDigit() }
                                    },
                                    label = { Text("رقم الإصدار (Version Code)") },
                                    placeholder = { Text("2") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("apk_version_code_input")
                                )

                                OutlinedTextField(
                                    value = updateVersionNameInput,
                                    onValueChange = { updateVersionNameInput = it },
                                    label = { Text("اسم الإصدار (Version Name)") },
                                    placeholder = { Text("1.1") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("apk_version_name_input")
                                )
                            }

                            OutlinedTextField(
                                value = updateReleaseNotesInput,
                                onValueChange = { updateReleaseNotesInput = it },
                                label = { Text("ملاحظات التحديث (Release Notes)") },
                                placeholder = { Text("ما الجديد في هذا التحديث...") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("apk_release_notes_input")
                            )

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (updateForceToggle) "نوع التحديث: إجباري (Force Update)" else "نوع التحديث: اختياري (Optional)",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (updateForceToggle) {
                                                "يلزم الطالب بتثبيت التحديث قبل متابعة تصفح المنصة"
                                            } else {
                                                "يمكن للطالب التحديث الآن أو التأجيل لوقت لاحق"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = updateForceToggle,
                                        onCheckedChange = { updateForceToggle = it },
                                        modifier = Modifier.testTag("apk_force_update_switch")
                                    )
                                }
                            }

                            if (isPublishingAppUpdate) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "جاري رفع ملف APK إلى مخزن 5TB Google Drive...",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${appUpdatePublishProgress.coerceIn(0, 100)}%",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (appUpdatePublishProgress.coerceIn(0, 100)) / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(50)),
                                        color = Color(0xFF0F3D2E)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val code = updateVersionCodeInput.trim().toLongOrNull() ?: 2L
                                    val vName = updateVersionNameInput.trim().ifBlank { "1.$code" }
                                    val effectiveUri = if (selectedApkUriString.isBlank() &&
                                        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
                                    ) {
                                        val stubApk = File(context.cacheDir, "stub_release_update.apk").apply {
                                            writeBytes("PK\u0003\u0004ElghalizApkUpdate".toByteArray(Charsets.UTF_8))
                                        }
                                        stubApk.absolutePath
                                    } else {
                                        selectedApkUriString
                                    }
                                    onPublishAppUpdateApk(
                                        effectiveUri,
                                        code,
                                        vName,
                                        updateReleaseNotesInput.trim(),
                                        updateForceToggle
                                    )
                                },
                                enabled = !isPublishingAppUpdate && (
                                    selectedApkUriString.isNotBlank() ||
                                        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
                                    ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("publish_apk_update_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0F3D2E),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = Color(0xFFC9A24D)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPublishingAppUpdate) {
                                        "جاري نشر التحديث (${appUpdatePublishProgress.coerceIn(0, 100)}%)..."
                                    } else {
                                        "نشر التحديث"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // STUDENT MODE: Offline Course Folders + Download Tasks
        // =========================================================================
        if (!isAdminMode) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DownloadDone,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "مجلدات التنزيلات حسب الكورس",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "مرتبة في مجلدات حسب الكورسات المشترك بها للمشاهدة بدون إنترنت.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "المساحة المستخدمة: $usedStorageMb MB • ترتبط صلاحية التنزيلات بفترة الاشتراك",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Active / Paused Downloads Queue Card
            if (activeOrPausedTasks.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("active_downloads_queue_card"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "التنزيلات الجارية والمتوقفة مؤقتاً (${activeOrPausedTasks.size}):",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            activeOrPausedTasks.forEach { task ->
                                val lectureObj = lectureById[task.lectureId] ?: Lecture(
                                    id = task.lectureId,
                                    title = task.lectureTitle,
                                    categoryName = task.categoryName,
                                    chunkCount = task.totalChunks.toLong()
                                )
                                val isPaused = task.status == DownloadTaskStatus.PAUSED

                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = task.lectureTitle,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (isPaused) {
                                                        "مجلد الكورس: ${task.categoryName} • متوقف مؤقتاً (${task.progressPercent.coerceIn(0, 99)}%)"
                                                    } else {
                                                        "مجلد الكورس: ${task.categoryName} • جاري التنزيل (${task.progressPercent.coerceIn(0, 99)}%)"
                                                    },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = "${task.progressPercent.coerceIn(0, 100)}%",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        LinearProgressIndicator(
                                            progress = { (task.progressPercent.coerceIn(0, 100)) / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(50)),
                                            color = if (isPaused) Color(0xFF0D6E52) else MaterialTheme.colorScheme.primary
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (isPaused) {
                                                Button(
                                                    onClick = { onResumeDownload(lectureObj) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("queue_resume_btn_${task.lectureId}"),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF0D6E52),
                                                        contentColor = Color.White
                                                    )
                                                ) {
                                                    Text(
                                                        text = "▶️ استمرار (من ${task.progressPercent.coerceIn(0, 99)}%)",
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = { onPauseDownload(lectureObj) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("queue_pause_btn_${task.lectureId}"),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text(
                                                        text = "|| إيقاف مؤقت",
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            OutlinedButton(
                                                onClick = { onCancelDownload(task.lectureId, task.lectureTitle) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("queue_cancel_btn_${task.lectureId}"),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Icon(imageVector = Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("إلغاء التنزيل")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Course Folders for Downloaded Lectures
            if (downloads.isEmpty() && enrolledCourseFolderNames.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "لا توجد محاضرات محملة داخل التطبيق حالياً",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "اضغط على زر \"تنزيل داخل التطبيق\" أسفل أي محاضرة في كورساتك المفعلة لحفظها داخل مجلد الكورس الخاص بها.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(enrolledCourseFolderNames, key = { "folder_$it" }) { folderCourseName ->
                    val folderItems = downloadsByCourseFolder[folderCourseName] ?: emptyList()
                    val isExpanded = expandedFolderMap.getOrPut(folderCourseName) { true }

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("course_download_folder_$folderCourseName"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedFolderMap[folderCourseName] = !isExpanded }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "مجلد كورس: $folderCourseName",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${folderItems.size} محاضرة محملة",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "طي المجلد" else "فتح المجلد",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (folderItems.isEmpty()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                        ) {
                                            Text(
                                                text = "هذا المجلد فارغ حالياً — قم بتنزيل محاضرات كورس ($folderCourseName) لتظهر هنا.",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    } else {
                                        folderItems.forEach { item ->
                                            val mins = (item.durationSeconds.coerceAtLeast(1L) / 60L)
                                            val secs = (item.durationSeconds.coerceAtLeast(1L) % 60L)
                                            val durationText = if (mins > 0L) {
                                                "%02d:%02d (%d دقيقة)".format(mins, secs, mins)
                                            } else {
                                                "$secs ثانية"
                                            }

                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                                shape = RoundedCornerShape(18.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 8.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // 1. العنوان بخط كبير وبارز وعريض + شارة الكورس بجانبه
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = item.title,
                                                            style = MaterialTheme.typography.titleLarge,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.primary,
                                                            shape = RoundedCornerShape(50)
                                                        ) {
                                                            Text(
                                                                text = item.categoryName.ifBlank { folderCourseName },
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                            )
                                                        }
                                                    }

                                                    // 2. وقت/مدة المحاضرة وحجم المحاضرة بالميجابايت تحت العنوان مباشرة
                                                    Text(
                                                        text = "المدة: $durationText  •  الحجم: ${item.fileSizeMb.coerceAtLeast(1L)} MB  •  د. ${item.doctorName}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )

                                                    // 3. أزرار التشغيل والحذف
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Button(
                                                            onClick = { onPlayOfflineDownload(item) },
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = MaterialTheme.colorScheme.primary
                                                            ),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.PlayArrow,
                                                                contentDescription = "تشغيل بدون إنترنت",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = "تشغيل المحاضرة بدون إنترنت",
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        IconButton(onClick = { onRemoveOfflineDownload(item.lectureId) }) {
                                                            Icon(
                                                                imageVector = Icons.Default.DeleteOutline,
                                                                contentDescription = "حذف التنزيل",
                                                                tint = MaterialTheme.colorScheme.error
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
