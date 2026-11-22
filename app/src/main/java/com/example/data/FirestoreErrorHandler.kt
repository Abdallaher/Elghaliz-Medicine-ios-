package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun maskEmailForLog(rawEmail: String?): String {
    if (rawEmail.isNullOrBlank()) return "redacted"
    val clean = rawEmail.trim()
    val atIdx = clean.indexOf('@')
    if (atIdx <= 0) return "${clean.take(2)}***"
    val localPart = clean.substring(0, atIdx)
    val domainPart = clean.substring(atIdx)
    val visiblePrefix = localPart.take(2).ifEmpty { "*" }
    return "$visiblePrefix***$domainPart"
}

fun maskUidForLog(rawUid: String?): String {
    if (rawUid.isNullOrBlank()) return "redacted"
    val clean = rawUid.trim()
    return "${clean.take(4)}***"
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        null
    }
    val currentUser = auth?.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", maskEmailForLog(provider.email))
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", maskUidForLog(currentUser?.uid))
        put("email", maskEmailForLog(currentUser?.email))
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

/**
 * تحويل الأخطاء التقنية المزعجة (OOM، Timeout، انقطاع الشبكة) إلى رسائل عربية واضحة ومطمئنة للمستخدم
 */
fun formatArabicUserFriendlyError(error: Throwable?): String {
    if (error == null) return "حدث خطأ غير متوقع. يرجى إعادة المحاولة."
    val msg = error.message ?: ""
    val lowerMsg = msg.lowercase()
    val errType = error::class.java.simpleName.lowercase()

    return when {
        // خطأ نفاد الذاكرة OOM
        error is OutOfMemoryError || lowerMsg.contains("outofmemory") || lowerMsg.contains("failed to allocate") ->
            "تعذر معالجة الفيديو في الذاكرة المؤقتة بسبب حجمه الكبير. تم تفعيل نظام الرفع المتدفق الخفيف لتفادي استهلاك الذاكرة."

        // خطأ انتهاء المهلة (Timed out)
        lowerMsg.contains("timed out") || lowerMsg.contains("timeout") || errType.contains("timeout") ->
            "انتهت مهلة الانتظار بسبب بطء أو تذبذب اتصال الإنترنت. تم حفظ تقدمك المرفوع ويمكنك الضغط على 'استئناف' لمتابعة الرفع."

        // خطأ تجاوز الحجم أو المساحة
        (lowerMsg.contains("الحد الأقصى") || lowerMsg.contains("تجاوز")) && (lowerMsg.contains("حجم") || lowerMsg.contains("ميغابايت") || lowerMsg.contains("mb")) ->
            msg

        // خطأ الإيقاف اليدوي
        lowerMsg.contains("paused_by_user") || lowerMsg.contains("إيقاف مؤقت") ->
            "تم إيقاف عملية الرفع مؤقتاً بواسطة المستخدم. يمكنك استئناف الرفع في أي وقت."

        // خطأ انقطاع الاتصال بالإنترنت
        lowerMsg.contains("unable to resolve host") ||
        lowerMsg.contains("network") ||
        lowerMsg.contains("connection") ||
        lowerMsg.contains("econnreset") ||
        lowerMsg.contains("socket") ->
            "انقطع اتصال الإنترنت أثناء الرفع. تم حفظ تقدمك المرفوع، وجاري إعادة المحاولة تلقائياً عند عودة الاتصال."

        // خطأ الصلاحيات
        lowerMsg.contains("permission_denied") || lowerMsg.contains("permission-denied") ->
            "ليس لديك الصلاحية الكافية لإتمام هذه العملية. يرجى التأكد من تسجيل الدخول بحساب المشرف/الدكتور المعتمد."

        // إذا كانت الرسالة بالعربي مسبقاً ومفهومة
        msg.isNotBlank() && msg.any { it in '\u0600'..'\u06FF' } ->
            msg

        // رسالة عامة مطمئنة
        else ->
            "حدث عائق أثناء نقل البيانات عبر الشبكة. تم حفظ تقدمك ويمكنك استئناف الرفع في أي وقت."
    }
}
