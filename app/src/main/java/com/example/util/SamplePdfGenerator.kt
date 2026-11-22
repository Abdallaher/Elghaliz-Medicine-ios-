package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object SamplePdfGenerator {

    /**
     * ينشئ ملف اختبار طبي بصيغة PDF حقيقية غير مشفرة جاهز للرفع أو المعاينة
     */
    fun createMedicalExamPdfFile(
        context: Context,
        examTitle: String,
        courseName: String,
        doctorName: String,
        scheduleLabel: String
    ): File {
        val dir = File(context.cacheDir, "generated_exam_pdfs").apply {
            if (!exists()) mkdirs()
        }
        val safeName = examTitle.replace(Regex("[^a-zA-Z0-9أ-ي_\\-]"), "_").take(32).ifBlank { "medical_exam" }
        val outFile = File(dir, "${safeName}_${System.currentTimeMillis()}.pdf")

        runCatching {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Standard
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(13, 107, 82)
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, 595f, 115f, headerPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 20f
                isFakeBoldText = true
            }
            canvas.drawText("Elghaliz Medicine — ورقة اختبار رسمية", 40f, 45f, titlePaint)

            val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(220, 245, 235)
                textSize = 13f
            }
            canvas.drawText("الكورس: $courseName  |  إشراف: $doctorName", 40f, 75f, subHeaderPaint)
            canvas.drawText("موعد الاختبار: $scheduleLabel", 40f, 98f, subHeaderPaint)

            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(13, 107, 82)
                textSize = 16f
                isFakeBoldText = true
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 45, 40)
                textSize = 13f
            }

            canvas.drawText("عنوان الاختبار: $examTitle", 40f, 155f, sectionPaint)

            val questions = listOf(
                "Q1. Explain the primary clinical mechanism and diagnostic markers in $courseName.",
                "    A) First-line cellular pathway   B) Receptor-mediated inhibition",
                "    C) Enzymatic feedback loop       D) Systemic inflammatory cascade",
                "",
                "Q2. A 45-year-old patient presents with acute clinical findings related to $courseName.",
                "    Which therapeutic protocol is most appropriate?",
                "    A) Immediate targeted therapy    B) Conservative monitoring",
                "    C) Broad-spectrum prophylaxis    D) Surgical intervention",
                "",
                "Q3. Compare the pathophysiological stages and clinical prognosis in $courseName.",
                "    Write a concise clinical summary below:",
                "    ____________________________________________________________________",
                "    ____________________________________________________________________",
                "    ____________________________________________________________________"
            )

            var yPos = 195f
            for (line in questions) {
                canvas.drawText(line, 40f, yPos, bodyPaint)
                yPos += 28f
            }

            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 120, 112)
                textSize = 11f
            }
            canvas.drawText("ملف اختبار PDF غير مشفر — متاح للتنزيل المباشر على الهاتف للطلاب المشتركين في الكورس", 40f, 800f, footerPaint)

            pdfDocument.finishPage(page)
            FileOutputStream(outFile).use { fos ->
                pdfDocument.writeTo(fos)
                fos.flush()
            }
            pdfDocument.close()
        }

        if (!outFile.exists() || outFile.length() == 0L) {
            outFile.writeBytes(
                "%PDF-1.7\n%Elghaliz Medicine Official Exam PDF\n%Title: $examTitle\n%Course: $courseName\n%Doctor: $doctorName\n%Schedule: $scheduleLabel\n%%EOF".toByteArray(Charsets.UTF_8)
            )
        }
        return outFile
    }

    /**
     * يحفظ ملف PDF الخاص بالاختبار بشكل غير مشفر ومباشر في مجلد التنزيلات العام على هاتف الطالب (Downloads)
     */
    fun saveUnencryptedPdfToPhoneDownloads(
        context: Context,
        sourcePdfFile: File,
        desiredFileName: String
    ): Result<String> = runCatching {
        val cleanName = if (desiredFileName.lowercase().endsWith(".pdf")) {
            desiredFileName
        } else {
            "$desiredFileName.pdf"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, cleanName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Elghaliz_Exams")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: error("تعذر إنشاء ملف PDF في مجلد التنزيلات")
            resolver.openOutputStream(uri)?.use { out ->
                sourcePdfFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            "Downloads/Elghaliz_Exams/$cleanName"
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadsDir, "Elghaliz_Exams").apply {
                if (!exists()) mkdirs()
            }
            val destFile = File(targetDir, cleanName)
            sourcePdfFile.copyTo(destFile, overwrite = true)
            destFile.absolutePath
        }
    }
}
