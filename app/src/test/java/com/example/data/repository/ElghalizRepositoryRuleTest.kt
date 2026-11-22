package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Lecture
import com.example.data.model.SessionPreferences
import com.example.data.model.UserRoleMode
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class ElghalizRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun studentApprovalWorkflow_defaultFiveMedicalSubjects_multiChunkUploadAndStudentDownload_enforcedBySecurityRules() = runBlocking {
        val runTag = UUID.randomUUID().toString().take(8)
        val primaryAdminEmail = "dr.primary.$runTag@elghaliz.med"
        val studentEmail = "student.$runTag@elghaliz.med"

        val tempRoot = File(System.getProperty("java.io.tmpdir", "/tmp"), "elghaliz_test_$runTag")
        val adminVault = File(tempRoot, "admin_vault")
        val studentVault = File(tempRoot, "student_vault")

        // 0. Verify SessionPreferences saves and restores last logged-in email and role
        val context = ApplicationProvider.getApplicationContext<Context>()
        SessionPreferences.saveSession(
            context = context,
            email = primaryAdminEmail,
            displayName = "د. الغليظ",
            roleMode = UserRoleMode.ADMIN,
            autoSignInEnabled = true
        )
        val restoredSession = SessionPreferences.getSavedSession(context)
        assertEquals(primaryAdminEmail, restoredSession.email)
        assertEquals(UserRoleMode.ADMIN, restoredSession.roleMode)
        assertTrue(restoredSession.autoSignInEnabled)

        // 1. Sign in as primary admin and bootstrap
        signInTestUser(primaryAdminEmail)
        val adminRepo = ElghalizRepository(firestore, adminVault)

        val verifyResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.verifyOrBootstrapAdmin(primaryAdminEmail).getOrThrow()
        }
        assertTrue(verifyResult.isAuthorizedAdmin)

        // 1b. Primary Admin activates the central 5TB Google Drive Vault (system_state/drive_config)
        val activatedDriveCfg = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.activateCentral5TbDriveVault(
                accessToken = "local_test_ya29_5tb_token_$runTag",
                refreshToken = "local_test_refresh_token_$runTag",
                tokenExpiresAtMillis = System.currentTimeMillis() + 3_600_000L,
                overrideFolderId = "folder_5tb_vault_$runTag"
            ).getOrThrow()
        }
        assertTrue(activatedDriveCfg.isEnabled)
        assertEquals("Elghaliz_Medicine_5TB_Vault", activatedDriveCfg.vaultFolderName)
        assertEquals("abdallahxh909@gmail.com", activatedDriveCfg.primaryAdminEmail)

        val fetchedDriveCfg = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.getCentralDriveConfig().getOrThrow()
        }
        assertNotNull(fetchedDriveCfg)
        assertEquals("folder_5tb_vault_$runTag", fetchedDriveCfg?.vaultFolderId)

        // Admin dynamically adds Pharmacology course
        val pharmacologyCatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createCategory(
                name = "Pharmacology",
                description = "علم الأدوية والعقاقير الطبية السريرية",
                doctorName = "د. الغليظ"
            ).getOrThrow()
        }
        assertTrue(pharmacologyCatId.isNotEmpty())

        val categories = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeCategories().first()
        }
        val categoryNames = categories.map { it.name }.toSet()
        assertTrue(categoryNames.contains("Pharmacology"))

        // 2. Admin picks a multi-chunk MP4 video (~300 KB -> 3 adaptive 128 KB chunks) and uploads to Pharmacology
        val multiChunkMp4Bytes = ByteArray(300 * 1024) { idx -> ((idx * 31 + 7) and 0xFF).toByte() }
        val doctorPickedFile = File(tempRoot, "pharmacology_lecture.mp4").apply {
            parentFile?.mkdirs()
            writeBytes(multiChunkMp4Bytes)
        }

        val uploadProgressSteps = mutableListOf<Int>()
        val lectureId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.uploadLecture(
                title = "محاضرة الفارماكولوجي السريرية $runTag",
                description = "ملف فيديو كامل متعدد الأجزاء مرفوع من الهاتف",
                categoryId = pharmacologyCatId,
                categoryName = "Pharmacology",
                doctorName = "د. الغليظ",
                videoSourceUri = doctorPickedFile.absolutePath,
                durationSeconds = 240L,
                fileSizeMb = 1L,
                onProgress = { pct -> uploadProgressSteps.add(pct) }
            ).getOrThrow()
        }
        assertTrue(lectureId.isNotEmpty())
        assertTrue(uploadProgressSteps.isNotEmpty())
        assertEquals(100, uploadProgressSteps.last())

        val meetingId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.startLiveMeeting(
                title = "بث مباشر لمادة Pharmacology $runTag",
                hostName = "د. الغليظ",
                initialPresentationMode = "slides"
            ).getOrThrow()
        }
        assertTrue(meetingId.isNotEmpty())

        // 3. Switch to a new Student account -> registers as "pending"
        val studentUid = signInTestUser(studentEmail)
        val studentRepo = ElghalizRepository(firestore, studentVault)

        val regReq = withTimeout(DEFAULT_TIMEOUT_MS) {
            studentRepo.ensureStudentRequestRegistered(studentEmail, "طالب الطب").getOrThrow()
        }
        assertEquals("pending", regReq.status)

        // Unapproved student CANNOT request course enrollment yet
        val unapprovedCourseReq = withTimeout(DEFAULT_TIMEOUT_MS) {
            studentRepo.requestCourseEnrollment(
                rawEmail = studentEmail,
                rawDisplayName = "طالب الطب",
                categoryId = pharmacologyCatId,
                categoryName = "Pharmacology"
            )
        }
        assertTrue(unapprovedCourseReq.isFailure)

        // 4. Admin approves the student's main account ("approved")
        signInTestUser(primaryAdminEmail)
        val approveRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateStudentApprovalStatus(studentUid, "approved")
        }
        assertTrue(approveRes.isSuccess)

        // 5. Approved student requests enrollment in Pharmacology -> starts "pending"
        signInTestUser(studentEmail)
        val courseReq = withTimeout(DEFAULT_TIMEOUT_MS) {
            studentRepo.requestCourseEnrollment(
                rawEmail = studentEmail,
                rawDisplayName = "طالب الطب",
                categoryId = pharmacologyCatId,
                categoryName = "Pharmacology"
            ).getOrThrow()
        }
        assertEquals("pending", courseReq.status)

        // 6. Admin approves the student's Pharmacology Course Request ("approved")
        signInTestUser(primaryAdminEmail)
        val approveCourseRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.setStudentCourseEnrollmentStatus(
                studentUid = studentUid,
                studentEmail = studentEmail,
                studentName = "طالب الطب",
                categoryId = pharmacologyCatId,
                categoryName = "Pharmacology",
                newStatus = "approved"
            )
        }
        assertTrue(approveCourseRes.isSuccess)

        // 7. Enrolled student downloads the multi-chunk video on an isolated device -> verifies resumable DRM encryption (.elgdrm) and exact decrypted MP4 bytes!
        signInTestUser(studentEmail)
        val freshStudentVault = File(tempRoot, "isolated_student_device/offline_vault")
        val isolatedStudentRepo = ElghalizRepository(firestore, freshStudentVault, context)

        val remoteLecture = Lecture(
            id = lectureId,
            title = "محاضرة الفارماكولوجي السريرية $runTag",
            description = "ملف فيديو كامل متعدد الأجزاء مرفوع من الهاتف",
            categoryId = "cat_pharmacology",
            categoryName = "Pharmacology",
            uploadedByUid = "admin",
            doctorName = "د. الغليظ",
            videoSourceUri = "elghaliz://remote_only",
            durationSeconds = 240L,
            fileSizeMb = 1L,
            chunkCount = 1L
        )

        val downloadProgressSteps = mutableListOf<Int>()
        val offlineDownload = withTimeout(DEFAULT_TIMEOUT_MS) {
            isolatedStudentRepo.downloadLectureResumableEncrypted(
                lecture = remoteLecture,
                isPausedCheck = { false },
                onChunkProgress = { pct, _, _ -> downloadProgressSteps.add(pct) }
            ).getOrThrow()
        }
        assertTrue(downloadProgressSteps.isNotEmpty())
        assertEquals(100, downloadProgressSteps.last())

        val drmVaultFile = File(android.net.Uri.parse(offlineDownload.internalFilePath).path ?: "")
        assertTrue(drmVaultFile.exists())
        assertTrue(offlineDownload.internalFilePath.endsWith("$lectureId.elgdrm"))
        val encryptedOnDisk = drmVaultFile.readBytes()
        assertFalse(encryptedOnDisk.contentEquals(multiChunkMp4Bytes))
        val decryptedInMemory = com.example.data.storage.EncryptedVideoVault.decryptBytesAtOffset(
            lectureId = lectureId,
            encryptedBytes = encryptedOnDisk,
            startByteIndex = 0L
        )
        assertArrayEquals(multiChunkMp4Bytes, decryptedInMemory)

        // Student can also send questions in the live meeting chat
        val approvedChat = withTimeout(DEFAULT_TIMEOUT_MS) {
            studentRepo.sendMeetingChatMessage(
                meetingId = meetingId,
                senderName = "طالب الطب",
                senderRole = "student",
                text = "تم تنزيل محاضرة Pharmacology بالكامل بدون تقطيع!"
            )
        }
        assertTrue(approvedChat.isSuccess)

        // 7b. Verify student CANNOT read drive_config or create/delete categories or lectures
        val studentDriveRead = withTimeout(DEFAULT_TIMEOUT_MS) {
            studentRepo.getCentralDriveConfig()
        }
        assertTrue(studentDriveRead.isFailure)

        val studentCreateCat = withTimeout(DEFAULT_TIMEOUT_MS) {
            studentRepo.createCategory("Unauthorized", "Should fail", "Student")
        }
        assertTrue(studentCreateCat.isFailure)

        val studentDeleteLec = withTimeout(DEFAULT_TIMEOUT_MS) {
            studentRepo.deleteLecture(lectureId)
        }
        assertTrue(studentDeleteLec.isFailure)

        // 7c. Verify Strict Permanent 1-Device Lock in /student_requests/{uid}
        val dev1Res = withTimeout(DEFAULT_TIMEOUT_MS) {
            studentRepo.verifyOrRegisterStudentDevice("device_alpha_1").getOrThrow()
        }
        assertTrue(dev1Res.first)
        assertEquals(1, dev1Res.second.size)
        assertEquals(listOf("device_alpha_1"), dev1Res.second)

        // Any 2nd distinct device is permanently rejected by the 1-device lock per email
        val dev2Res = withTimeout(DEFAULT_TIMEOUT_MS) {
            studentRepo.verifyOrRegisterStudentDevice("device_beta_2").getOrThrow()
        }
        assertFalse(dev2Res.first)
        assertEquals(1, dev2Res.second.size)
        assertEquals(listOf("device_alpha_1"), dev2Res.second)

        // 7d. Verify SessionPreferences encrypts tokens and verified_account_$uid on disk
        SessionPreferences.saveLastKnownDriveToken(context, "secret_drive_token_$runTag", "folder_1")
        SessionPreferences.saveVerifiedAccountState(
            context = context,
            uid = studentUid,
            email = studentEmail,
            isVerifiedAdmin = false,
            isPrimaryAdmin = false,
            approvalStatus = "approved"
        )
        val rawPrefs = context.getSharedPreferences("elghaliz_session_prefs", Context.MODE_PRIVATE)
        val rawStoredToken = rawPrefs.getString("last_known_drive_token", "") ?: ""
        val rawStoredAccount = rawPrefs.getString("verified_account_$studentUid", "") ?: ""
        assertTrue(rawStoredToken.startsWith("ENC1:"))
        assertFalse(rawStoredToken.contains("secret_drive_token_$runTag"))
        assertTrue(rawStoredAccount.startsWith("ENC1:"))
        assertFalse(rawStoredAccount.contains(studentEmail))
        assertEquals("secret_drive_token_$runTag", SessionPreferences.getLastKnownDriveToken(context))
        assertEquals("approved", SessionPreferences.getVerifiedAccountState(context, studentUid, studentEmail)?.approvalStatus)

        // 8. Admin suspends the student ("suspended") -> Student loses access immediately
        signInTestUser(primaryAdminEmail)
        val suspendRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateStudentApprovalStatus(studentUid, "suspended")
        }
        assertTrue(suspendRes.isSuccess)

        signInTestUser(studentEmail)
        val suspendedChat = withTimeout(DEFAULT_TIMEOUT_MS) {
            studentRepo.sendMeetingChatMessage(
                meetingId = meetingId,
                senderName = "طالب الطب",
                senderRole = "student",
                text = "رسالة بعد الإيقاف"
            )
        }
        assertTrue(suspendedChat.isFailure)
        val suspEx = suspendedChat.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(suspEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, suspEx?.code)
    }

    @Test
    fun timedPdfExams_uploadByAdmin_andDirectDownloadByEnrolledStudent() = runBlocking {
        val runTag = UUID.randomUUID().toString().take(8)
        val adminEmail = "dr.exam.$runTag@elghaliz.med"
        val enrolledStudentEmail = "enrolled.$runTag@elghaliz.med"
        val unenrolledStudentEmail = "unenrolled.$runTag@elghaliz.med"

        val tempRoot = File(System.getProperty("java.io.tmpdir", "/tmp"), "elghaliz_exam_$runTag")
        val pdfBytes = "%PDF-1.7\nElghaliz Medicine Pharmacology Exam $runTag\n%%EOF".toByteArray()
        val samplePdfFile = File(tempRoot, "pharma_exam.pdf").apply {
            parentFile?.mkdirs()
            writeBytes(pdfBytes)
        }

        signInTestUser(adminEmail)
        val adminRepo = ElghalizRepository(firestore, File(tempRoot, "admin_dir/admin_vault"))
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.verifyOrBootstrapAdmin(adminEmail).getOrThrow()
        }

        val pharmaCatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.createCategory(
                name = "Pharmacology",
                description = "علم الأدوية والعقاقير الطبية",
                doctorName = "د. الغليظ"
            ).getOrThrow()
        }

        val now = System.currentTimeMillis()
        val examId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.uploadExamPdf(
                title = "اختبار منتصف الفصل Pharmacology $runTag",
                description = "اختبار PDF مجدول",
                categoryId = pharmaCatId,
                categoryName = "Pharmacology",
                doctorName = "د. الغليظ",
                pdfFileName = "pharma_exam.pdf",
                pdfSourceUri = samplePdfFile.absolutePath,
                startTimeMillis = now - 60_000L,
                endTimeMillis = now + 3_600_000L,
                scheduleLabel = "السبت 2026-04-04 | من 10:00 إلى 12:00"
            ).getOrThrow()
        }
        assertTrue(examId.isNotEmpty())

        // Register and approve both students at account level, but enroll only enrolledStudent in cat_pharmacology
        val enrolledUid = signInTestUser(enrolledStudentEmail)
        val enrolledRepo = ElghalizRepository(firestore, File(tempRoot, "enrolled_dir/enrolled_vault"))
        withTimeout(DEFAULT_TIMEOUT_MS) {
            enrolledRepo.ensureStudentRequestRegistered(enrolledStudentEmail, "طالب مشترك").getOrThrow()
        }

        val unenrolledUid = signInTestUser(unenrolledStudentEmail)
        val unenrolledRepo = ElghalizRepository(firestore, File(tempRoot, "unenrolled_dir/unenrolled_vault"))
        withTimeout(DEFAULT_TIMEOUT_MS) {
            unenrolledRepo.ensureStudentRequestRegistered(unenrolledStudentEmail, "طالب غير مشترك").getOrThrow()
        }

        signInTestUser(adminEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateStudentApprovalStatus(enrolledUid, "approved").getOrThrow()
            adminRepo.updateStudentApprovalStatus(unenrolledUid, "approved").getOrThrow()
            adminRepo.setStudentCourseEnrollmentStatus(
                studentUid = enrolledUid,
                studentEmail = enrolledStudentEmail,
                studentName = "طالب مشترك",
                categoryId = pharmaCatId,
                categoryName = "Pharmacology",
                newStatus = "approved"
            ).getOrThrow()
        }

        val examItem = com.example.data.model.ExamItem(
            id = examId,
            title = "اختبار منتصف الفصل Pharmacology $runTag",
            description = "اختبار PDF مجدول",
            categoryId = pharmaCatId,
            categoryName = "Pharmacology",
            uploadedByUid = "admin",
            doctorName = "د. الغليظ",
            pdfFileName = "pharma_exam.pdf",
            fileSizeKb = 1L,
            chunkCount = 1L,
            startTimeMillis = now - 60_000L,
            endTimeMillis = now + 3_600_000L,
            scheduleLabel = "السبت 2026-04-04 | من 10:00 إلى 12:00"
        )

        // Enrolled student downloads unencrypted PDF directly
        signInTestUser(enrolledStudentEmail)
        val downloadPdfRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            enrolledRepo.resolveUnencryptedExamPdfFile(examItem)
        }
        assertTrue(downloadPdfRes.isSuccess)
        assertArrayEquals(pdfBytes, downloadPdfRes.getOrThrow().readBytes())

        // Unenrolled student is blocked by Firestore rules from reading exam PDF chunks
        signInTestUser(unenrolledStudentEmail)
        val blockedRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            unenrolledRepo.resolveUnencryptedExamPdfFile(examItem)
        }
        assertTrue(blockedRes.isFailure)
    }

    @Test
    fun offlineDownloads_crossUserIsolation_andUnauthenticatedRejection() = runBlocking {
        val runTag = UUID.randomUUID().toString().take(8)
        val adminEmail = "dr.admin.$runTag@elghaliz.med"
        val aliceEmail = "alice.$runTag@elghaliz.med"
        val bobEmail = "bob.$runTag@elghaliz.med"

        signInTestUser(adminEmail)
        val adminRepo = ElghalizRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.verifyOrBootstrapAdmin(adminEmail).getOrThrow()
        }

        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = ElghalizRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureStudentRequestRegistered(aliceEmail, "Alice").getOrThrow()
        }

        signInTestUser(adminEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateStudentApprovalStatus(aliceUid, "approved").getOrThrow()
        }

        signInTestUser(aliceEmail)
        val sampleLecture = Lecture(
            id = "lec_$runTag",
            title = "محاضرة Nutrition العلاجية",
            description = "أساسيات التغذية العلاجية",
            categoryId = "cat_nutrition",
            categoryName = "Nutrition",
            uploadedByUid = aliceUid,
            doctorName = "د. الغليظ",
            videoSourceUri = "file:///tmp/nutr_$runTag.mp4",
            durationSeconds = 1800L,
            fileSizeMb = 50L
        )

        val downloadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveLectureOfflineInAppOnly(sampleLecture)
        }
        assertTrue(downloadResult.isSuccess)

        // Bob tries to read Alice's offline download record -> PERMISSION_DENIED
        signInTestUser(bobEmail)
        val bobRepo = ElghalizRepository(firestore)
        val crossRead = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getDownloadForUser(aliceUid, sampleLecture.id)
        }
        assertFalse(crossRead.isSuccess)
        val crossEx = crossRead.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(crossEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, crossEx?.code)

        // Unauthenticated user tries to observe categories -> PERMISSION_DENIED
        auth.signOut()
        var thrownEx: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeCategories().first()
            }
        } catch (e: Throwable) {
            thrownEx = e
        }
        assertNotNull(thrownEx)
        val unauthEx = thrownEx?.unwrapFirestoreException()
        assertNotNull(unauthEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, unauthEx?.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
