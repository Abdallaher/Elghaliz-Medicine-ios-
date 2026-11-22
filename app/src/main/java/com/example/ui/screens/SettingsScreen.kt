package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhonelinkErase
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Category
import com.example.data.model.CourseRequest
import com.example.data.model.UserRoleMode
import com.example.ui.viewmodel.UiState

private val SettingsDeepGreen = Color(0xFF0F3D2E)
private val SettingsMidGreen = Color(0xFF1F6B52)
private val SettingsGold = Color(0xFFC9A24D)
private val SettingsMint = Color(0xFFE9F0E8)
private val SettingsDangerBg = Color(0xFFFBEDEA)
private val SettingsDangerText = Color(0xFFB04A44)

@Composable
fun SettingsScreen(
    userId: String,
    fullName: String,
    email: String,
    activeRole: UserRoleMode,
    isVerifiedAdmin: Boolean,
    isPrimaryAdmin: Boolean,
    centralDriveConfig: com.example.data.model.CentralDriveConfig? = null,
    isActivatingDriveVault: Boolean = false,
    onActivateDriveVault: (String, String, Long) -> Unit = { _, _, _ -> },
    onDriveVaultMessage: (String) -> Unit = {},
    categories: List<Category> = emptyList(),
    myCourseRequestsList: List<CourseRequest> = emptyList(),
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    onFullDataWipe: () -> Unit = {},
    registeredDevices: List<String> = listOf("Android_Primary"),
    onSignOutFromAllOtherDevices: () -> Unit = {},
    onSignOutClick: () -> Unit
) {
    SettingsScreen(
        currentUserId = userId,
        currentUserEmail = email,
        currentUserDisplayName = fullName,
        activeRole = activeRole,
        isVerifiedAdmin = isVerifiedAdmin,
        isPrimaryAdmin = isPrimaryAdmin,
        studentApprovalStatus = "approved",
        myCourseRequestsList = myCourseRequestsList,
        categoriesState = UiState.Success(categories),
        isDarkTheme = isDarkMode,
        onToggleDarkTheme = { onToggleDarkMode() },
        onSwitchRoleMode = {},
        onSignOut = onSignOutClick,
        registeredDevices = registeredDevices,
        onSignOutFromAllOtherDevices = onSignOutFromAllOtherDevices
    )
}

