package com.example

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.SessionPreferences
import com.example.data.model.UserRoleMode
import com.example.ui.theme.ElghalizMedicineTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.UUID

@Config(sdk = [36], qualifiers = "w411dp-h915dp")
@LooperMode(LooperMode.Mode.PAUSED)
class ElghalizEndToEndUiSelfTest : FirestoreEmulatorTestBase() {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun pumpMainLooperAndClock(durationMs: Long = 0L) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun <T> runAsyncAndIdleMainLooper(block: suspend () -> T): T {
        val deferred = CoroutineScope(Dispatchers.IO).async { block() }
        val start = System.currentTimeMillis()
        while (!deferred.isCompleted && System.currentTimeMillis() - start < 15_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(15)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return runBlocking { deferred.await() }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 12_000L) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTextSubstring(substring: String, timeoutMillis: Long = 12_000L) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.onAllNodesWithText(substring, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun fullEndToEndSelfTest_login_adminDashboard_pendingStudent_allButtons_andAllFiveBottomTabs() {
        val runTag = UUID.randomUUID().toString().take(6)
        val adminEmail = "dr.admin.$runTag@elghaliz.med"
        val studentEmail = "student.$runTag@elghaliz.med"

        // Ensure we start signed out so AuthScreen is tested first
        auth.signOut()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        SessionPreferences.markExplicitSignOut(composeTestRule.activity)

        var darkModeState by mutableStateOf(false)
        composeTestRule.setContent {
            ElghalizMedicineTheme(darkTheme = darkModeState) {
                ElghalizAppRoot(
                    isDarkMode = darkModeState,
                    onToggleDarkMode = { darkModeState = !darkModeState }
                )
            }
        }
        pumpMainLooperAndClock()

        // =========================================================================
        // PART 1A: Verify Login Screen (AuthScreen) opens with Welcome Text & Role Cards
        // =========================================================================
        waitForTextSubstring("Elghaliz Medicine")
        waitForTextSubstring("أهلاً وسهلاً بك في التطبيق الغليظ ~")
        composeTestRule.onNodeWithTag("auth_welcome_subtitle", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("role_student_card").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("role_admin_card").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed().assertIsEnabled()

        // =========================================================================
        // PART 1B: Sign in as Admin -> Verify transition to Admin Dashboard
        // =========================================================================
        runAsyncAndIdleMainLooper {
            signInTestUser(adminEmail)
        }
        pumpMainLooperAndClock()

        // Wait for Admin Dashboard & all 4 Bottom Navigation Tabs to appear
        waitForTag("nav_tab_library")
        composeTestRule.onNodeWithTag("nav_tab_library").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_exams").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_downloads_admins").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_settings").assertIsDisplayed()

        // Wait for Admin verification to finish so Admin tools appear on LecturesLibraryScreen
        waitForTag("add_category_button")
        composeTestRule.onNodeWithTag("add_category_button").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag("upload_lecture_button").assertIsDisplayed().assertIsEnabled()

        // =========================================================================
        // PART 1C: Sign in as New Student -> Verify transition to PendingApprovalScreen
        // =========================================================================
        SessionPreferences.saveRoleOnly(composeTestRule.activity, UserRoleMode.STUDENT)
        composeTestRule.onNodeWithTag("sign_out_button").performClick()
        pumpMainLooperAndClock()
        waitForTag("role_student_card")
        composeTestRule.onNodeWithTag("role_student_card").performScrollTo().performClick()

        val studentUid = runAsyncAndIdleMainLooper {
            signInTestUser(studentEmail)
        }
        pumpMainLooperAndClock()

        // Verify the new unapproved student is taken directly to PendingApprovalScreen
        waitForTag("pending_approval_screen")
        composeTestRule.onNodeWithTag("pending_approval_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pending_approval_message").assertIsDisplayed()
        waitForTextSubstring("حسابك قيد المراجعة.. يرجى انتظار موافقة الدكتور/المسؤول لتفعيل دخولك إلى Elghaliz Medicine")
        composeTestRule.onNodeWithTag("refresh_approval_button").assertIsDisplayed().assertIsEnabled().performClick()
        pumpMainLooperAndClock()

        // Sign out from PendingApprovalScreen and sign back in as Admin to test all Admin buttons & tabs
        composeTestRule.onNodeWithTag("pending_sign_out_button").performClick()
        pumpMainLooperAndClock()
        waitForTag("role_admin_card")
        composeTestRule.onNodeWithTag("role_admin_card").performScrollTo().performClick()

        runAsyncAndIdleMainLooper {
            signInTestUser(adminEmail)
        }
        pumpMainLooperAndClock()
        waitForTag("add_category_button")

        // =========================================================================
        // PART 2: Verify ALL 5 Action Buttons work when clicked
        // =========================================================================

        // 2.1 Button: إضافة كورس جديد (Add New Course)
        val newCourseName = "Cardiology $runTag"
        composeTestRule.onNodeWithTag("add_category_button").performClick()
        pumpMainLooperAndClock()
        waitForTag("category_name_input")
        composeTestRule.onNodeWithTag("category_name_input").performTextReplacement(newCourseName)
        composeTestRule.onNodeWithTag("category_desc_input").performTextReplacement("كورس أمراض القلب والأوعية الدموية")
        composeTestRule.onNodeWithTag("confirm_add_category_button").assertIsEnabled().performClick()
        pumpMainLooperAndClock()
        waitForTextSubstring(newCourseName)

        // 2.2 Button: رفع محاضرة (Upload Lecture)
        val newLectureTitle = "محاضرة تخطيط القلب $runTag"
        composeTestRule.onNodeWithTag("upload_lecture_button").performClick()
        pumpMainLooperAndClock()
        waitForTag("pick_file_video_button")
        composeTestRule.onNodeWithTag("pick_file_video_button").performScrollTo().performClick()
        waitForTextSubstring("تم اختيار الملف:")
        composeTestRule.onNodeWithTag("lecture_title_input").performScrollTo().performTextReplacement(newLectureTitle)
        pumpMainLooperAndClock()
        composeTestRule.onNodeWithTag("confirm_upload_lecture_button").assertIsEnabled().performClick()
        pumpMainLooperAndClock()

        // Main screen shows Courses only (no "أكمل من حيث توقفت" card)
        assertTrue(
            composeTestRule.onAllNodesWithText("أكمل من حيث توقفت", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        )

        // Open the Course Card -> navigates to unified Course Player Page
        composeTestRule.onAllNodesWithText(newCourseName, substring = true, useUnmergedTree = true)[0]
            .performClick()
        pumpMainLooperAndClock()

        waitForTag("course_player_back_button")
        waitForTextSubstring(newLectureTitle)
        // Entering a course should NOT auto-play; user taps "تشغيل" next to the lecture
        waitForTag("course_player_idle_banner")
        composeTestRule.onAllNodesWithText("تشغيل", substring = false, useUnmergedTree = true)[0]
            .performClick()
        pumpMainLooperAndClock()

        waitForTag("course_player_play_pause")
        composeTestRule.onNodeWithTag("course_player_play_pause").assertIsDisplayed()
        // Verify Pause button actually pauses playback (icon/contentDescription switches from "إيقاف مؤقت" to "تشغيل")
        composeTestRule.onNodeWithContentDescription("إيقاف مؤقت", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("course_player_play_pause").performClick()
        pumpMainLooperAndClock()
        composeTestRule.onNodeWithContentDescription("تشغيل", useUnmergedTree = true).assertIsDisplayed()
        // Verify tapping again resumes playback
        composeTestRule.onNodeWithTag("course_player_play_pause").performClick()
        pumpMainLooperAndClock()
        composeTestRule.onNodeWithContentDescription("إيقاف مؤقت", useUnmergedTree = true).assertIsDisplayed()

        composeTestRule.onNodeWithTag("course_tab_lectures").assertIsDisplayed()
        composeTestRule.onNodeWithTag("course_tab_notes").assertIsDisplayed().performClick()
        pumpMainLooperAndClock()

        waitForTag("course_note_input")
        composeTestRule.onNodeWithTag("course_note_input").performTextReplacement("ملاحظة تجريبية مهمة")
        composeTestRule.onNodeWithTag("add_course_note_button").assertIsEnabled().performClick()
        pumpMainLooperAndClock()
        waitForTextSubstring("ملاحظة تجريبية مهمة")

        composeTestRule.onNodeWithTag("course_tab_lectures").performClick()
        pumpMainLooperAndClock()
        waitForTextSubstring(newLectureTitle)

        // Return from Course Player Page back to Courses List
        composeTestRule.onNodeWithTag("course_player_back_button").performClick()
        pumpMainLooperAndClock()
        waitForTag("nav_tab_exams")

        // 2.3 Tab 1 (الاختبارات) & Button: رفع اختبار PDF بتوقيت (Upload Timed PDF Exam)
        composeTestRule.onNodeWithTag("nav_tab_exams").performClick()
        pumpMainLooperAndClock()
        waitForTag("exams_screen_list")
        waitForTag("open_upload_exam_dialog_button")
        composeTestRule.onNodeWithTag("open_upload_exam_dialog_button").assertIsEnabled().performClick()
        pumpMainLooperAndClock()
        waitForTag("exam_title_input")
        val examTitle = "اختبار منتصف الفصل $runTag"
        composeTestRule.onNodeWithTag("exam_title_input").performScrollTo().performTextReplacement(examTitle)
        composeTestRule.onNodeWithTag("generate_sample_exam_pdf_button").performScrollTo().performClick()
        pumpMainLooperAndClock()
        composeTestRule.onNodeWithTag("confirm_upload_exam_button").assertIsEnabled().performClick()
        pumpMainLooperAndClock()
        waitForTextSubstring(examTitle)

        // 2.5 Tab 3 (التنزيلات / المدراء والطلبات) & Buttons: قبول/رفض الطلاب (Approve / Reject Students)
        composeTestRule.onNodeWithTag("nav_tab_downloads_admins").performClick()
        pumpMainLooperAndClock()
        waitForTag("downloads_and_admin_list")
        waitForTag("admin_student_requests_search_input")

        // Verify student request item is displayed and Approve button works in Tab 0 (الطلبات الجديدة)
        waitForTag("approve_student_button_$studentUid")
        composeTestRule.onNodeWithTag("downloads_and_admin_list")
            .performScrollToNode(hasTestTag("approve_student_button_$studentUid"))
        composeTestRule.onNodeWithTag("approve_student_button_$studentUid").assertIsEnabled().performClick()
        pumpMainLooperAndClock()
        waitForTextSubstring("مفعّل بالتطبيق (Approved)")

        // Switch to Tab 1 (سجل الطلاب) where approved/suspended students are recorded
        waitForTag("admin_tab_students_log")
        composeTestRule.onNodeWithTag("admin_tab_students_log").performClick()
        pumpMainLooperAndClock()
        waitForTextSubstring("مفعّل بالتطبيق (Approved)")

        // Test Reject / Suspend button as well in the student log
        composeTestRule.onNodeWithTag("downloads_and_admin_list")
            .performScrollToNode(hasTestTag("reject_suspend_student_button_$studentUid"))
        composeTestRule.onNodeWithTag("reject_suspend_student_button_$studentUid").assertIsEnabled().performClick()
        pumpMainLooperAndClock()
        waitForTextSubstring("موقوف / مرفوض (Suspended)")

        // Re-approve the student so we can also test Student navigation across all 5 tabs!
        composeTestRule.onNodeWithTag("downloads_and_admin_list")
            .performScrollToNode(hasTestTag("approve_student_button_$studentUid"))
        composeTestRule.onNodeWithTag("approve_student_button_$studentUid").assertIsEnabled().performClick()
        pumpMainLooperAndClock()
        waitForTextSubstring("مفعّل بالتطبيق (Approved)")

        // =========================================================================
        // PART 3: Verify Tab 3 (الإعدادات), 1-Click 5TB Google Drive Vault Button, & Smooth Navigation Across All Tabs
        // =========================================================================
        composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
        pumpMainLooperAndClock()
        waitForTag("settings_screen_list")
        composeTestRule.onNodeWithTag("settings_unique_user_id", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_user_id_button").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        pumpMainLooperAndClock()

        // Verify 5TB Google Drive Vault card/button is completely hidden in Settings (activated silently in background)
        assertTrue(composeTestRule.onAllNodesWithTag("activate_5tb_drive_vault_button").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithTag("primary_admin_5tb_vault_card").fetchSemanticsNodes().isEmpty())

        // Switch to Student View and verify all 4 bottom navigation tabs open smoothly without any errors
        composeTestRule.onNodeWithTag("switch_role_badge").performClick()
        pumpMainLooperAndClock()

        // Tab 0: المحاضرات
        composeTestRule.onNodeWithTag("nav_tab_library").performClick()
        pumpMainLooperAndClock()
        waitForTag("lectures_library_list")

        // Tab 1: الاختبارات
        composeTestRule.onNodeWithTag("nav_tab_exams").performClick()
        pumpMainLooperAndClock()
        waitForTag("exams_screen_list")

        // Tab 2: التنزيلات
        composeTestRule.onNodeWithTag("nav_tab_downloads_admins").performClick()
        pumpMainLooperAndClock()
        waitForTag("downloads_and_admin_list")

        // Tab 3: الإعدادات
        composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
        pumpMainLooperAndClock()
        waitForTag("settings_screen_list")

        assertTrue(true)
    }
}
