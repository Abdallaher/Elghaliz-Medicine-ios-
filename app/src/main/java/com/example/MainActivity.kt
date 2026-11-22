package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.AppUpdateInfo
import com.example.data.model.Category
import com.example.data.model.SessionPreferences
import com.example.data.model.UserRoleMode
import com.example.data.repository.ElghalizRepository
import com.example.ui.components.MedicalVideoPlayerDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CoursePlayerScreen
import com.example.ui.screens.DownloadsAndAdminScreen
import com.example.ui.screens.ExamsScreen
import com.example.ui.screens.LecturesLibraryScreen
import com.example.ui.screens.PendingApprovalScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.signOut
import androidx.lifecycle.lifecycleScope
import com.example.data.storage.GoogleDrive5TbVaultManager
import com.example.ui.theme.ElghalizMedicineTheme
import com.example.ui.viewmodel.ElghalizViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // حماية المحتوى الطبي ومنع لقطات وتسجيل الشاشة 100%
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        // تسخين تصريح Google Drive مسبقاً (Zero-Latency Token Pre-Warming) في الخلفية فور تشغيل التطبيق
        // مع تأخير عشوائي بسيط (0–1500ms) لمنع ضرب السيرفر في نفس الثانية عند دخول 400 طالب
        val appCtx = applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            GoogleDrive5TbVaultManager.seedCachedTokenIfEmpty(
                SessionPreferences.getLastKnownDriveToken(appCtx)
            )
            runCatching { com.example.ui.components.MediaStreamCache.getOrCreate(appCtx) }
            if (!Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
                kotlinx.coroutines.delay(GoogleDrive5TbVaultManager.computeStartupBackgroundJitterMs())
                if (!GoogleDrive5TbVaultManager.isCircuitBreakerOpen()) {
                    GoogleDrive5TbVaultManager.fetchUploadParamsFromAppsScript(isBackgroundRequest = true)
                        .onSuccess { params ->
                            if (params.accessToken.isNotBlank()) {
                                SessionPreferences.saveLastKnownDriveToken(
                                    appCtx,
                                    params.accessToken,
                                    params.folderId
                                )
                            }
                        }
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var isDarkMode by rememberSaveable {
                mutableStateOf(SessionPreferences.isDarkMode(context))
            }

            ElghalizMedicineTheme(darkTheme = isDarkMode) {
                ElghalizAppRoot(
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = {
                        val nextMode = !isDarkMode
                        isDarkMode = nextMode
                        SessionPreferences.saveDarkMode(context, nextMode)
                    }
                )
            }
        }
    }
}

