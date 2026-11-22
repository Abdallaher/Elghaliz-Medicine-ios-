package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest

enum class UserRoleMode {
    STUDENT,
    ADMIN
}

@IgnoreExtraProperties
data class SystemState(
    @DocumentId val id: String = "",
    val primaryAdminUid: String = "",
    val adminEmailHashes: List<String> = emptyList(),
    val scopeKey: String = "global",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CentralDriveConfig(
    @DocumentId val id: String = "",
    val primaryAdminEmail: String = "abdallahxh909@gmail.com",
    val primaryAdminUid: String = "",
    val vaultFolderId: String = "",
    val vaultFolderName: String = "Elghaliz_Medicine_5TB_Vault",
    val accessToken: String = "",
    val refreshToken: String = "",
    val tokenExpiresAtMillis: Long = 0L,
    @get:PropertyName("isEnabled") @set:PropertyName("isEnabled")
    var isEnabled: Boolean = false,
    val scopeKey: String = "global",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppUpdateInfo(
    @DocumentId val id: String = "",
    val latestVersionCode: Long = 1L,
    val latestVersionName: String = "1.0",
    val apkDriveFileId: String = "",
    val releaseNotes: String = "",
    @get:PropertyName("forceUpdate") @set:PropertyName("forceUpdate")
    var forceUpdate: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AdminMember(
    @DocumentId val id: String = "",
    val userId: String = "",
    val emailHash: String = "",
    @get:PropertyName("isPrimary") @set:PropertyName("isPrimary")
    var isPrimary: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AdminEntry(
    @DocumentId val id: String = "",
    val email: String = "",
    val emailHash: String = "",
    val roleTitle: String = "",
    val addedByUid: String = "",
    @get:PropertyName("isPrimary") @set:PropertyName("isPrimary")
    var isPrimary: Boolean = false,
    val scopeKey: String = "global",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudentRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val status: String = "pending", // "pending", "approved", "suspended"
    val scopeKey: String = "global",
    val registeredDeviceIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CourseRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentEmail: String = "",
    val studentName: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val status: String = "pending", // "pending", "approved", "suspended"
    val scopeKey: String = "global",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Category(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    val visibility: String = "published",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Lecture(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val uploadedByUid: String = "",
    val doctorName: String = "",
    val videoSourceUri: String = "",
    val durationSeconds: Long = 120L,
    val fileSizeMb: Long = 5L,
    val chunkCount: Long = 0L,
    @get:PropertyName("isRecordedMeeting") @set:PropertyName("isRecordedMeeting")
    var isRecordedMeeting: Boolean = false,
    val visibility: String = "published",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LectureVideoChunk(
    @DocumentId val id: String = "",
    val lectureId: String = "",
    val chunkIndex: Long = 0L,
    val dataBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Meeting(
    @DocumentId val id: String = "",
    val title: String = "",
    val hostUid: String = "",
    val hostName: String = "",
    val presentationMode: String = "camera", // "camera" or "slides"
    val currentSlideIndex: Long = 0L,
    val studentsMuted: Boolean = false,
    val raisedHandsCount: Long = 0L,
    val status: String = "live",
    @get:PropertyName("isRecording") @set:PropertyName("isRecording")
    var isRecording: Boolean = false,
    @get:PropertyName("isScreenSharing") @set:PropertyName("isScreenSharing")
    var isScreenSharing: Boolean = false,
    val participantCount: Long = 1L,
    val visibility: String = "published",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MeetingMessage(
    @DocumentId val id: String = "",
    val meetingId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "student", // "admin" or "student"
    val text: String = "",
    @get:PropertyName("isHandRaise") @set:PropertyName("isHandRaise")
    var isHandRaise: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class OfflineDownload(
    @DocumentId val id: String = "",
    val userId: String = "",
    val lectureId: String = "",
    val title: String = "",
    val description: String = "",
    val categoryName: String = "",
    val doctorName: String = "",
    val internalFilePath: String = "",
    val durationSeconds: Long = 120L,
    val fileSizeMb: Long = 5L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ExamItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val uploadedByUid: String = "",
    val doctorName: String = "",
    val pdfFileName: String = "",
    val fileSizeKb: Long = 100L,
    val chunkCount: Long = 1L,
    val startTimeMillis: Long = 0L,
    val endTimeMillis: Long = 0L,
    val scheduleLabel: String = "",
    val visibility: String = "published",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isCurrentlyOpen(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return nowMillis in startTimeMillis..endTimeMillis
    }

    fun isUpcoming(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return nowMillis < startTimeMillis
    }

    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return nowMillis > endTimeMillis
    }
}

@IgnoreExtraProperties
data class ExamPdfChunk(
    @DocumentId val id: String = "",
    val examId: String = "",
    val chunkIndex: Long = 0L,
    val dataBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CourseNotification(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val eventType: String = "new_lecture", // "new_lecture" or "new_exam"
    val targetItemId: String = "",
    val createdByUid: String = "",
    val visibility: String = "published",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class DownloadTaskStatus {
    IDLE,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadTaskState(
    val lectureId: String,
    val lectureTitle: String,
    val categoryName: String,
    val status: DownloadTaskStatus = DownloadTaskStatus.IDLE,
    val progressPercent: Int = 0,
    val downloadedChunks: Int = 0,
    val totalChunks: Int = 1,
    val statusLabel: String = "",
    val retryCount: Int = 0
)

object DefaultMedicalCategories {
    val ALL: List<Category> = listOf(
        Category(
            id = "cat_nutrition",
            name = "Nutrition",
            description = "كورس التغذية الطبية والعلاجية (Clinical & Medical Nutrition)",
            createdByUid = "system_elghaliz",
            createdByName = "د. الغليظ",
            visibility = "published"
        ),
        Category(
            id = "cat_microbiology",
            name = "Microbiology",
            description = "كورس الأحياء الدقيقة الطبية والمناعة (Medical Microbiology)",
            createdByUid = "system_elghaliz",
            createdByName = "د. الغليظ",
            visibility = "published"
        ),
        Category(
            id = "cat_pharmacology",
            name = "Pharmacology",
            description = "كورس علم الأدوية السريري والعلاجات (Clinical Pharmacology)",
            createdByUid = "system_elghaliz",
            createdByName = "د. الغليظ",
            visibility = "published"
        ),
        Category(
            id = "cat_pathology",
            name = "Pathology",
            description = "كورس علم الأمراض العام والخاص (General & Systemic Pathology)",
            createdByUid = "system_elghaliz",
            createdByName = "د. الغليظ",
            visibility = "published"
        ),
        Category(
            id = "cat_genetics",
            name = "Genetics",
            description = "كورس علم الوراثة الطبية والجزيئية (Medical Genetics)",
            createdByUid = "system_elghaliz",
            createdByName = "د. الغليظ",
            visibility = "published"
        )
    )
}

object SecurityHashUtil {
    fun sha256Hex(rawEmail: String): String {
        val normalized = rawEmail.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(normalized.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun emailToSafeDocId(rawEmail: String): String {
        val hash = sha256Hex(rawEmail)
        return "adm_${hash.take(28)}"
    }

    fun formatUserIdCode(uid: String): String {
        if (uid.isBlank()) return "ELG-000000"
        val clean = uid.uppercase().filter { it.isLetterOrDigit() }
        val suffix = clean.takeLast(8).padStart(8, '0')
        return "ELG-$suffix"
    }
}

/**
 * حالات مهمة رفع الفيديو مع دعم الإيقاف المؤقت، الاستئناف، وإعادة المحاولة التلقائية
 */
enum class UploadTaskStatus {
    IDLE,
    UPLOADING,
    PAUSED,
    RETRYING,
    COMPLETED,
    ERROR
}

data class UploadTaskState(
    val lectureId: String = "",
    val lectureTitle: String = "",
    val categoryName: String = "",
    val categoryId: String = "",
    val videoSourceUri: String = "",
    val durationSeconds: Long = 120L,
    val fileSizeMb: Long = 5L,
    val isRecordedMeeting: Boolean = false,
    val status: UploadTaskStatus = UploadTaskStatus.IDLE,
    val progressPercent: Int = 0,
    val uploadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val uploadedChunks: Int = 0,
    val totalChunks: Int = 0,
    val statusLabel: String = "",
    val errorMessage: String? = null
)

/**
 * حجم الفيديو مفتوح بالكامل بدون حد أقصى (يدعم حتى 100 جيجابايت وأكثر عبر الرفع المتدفق)
 */
const val MAX_VIDEO_FILE_SIZE_MB: Long = 100_000L


