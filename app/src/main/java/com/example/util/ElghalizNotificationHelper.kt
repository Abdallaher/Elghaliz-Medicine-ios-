package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import java.util.concurrent.atomic.AtomicInteger

object ElghalizNotificationHelper {
    private const val CHANNEL_GENERAL_ID = "elghaliz_smart_alerts"
    private const val CHANNEL_PROGRESS_ID = "elghaliz_transfer_progress"
    private val nextId = AtomicInteger(2000)

    @Volatile
    private var channelsCreated = false
    private val lastProgressNotificationMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val lastProgressNotificationPct = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun ensureChannels(context: Context) {
        if (channelsCreated) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val smartAlertsChannel = NotificationChannel(
                CHANNEL_GENERAL_ID,
                "إشعارات المحاضرات والاختبارات والتنزيلات",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات فورية عند رفع محاضرات أو اختبارات جديدة أو اكتمال الرفع والتنزيل"
                enableVibration(true)
            }

            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "تقدم التنزيل والرفع في الخلفية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "شريط تقدم التنزيل والرفع المستمر في الخلفية"
                setSound(null, null)
            }

            manager.createNotificationChannel(smartAlertsChannel)
            manager.createNotificationChannel(progressChannel)
        }
        channelsCreated = true
    }

    private fun canPostNotifications(context: Context): Boolean {
        ensureChannels(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createLaunchPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 1. للطلاب: إشعار فوري للطلاب المشتركين في الكورس عند رفع محاضرة جديدة أو اختبار جديد.
     */
    fun notifyCourseContentPublished(
        context: Context,
        title: String,
        body: String,
        notificationKey: String
    ) {
        if (!canPostNotifications(context)) return
        val notifId = notificationKey.hashCode()
        val builder = NotificationCompat.Builder(context, CHANNEL_GENERAL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createLaunchPendingIntent(context))

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    /**
     * 2. للطالب: إشعار عند اكتمال تنزيل المحاضرة على جهازه بنسبة 100% + تحديث شريط التقدم في الخلفية.
     */
    fun notifyDownloadProgress(
        context: Context,
        lectureId: String,
        lectureTitle: String,
        progressPercent: Int,
        isPaused: Boolean = false
    ) {
        if (!canPostNotifications(context)) return
        val nowMs = System.currentTimeMillis()
        if (!isPaused) {
            val prevPct = lastProgressNotificationPct[lectureId]
            val prevMs = lastProgressNotificationMs[lectureId] ?: 0L
            if (prevPct == progressPercent || (nowMs - prevMs) < 500L) {
                return
            }
        }
        lastProgressNotificationPct[lectureId] = progressPercent
        lastProgressNotificationMs[lectureId] = nowMs

        val notifId = ("dl_$lectureId").hashCode()
        val statusText = if (isPaused) {
            "متوقف مؤقتاً ($progressPercent%)"
        } else {
            "جاري التنزيل ($progressPercent%)"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("تنزيل: $lectureTitle")
            .setContentText(statusText)
            .setProgress(100, progressPercent.coerceIn(0, 100), false)
            .setOngoing(!isPaused && progressPercent in 0..99)
            .setOnlyAlertOnce(true)
            .setContentIntent(createLaunchPendingIntent(context))

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelDownloadNotification(context: Context, lectureId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(("dl_$lectureId").hashCode())
        } catch (_: Exception) {
        }
    }

    fun notifyStudentDownloadCompleted(
        context: Context,
        lectureId: String,
        lectureTitle: String,
        categoryName: String
    ) {
        cancelDownloadNotification(context, lectureId)
        if (!canPostNotifications(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_GENERAL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("اكتمل تنزيل المحاضرة ✅")
            .setContentText("تم حفظ \"$lectureTitle\" ($categoryName) للمشاهدة بدون إنترنت.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "تم حفظ المحاضرة \"$lectureTitle\" ضمن مجلد كورس ($categoryName) بنجاح."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createLaunchPendingIntent(context))

        try {
            NotificationManagerCompat.from(context).notify(nextId.incrementAndGet(), builder.build())
        } catch (_: SecurityException) {
        }
    }

    /**
     * 3. للمسؤول (الدكتور): إشعار عند اكتمال رفع المحاضرة أو الاختبار إلى التطبيق بنجاح بنسبة 100%.
     */
    fun notifyAdminUploadCompleted(
        context: Context,
        itemTitle: String,
        categoryName: String,
        isExam: Boolean = false
    ) {
        if (!canPostNotifications(context)) return
        val typeLabel = if (isExam) "الاختبار" else "المحاضرة"
        val builder = NotificationCompat.Builder(context, CHANNEL_GENERAL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("اكتمل رفع $typeLabel 100% ✅")
            .setContentText("تم نشر \"$itemTitle\" في كورس ($categoryName) وإشعار الطلاب المشتركين.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "تم رفع ونشر $typeLabel \"$itemTitle\" في كورس ($categoryName) بنجاح بنسبة 100%، وتم إرسال تنبيه فوري للطلاب المشتركين."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createLaunchPendingIntent(context))

        try {
            NotificationManagerCompat.from(context).notify(nextId.incrementAndGet(), builder.build())
        } catch (_: SecurityException) {
        }
    }
}