@Composable
fun ElghalizAppRoot(
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val isRobolectric = remember { Build.FINGERPRINT.equals("robolectric", ignoreCase = true) }
    val initialSavedSession = remember { SessionPreferences.getSavedSession(context) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var offlineSessionFallback by remember {
        mutableStateOf(
            if (!isRobolectric &&
                initialSavedSession.autoSignInEnabled &&
                (initialSavedSession.uid.isNotBlank() || initialSavedSession.email.isNotBlank())
            ) {
                initialSavedSession
            } else {
                null
            }
        )
    }
    var selectedLoginRole by rememberSaveable { mutableStateOf(initialSavedSession.roleMode) }
    var sessionGeneration by remember { mutableIntStateOf(0) }

    var showEntrySplash by rememberSaveable { mutableStateOf(!isRobolectric) }
    var splashTriggerCount by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(splashTriggerCount) {
        if (!isRobolectric && showEntrySplash) {
            kotlinx.coroutines.delay(650L)
            showEntrySplash = false
        }
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            val previousUid = currentUser?.uid
            currentUser = user
            if (user != null) {
                sessionGeneration++
                if (previousUid == null && !isRobolectric) {
                    showEntrySplash = true
                    splashTriggerCount++
                }
                SessionPreferences.saveSession(
                    context = context,
                    email = user.email ?: "",
                    displayName = user.displayName ?: "",
                    roleMode = selectedLoginRole,
                    autoSignInEnabled = true,
                    uid = user.uid
                )
                if (!isRobolectric) {
                    offlineSessionFallback = SessionPreferences.getSavedSession(context)
                }
            } else {
                val latestSaved = SessionPreferences.getSavedSession(context)
                if (isRobolectric || !latestSaved.autoSignInEnabled) {
                    offlineSessionFallback = null
                }
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val user = currentUser
        val savedOffline = offlineSessionFallback
        val activeUid = user?.uid ?: savedOffline?.uid?.takeIf { it.isNotBlank() }
            ?: savedOffline?.email?.takeIf { it.isNotBlank() }?.let {
                "offline_${com.example.data.model.SecurityHashUtil.sha256Hex(it).take(20)}"
            }
        val activeEmail = user?.email?.takeIf { it.isNotBlank() } ?: savedOffline?.email.orEmpty()
        val activeDisplayName = user?.displayName?.takeIf { it.isNotBlank() } ?: savedOffline?.displayName.orEmpty()

        if (activeUid == null) {
            AuthScreen(
                selectedRole = selectedLoginRole,
                onRoleSelected = {
                    selectedLoginRole = it
                    SessionPreferences.saveRoleOnly(context, it)
                },
                onAuthSuccess = {
                    sessionGeneration++
                    val loggedIn = Firebase.auth.currentUser
                    currentUser = loggedIn
                    if (loggedIn != null) {
                        SessionPreferences.saveSession(
                            context = context,
                            email = loggedIn.email ?: "",
                            displayName = loggedIn.displayName ?: "",
                            roleMode = selectedLoginRole,
                            autoSignInEnabled = true,
                            uid = loggedIn.uid
                        )
                        if (!isRobolectric) {
                            offlineSessionFallback = SessionPreferences.getSavedSession(context)
                        }
                    }
                    if (!isRobolectric) {
                        showEntrySplash = true
                        splashTriggerCount++
                    }
                },
                isDarkMode = isDarkMode,
                onToggleDarkMode = onToggleDarkMode
            )
        } else {
            LaunchedEffect(activeUid, activeEmail, selectedLoginRole) {
                SessionPreferences.saveSession(
                    context = context,
                    email = activeEmail,
                    displayName = activeDisplayName,
                    roleMode = selectedLoginRole,
                    autoSignInEnabled = true,
                    uid = activeUid
                )
            }

            key(activeUid, selectedLoginRole, sessionGeneration) {
                val viewModel: ElghalizViewModel = viewModel(
                    key = "${activeUid}_${selectedLoginRole.name}_$sessionGeneration",
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY]) {
                                "APPLICATION_KEY missing from CreationExtras"
                            }
                            val databaseId = app.getString(R.string.firestore_database_id)
                            val db = FirebaseFirestore.getInstance(databaseId)
                            val vaultRoot = File(app.filesDir, "offline_vault")
                            val repository = ElghalizRepository(db, vaultRoot, app.applicationContext)
                            ElghalizViewModel(
                                repository = repository,
                                currentUserId = activeUid,
                                currentUserEmail = activeEmail,
                                currentUserDisplayName = activeDisplayName,
                                initialRoleChoice = selectedLoginRole,
                                appContext = app.applicationContext
                            )
                        }
                    }
                )

                MainMedicalWorkspace(
                    viewModel = viewModel,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onSignOutComplete = {
                        SessionPreferences.markExplicitSignOut(context)
                        offlineSessionFallback = null
                        currentUser = null
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showEntrySplash,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(380)) + scaleOut(targetScale = 1.04f, animationSpec = tween(380))
        ) {
            MedicalSplashEntryScreen()
        }
    }
}

