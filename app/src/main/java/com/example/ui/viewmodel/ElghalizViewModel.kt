package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.formatArabicUserFriendlyError
import com.example.data.model.AdminEntry
import com.example.data.model.AppUpdateInfo
import com.example.data.model.Category
import com.example.data.model.CentralDriveConfig
import com.example.data.model.CourseNotification
import com.example.data.model.CourseRequest
import com.example.data.model.DownloadTaskState
import com.example.data.model.DownloadTaskStatus
import com.example.data.model.ExamItem
import com.example.data.model.Lecture
import com.example.data.model.MAX_VIDEO_FILE_SIZE_MB
import com.example.data.model.Meeting
import com.example.data.model.MeetingMessage
import com.example.data.model.OfflineDownload
import com.example.data.model.SessionPreferences
import com.example.data.model.StudentRequest
import com.example.data.model.UploadTaskState
import com.example.data.model.UploadTaskStatus
import com.example.data.model.UserRoleMode
import com.example.data.repository.ElghalizRepository
import com.example.data.storage.GoogleDrive5TbVaultManager
import com.example.util.ElghalizNotificationHelper
import com.example.util.SamplePdfGenerator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Collections

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class ElghalizViewModel(
    private val repository: ElghalizRepository,
    val currentUserId: String,
    val currentUserEmail: String,
    val currentUserDisplayName: String,
    initialRoleChoice: UserRoleMode,
    private val appContext: Context? = null
) : ViewModel() {

    // Background engine scope so downloads continue seamlessly across tab switches and backgrounding
    private val backgroundDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloadJobs = Collections.synchronizedMap(mutableMapOf<String, Job>())
    private val pausedLectureIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val deliveredNotificationIds = Collections.synchronizedSet(mutableSetOf<String>())
    private var initialNotificationsLoaded = false

    private val isRobolectricRuntime = android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
    private val initialCachedVerification = if (appContext != null && !isRobolectricRuntime) {
        SessionPreferences.getVerifiedAccountState(appContext, currentUserId, currentUserEmail)
    } else {
        null
    }
    private val hasOfflineWorkspaceData = if (appContext != null && !isRobolectricRuntime) {
        SessionPreferences.hasOfflineStudentWorkspaceData(appContext, currentUserId)
    } else {
        false
    }
    private val hasPreVerifiedOfflineAccess = (initialCachedVerification != null &&
        (initialCachedVerification.isVerifiedAdmin || initialCachedVerification.approvalStatus == "approved")) ||
        hasOfflineWorkspaceData

    private val _activeRole = MutableStateFlow(
        if (initialRoleChoice == UserRoleMode.ADMIN && initialCachedVerification?.isVerifiedAdmin == true) {
            UserRoleMode.ADMIN
        } else {
            UserRoleMode.STUDENT
        }
    )
    val activeRole: StateFlow<UserRoleMode> = _activeRole.asStateFlow()

    private val _isVerifiedAdmin = MutableStateFlow(initialCachedVerification?.isVerifiedAdmin == true)
    val isVerifiedAdmin: StateFlow<Boolean> = _isVerifiedAdmin.asStateFlow()

    private val _isPrimaryAdmin = MutableStateFlow(initialCachedVerification?.isPrimaryAdmin == true)
    val isPrimaryAdmin: StateFlow<Boolean> = _isPrimaryAdmin.asStateFlow()

    private val _isCheckingRole = MutableStateFlow(!hasPreVerifiedOfflineAccess)
    val isCheckingRole: StateFlow<Boolean> = _isCheckingRole.asStateFlow()

    // Student Approval Status: "loading", "pending", "approved", "suspended"
    private val _studentApprovalStatus = MutableStateFlow(
        when {
            hasPreVerifiedOfflineAccess -> "approved"
            initialCachedVerification != null && initialCachedVerification.approvalStatus.isNotBlank() ->
                initialCachedVerification.approvalStatus
            else -> "loading"
        }
    )
    val studentApprovalStatus: StateFlow<String> = _studentApprovalStatus.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _openedCourseId = MutableStateFlow<String?>(null)
    val openedCourseId: StateFlow<String?> = _openedCourseId.asStateFlow()

    private val _activePlayingLecture = MutableStateFlow<Lecture?>(null)
    val activePlayingLecture: StateFlow<Lecture?> = _activePlayingLecture.asStateFlow()

    private val _isPreparingVideo = MutableStateFlow(false)
    val isPreparingVideo: StateFlow<Boolean> = _isPreparingVideo.asStateFlow()

    private val _preparingVideoProgress = MutableStateFlow(0)
    val preparingVideoProgress: StateFlow<Int> = _preparingVideoProgress.asStateFlow()

    private val _isUploadingLecture = MutableStateFlow(false)
    val isUploadingLecture: StateFlow<Boolean> = _isUploadingLecture.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0)
    val uploadProgress: StateFlow<Int> = _uploadProgress.asStateFlow()

    private val _uploadTaskState = MutableStateFlow<UploadTaskState?>(null)
    val uploadTaskState: StateFlow<UploadTaskState?> = _uploadTaskState.asStateFlow()

    private var activeUploadJob: Job? = null
    private var isUploadPausedFlag = false
    private var lastUploadedLectureParams: UploadLectureParams? = null

    private data class UploadLectureParams(
        val lectureId: String,
        val title: String,
        val description: String,
        val categoryId: String,
        val categoryName: String,
        val videoSourceUri: String,
        val durationSeconds: Long,
        val fileSizeMb: Long,
        val isRecordedMeeting: Boolean
    )

    private val _isUploadingExam = MutableStateFlow(false)
    val isUploadingExam: StateFlow<Boolean> = _isUploadingExam.asStateFlow()

    private val _examUploadProgress = MutableStateFlow(0)
    val examUploadProgress: StateFlow<Int> = _examUploadProgress.asStateFlow()

    private val _downloadingExamIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingExamIds: StateFlow<Set<String>> = _downloadingExamIds.asStateFlow()

    private val _downloadingLectureIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingLectureIds: StateFlow<Set<String>> = _downloadingLectureIds.asStateFlow()

    private val _deletingLectureIds = MutableStateFlow<Set<String>>(emptySet())
    val deletingLectureIds: StateFlow<Set<String>> = _deletingLectureIds.asStateFlow()

    private val _locallyDeletedLectureIds = MutableStateFlow<Set<String>>(emptySet())

    private val _downloadProgressMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Int>> = _downloadProgressMap.asStateFlow()

    private val _downloadStatesMap = MutableStateFlow<Map<String, DownloadTaskState>>(emptyMap())
    val downloadStatesMap: StateFlow<Map<String, DownloadTaskState>> = _downloadStatesMap.asStateFlow()

    private val _courseNotifications = MutableStateFlow<List<CourseNotification>>(emptyList())
    val courseNotifications: StateFlow<List<CourseNotification>> = _courseNotifications.asStateFlow()

    private val _activeLiveRoomId = MutableStateFlow<String?>(null)
    val activeLiveRoomId: StateFlow<String?> = _activeLiveRoomId.asStateFlow()

    private val _pendingRecordingMeeting = MutableStateFlow<Meeting?>(null)
    val pendingRecordingMeeting: StateFlow<Meeting?> = _pendingRecordingMeeting.asStateFlow()

    private val _adminsList = MutableStateFlow<List<AdminEntry>>(emptyList())
    val adminsList: StateFlow<List<AdminEntry>> = _adminsList.asStateFlow()

    private val _studentRequestsList = MutableStateFlow<List<StudentRequest>>(emptyList())
    val studentRequestsList: StateFlow<List<StudentRequest>> = _studentRequestsList.asStateFlow()

    // Per-Category (Course) Requests for current student & for Admins
    private val _myCourseRequestsList = MutableStateFlow<List<CourseRequest>>(
        if (appContext != null && !isRobolectricRuntime) {
            SessionPreferences.getLocalCourseRequestsCache(appContext, currentUserId)
        } else {
            emptyList()
        }
    )
    val myCourseRequestsList: StateFlow<List<CourseRequest>> = _myCourseRequestsList.asStateFlow()

    private val _allCourseRequestsList = MutableStateFlow<List<CourseRequest>>(emptyList())
    val allCourseRequestsList: StateFlow<List<CourseRequest>> = _allCourseRequestsList.asStateFlow()

    private val _centralDriveConfig = MutableStateFlow<CentralDriveConfig?>(null)
    val centralDriveConfig: StateFlow<CentralDriveConfig?> = _centralDriveConfig.asStateFlow()

    private val _isActivatingDriveVault = MutableStateFlow(false)
    val isActivatingDriveVault: StateFlow<Boolean> = _isActivatingDriveVault.asStateFlow()

    private val _appUpdateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val appUpdateInfo: StateFlow<AppUpdateInfo?> = _appUpdateInfo.asStateFlow()

    private val _isPublishingAppUpdate = MutableStateFlow(false)
    val isPublishingAppUpdate: StateFlow<Boolean> = _isPublishingAppUpdate.asStateFlow()

    private val _appUpdatePublishProgress = MutableStateFlow(0)
    val appUpdatePublishProgress: StateFlow<Int> = _appUpdatePublishProgress.asStateFlow()

    private val _isDownloadingAppUpdate = MutableStateFlow(false)
    val isDownloadingAppUpdate: StateFlow<Boolean> = _isDownloadingAppUpdate.asStateFlow()

    private val _appUpdateDownloadProgress = MutableStateFlow(0)
    val appUpdateDownloadProgress: StateFlow<Int> = _appUpdateDownloadProgress.asStateFlow()

    private val _downloadedUpdateApkFile = MutableStateFlow<File?>(null)
    val downloadedUpdateApkFile: StateFlow<File?> = _downloadedUpdateApkFile.asStateFlow()

    private val _registeredDevices = MutableStateFlow<List<String>>(
        if (appContext != null) {
            SessionPreferences.registerAndGetActiveDevices(appContext, currentUserId)
        } else {
            listOf("Android_Primary")
        }
    )
    val registeredDevices: StateFlow<List<String>> = _registeredDevices.asStateFlow()

    private val _isDeviceLimitExceeded = MutableStateFlow(false)
    val isDeviceLimitExceeded: StateFlow<Boolean> = _isDeviceLimitExceeded.asStateFlow()

    private var studentStatusJob: Job? = null
    private var myCourseRequestsJob: Job? = null
    private var adminListsJob: Job? = null
    private var notificationsJob: Job? = null
    private var driveConfigJob: Job? = null
    private var appUpdateJob: Job? = null

    private val _isSignedOut = MutableStateFlow(false)

    private val canAccessProtectedContent = combine(_isVerifiedAdmin, _studentApprovalStatus, _isSignedOut) { isAdmin, status, signedOut ->
        !signedOut &&
            (Firebase.auth.currentUser != null || (!isRobolectricRuntime && currentUserId.isNotBlank())) &&
            (isAdmin || status == "approved")
    }

    val categoriesState: StateFlow<UiState<List<Category>>> = canAccessProtectedContent
        .flatMapLatest { allowed ->
            if (!allowed) {
                flowOf(UiState.Success(emptyList()))
            } else if (Firebase.auth.currentUser == null) {
                val cached = if (appContext != null && !isRobolectricRuntime) {
                    SessionPreferences.getLocalCategoriesCache(appContext)
                } else {
                    emptyList()
                }
                flowOf(UiState.Success(cached))
            } else {
                repository.observeCategories()
                    .map<List<Category>, UiState<List<Category>>> { list ->
                        if (appContext != null && !isRobolectricRuntime && list.isNotEmpty()) {
                            SessionPreferences.saveLocalCategoriesCache(appContext, list)
                            SessionPreferences.saveVerifiedAccountState(
                                context = appContext,
                                uid = currentUserId,
                                email = currentUserEmail,
                                isVerifiedAdmin = _isVerifiedAdmin.value,
                                isPrimaryAdmin = _isPrimaryAdmin.value,
                                approvalStatus = "approved"
                            )
                        }
                        val effectiveList = if (list.isEmpty() && appContext != null && !isRobolectricRuntime) {
                            SessionPreferences.getLocalCategoriesCache(appContext)
                        } else {
                            list
                        }
                        UiState.Success(effectiveList)
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing categories", rootError)
                        if (appContext != null && !isRobolectricRuntime) {
                            emit(UiState.Success(SessionPreferences.getLocalCategoriesCache(appContext)))
                        } else {
                            emit(UiState.Error(rootError.message ?: "تعذر تحميل التصنيفات الدراسية"))
                        }
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = if (hasPreVerifiedOfflineAccess && appContext != null && !isRobolectricRuntime) {
                UiState.Success(SessionPreferences.getLocalCategoriesCache(appContext))
            } else {
                UiState.Loading
            }
        )

    val lecturesState: StateFlow<UiState<List<Lecture>>> = combine(
        canAccessProtectedContent,
        _locallyDeletedLectureIds
    ) { allowed, deletedIds -> allowed to deletedIds }
        .flatMapLatest { (allowed, deletedIds) ->
            if (!allowed) {
                flowOf(UiState.Success(emptyList()))
            } else if (Firebase.auth.currentUser == null) {
                val cached = if (appContext != null && !isRobolectricRuntime) {
                    SessionPreferences.getLocalLecturesCache(appContext).filterNot { deletedIds.contains(it.id) }
                } else {
                    emptyList()
                }
                flowOf(UiState.Success(cached))
            } else {
                repository.observeLectures()
                    .map<List<Lecture>, UiState<List<Lecture>>> { list ->
                        val filtered = if (deletedIds.isEmpty()) {
                            list
                        } else {
                            list.filterNot { deletedIds.contains(it.id) }
                        }
                        val effectiveList = if (filtered.isEmpty() && deletedIds.isEmpty() && appContext != null && !isRobolectricRuntime) {
                            SessionPreferences.getLocalLecturesCache(appContext)
                        } else {
                            if (appContext != null && !isRobolectricRuntime) {
                                SessionPreferences.saveLocalLecturesCache(appContext, filtered)
                                SessionPreferences.saveVerifiedAccountState(
                                    context = appContext,
                                    uid = currentUserId,
                                    email = currentUserEmail,
                                    isVerifiedAdmin = _isVerifiedAdmin.value,
                                    isPrimaryAdmin = _isPrimaryAdmin.value,
                                    approvalStatus = "approved"
                                )
                            }
                            filtered
                        }
                        UiState.Success(effectiveList)
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing lectures", rootError)
                        if (appContext != null && !isRobolectricRuntime) {
                            val cached = SessionPreferences.getLocalLecturesCache(appContext)
                                .filterNot { deletedIds.contains(it.id) }
                            emit(UiState.Success(cached))
                        } else {
                            emit(UiState.Error(rootError.message ?: "تعذر تحميل مكتبة المحاضرات"))
                        }
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = if (hasPreVerifiedOfflineAccess && appContext != null && !isRobolectricRuntime) {
                UiState.Success(SessionPreferences.getLocalLecturesCache(appContext))
            } else {
                UiState.Loading
            }
        )

    val examsState: StateFlow<UiState<List<ExamItem>>> = canAccessProtectedContent
        .flatMapLatest { allowed ->
            if (!allowed || Firebase.auth.currentUser == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeExams()
                    .map<List<ExamItem>, UiState<List<ExamItem>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing exams", rootError)
                        emit(UiState.Error(rootError.message ?: "تعذر تحميل الاختبارات"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val meetingsState: StateFlow<UiState<List<Meeting>>> = canAccessProtectedContent
        .flatMapLatest { allowed ->
            if (!allowed || Firebase.auth.currentUser == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMeetings()
                    .map<List<Meeting>, UiState<List<Meeting>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing meetings", rootError)
                        emit(UiState.Error(rootError.message ?: "تعذر تحميل الاجتماعات المباشرة"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val downloadsState: StateFlow<UiState<List<OfflineDownload>>> = canAccessProtectedContent
        .flatMapLatest { allowed ->
            if (!allowed) {
                flowOf(UiState.Success(emptyList()))
            } else if (Firebase.auth.currentUser == null) {
                val cached = if (appContext != null && !isRobolectricRuntime) {
                    SessionPreferences.getLocalDownloadsCache(appContext, currentUserId)
                } else {
                    emptyList()
                }
                flowOf(UiState.Success(cached))
            } else {
                repository.observeUserDownloads()
                    .map<List<OfflineDownload>, UiState<List<OfflineDownload>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing downloads", rootError)
                        if (appContext != null && !isRobolectricRuntime) {
                            emit(UiState.Success(SessionPreferences.getLocalDownloadsCache(appContext, currentUserId)))
                        } else {
                            emit(UiState.Error(rootError.message ?: "تعذر تحميل التنزيلات الداخلية"))
                        }
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = if (hasPreVerifiedOfflineAccess && appContext != null && !isRobolectricRuntime) {
                UiState.Success(SessionPreferences.getLocalDownloadsCache(appContext, currentUserId))
            } else {
                UiState.Loading
            }
        )

    val activeRoomMessages: StateFlow<List<MeetingMessage>> = _activeLiveRoomId
        .flatMapLatest { meetingId ->
            if (meetingId.isNullOrBlank() || Firebase.auth.currentUser == null) {
                flowOf(emptyList())
            } else {
                repository.observeMeetingMessages(meetingId)
                    .catch { error ->
                        Log.w(TAG, "Error observing meeting messages", error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    init {
        if (appContext != null) {
            viewModelScope.launch(Dispatchers.IO) {
                GoogleDrive5TbVaultManager.seedCachedTokenIfEmpty(
                    SessionPreferences.getLastKnownDriveToken(appContext)
                )
                ElghalizNotificationHelper.ensureChannels(appContext)
            }
        }
        // تسخين تصريح مخزن الـ 5 تيرابايت (Google Drive) مسبقاً في الخلفية عند تشغيل التطبيق
        // مع Random Jitter (0–1500ms عند الإقلاع، و30–35 دقيقة ± 0–180 ثانية للتجديد الاستباقي)
        // وحماية Circuit Breaker لمنع إغراق الحساب عند دخول 400 طالب متزامن
        viewModelScope.launch(Dispatchers.IO) {
            if (!isRobolectricRuntime) {
                kotlinx.coroutines.delay(GoogleDrive5TbVaultManager.computeStartupBackgroundJitterMs())
                if (!GoogleDrive5TbVaultManager.isCircuitBreakerOpen()) {
                    GoogleDrive5TbVaultManager.fetchUploadParamsFromAppsScript(isBackgroundRequest = true)
                        .onSuccess { params ->
                            if (appContext != null && params.accessToken.isNotBlank()) {
                                SessionPreferences.saveLastKnownDriveToken(
                                    appContext,
                                    params.accessToken,
                                    params.folderId
                                )
                            }
                        }
                }
                while (true) {
                    kotlinx.coroutines.delay(
                        GoogleDrive5TbVaultManager.computePreemptiveRefreshIntervalWithJitterMs()
                    )
                    if (GoogleDrive5TbVaultManager.isCircuitBreakerOpen()) {
                        continue
                    }
                    try {
                        GoogleDrive5TbVaultManager.fetchUploadParamsFromAppsScript(
                            forceRefresh = true,
                            isBackgroundRequest = true
                        ).onSuccess { params ->
                            if (appContext != null && params.accessToken.isNotBlank()) {
                                SessionPreferences.saveLastKnownDriveToken(
                                    appContext,
                                    params.accessToken,
                                    params.folderId
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        if (Firebase.auth.currentUser != null) {
            if (initialRoleChoice == UserRoleMode.ADMIN) {
                requestAdminAccess()
            } else {
                initializeStudentApprovalFlow()
            }
        } else {
            if (hasPreVerifiedOfflineAccess) {
                _studentApprovalStatus.value = "approved"
            }
            _isCheckingRole.value = false
        }
    }

    fun initializeStudentApprovalFlow() {
        if (_isSignedOut.value) {
            _isCheckingRole.value = false
            return
        }
        if (Firebase.auth.currentUser == null) {
            if (hasPreVerifiedOfflineAccess) {
                _studentApprovalStatus.value = "approved"
            }
            _isCheckingRole.value = false
            return
        }
        viewModelScope.launch {
            val cachedState = if (appContext != null && !isRobolectricRuntime) {
                SessionPreferences.getVerifiedAccountState(appContext, currentUserId, currentUserEmail)
            } else {
                null
            }
            val alreadyVerifiedOffline = hasPreVerifiedOfflineAccess ||
                (cachedState != null && (cachedState.approvalStatus == "approved" || cachedState.isVerifiedAdmin))

            if (alreadyVerifiedOffline) {
                _studentApprovalStatus.value = "approved"
                _isCheckingRole.value = false
            } else {
                _isCheckingRole.value = true
            }
            _activeRole.value = UserRoleMode.STUDENT

            if (Firebase.auth.currentUser != null && !_isSignedOut.value) {
                startObservingMyStudentStatus()
                startObservingMyCourseRequests()
                startObservingSmartCourseNotifications()
            }

            val currentDeviceId = appContext?.let { SessionPreferences.getCurrentDeviceId(it) }.orEmpty()
            val regResult = repository.ensureStudentRequestRegistered(
                rawEmail = currentUserEmail,
                rawDisplayName = currentUserDisplayName,
                currentDeviceId = currentDeviceId
            )
            regResult.onSuccess { req ->
                _studentApprovalStatus.value = req.status
                evaluateServerDeviceLimit(req.registeredDeviceIds)
                if (appContext != null && !isRobolectricRuntime) {
                    SessionPreferences.saveVerifiedAccountState(
                        context = appContext,
                        uid = currentUserId,
                        email = currentUserEmail,
                        isVerifiedAdmin = _isVerifiedAdmin.value,
                        isPrimaryAdmin = _isPrimaryAdmin.value,
                        approvalStatus = req.status
                    )
                }
            }.onFailure {
                // إذا كان الجهاز غير متصل بالإنترنت وكان الحساب قد تم التحقق منه والموافقة عليه سابقاً، نحافظ على دخوله الأوفلاين
                // أما إذا لم يكن مقبولاً من قبل، فنحافظ على بوابة التحقق (pending / suspended)
                if (!alreadyVerifiedOffline && _studentApprovalStatus.value != "approved") {
                    val fallbackStatus = cachedState?.approvalStatus?.takeIf { it != "loading" } ?: "pending"
                    _studentApprovalStatus.value = fallbackStatus
                }
            }
            _isCheckingRole.value = false
        }
    }

    private fun evaluateServerDeviceLimit(serverDeviceIds: List<String>) {
        if (_isVerifiedAdmin.value) {
            _isDeviceLimitExceeded.value = false
            return
        }
        val currentDeviceId = appContext?.let { SessionPreferences.getCurrentDeviceId(it) }.orEmpty()
        val distinctDevices = serverDeviceIds.filter { it.isNotBlank() }.distinct()
        if (distinctDevices.isNotEmpty()) {
            _registeredDevices.value = distinctDevices.take(1)
            if (appContext != null) {
                SessionPreferences.syncRegisteredDevicesFromServer(appContext, currentUserId, distinctDevices.take(1))
            }
        }
        if (currentDeviceId.isNotBlank()) {
            if (distinctDevices.isNotEmpty() && !distinctDevices.contains(currentDeviceId)) {
                _isDeviceLimitExceeded.value = true
                _bannerMessage.value = DEVICE_LIMIT_EXCEEDED_ALERT_AR
            } else if (distinctDevices.contains(currentDeviceId)) {
                _isDeviceLimitExceeded.value = false
            } else {
                _isDeviceLimitExceeded.value = false
                viewModelScope.launch {
                    repository.verifyOrRegisterStudentDevice(currentDeviceId).onSuccess { (allowed, updatedList) ->
                        _registeredDevices.value = updatedList.take(1)
                        _isDeviceLimitExceeded.value = !allowed
                        if (!allowed) {
                            _bannerMessage.value = DEVICE_LIMIT_EXCEEDED_ALERT_AR
                        } else if (appContext != null) {
                            SessionPreferences.syncRegisteredDevicesFromServer(appContext, currentUserId, updatedList.take(1))
                        }
                    }
                }
            }
        }
    }

    fun signOutFromAllOtherDevices() {
        // Strict permanent 1-device lock per email: device unlinking is permanently disabled
    }

    private fun startObservingMyStudentStatus() {
        studentStatusJob?.cancel()
        if (Firebase.auth.currentUser == null || _isSignedOut.value) return
        studentStatusJob = viewModelScope.launch {
            repository.observeMyStudentRequest(currentUserId)
                .catch { e ->
                    Log.w(TAG, "Failed to observe student request status", e)
                }
                .collect { req ->
                    if (req != null) {
                        val prev = _studentApprovalStatus.value
                        _studentApprovalStatus.value = req.status
                        evaluateServerDeviceLimit(req.registeredDeviceIds)
                        if (appContext != null && !isRobolectricRuntime) {
                            SessionPreferences.saveVerifiedAccountState(
                                context = appContext,
                                uid = currentUserId,
                                email = currentUserEmail,
                                isVerifiedAdmin = _isVerifiedAdmin.value,
                                isPrimaryAdmin = _isPrimaryAdmin.value,
                                approvalStatus = req.status
                            )
                        }
                        if (prev != "approved" && req.status == "approved") {
                            _bannerMessage.value = "تمت الموافقة على حسابك من قِبل الدكتور/المسؤول! مرحباً بك في Elghaliz Medicine"
                            startObservingSmartCourseNotifications()
                        } else if (req.status == "suspended") {
                            _activeLiveRoomId.value = null
                            _activePlayingLecture.value = null
                        }
                    }
                }
        }
    }

    private fun startObservingMyCourseRequests() {
        myCourseRequestsJob?.cancel()
        if (Firebase.auth.currentUser == null || _isSignedOut.value) return
        myCourseRequestsJob = viewModelScope.launch {
            repository.observeMyCourseRequests(currentUserId)
                .catch { e ->
                    Log.w(TAG, "Failed to observe my course requests", e)
                }
                .collect { list ->
                    if (list.isNotEmpty() || _myCourseRequestsList.value.isEmpty()) {
                        _myCourseRequestsList.value = list
                    }
                    if (appContext != null && !isRobolectricRuntime && list.isNotEmpty()) {
                        SessionPreferences.saveLocalCourseRequestsCache(appContext, currentUserId, list)
                    }
                }
        }
    }

    private fun startObservingSmartCourseNotifications() {
        notificationsJob?.cancel()
        if (Firebase.auth.currentUser == null || _isSignedOut.value) return
        notificationsJob = viewModelScope.launch {
            repository.observeCourseNotifications()
                .catch { e ->
                    Log.w(TAG, "Failed to observe course notifications", e)
                }
                .collect { list ->
                    _courseNotifications.value = list
                    if (!initialNotificationsLoaded) {
                        list.forEach { deliveredNotificationIds.add(it.id) }
                        initialNotificationsLoaded = true
                        return@collect
                    }
                    val enrolledCatIds = _myCourseRequestsList.value
                        .filter { it.status == "approved" }
                        .map { it.categoryId }
                        .toSet()

                    for (notif in list) {
                        if (deliveredNotificationIds.add(notif.id)) {
                            // Notify enrolled students (or admins viewing student notifications) when a new lecture or exam is published
                            val isRelevantForUser = _isVerifiedAdmin.value || enrolledCatIds.contains(notif.categoryId)
                            if (isRelevantForUser && notif.createdByUid != currentUserId) {
                                _bannerMessage.value = "🔔 ${notif.title}: ${notif.body}"
                                if (appContext != null) {
                                    ElghalizNotificationHelper.notifyCourseContentPublished(
                                        context = appContext,
                                        title = notif.title,
                                        body = notif.body,
                                        notificationKey = notif.id
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }

    fun refreshStudentApprovalStatus() {
        initializeStudentApprovalFlow()
    }

    fun requestAdminAccess() {
        if (Firebase.auth.currentUser == null || _isSignedOut.value) {
            _isCheckingRole.value = false
            return
        }
        viewModelScope.launch {
            val cachedState = if (appContext != null && !isRobolectricRuntime) {
                SessionPreferences.getVerifiedAccountState(appContext, currentUserId, currentUserEmail)
            } else {
                null
            }
            if (cachedState?.isVerifiedAdmin == true) {
                _isVerifiedAdmin.value = true
                _isPrimaryAdmin.value = cachedState.isPrimaryAdmin
                _activeRole.value = UserRoleMode.ADMIN
                _studentApprovalStatus.value = "approved"
                _isCheckingRole.value = false
                startObservingAdminCollections()
                startObservingMyCourseRequests()
                startObservingSmartCourseNotifications()
                startObservingCentralDriveConfig()
            } else {
                _isCheckingRole.value = true
            }

            val result = repository.verifyOrBootstrapAdmin(currentUserEmail)
            _isCheckingRole.value = false
            if (Firebase.auth.currentUser == null || _isSignedOut.value) return@launch
            result.onSuccess { verification ->
                if (verification.isAuthorizedAdmin) {
                    _isVerifiedAdmin.value = true
                    _isPrimaryAdmin.value = verification.isPrimaryAdmin
                    _activeRole.value = UserRoleMode.ADMIN
                    _studentApprovalStatus.value = "approved"
                    _bannerMessage.value = verification.messageAr
                    if (appContext != null && !isRobolectricRuntime) {
                        SessionPreferences.saveVerifiedAccountState(
                            context = appContext,
                            uid = currentUserId,
                            email = currentUserEmail,
                            isVerifiedAdmin = true,
                            isPrimaryAdmin = verification.isPrimaryAdmin,
                            approvalStatus = "approved"
                        )
                    }
                    startObservingAdminCollections()
                    startObservingMyCourseRequests()
                    startObservingSmartCourseNotifications()
                    startObservingCentralDriveConfig()
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.syncCentralDriveVaultSilentlyInBackground(isAdmin = true)
                    }
                } else {
                    _isVerifiedAdmin.value = false
                    _isPrimaryAdmin.value = false
                    _activeRole.value = UserRoleMode.STUDENT
                    if (appContext != null && !isRobolectricRuntime) {
                        SessionPreferences.saveVerifiedAccountState(
                            context = appContext,
                            uid = currentUserId,
                            email = currentUserEmail,
                            isVerifiedAdmin = false,
                            isPrimaryAdmin = false,
                            approvalStatus = cachedState?.approvalStatus ?: "pending"
                        )
                    }
                    _bannerMessage.value = verification.messageAr
                    initializeStudentApprovalFlow()
                }
            }.onFailure { err ->
                // إذا كان المدير قد تم التحقق منه مسبقاً وانقطع الإنترنت، نحافظ على جلسته الأوفلاين
                if (cachedState?.isVerifiedAdmin == true) {
                    _isVerifiedAdmin.value = true
                    _isPrimaryAdmin.value = cachedState.isPrimaryAdmin
                    _activeRole.value = UserRoleMode.ADMIN
                    _studentApprovalStatus.value = "approved"
                    _isCheckingRole.value = false
                } else {
                    _isVerifiedAdmin.value = false
                    _activeRole.value = UserRoleMode.STUDENT
                    _bannerMessage.value = err.localizedMessage ?: "تعذر التحقق من صلاحيات المسؤول"
                    initializeStudentApprovalFlow()
                }
            }
        }
    }

    private fun startObservingCentralDriveConfig() {
        driveConfigJob?.cancel()
        if (Firebase.auth.currentUser == null || _isSignedOut.value) return
        driveConfigJob = viewModelScope.launch {
            repository.observeCentralDriveConfig()
                .catch { error -> Log.w(TAG, "Failed to observe central drive config", error) }
                .collect { cfg -> _centralDriveConfig.value = cfg }
        }
    }

    fun activateCentral5TbDriveVault(
        accessToken: String,
        refreshToken: String,
        expiresAtMillis: Long
    ) {
        viewModelScope.launch {
            _isActivatingDriveVault.value = true
            repository.activateCentral5TbDriveVault(
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenExpiresAtMillis = expiresAtMillis
            ).onSuccess { cfg ->
                _isActivatingDriveVault.value = false
                _centralDriveConfig.value = cfg
            }.onFailure {
                _isActivatingDriveVault.value = false
            }
        }
    }

    fun reportDriveVaultAuthMessage(message: String) {
        // Silent in background per user specification (no drive vault banners shown)
        Log.d(TAG, "Drive vault status: $message")
    }

    fun switchToStudentView() {
        _activeRole.value = UserRoleMode.STUDENT
        if (_isVerifiedAdmin.value) {
            _studentApprovalStatus.value = "approved"
        }
        _bannerMessage.value = "تم التبديل إلى واجهة الطالب للتصفح والمشاهدة"
    }

    fun switchToAdminView() {
        if (_isVerifiedAdmin.value) {
            _activeRole.value = UserRoleMode.ADMIN
            _bannerMessage.value = "تم التبديل إلى واجهة الدكتور / المسؤول"
        } else {
            requestAdminAccess()
        }
    }

    private fun startObservingAdminCollections() {
        adminListsJob?.cancel()
        if (Firebase.auth.currentUser == null || _isSignedOut.value) return
        adminListsJob = viewModelScope.launch {
            launch {
                repository.observeAdmins()
                    .catch { error -> Log.w(TAG, "Failed to observe admins list", error) }
                    .collect { entries -> _adminsList.value = entries }
            }
            launch {
                repository.observeAllStudentRequests()
                    .catch { error -> Log.w(TAG, "Failed to observe student requests", error) }
                    .collect { requests -> _studentRequestsList.value = requests }
            }
            launch {
                repository.observeAllCourseRequests()
                    .catch { error -> Log.w(TAG, "Failed to observe all course requests", error) }
                    .collect { courseReqs -> _allCourseRequestsList.value = courseReqs }
            }
        }
    }

    fun executeProductionCleanBoot() {
        viewModelScope.launch {
            _bannerMessage.value = "جاري تصفير المنصة والبدء الإنتاجي النظيف..."
            val result = repository.executeProductionCleanFactoryReset(currentUserEmail)
            result.onSuccess { msg ->
                _bannerMessage.value = msg
                val verifyRes = repository.verifyOrBootstrapAdmin(currentUserEmail)
                verifyRes.onSuccess { v ->
                    _isVerifiedAdmin.value = v.isAuthorizedAdmin
                    _isPrimaryAdmin.value = v.isPrimaryAdmin
                    _activeRole.value = UserRoleMode.ADMIN
                }
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "تعذر إتمام التصفير الشامل"
            }
        }
    }

    fun approveStudentRequest(student: StudentRequest) {
        viewModelScope.launch {
            repository.updateStudentApprovalStatus(student.id.ifBlank { student.userId }, "approved")
                .onSuccess {
                    _bannerMessage.value = "تم قبول وتفعيل حساب الطالب (${student.displayName} - ${student.email})"
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "تعذر تفعيل حساب الطالب"
                }
        }
    }

    fun rejectOrSuspendStudentRequest(student: StudentRequest) {
        viewModelScope.launch {
            repository.updateStudentApprovalStatus(student.id.ifBlank { student.userId }, "suspended")
                .onSuccess {
                    _bannerMessage.value = "تم إيقاف / رفض حساب الطالب (${student.displayName} - ${student.email})"
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "تعذر إيقاف حساب الطالب"
                }
        }
    }

    // Per-Category (Course) Enrollment Actions
    fun requestCourseEnrollment(category: Category) {
        viewModelScope.launch {
            repository.requestCourseEnrollment(
                rawEmail = currentUserEmail,
                rawDisplayName = currentUserDisplayName,
                categoryId = category.id,
                categoryName = category.name
            ).onSuccess {
                _bannerMessage.value = "تم إرسال طلب الانضمام لكورس \"${category.name}\" إلى الدكتور للموافقة عليه"
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "تعذر إرسال طلب الانضمام للكورس"
            }
        }
    }

    fun approveCourseRequest(courseRequest: CourseRequest) {
        viewModelScope.launch {
            repository.setStudentCourseEnrollmentStatus(
                studentUid = courseRequest.userId,
                studentEmail = courseRequest.studentEmail,
                studentName = courseRequest.studentName,
                categoryId = courseRequest.categoryId,
                categoryName = courseRequest.categoryName,
                newStatus = "approved"
            ).onSuccess {
                _bannerMessage.value = "تم قبول الطالب (${courseRequest.studentName}) في كورس \"${courseRequest.categoryName}\""
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "تعذر قبول طلب الكورس"
            }
        }
    }

    fun rejectOrSuspendCourseRequest(courseRequest: CourseRequest) {
        viewModelScope.launch {
            repository.setStudentCourseEnrollmentStatus(
                studentUid = courseRequest.userId,
                studentEmail = courseRequest.studentEmail,
                studentName = courseRequest.studentName,
                categoryId = courseRequest.categoryId,
                categoryName = courseRequest.categoryName,
                newStatus = "suspended"
            ).onSuccess {
                _bannerMessage.value = "تم إيقاف / إلغاء اشتراك الطالب (${courseRequest.studentName}) في كورس \"${courseRequest.categoryName}\""
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "تعذر إيقاف اشتراك الكورس"
            }
        }
    }

    fun setDirectStudentCourseAccess(student: StudentRequest, category: Category, approve: Boolean) {
        viewModelScope.launch {
            val studentUid = student.userId.ifBlank { student.id }
            val nextStatus = if (approve) "approved" else "suspended"
            repository.setStudentCourseEnrollmentStatus(
                studentUid = studentUid,
                studentEmail = student.email,
                studentName = student.displayName,
                categoryId = category.id,
                categoryName = category.name,
                newStatus = nextStatus
            ).onSuccess {
                _bannerMessage.value = if (approve) {
                    "تم تفعيل كورس \"${category.name}\" للطالب (${student.displayName})"
                } else {
                    "تم إلغاء اشتراك الطالب (${student.displayName}) من كورس \"${category.name}\""
                }
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "تعذر تحديث صلاحية الكورس للطالب"
            }
        }
    }

    fun clearBannerMessage() {
        _bannerMessage.value = null
    }

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun openCourse(categoryId: String) {
        if (!_isVerifiedAdmin.value && _isDeviceLimitExceeded.value) {
            _bannerMessage.value = DEVICE_LIMIT_EXCEEDED_ALERT_AR
            return
        }
        _selectedCategoryId.value = categoryId
        _openedCourseId.value = categoryId
    }

    fun closeCourse() {
        _openedCourseId.value = null
    }

    fun openLecturePlayer(lecture: Lecture) {
        if (!_isVerifiedAdmin.value && _isDeviceLimitExceeded.value) {
            _activePlayingLecture.value = null
            _bannerMessage.value = DEVICE_LIMIT_EXCEEDED_ALERT_AR
            return
        }
        _isPreparingVideo.value = false
        _preparingVideoProgress.value = 100
        _activePlayingLecture.value = lecture
    }

    fun openDownloadedLecturePlayer(download: OfflineDownload) {
        if (!_isVerifiedAdmin.value && _isDeviceLimitExceeded.value) {
            _activePlayingLecture.value = null
            _bannerMessage.value = DEVICE_LIMIT_EXCEEDED_ALERT_AR
            return
        }
        val matchingLecture = ((lecturesState.value as? UiState.Success)?.data ?: emptyList())
            .firstOrNull { it.id == download.lectureId }
        val initialLecture = Lecture(
            id = download.lectureId,
            title = download.title.ifBlank { matchingLecture?.title.orEmpty() },
            description = download.description.ifBlank { matchingLecture?.description.orEmpty() },
            categoryId = matchingLecture?.categoryId?.ifBlank { "offline" } ?: "offline",
            categoryName = download.categoryName.ifBlank { matchingLecture?.categoryName.orEmpty() },
            uploadedByUid = download.userId,
            doctorName = download.doctorName.ifBlank { matchingLecture?.doctorName.orEmpty() },
            videoSourceUri = matchingLecture?.videoSourceUri?.takeIf { it.isNotBlank() }
                ?: download.internalFilePath,
            durationSeconds = download.durationSeconds.takeIf { it > 0L }
                ?: (matchingLecture?.durationSeconds ?: 120L),
            fileSizeMb = download.fileSizeMb.takeIf { it > 0L }
                ?: (matchingLecture?.fileSizeMb ?: 1L),
            chunkCount = 1L,
            isRecordedMeeting = false
        )
        openLecturePlayer(initialLecture)
    }

    fun closeLecturePlayer() {
        _activePlayingLecture.value = null
        _isPreparingVideo.value = false
        _preparingVideoProgress.value = 0
    }

    fun createCategory(name: String, description: String, onCreated: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val doctorTitle = currentUserDisplayName.ifBlank {
                currentUserEmail.substringBefore("@").ifBlank { "دكتور المشرف" }
            }
            repository.createCategory(name, description, doctorTitle)
                .onSuccess { catId ->
                    _bannerMessage.value = "تمت إضافة التصنيف الدراسي \"$name\" بنجاح"
                    onCreated?.invoke(catId)
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "تعذر إضافة التصنيف الدراسي"
                }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
                .onSuccess {
                    if (_selectedCategoryId.value == categoryId) {
                        _selectedCategoryId.value = null
                    }
                    if (_openedCourseId.value == categoryId) {
                        _openedCourseId.value = null
                    }
                    _bannerMessage.value = "تم حذف التصنيف الدراسي"
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "تعذر حذف التصنيف"
                }
        }
    }

    fun uploadLecture(
        title: String,
        description: String,
        categoryId: String,
        categoryName: String,
        videoSourceUri: String,
        durationSeconds: Long,
        fileSizeMb: Long,
        isRecordedMeeting: Boolean = false,
        existingLectureId: String? = null
    ) {
        val stableLectureId = existingLectureId?.takeIf { it.isNotBlank() }
            ?: "lec_${java.util.UUID.randomUUID().toString().replace("-", "").take(16)}"

        lastUploadedLectureParams = UploadLectureParams(
            lectureId = stableLectureId,
            title = title,
            description = description,
            categoryId = categoryId,
            categoryName = categoryName,
            videoSourceUri = videoSourceUri,
            durationSeconds = durationSeconds,
            fileSizeMb = fileSizeMb,
            isRecordedMeeting = isRecordedMeeting
        )
        isUploadPausedFlag = false
        activeUploadJob?.cancel()

        _isUploadingLecture.value = true
        val resumedPct = if (existingLectureId != null) {
            _uploadProgress.value.coerceIn(0, 99)
        } else {
            0
        }
        _uploadProgress.value = resumedPct

        val initialTask = UploadTaskState(
            lectureId = stableLectureId,
            lectureTitle = title,
            categoryName = categoryName,
            categoryId = categoryId,
            videoSourceUri = videoSourceUri,
            durationSeconds = durationSeconds,
            fileSizeMb = fileSizeMb,
            isRecordedMeeting = isRecordedMeeting,
            status = UploadTaskStatus.UPLOADING,
            progressPercent = resumedPct,
            uploadedBytes = 0L,
            totalBytes = fileSizeMb.coerceAtLeast(1L) * 1024L * 1024L,
            uploadedChunks = 0,
            totalChunks = 1,
            statusLabel = "جاري الرفع..."
        )
        _uploadTaskState.value = initialTask

        val doctorTitle = currentUserDisplayName.ifBlank {
            currentUserEmail.substringBefore("@").ifBlank { "د. الغليظ" }
        }

        activeUploadJob = viewModelScope.launch {
            repository.uploadLecture(
                title = title,
                description = description,
                categoryId = categoryId,
                categoryName = categoryName,
                doctorName = doctorTitle,
                videoSourceUri = videoSourceUri,
                durationSeconds = durationSeconds,
                fileSizeMb = fileSizeMb,
                isRecordedMeeting = isRecordedMeeting,
                existingLectureId = stableLectureId,
                isPausedCheck = { isUploadPausedFlag },
                onProgress = { pct ->
                    _uploadProgress.value = pct
                },
                onDetailedProgress = { pct, uploadedBytes, totalBytes, chunkIdx, totalChunks, _ ->
                    if (!isUploadPausedFlag) {
                        _uploadProgress.value = pct
                        _uploadTaskState.value = _uploadTaskState.value?.copy(
                            lectureId = stableLectureId,
                            status = UploadTaskStatus.UPLOADING,
                            progressPercent = pct,
                            uploadedBytes = uploadedBytes,
                            totalBytes = totalBytes,
                            uploadedChunks = chunkIdx,
                            totalChunks = totalChunks,
                            statusLabel = "جاري الرفع..."
                        )
                    }
                }
            ).onSuccess { lectureId ->
                _isUploadingLecture.value = false
                _uploadProgress.value = 100
                _uploadTaskState.value = _uploadTaskState.value?.copy(
                    lectureId = lectureId,
                    status = UploadTaskStatus.COMPLETED,
                    progressPercent = 100,
                    statusLabel = "اكتمل الرفع (100%)"
                )
                if (appContext != null) {
                    ElghalizNotificationHelper.notifyAdminUploadCompleted(
                        context = appContext,
                        itemTitle = title,
                        categoryName = categoryName,
                        isExam = false
                    )
                }
                _bannerMessage.value = if (isRecordedMeeting) {
                    "تم حفظ تسجيل البث المباشر في تصنيف \"$categoryName\" بنجاح"
                } else {
                    "اكتمل رفع المحاضرة \"$title\" في كورس \"$categoryName\" بنجاح!"
                }
            }.onFailure { err ->
                if (isUploadPausedFlag || err.message == "PAUSED_BY_USER") {
                    val currentProgress = _uploadProgress.value
                    _isUploadingLecture.value = false
                    _uploadTaskState.value = _uploadTaskState.value?.copy(
                        lectureId = stableLectureId,
                        status = UploadTaskStatus.PAUSED,
                        statusLabel = "متوقف مؤقتاً ($currentProgress%)"
                    )
                    _bannerMessage.value = "تم إيقاف الرفع مؤقتاً ($currentProgress%)."
                } else {
                    _isUploadingLecture.value = false
                    val friendlyError = formatArabicUserFriendlyError(err)
                    _uploadTaskState.value = _uploadTaskState.value?.copy(
                        lectureId = stableLectureId,
                        status = UploadTaskStatus.ERROR,
                        errorMessage = friendlyError,
                        statusLabel = friendlyError
                    )
                    _bannerMessage.value = friendlyError
                }
            }
        }
    }

    fun pauseLectureUpload() {
        isUploadPausedFlag = true
        activeUploadJob?.cancel()
        val currentProgress = _uploadProgress.value
        _isUploadingLecture.value = false
        _uploadTaskState.value = _uploadTaskState.value?.copy(
            status = UploadTaskStatus.PAUSED,
            statusLabel = "متوقف مؤقتاً ($currentProgress%)"
        )
        _bannerMessage.value = "تم إيقاف الرفع مؤقتاً ($currentProgress%)."
    }

    fun resumeLectureUpload() {
        val params = lastUploadedLectureParams ?: return
        isUploadPausedFlag = false
        _uploadTaskState.value = _uploadTaskState.value?.copy(
            status = UploadTaskStatus.UPLOADING,
            statusLabel = "جاري الرفع..."
        )
        uploadLecture(
            title = params.title,
            description = params.description,
            categoryId = params.categoryId,
            categoryName = params.categoryName,
            videoSourceUri = params.videoSourceUri,
            durationSeconds = params.durationSeconds,
            fileSizeMb = params.fileSizeMb,
            isRecordedMeeting = params.isRecordedMeeting,
            existingLectureId = params.lectureId
        )
    }

    fun cancelLectureUpload() {
        isUploadPausedFlag = false
        activeUploadJob?.cancel()
        val lectureId = _uploadTaskState.value?.lectureId ?: ""
        if (lectureId.isNotBlank()) {
            repository.clearUploadCheckpoint(lectureId)
        }
        _isUploadingLecture.value = false
        _uploadProgress.value = 0
        _uploadTaskState.value = null
        lastUploadedLectureParams = null
        _bannerMessage.value = "تم إلغاء رفع المحاضرة."
    }

    fun deleteLecture(lectureId: String) {
        if (lectureId.isBlank() || _deletingLectureIds.value.contains(lectureId)) return
        _deletingLectureIds.value = _deletingLectureIds.value + lectureId
        viewModelScope.launch {
            repository.deleteLecture(lectureId)
                .onSuccess {
                    _deletingLectureIds.value = _deletingLectureIds.value - lectureId
                    _locallyDeletedLectureIds.value = _locallyDeletedLectureIds.value + lectureId
                    if (_activePlayingLecture.value?.id == lectureId) {
                        closeLecturePlayer()
                    }
                    activeDownloadJobs.remove(lectureId)?.cancel()
                    pausedLectureIds.remove(lectureId)
                    _downloadingLectureIds.value = _downloadingLectureIds.value - lectureId
                    _downloadProgressMap.value = _downloadProgressMap.value - lectureId
                    _downloadStatesMap.value = _downloadStatesMap.value - lectureId
                    if (appContext != null) {
                        val updatedCache = SessionPreferences.getLocalLecturesCache(appContext)
                            .filterNot { it.id == lectureId }
                        SessionPreferences.saveLocalLecturesCache(appContext, updatedCache)
                    }
                    _bannerMessage.value = "تم حذف المحاضرة من المكتبة"
                }
                .onFailure { err ->
                    _deletingLectureIds.value = _deletingLectureIds.value - lectureId
                    val reason = err.localizedMessage?.takeIf { it.isNotBlank() } ?: "خطأ في الصلاحيات أو الاتصال بالخادم"
                    _bannerMessage.value = "تعذر حذف المحاضرة: $reason"
                }
        }
    }

    // =========================================================================
    // Advanced Download Engine (0%-100% + Pause + Resume + Cancel + DRM AES-256)
    // =========================================================================

    fun downloadLectureInApp(lecture: Lecture) {
        if (!_isVerifiedAdmin.value && _isDeviceLimitExceeded.value) {
            _bannerMessage.value = DEVICE_LIMIT_EXCEEDED_ALERT_AR
            return
        }
        startOrResumeLectureDownload(lecture)
    }

    fun downloadLectureInAppOnly(lecture: Lecture) {
        if (!_isVerifiedAdmin.value && _isDeviceLimitExceeded.value) {
            _bannerMessage.value = DEVICE_LIMIT_EXCEEDED_ALERT_AR
            return
        }
        startOrResumeLectureDownload(lecture)
    }

    fun startOrResumeLectureDownload(lecture: Lecture) {
        if (!_isVerifiedAdmin.value && _isDeviceLimitExceeded.value) {
            _bannerMessage.value = DEVICE_LIMIT_EXCEEDED_ALERT_AR
            return
        }
        val currentState = _downloadStatesMap.value[lecture.id]
        if (currentState?.status == DownloadTaskStatus.DOWNLOADING) return

        pausedLectureIds.remove(lecture.id)
        val cachedPct = (_downloadProgressMap.value[lecture.id] ?: currentState?.progressPercent ?: 0).coerceIn(0, 99)

        val initialState = DownloadTaskState(
            lectureId = lecture.id,
            lectureTitle = lecture.title,
            categoryName = lecture.categoryName,
            status = DownloadTaskStatus.DOWNLOADING,
            progressPercent = cachedPct,
            downloadedChunks = 0,
            totalChunks = 1,
            statusLabel = "جاري التنزيل ($cachedPct%)"
        )
        _downloadStatesMap.value = _downloadStatesMap.value + (lecture.id to initialState)
        _downloadingLectureIds.value = _downloadingLectureIds.value + lecture.id
        _downloadProgressMap.value = _downloadProgressMap.value + (lecture.id to cachedPct)

        val job = backgroundDownloadScope.launch {
            val (_, diskPct) = repository.getSavedDownloadBytesAndPercent(lecture)
            val initialPct = maxOf(diskPct, cachedPct).coerceIn(0, 99)
            if (initialPct > cachedPct && !pausedLectureIds.contains(lecture.id)) {
                val syncedState = initialState.copy(
                    progressPercent = initialPct,
                    statusLabel = "جاري التنزيل ($initialPct%)"
                )
                _downloadStatesMap.value = _downloadStatesMap.value + (lecture.id to syncedState)
                _downloadProgressMap.value = _downloadProgressMap.value + (lecture.id to initialPct)
            }
            val result = repository.downloadLectureResumableEncrypted(
                lecture = lecture,
                isPausedCheck = { pausedLectureIds.contains(lecture.id) },
                onChunkProgress = { pct, dlChunks, totChunks ->
                    if (!pausedLectureIds.contains(lecture.id)) {
                        val safePct = maxOf(pct, _downloadProgressMap.value[lecture.id] ?: 0).coerceIn(0, 100)
                        val updated = DownloadTaskState(
                            lectureId = lecture.id,
                            lectureTitle = lecture.title,
                            categoryName = lecture.categoryName,
                            status = DownloadTaskStatus.DOWNLOADING,
                            progressPercent = safePct,
                            downloadedChunks = dlChunks,
                            totalChunks = totChunks,
                            statusLabel = "جاري التنزيل ($safePct%)"
                        )
                        _downloadStatesMap.value = _downloadStatesMap.value + (lecture.id to updated)
                        _downloadProgressMap.value = _downloadProgressMap.value + (lecture.id to safePct)
                        if (appContext != null) {
                            ElghalizNotificationHelper.notifyDownloadProgress(
                                context = appContext,
                                lectureId = lecture.id,
                                lectureTitle = lecture.title,
                                progressPercent = safePct,
                                isPaused = false
                            )
                        }
                    }
                }
            )

            result.onSuccess {
                activeDownloadJobs.remove(lecture.id)
                pausedLectureIds.remove(lecture.id)
                _downloadingLectureIds.value = _downloadingLectureIds.value - lecture.id
                _downloadProgressMap.value = _downloadProgressMap.value - lecture.id
                val completedState = DownloadTaskState(
                    lectureId = lecture.id,
                    lectureTitle = lecture.title,
                    categoryName = lecture.categoryName,
                    status = DownloadTaskStatus.COMPLETED,
                    progressPercent = 100,
                    downloadedChunks = 1,
                    totalChunks = 1,
                    statusLabel = "تم التنزيل (100%)"
                )
                _downloadStatesMap.value = _downloadStatesMap.value + (lecture.id to completedState)
                if (appContext != null) {
                    ElghalizNotificationHelper.notifyStudentDownloadCompleted(
                        context = appContext,
                        lectureId = lecture.id,
                        lectureTitle = lecture.title,
                        categoryName = lecture.categoryName
                    )
                }
                _bannerMessage.value = "اكتمل تنزيل المحاضرة \"${lecture.title}\" بنجاح!"
            }.onFailure { err ->
                activeDownloadJobs.remove(lecture.id)
                val (_, savedDiskPct) = repository.getSavedDownloadBytesAndPercent(lecture)
                val pausedPct = maxOf(
                    savedDiskPct,
                    _downloadProgressMap.value[lecture.id]
                        ?: _downloadStatesMap.value[lecture.id]?.progressPercent
                        ?: 0
                ).coerceIn(0, 99)

                if (pausedLectureIds.contains(lecture.id) || err is CancellationException || err.message == "PAUSED_BY_USER") {
                    val pausedState = DownloadTaskState(
                        lectureId = lecture.id,
                        lectureTitle = lecture.title,
                        categoryName = lecture.categoryName,
                        status = DownloadTaskStatus.PAUSED,
                        progressPercent = pausedPct,
                        downloadedChunks = 0,
                        totalChunks = 1,
                        statusLabel = "متوقف مؤقتاً ($pausedPct%)"
                    )
                    _downloadStatesMap.value = _downloadStatesMap.value + (lecture.id to pausedState)
                    _downloadProgressMap.value = _downloadProgressMap.value + (lecture.id to pausedPct)
                    _downloadingLectureIds.value = _downloadingLectureIds.value - lecture.id
                    if (appContext != null) {
                        ElghalizNotificationHelper.notifyDownloadProgress(
                            context = appContext,
                            lectureId = lecture.id,
                            lectureTitle = lecture.title,
                            progressPercent = pausedPct,
                            isPaused = true
                        )
                    }
                } else {
                    _downloadingLectureIds.value = _downloadingLectureIds.value - lecture.id
                    val failedState = DownloadTaskState(
                        lectureId = lecture.id,
                        lectureTitle = lecture.title,
                        categoryName = lecture.categoryName,
                        status = DownloadTaskStatus.PAUSED,
                        progressPercent = pausedPct,
                        downloadedChunks = 0,
                        totalChunks = 1,
                        statusLabel = "متوقف مؤقتاً ($pausedPct%)"
                    )
                    _downloadStatesMap.value = _downloadStatesMap.value + (lecture.id to failedState)
                    _downloadProgressMap.value = _downloadProgressMap.value + (lecture.id to pausedPct)
                    _bannerMessage.value = err.localizedMessage ?: "توقف التنزيل مؤقتاً — يمكنك الاستمرار من نفس النقطة"
                }
            }
        }
        activeDownloadJobs[lecture.id] = job
    }

    fun pauseLectureDownload(lecture: Lecture) {
        pausedLectureIds.add(lecture.id)
        GoogleDrive5TbVaultManager.abortActiveDownloadConnection(lecture.id)
        activeDownloadJobs.remove(lecture.id)?.cancel(CancellationException("PAUSED_BY_USER"))
        val inMemoryPct = (
            _downloadProgressMap.value[lecture.id]
                ?: _downloadStatesMap.value[lecture.id]?.progressPercent
                ?: 0
            ).coerceIn(0, 99)

        val pausedState = DownloadTaskState(
            lectureId = lecture.id,
            lectureTitle = lecture.title,
            categoryName = lecture.categoryName,
            status = DownloadTaskStatus.PAUSED,
            progressPercent = inMemoryPct,
            downloadedChunks = 0,
            totalChunks = 1,
            statusLabel = "متوقف مؤقتاً ($inMemoryPct%)"
        )
        _downloadStatesMap.value = _downloadStatesMap.value + (lecture.id to pausedState)
        _downloadProgressMap.value = _downloadProgressMap.value + (lecture.id to inMemoryPct)
        _downloadingLectureIds.value = _downloadingLectureIds.value - lecture.id

        backgroundDownloadScope.launch {
            val (_, savedDiskPct) = repository.getSavedDownloadBytesAndPercent(lecture)
            val currentPct = maxOf(savedDiskPct, inMemoryPct).coerceIn(0, 99)
            if (pausedLectureIds.contains(lecture.id)) {
                val syncedPausedState = pausedState.copy(
                    progressPercent = currentPct,
                    statusLabel = "متوقف مؤقتاً ($currentPct%)"
                )
                _downloadStatesMap.value = _downloadStatesMap.value + (lecture.id to syncedPausedState)
                _downloadProgressMap.value = _downloadProgressMap.value + (lecture.id to currentPct)
                if (appContext != null) {
                    ElghalizNotificationHelper.notifyDownloadProgress(
                        context = appContext,
                        lectureId = lecture.id,
                        lectureTitle = lecture.title,
                        progressPercent = currentPct,
                        isPaused = true
                    )
                }
            }
        }
    }

    fun cancelLectureDownload(lectureId: String, lectureTitle: String = "") {
        pausedLectureIds.remove(lectureId)
        activeDownloadJobs.remove(lectureId)?.cancel(CancellationException("CANCELLED_BY_USER"))
        _downloadingLectureIds.value = _downloadingLectureIds.value - lectureId
        _downloadProgressMap.value = _downloadProgressMap.value - lectureId
        _downloadStatesMap.value = _downloadStatesMap.value - lectureId
        backgroundDownloadScope.launch {
            repository.cancelPartialLectureDownload(lectureId)
            if (appContext != null) {
                ElghalizNotificationHelper.cancelDownloadNotification(appContext, lectureId)
            }
        }
        val label = if (lectureTitle.isNotBlank()) "\"$lectureTitle\"" else "المحاضرة"
        _bannerMessage.value = "تم إلغاء تنزيل $label"
    }

    fun removeOfflineDownload(lectureId: String) {
        viewModelScope.launch {
            pausedLectureIds.remove(lectureId)
            activeDownloadJobs.remove(lectureId)?.cancel()
            _downloadStatesMap.value = _downloadStatesMap.value - lectureId
            _downloadProgressMap.value = _downloadProgressMap.value - lectureId
            repository.removeOfflineDownload(lectureId)
                .onSuccess {
                    _bannerMessage.value = "تمت إزالة المحاضرة من التنزيلات"
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "تعذر إزالة التنزيل"
                }
        }
    }

    // =========================================================================
    // Timed Course PDF Exams (Admin Upload & Unencrypted Student Phone Download)
    // =========================================================================

    fun uploadExamPdf(
        title: String,
        description: String,
        categoryId: String,
        categoryName: String,
        pdfFileName: String,
        pdfSourceUri: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        scheduleLabel: String
    ) {
        viewModelScope.launch {
            _isUploadingExam.value = true
            _examUploadProgress.value = 5
            val doctorTitle = currentUserDisplayName.ifBlank {
                currentUserEmail.substringBefore("@").ifBlank { "د. الغليظ" }
            }
            repository.uploadExamPdf(
                title = title,
                description = description,
                categoryId = categoryId,
                categoryName = categoryName,
                doctorName = doctorTitle,
                pdfFileName = pdfFileName,
                pdfSourceUri = pdfSourceUri,
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis,
                scheduleLabel = scheduleLabel,
                onProgress = { pct ->
                    _examUploadProgress.value = pct
                }
            ).onSuccess {
                _isUploadingExam.value = false
                _examUploadProgress.value = 0
                if (appContext != null) {
                    ElghalizNotificationHelper.notifyAdminUploadCompleted(
                        context = appContext,
                        itemTitle = title,
                        categoryName = categoryName,
                        isExam = true
                    )
                }
                _bannerMessage.value = "اكتمل رفع اختبار PDF \"$title\" (100%) في كورس \"$categoryName\" وتم إشعار الطلاب!"
            }.onFailure { err ->
                _isUploadingExam.value = false
                _examUploadProgress.value = 0
                _bannerMessage.value = err.localizedMessage ?: "تعذر رفع ملف الاختبار PDF"
            }
        }
    }

    fun deleteExam(examId: String) {
        viewModelScope.launch {
            repository.deleteExam(examId)
                .onSuccess {
                    _bannerMessage.value = "تم حذف الاختبار من الكورس"
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "تعذر حذف الاختبار"
                }
        }
    }

    fun downloadUnencryptedExamPdfToPhone(
        exam: ExamItem,
        onPdfResolved: ((File) -> Unit)? = null
    ) {
        if (_downloadingExamIds.value.contains(exam.id)) return
        viewModelScope.launch {
            _downloadingExamIds.value = _downloadingExamIds.value + exam.id
            repository.resolveUnencryptedExamPdfFile(exam)
                .onSuccess { localPdfFile ->
                    _downloadingExamIds.value = _downloadingExamIds.value - exam.id
                    if (appContext != null) {
                        val savedPathResult = SamplePdfGenerator.saveUnencryptedPdfToPhoneDownloads(
                            context = appContext,
                            sourcePdfFile = localPdfFile,
                            desiredFileName = exam.pdfFileName.ifBlank { "${exam.title}.pdf" }
                        )
                        savedPathResult.onSuccess { savedLocation ->
                            _bannerMessage.value = "تم تنزيل ملف الاختبار PDF غير مشفر مباشرة إلى هاتفك ($savedLocation)"
                        }.onFailure {
                            _bannerMessage.value = "تم تجهيز ملف الاختبار PDF (${exam.pdfFileName}) بنجاح"
                        }
                    } else {
                        _bannerMessage.value = "تم تنزيل ملف الاختبار PDF (${exam.pdfFileName}) بنجاح"
                    }
                    onPdfResolved?.invoke(localPdfFile)
                }
                .onFailure { err ->
                    _downloadingExamIds.value = _downloadingExamIds.value - exam.id
                    _bannerMessage.value = err.localizedMessage ?: "تعذر تنزيل ملف الاختبار PDF"
                }
        }
    }

    fun startNativeLiveMeeting(title: String, initialPresentationMode: String) {
        viewModelScope.launch {
            val hostName = currentUserDisplayName.ifBlank {
                currentUserEmail.substringBefore("@").ifBlank { "دكتور المادة" }
            }
            repository.startLiveMeeting(title, hostName, initialPresentationMode)
                .onSuccess { meetingId ->
                    _activeLiveRoomId.value = meetingId
                    _bannerMessage.value = "تم فتح غرفة البث المباشر الداخلية! يمكن للطلاب المفعّلين الانضمام الآن."
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "تعذر بدء الاجتماع المباشر"
                }
        }
    }

    fun enterMeetingRoom(meeting: Meeting) {
        _activeLiveRoomId.value = meeting.id
        if (_activeRole.value == UserRoleMode.STUDENT) {
            viewModelScope.launch {
                repository.joinLiveMeetingAsStudent(meeting)
            }
        }
    }

    fun leaveMeetingRoom() {
        _activeLiveRoomId.value = null
    }

    fun updateMeetingDoctorControls(
        meeting: Meeting,
        isRecording: Boolean,
        isScreenSharing: Boolean,
        presentationMode: String,
        currentSlideIndex: Long,
        studentsMuted: Boolean
    ) {
        viewModelScope.launch {
            repository.updateMeetingDoctorControls(
                meetingId = meeting.id,
                isRecording = isRecording,
                isScreenSharing = isScreenSharing,
                presentationMode = presentationMode,
                currentSlideIndex = currentSlideIndex,
                studentsMuted = studentsMuted
            )
        }
    }

    fun toggleStudentHandRaise(meeting: Meeting, isRaising: Boolean) {
        viewModelScope.launch {
            val studentName = currentUserDisplayName.ifBlank {
                currentUserEmail.substringBefore("@").ifBlank { "طالب" }
            }
            repository.updateStudentHandRaiseInMeeting(meeting, isRaising, studentName)
        }
    }

    fun sendLiveChatMessage(meetingId: String, text: String) {
        viewModelScope.launch {
            val senderName = currentUserDisplayName.ifBlank {
                currentUserEmail.substringBefore("@").ifBlank { "مشارك" }
            }
            val role = if (_activeRole.value == UserRoleMode.ADMIN && _isVerifiedAdmin.value) "admin" else "student"
            repository.sendMeetingChatMessage(
                meetingId = meetingId,
                senderName = senderName,
                senderRole = role,
                text = text,
                isHandRaise = false
            )
        }
    }

    fun finishMeetingAndPromptSaveIfRecorded(meeting: Meeting, wasRecorded: Boolean) {
        viewModelScope.launch {
            repository.endLiveMeeting(meeting.id)
                .onSuccess {
                    _activeLiveRoomId.value = null
                    if (wasRecorded) {
                        _pendingRecordingMeeting.value = meeting
                    } else {
                        _bannerMessage.value = "تم إنهاء غرفة الاجتماع المباشر الداخلي"
                    }
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "تعذر إنهاء الاجتماع المباشر"
                }
        }
    }

    fun dismissPendingRecordingSave() {
        _pendingRecordingMeeting.value = null
    }

    fun saveRecordedMeetingToCategory(
        meeting: Meeting,
        category: Category,
        customTitle: String,
        recordedVideoUri: String,
        durationSeconds: Long,
        fileSizeMb: Long = 5L
    ) {
        _pendingRecordingMeeting.value = null
        uploadLecture(
            title = customTitle.ifBlank { "تسجيل مباشر: ${meeting.title}" },
            description = "تسجيل كامل بالصوت والصورة من غرفة البث المباشر الداخلية - تقديم د. ${meeting.hostName}",
            categoryId = category.id,
            categoryName = category.name,
            videoSourceUri = recordedVideoUri,
            durationSeconds = durationSeconds.coerceAtLeast(1L),
            fileSizeMb = fileSizeMb.coerceAtLeast(1L),
            isRecordedMeeting = true
        )
    }

    fun addAdminEmail(email: String, roleTitle: String) {
        viewModelScope.launch {
            repository.addAdminEmail(email, roleTitle)
                .onSuccess {
                    _bannerMessage.value = "تمت إضافة البريد الإلكتروني ($email) إلى قائمة المدراء بنجاح"
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "تعذر إضافة المسؤول"
                }
        }
    }

    fun removeAdminEntry(entry: AdminEntry) {
        viewModelScope.launch {
            repository.removeAdminEntry(entry)
                .onSuccess {
                    _bannerMessage.value = "تم حذف (${entry.email}) من قائمة المدراء"
                }
                .onFailure { err ->
                    _bannerMessage.value = err.localizedMessage ?: "تعذر حذف المسؤول"
                }
        }
    }

    private fun startObservingAppUpdateInfo() {
        appUpdateJob?.cancel()
        if (Firebase.auth.currentUser == null || _isSignedOut.value) return
        appUpdateJob = viewModelScope.launch {
            repository.observeAppUpdateInfo()
                .catch { error -> Log.w(TAG, "Failed to observe app update info", error) }
                .collect { info -> _appUpdateInfo.value = info }
        }
    }

    fun publishAppUpdateApk(
        apkSourceUri: String,
        latestVersionCode: Long,
        latestVersionName: String,
        releaseNotes: String,
        forceUpdate: Boolean
    ) {
        if (_isPublishingAppUpdate.value) return
        viewModelScope.launch {
            _isPublishingAppUpdate.value = true
            _appUpdatePublishProgress.value = 1
            repository.publishAppUpdateApk(
                apkSourceUri = apkSourceUri,
                latestVersionCode = latestVersionCode,
                latestVersionName = latestVersionName,
                releaseNotes = releaseNotes,
                forceUpdate = forceUpdate,
                onProgress = { pct ->
                    _appUpdatePublishProgress.value = pct.coerceIn(1, 100)
                }
            ).onSuccess { publishedInfo ->
                _isPublishingAppUpdate.value = false
                _appUpdatePublishProgress.value = 100
                _appUpdateInfo.value = publishedInfo
                _bannerMessage.value = "تم رفع ونشر تحديث التطبيق (الإصدار ${publishedInfo.latestVersionName}) بنجاح لجميع الطلاب!"
            }.onFailure { err ->
                _isPublishingAppUpdate.value = false
                _appUpdatePublishProgress.value = 0
                _bannerMessage.value = formatArabicUserFriendlyError(err)
            }
        }
    }

    fun startAppUpdateDownload(apkDriveFileId: String) {
        if (_isDownloadingAppUpdate.value) return
        val cleanId = apkDriveFileId.trim().ifBlank { _appUpdateInfo.value?.apkDriveFileId.orEmpty() }
        if (cleanId.isBlank()) {
            _bannerMessage.value = "ملف التحديث غير متوفر حالياً على السحابة"
            return
        }
        viewModelScope.launch {
            _isDownloadingAppUpdate.value = true
            _appUpdateDownloadProgress.value = 1
            _downloadedUpdateApkFile.value = null
            repository.downloadAppUpdateApkToCache(
                apkDriveFileId = cleanId,
                onProgress = { pct ->
                    _appUpdateDownloadProgress.value = pct.coerceIn(1, 100)
                }
            ).onSuccess { apkFile ->
                _isDownloadingAppUpdate.value = false
                _appUpdateDownloadProgress.value = 100
                _downloadedUpdateApkFile.value = apkFile
            }.onFailure { err ->
                _isDownloadingAppUpdate.value = false
                _appUpdateDownloadProgress.value = 0
                _bannerMessage.value = formatArabicUserFriendlyError(err)
            }
        }
    }

    fun clearDownloadedUpdateApkFile() {
        _downloadedUpdateApkFile.value = null
    }

    fun prepareForSignOut() {
        _isSignedOut.value = true
        _isVerifiedAdmin.value = false
        _isPrimaryAdmin.value = false
        _openedCourseId.value = null
        _studentApprovalStatus.value = "loading"
        studentStatusJob?.cancel()
        myCourseRequestsJob?.cancel()
        adminListsJob?.cancel()
        notificationsJob?.cancel()
        driveConfigJob?.cancel()
        appUpdateJob?.cancel()
    }

    private companion object {
        const val TAG = "ElghalizVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val DEVICE_LIMIT_EXCEEDED_ALERT_AR =
            "هذا الإيميل مرتبط بجهاز آخر بالفعل (يُسمح بجهاز واحد فقط لكل إيميل)"
    }
}