@Composable
fun SettingsScreen(
    currentUserId: String,
    currentUserEmail: String,
    currentUserDisplayName: String,
    activeRole: UserRoleMode,
    isVerifiedAdmin: Boolean,
    isPrimaryAdmin: Boolean,
    studentApprovalStatus: String,
    myCourseRequestsList: List<CourseRequest>,
    categoriesState: UiState<List<Category>>,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onSwitchRoleMode: () -> Unit,
    onSignOut: () -> Unit,
    usedStorageMb: Long = 0L,
    wifiOnlyDownload: Boolean = true,
    onToggleWifiOnlyDownload: (Boolean) -> Unit = {},
    autoDeleteWatched: Boolean = false,
    onToggleAutoDeleteWatched: (Boolean) -> Unit = {},
    fontSizeScale: Float = 1.0f,
    onChangeFontSizeScale: (Float) -> Unit = {},
    appLanguage: String = "ar",
    onChangeAppLanguage: (String) -> Unit = {},
    registeredDevices: List<String> = listOf("Android_Primary"),
    onSignOutFromAllOtherDevices: () -> Unit = {},
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    var copiedUserIdFeedback by remember { mutableStateOf(false) }

    val categories = (categoriesState as? UiState.Success)?.data ?: emptyList()
    val categoryNameById = remember(categories) {
        categories.associate { it.id to it.name }
    }

    val formattedUniqueId = remember(currentUserId) {
        val raw = currentUserId.trim()
        if (raw.length >= 10) {
            "ELG-${raw.take(5).uppercase()}-${raw.takeLast(5).uppercase()}"
        } else if (raw.isNotBlank()) {
            "ELG-${raw.uppercase()}"
        } else {
            "ELG-GUEST-0000"
        }
    }

    val approvedCourses = remember(myCourseRequestsList) {
        myCourseRequestsList.filter { it.status == "approved" }
    }
    val pendingCourses = remember(myCourseRequestsList) {
        myCourseRequestsList.filter { it.status == "pending" }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Deep-Green Header Profile Card + Floating User ID Card
        item(key = "settings_profile_header") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = SettingsDeepGreen),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(SettingsGold, Color(0xFFB58C38))
                                    )
                                )
                                .border(2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (activeRole == UserRoleMode.ADMIN && isVerifiedAdmin) {
                                    Icons.Default.VerifiedUser
                                } else {
                                    Icons.Default.School
                                },
                                contentDescription = null,
                                tint = SettingsDeepGreen,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = currentUserDisplayName.ifBlank { "مستخدم Elghaliz Medicine" },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = currentUserEmail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.82f)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = SettingsGold.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, SettingsGold.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = when {
                                    isPrimaryAdmin && activeRole == UserRoleMode.ADMIN ->
                                        "الصفة الحالية: المدير الرئيسي / دكتور مؤسس"
                                    isVerifiedAdmin && activeRole == UserRoleMode.ADMIN ->
                                        "الصفة الحالية: دكتور / مسؤول معتمد"
                                    isVerifiedAdmin && activeRole == UserRoleMode.STUDENT ->
                                        "الصفة الحالية: معاينة واجهة الطالب (حساب مسؤول)"
                                    studentApprovalStatus == "approved" ->
                                        "الصفة الحالية: طالب معتمد ومفعّل ✓"
                                    else ->
                                        "الصفة الحالية: طالب ($studentApprovalStatus)"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = SettingsGold,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Floating Unique User ID Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
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
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SettingsMint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = null,
                                        tint = SettingsMidGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "معرّف الحساب الخاص بك (User ID)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formattedUniqueId,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.testTag("settings_unique_user_id")
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipText = "ID: $formattedUniqueId | UID: $currentUserId | Email: $currentUserEmail"
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Elghaliz User ID", clipText))
                                    copiedUserIdFeedback = true
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SettingsGold,
                                    contentColor = SettingsDeepGreen
                                ),
                                modifier = Modifier
                                    .height(42.dp)
                                    .testTag("copy_user_id_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "نسخ المعرف",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (copiedUserIdFeedback) "تم النسخ ✓" else "نسخ ID",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (copiedUserIdFeedback) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "تم نسخ معرّف الـ ID ($formattedUniqueId) إلى الحافظة ✓",
                                style = MaterialTheme.typography.labelSmall,
                                color = SettingsMidGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. Enrolled Courses Section ("كورساتي المسجلة")
        item(key = "settings_my_courses") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_my_courses_card"),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "كورساتي المسجلة",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isVerifiedAdmin && activeRole == UserRoleMode.ADMIN) {
                        Text(
                            text = "بصفتك دكتور / مسؤول معتمد، لديك صلاحية الوصول الكامل وإدارة جميع التصنيفات والكورسات (${categories.size} كورسات).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (approvedCourses.isEmpty() && pendingCourses.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "أنت غير مشترك في أي كورس حالياً. يمكنك إرسال طلب انضمام لأي كورس من تبويب (المحاضرات).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        if (approvedCourses.isNotEmpty()) {
                            approvedCourses.forEach { req ->
                                val title = req.categoryName.ifBlank {
                                    categoryNameById[req.categoryId] ?: "كورس طبي"
                                }
                                Surface(
                                    color = SettingsMint.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = SettingsMidGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = SettingsDeepGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Surface(
                                            color = SettingsMidGreen,
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                text = "مفعّل",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (pendingCourses.isNotEmpty()) {
                            pendingCourses.forEach { req ->
                                val title = req.categoryName.ifBlank {
                                    categoryNameById[req.categoryId] ?: "كورس طبي"
                                }
                                Surface(
                                    color = SettingsGold.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.HourglassTop,
                                                contentDescription = null,
                                                tint = Color(0xFFB5821E),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Text(
                                            text = "بانتظار موافقة الدكتور",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFB5821E),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Storage, Offline Downloads & Device Security Card
        item(key = "settings_storage_and_devices") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "إدارة التنزيلات وحماية الأجهزة",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "المساحة المستخدمة للمحاضرات المحمّلة: $usedStorageMb MB",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Wi-Fi Only Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "تنزيل المحاضرات عبر الواي فاي فقط",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "حماية باقة بيانات الهاتف عند تنزيل الفيديوهات الكبيرة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = wifiOnlyDownload,
                            onCheckedChange = onToggleWifiOnlyDownload,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SettingsGold,
                                checkedTrackColor = SettingsDeepGreen
                            )
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Auto-delete watched lectures toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "حذف المحاضرات المشاهَدة تلقائياً",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "توفير المساحة تلقائياً فور إكمال مشاهدة المحاضرة (🟢 مكتملة)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoDeleteWatched,
                            onCheckedChange = onToggleAutoDeleteWatched,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SettingsGold,
                                checkedTrackColor = SettingsDeepGreen
                            )
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Strict Permanent 1-Device Lock Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "حماية الحساب: مرتبط بهذا الجهاز فقط (1/1 — جهاز واحد لكل إيميل)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4. Appearance, Font Size, Language & Role Switch
        item(key = "settings_appearance_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "المظهر والخط واللغة",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )

                    // Dark Mode Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_dark_mode_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "الوضع الليلي (Dark Mode)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isDarkTheme) "مفعّل حالياً لراحة العينين" else "الوضع النهاري الكريمي مفعّل",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = onToggleDarkTheme,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SettingsGold,
                                checkedTrackColor = SettingsDeepGreen
                            ),
                            modifier = Modifier.testTag("settings_dark_mode_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Font Size Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "حجم الخط:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1.0f to "عادي", 1.1f to "كبير", 1.2f to "كبير جداً").forEach { (scale, label) ->
                                FilterChip(
                                    selected = kotlin.math.abs(fontSizeScale - scale) < 0.04f,
                                    onClick = { onChangeFontSizeScale(scale) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SettingsDeepGreen,
                                        selectedLabelColor = SettingsGold
                                    )
                                )
                            }
                        }
                    }

                    // Language Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "لغة الواجهة:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("ar" to "العربية (RTL)", "en" to "English").forEach { (code, label) ->
                                FilterChip(
                                    selected = appLanguage == code,
                                    onClick = { onChangeAppLanguage(code) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SettingsDeepGreen,
                                        selectedLabelColor = SettingsGold
                                    )
                                )
                            }
                        }
                    }

                    if (isVerifiedAdmin) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        OutlinedButton(
                            onClick = onSwitchRoleMode,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("settings_switch_role_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (activeRole == UserRoleMode.ADMIN) {
                                    "التبديل إلى معاينة واجهة الطالب"
                                } else {
                                    "العودة إلى لوحة الدكتور / المسؤول"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 5. Soft Red Logout Button (#FBEDEA background, #B04A44 text)
        item(key = "settings_logout_button") {
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SettingsDangerBg,
                    contentColor = SettingsDangerText
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SettingsDangerText.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("settings_sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = SettingsDangerText
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "تسجيل الخروج من الحساب",
                    style = MaterialTheme.typography.titleMedium,
                    color = SettingsDangerText,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