@Composable
fun MedicalSplashEntryScreen() {
    var startReveal by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        startReveal = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "medical_splash_pulse")
    val heartbeatScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 680, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_heartbeat_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 680, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val ecgSweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ecg_sweep_progress"
    )

    val entryScale by animateFloatAsState(
        targetValue = if (startReveal) 1f else 0.82f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "entry_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF042F22),
                        Color(0xFF075940),
                        Color(0xFF0A6E4F)
                    )
                )
            )
            .testTag("medical_splash_entry_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .scale(entryScale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing Elghaliz Medicine Icon with Heartbeat Scale
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(124.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(heartbeatScale * 1.12f)
                        .clip(CircleShape)
                        .background(Color(0xFF42E6B5).copy(alpha = glowAlpha * 0.35f))
                )
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(heartbeatScale)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF10B981),
                                    Color(0xFF059669)
                                )
                            )
                        )
                        .border(2.5.dp, Color(0xFFA7F3D0), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalHospital,
                        contentDescription = "Elghaliz Medicine",
                        tint = Color.White,
                        modifier = Modifier.size(50.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Animated Green ECG Heartbeat Pulse Waveform
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(64.dp)
            ) {
                val w = size.width
                val h = size.height
                val midY = h * 0.54f

                val ecgPoints = listOf(
                    0.00f to 0.00f,
                    0.18f to 0.00f,
                    0.23f to -0.14f,
                    0.28f to 0.00f,
                    0.34f to 0.00f,
                    0.38f to 0.18f,
                    0.44f to -0.78f,
                    0.50f to 0.42f,
                    0.55f to 0.00f,
                    0.64f to 0.00f,
                    0.71f to -0.24f,
                    0.79f to 0.00f,
                    1.00f to 0.00f
                )

                val basePath = Path().apply {
                    moveTo(0f, midY)
                    for ((nx, ny) in ecgPoints) {
                        lineTo(nx * w, midY + ny * (h * 0.48f))
                    }
                }

                // Subtle background ECG trace
                drawPath(
                    path = basePath,
                    color = Color(0xFF34D399).copy(alpha = 0.24f),
                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Active sweeping glowing ECG pulse
                val activeEndX = (ecgSweepProgress * w).coerceIn(0f, w)
                val activePath = Path()
                var prevX = 0f
                var prevY = midY
                activePath.moveTo(0f, midY)
                var tipX = 0f
                var tipY = midY

                for (i in 1 until ecgPoints.size) {
                    val (nx, ny) = ecgPoints[i]
                    val px = nx * w
                    val py = midY + ny * (h * 0.48f)
                    if (px <= activeEndX) {
                        activePath.lineTo(px, py)
                        tipX = px
                        tipY = py
                        prevX = px
                        prevY = py
                    } else {
                        val segLen = (px - prevX).coerceAtLeast(0.001f)
                        val fraction = ((activeEndX - prevX) / segLen).coerceIn(0f, 1f)
                        tipX = prevX + (px - prevX) * fraction
                        tipY = prevY + (py - prevY) * fraction
                        activePath.lineTo(tipX, tipY)
                        break
                    }
                }

                drawPath(
                    path = activePath,
                    color = Color(0xFF6EE7B7).copy(alpha = 0.45f),
                    style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = activePath,
                    color = Color(0xFFA7F3D0),
                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                drawCircle(
                    color = Color(0xFF34D399).copy(alpha = 0.45f),
                    radius = 14f,
                    center = Offset(tipX, tipY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = Offset(tipX, tipY)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            AnimatedVisibility(
                visible = startReveal,
                enter = fadeIn(animationSpec = tween(520)) +
                    slideInVertically(animationSpec = tween(520)) { it / 3 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Elghaliz Medicine",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "أهلاً وسهلاً بك في التطبيق الغليظ ~",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                        color = Color(0xFFA7F3D0),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("splash_welcome_phrase")
                    )
                }
            }
        }
    }
}

@Composable
fun MainMedicalWorkspace(
    viewModel: ElghalizViewModel,
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    onSignOutComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    // Request POST_NOTIFICATIONS permission on Android 13+ for smart notifications
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
        ) {
            kotlinx.coroutines.delay(1800L)
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runCatching {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val activeRole by viewModel.activeRole.collectAsStateWithLifecycle()
    val isVerifiedAdmin by viewModel.isVerifiedAdmin.collectAsStateWithLifecycle()
    val isPrimaryAdmin by viewModel.isPrimaryAdmin.collectAsStateWithLifecycle()
    val isCheckingRole by viewModel.isCheckingRole.collectAsStateWithLifecycle()
    val studentApprovalStatus by viewModel.studentApprovalStatus.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val openedCourseId by viewModel.openedCourseId.collectAsStateWithLifecycle()
    val activePlayingLecture by viewModel.activePlayingLecture.collectAsStateWithLifecycle()
    val isPreparingVideo by viewModel.isPreparingVideo.collectAsStateWithLifecycle()
    val preparingVideoProgress by viewModel.preparingVideoProgress.collectAsStateWithLifecycle()
    val isUploadingLecture by viewModel.isUploadingLecture.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val uploadTaskState by viewModel.uploadTaskState.collectAsStateWithLifecycle()
    val isUploadingExam by viewModel.isUploadingExam.collectAsStateWithLifecycle()
    val examUploadProgress by viewModel.examUploadProgress.collectAsStateWithLifecycle()
    val downloadingExamIds by viewModel.downloadingExamIds.collectAsStateWithLifecycle()
    val downloadingLectureIds by viewModel.downloadingLectureIds.collectAsStateWithLifecycle()
    val deletingLectureIds by viewModel.deletingLectureIds.collectAsStateWithLifecycle()
    val downloadProgressMap by viewModel.downloadProgressMap.collectAsStateWithLifecycle()
    val downloadStatesMap by viewModel.downloadStatesMap.collectAsStateWithLifecycle()

    val categoriesState by viewModel.categoriesState.collectAsStateWithLifecycle()
    val lecturesState by viewModel.lecturesState.collectAsStateWithLifecycle()
    val examsState by viewModel.examsState.collectAsStateWithLifecycle()
    val downloadsState by viewModel.downloadsState.collectAsStateWithLifecycle()
    val adminsList by viewModel.adminsList.collectAsStateWithLifecycle()
    val studentRequestsList by viewModel.studentRequestsList.collectAsStateWithLifecycle()
    val myCourseRequestsList by viewModel.myCourseRequestsList.collectAsStateWithLifecycle()
    val allCourseRequestsList by viewModel.allCourseRequestsList.collectAsStateWithLifecycle()
    val centralDriveConfig by viewModel.centralDriveConfig.collectAsStateWithLifecycle()
    val isActivatingDriveVault by viewModel.isActivatingDriveVault.collectAsStateWithLifecycle()
    val registeredDevices by viewModel.registeredDevices.collectAsStateWithLifecycle()
    val appUpdateInfo by viewModel.appUpdateInfo.collectAsStateWithLifecycle()
    val isPublishingAppUpdate by viewModel.isPublishingAppUpdate.collectAsStateWithLifecycle()
    val appUpdatePublishProgress by viewModel.appUpdatePublishProgress.collectAsStateWithLifecycle()
    val isDownloadingAppUpdate by viewModel.isDownloadingAppUpdate.collectAsStateWithLifecycle()
    val appUpdateDownloadProgress by viewModel.appUpdateDownloadProgress.collectAsStateWithLifecycle()
    val downloadedUpdateApkFile by viewModel.downloadedUpdateApkFile.collectAsStateWithLifecycle()

    val currentAppVersionCode = remember(context) {
        runCatching {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(pkgInfo).coerceAtLeast(1L)
        }.getOrDefault(1L)
    }
    var dismissedOptionalUpdateVersion by rememberSaveable { mutableStateOf(0L) }

    val isAdminMode = activeRole == UserRoleMode.ADMIN && isVerifiedAdmin

    // Mandatory Student Approval Gate (First Gate: App Access)
    if (!isVerifiedAdmin && studentApprovalStatus != "approved") {
        if (isCheckingRole || studentApprovalStatus == "loading") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "جاري التحقق من حالة حسابك في Elghaliz Medicine...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            PendingApprovalScreen(
                studentEmail = viewModel.currentUserEmail,
                studentName = viewModel.currentUserDisplayName,
                approvalStatus = studentApprovalStatus,
                bannerMessage = bannerMessage,
                onRefreshStatus = viewModel::refreshStudentApprovalStatus,
                onTryVerifyAsAdmin = viewModel::requestAdminAccess,
                onSignOut = {
                    viewModel.prepareForSignOut()
                    signOut(
                        context = context,
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = onSignOutComplete
                    )
                }
            )
        }
        return
    }

    if (selectedTab != 0) {
        BackHandler {
            selectedTab = 0
        }
    }

    val downloadedLectureIds = ((downloadsState as? UiState.Success)?.data ?: emptyList())
        .map { it.lectureId }
        .toSet()

    val totalPendingRequestsCount = remember(studentRequestsList, allCourseRequestsList) {
        studentRequestsList.count { it.status == "pending" } +
            allCourseRequestsList.count { it.status == "pending" }
    }

    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            kotlinx.coroutines.delay(3000L)
            viewModel.clearBannerMessage()
        }
    }

    LaunchedEffect(selectedTab) {
        if (bannerMessage != null) {
            viewModel.clearBannerMessage()
        }
        if (selectedTab != 0 && openedCourseId != null) {
            viewModel.closeCourse()
        }
    }

    val categoriesList = (categoriesState as? UiState.Success<List<Category>>)?.data ?: emptyList()
    val openedCourse = remember(openedCourseId, categoriesList) {
        categoriesList.firstOrNull { it.id == openedCourseId }
    }

    if (selectedTab == 0 && openedCourse != null && activePlayingLecture == null) {
        val allLectures = (lecturesState as? UiState.Success)?.data ?: emptyList()
        CoursePlayerScreen(
            category = openedCourse,
            allLectures = allLectures,
            downloadsState = downloadsState,
            downloadingLectureIds = downloadingLectureIds,
            deletingLectureIds = deletingLectureIds,
            downloadProgressMap = downloadProgressMap,
            downloadStatesMap = downloadStatesMap,
            isAdminMode = isAdminMode,
            userEmail = viewModel.currentUserEmail,
            bannerMessage = bannerMessage,
            onDismissBanner = viewModel::clearBannerMessage,
            onBackClick = viewModel::closeCourse,
            onDeleteLecture = viewModel::deleteLecture,
            onDownloadInAppOnly = viewModel::startOrResumeLectureDownload,
            onPauseDownload = viewModel::pauseLectureDownload,
            onCancelDownload = { lec -> viewModel.cancelLectureDownload(lec.id, lec.title) }
        )
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Surface(
                color = Color(0xFF0F3D2E),
                shape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
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
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFC9A24D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalHospital,
                                    contentDescription = null,
                                    tint = Color(0xFF0F3D2E),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Elghaliz Medicine",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = viewModel.currentUserEmail.ifBlank { "abdallahxh909@gmail.com" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFE9F0E8).copy(alpha = 0.85f),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Dark Mode Toggle
                            Surface(
                                color = Color(0x26FFFFFF),
                                shape = CircleShape,
                                onClick = onToggleDarkMode,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("theme_mode_toggle_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = if (isDarkMode) "تفعيل المظهر الأبيض الفاتح" else "تفعيل المظهر الداكن",
                                        tint = Color(0xFFC9A24D),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            // Role Switcher Badge
                            Surface(
                                color = if (isAdminMode) Color(0xFF1F6B52) else Color(0x2EFFFFFF),
                                shape = RoundedCornerShape(50),
                                onClick = {
                                    if (isAdminMode) {
                                        SessionPreferences.saveRoleOnly(context, UserRoleMode.STUDENT)
                                        viewModel.switchToStudentView()
                                    } else {
                                        SessionPreferences.saveRoleOnly(context, UserRoleMode.ADMIN)
                                        viewModel.switchToAdminView()
                                    }
                                },
                                modifier = Modifier.testTag("switch_role_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isAdminMode) Icons.Default.AdminPanelSettings else Icons.Default.School,
                                        contentDescription = null,
                                        tint = Color(0xFFC9A24D),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isAdminMode) "مسؤول / دكتور" else "طالب مفعّل",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "تبديل الواجهة",
                                        tint = Color(0xFFC9A24D),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            // Sign Out Button
                            IconButton(
                                onClick = {
                                    viewModel.prepareForSignOut()
                                    signOut(
                                        context = context,
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onSignOutComplete = onSignOutComplete
                                    )
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "تسجيل الخروج",
                                    tint = Color.White.copy(alpha = 0.88f)
                                )
                            }
                        }
                    }

                    if (bannerMessage != null) {
                        Surface(
                            color = Color(0x33C9A24D),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            onClick = { viewModel.clearBannerMessage() }
                        ) {
                            Text(
                                text = bannerMessage ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Floating Bottom Nav: Deep green rounded bar (radius 28dp) floating 14dp above bottom edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF0F3D2E),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tab 0: المحاضرات
                        val tab0Selected = selectedTab == 0
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (tab0Selected) Color(0xFFC9A24D) else Color.Transparent,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.testTag("nav_tab_library")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (tab0Selected) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                                    contentDescription = "المحاضرات",
                                    tint = if (tab0Selected) Color(0xFF0F3D2E) else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                                if (tab0Selected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "المحاضرات",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F3D2E)
                                    )
                                }
                            }
                        }

                        // Tab 1: الاختبارات
                        val tab1Selected = selectedTab == 1
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (tab1Selected) Color(0xFFC9A24D) else Color.Transparent,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.testTag("nav_tab_exams")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (tab1Selected) Icons.Filled.PictureAsPdf else Icons.Outlined.PictureAsPdf,
                                    contentDescription = "الاختبارات",
                                    tint = if (tab1Selected) Color(0xFF0F3D2E) else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                                if (tab1Selected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "الاختبارات",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F3D2E)
                                    )
                                }
                            }
                        }

                        // Tab 2: المدراء والطلبات / التنزيلات
                        val tab2Selected = selectedTab == 2
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (tab2Selected) Color(0xFFC9A24D) else Color.Transparent,
                            onClick = { selectedTab = 2 },
                            modifier = Modifier.testTag("nav_tab_downloads_admins")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (isAdminMode && totalPendingRequestsCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = Color.White
                                            ) {
                                                Text("$totalPendingRequestsCount")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isAdminMode) {
                                            if (tab2Selected) Icons.Filled.AdminPanelSettings else Icons.Outlined.AdminPanelSettings
                                        } else {
                                            if (tab2Selected) Icons.Filled.DownloadForOffline else Icons.Outlined.DownloadForOffline
                                        },
                                        contentDescription = if (isAdminMode) "المدراء والطلبات" else "التنزيلات",
                                        tint = if (tab2Selected) Color(0xFF0F3D2E) else Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                if (tab2Selected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isAdminMode) "المدراء والطلبات" else "التنزيلات",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F3D2E)
                                    )
                                }
                            }
                        }

                        // Tab 3: الإعدادات
                        val tab3Selected = selectedTab == 3
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (tab3Selected) Color(0xFFC9A24D) else Color.Transparent,
                            onClick = { selectedTab = 3 },
                            modifier = Modifier.testTag("nav_tab_settings")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (tab3Selected) Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = "الإعدادات",
                                    tint = if (tab3Selected) Color(0xFF0F3D2E) else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                                if (tab3Selected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "الإعدادات",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F3D2E)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // تبديل فوري ومباشر في 0 ملي ثانية بدون أي أنيميشن انتقالي بطيء (Zero Lag)
            when (selectedTab) {
                0 -> LecturesLibraryScreen(
                    isAdminMode = isAdminMode,
                    categoriesState = categoriesState,
                    lecturesState = lecturesState,
                    myCourseRequestsList = myCourseRequestsList,
                    isUploadingLecture = isUploadingLecture,
                    uploadProgress = uploadProgress,
                    uploadTaskState = uploadTaskState,
                    onPauseUpload = viewModel::pauseLectureUpload,
                    onResumeUpload = viewModel::resumeLectureUpload,
                    onCancelUpload = viewModel::cancelLectureUpload,
                    onAddCategory = viewModel::createCategory,
                    onUploadLecture = { title, desc, catId, catName, uri, dur, size ->
                        viewModel.uploadLecture(title, desc, catId, catName, uri, dur, size)
                    },
                    onOpenCourse = { cat -> viewModel.openCourse(cat.id) },
                    onRequestCourseEnrollment = viewModel::requestCourseEnrollment
                )

                1 -> ExamsScreen(
                    isAdminMode = isAdminMode,
                    categoriesState = categoriesState,
                    examsState = examsState,
                    myCourseRequestsList = myCourseRequestsList,
                    isUploadingExam = isUploadingExam,
                    examUploadProgress = examUploadProgress,
                    downloadingExamIds = downloadingExamIds,
                    doctorDisplayName = viewModel.currentUserDisplayName,
                    onUploadExamPdf = { title, desc, catId, catName, fileName, uri, startMs, endMs, schedLabel ->
                        viewModel.uploadExamPdf(
                            title = title,
                            description = desc,
                            categoryId = catId,
                            categoryName = catName,
                            pdfFileName = fileName,
                            pdfSourceUri = uri,
                            startTimeMillis = startMs,
                            endTimeMillis = endMs,
                            scheduleLabel = schedLabel
                        )
                    },
                    onDownloadExamPdfToPhone = { exam, onResolved ->
                        viewModel.downloadUnencryptedExamPdfToPhone(exam, onResolved)
                    },
                    onDeleteExam = viewModel::deleteExam
                )

                2 -> DownloadsAndAdminScreen(
                    isAdminMode = isAdminMode,
                    isPrimaryAdmin = isPrimaryAdmin,
                    downloadsState = downloadsState,
                    lecturesState = lecturesState,
                    downloadStatesMap = downloadStatesMap,
                    myCourseRequestsList = myCourseRequestsList,
                    adminsList = adminsList,
                    studentRequestsList = studentRequestsList,
                    courseRequestsList = allCourseRequestsList,
                    categoriesState = categoriesState,
                    onPlayOfflineDownload = viewModel::openDownloadedLecturePlayer,
                    onRemoveOfflineDownload = viewModel::removeOfflineDownload,
                    onPauseDownload = viewModel::pauseLectureDownload,
                    onResumeDownload = viewModel::startOrResumeLectureDownload,
                    onCancelDownload = viewModel::cancelLectureDownload,
                    onApproveStudent = viewModel::approveStudentRequest,
                    onSuspendStudent = viewModel::rejectOrSuspendStudentRequest,
                    onApproveCourseRequest = viewModel::approveCourseRequest,
                    onRejectOrSuspendCourseRequest = viewModel::rejectOrSuspendCourseRequest,
                    onSetDirectStudentCourseAccess = viewModel::setDirectStudentCourseAccess,
                    onAddAdminEmail = viewModel::addAdminEmail,
                    onRemoveAdminEntry = viewModel::removeAdminEntry,
                    onDeleteCategory = viewModel::deleteCategory,
                    appUpdateInfo = appUpdateInfo,
                    isPublishingAppUpdate = isPublishingAppUpdate,
                    appUpdatePublishProgress = appUpdatePublishProgress,
                    onPublishAppUpdateApk = viewModel::publishAppUpdateApk
                )

                else -> SettingsScreen(
                    userId = viewModel.currentUserId,
                    fullName = viewModel.currentUserDisplayName,
                    email = viewModel.currentUserEmail,
                    activeRole = activeRole,
                    isVerifiedAdmin = isVerifiedAdmin,
                    isPrimaryAdmin = isPrimaryAdmin,
                    centralDriveConfig = centralDriveConfig,
                    isActivatingDriveVault = isActivatingDriveVault,
                    onActivateDriveVault = viewModel::activateCentral5TbDriveVault,
                    onDriveVaultMessage = viewModel::reportDriveVaultAuthMessage,
                    categories = categoriesList,
                    myCourseRequestsList = myCourseRequestsList,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onFullDataWipe = viewModel::executeProductionCleanBoot,
                    registeredDevices = registeredDevices,
                    onSignOutFromAllOtherDevices = viewModel::signOutFromAllOtherDevices,
                    onSignOutClick = {
                        viewModel.prepareForSignOut()
                        signOut(
                            context = context,
                            credentialManager = credentialManager,
                            scope = scope,
                            onSignOutComplete = onSignOutComplete
                        )
                    }
                )
            }
        }
    }

    // Built-in Medical Video Player Dialog (Widescreen + Landscape Fullscreen + Original Quality)
    val playingLecture = activePlayingLecture
    if (playingLecture != null) {
        MedicalVideoPlayerDialog(
            lecture = playingLecture,
            isPreparingVideo = isPreparingVideo,
            preparingVideoProgress = preparingVideoProgress,
            isDownloadedInApp = downloadedLectureIds.contains(playingLecture.id) || playingLecture.categoryId == "offline",
            isDownloadingNow = downloadingLectureIds.contains(playingLecture.id),
            downloadProgressPercent = downloadProgressMap[playingLecture.id] ?: 0,
            userEmail = viewModel.currentUserEmail,
            onDownloadInAppClick = { viewModel.startOrResumeLectureDownload(playingLecture) },
            onDismiss = viewModel::closeLecturePlayer
        )
    }

    // In-App APK Auto-Updater Dialog (Student / User View)
    val latestUpdate = appUpdateInfo
    val shouldShowUpdateDialog = latestUpdate != null &&
        latestUpdate.apkDriveFileId.isNotBlank() &&
        latestUpdate.latestVersionCode > currentAppVersionCode &&
        (latestUpdate.forceUpdate || dismissedOptionalUpdateVersion < latestUpdate.latestVersionCode)

    if (shouldShowUpdateDialog && latestUpdate != null) {
        InAppApkUpdateDialog(
            updateInfo = latestUpdate,
            isDownloading = isDownloadingAppUpdate,
            downloadProgress = appUpdateDownloadProgress,
            downloadedApkFile = downloadedUpdateApkFile,
            onStartDownloadClick = {
                viewModel.startAppUpdateDownload(latestUpdate.apkDriveFileId)
            },
            onDismissOptional = {
                if (!latestUpdate.forceUpdate) {
                    dismissedOptionalUpdateVersion = latestUpdate.latestVersionCode
                }
            }
        )
    }
}

@Composable
private fun InAppApkUpdateDialog(
    updateInfo: AppUpdateInfo,
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadedApkFile: File?,
    onStartDownloadClick: () -> Unit,
    onDismissOptional: () -> Unit
) {
    val context = LocalContext.current
    val isRobolectric = remember { Build.FINGERPRINT.equals("robolectric", ignoreCase = true) }
    var installStatusNote by remember { mutableStateOf<String?>(null) }

    val triggerApkInstall: (File) -> Unit = { apkFile ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !isRobolectric &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            installStatusNote = "يرجى تفعيل خيار \"السماح بتثبيت التطبيقات من هذا المصدر\" ثم العودة للضغط على \"تثبيت التحديث الآن\"."
            runCatching {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
            }
        } else {
            runCatching {
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (!isRobolectric) {
                    context.startActivity(installIntent)
                }
                installStatusNote = "تم فتح مثبت النظام لتحديث التطبيق مباشرة فوق النسخة الحالية."
            }.onFailure { err ->
                installStatusNote = "تعذر فتح مثبت الحزمة: ${err.localizedMessage ?: ""}"
            }
        }
    }

    LaunchedEffect(downloadedApkFile) {
        if (downloadedApkFile != null && downloadedApkFile.exists()) {
            triggerApkInstall(downloadedApkFile)
        }
    }

    Dialog(
        onDismissRequest = {
            if (!updateInfo.forceUpdate && !isDownloading) {
                onDismissOptional()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.forceUpdate && !isDownloading,
            dismissOnClickOutside = !updateInfo.forceUpdate && !isDownloading
        )
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFFAF7F0),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("in_app_update_dialog")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header in Elghaliz deep green (#0F3D2E) and gold (#C9A24D)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F3D2E))
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFC9A24D)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = Color(0xFF0F3D2E),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "يتوفر تحديث جديد (الإصدار ${updateInfo.latestVersionName})",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (updateInfo.forceUpdate) "تحديث إجباري للمنصة" else "تحديث رسمي متاح الآن",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFC9A24D),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = Color(0xFF0F3D2E).copy(alpha = 0.08f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✓ يُثبّت التحديث مباشرة فوق التطبيق الحالي دون حذف المحاضرات المحملة (.elgdrm) ودون المساس بتسجيل جهازك.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF0F3D2E),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    if (updateInfo.releaseNotes.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "ما الجديد في هذا الإصدار:",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF0F3D2E),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = updateInfo.releaseNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF2B3A34)
                            )
                        }
                    }

                    if (isDownloading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "جاري تنزيل التحديث...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF0F3D2E),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${downloadProgress.coerceIn(0, 100)}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF0F3D2E),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("app_update_download_progress_text")
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (downloadProgress.coerceIn(0, 100)) / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(9.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = Color(0xFF0F3D2E),
                                trackColor = Color(0xFFC9A24D).copy(alpha = 0.28f)
                            )
                        }
                    }

                    if (!installStatusNote.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFFC9A24D).copy(alpha = 0.22f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = installStatusNote ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF0F3D2E),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!updateInfo.forceUpdate && !isDownloading) {
                            OutlinedButton(
                                onClick = onDismissOptional,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("dismiss_optional_update_button")
                            ) {
                                Text(
                                    text = "لاحقاً",
                                    color = Color(0xFF0F3D2E),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (downloadedApkFile != null && downloadedApkFile.exists()) {
                                    triggerApkInstall(downloadedApkFile)
                                } else {
                                    onStartDownloadClick()
                                }
                            },
                            enabled = !isDownloading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0F3D2E),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(48.dp)
                                .testTag("start_app_update_download_button")
                        ) {
                            Text(
                                text = when {
                                    isDownloading -> "جاري التنزيل (${downloadProgress.coerceIn(0, 100)}%)..."
                                    downloadedApkFile != null && downloadedApkFile.exists() -> "تثبيت التحديث الآن"
                                    else -> "تحديث الآن"
                                },
                                color = Color(0xFFC9A24D),
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

