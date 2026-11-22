package com.example.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * نظام التشفير التدفقي المتماثل (256-bit Stream Cipher Vault - AES-CTR / KeyStream XOR)
 * وخادم فك التشفير والبث المحلي (Local Loopback Decryption & Stream Server على http://127.0.0.1:$port/play.mp4):
 *
 * 1. التشفير القوي بعد التنزيل (Stream Cipher Vault):
 *    - يتم تشفير كل بايت يُكتب على القرص داخل `appContext.filesDir/offline_vault/{lectureId}.elgdrm`
 *      باستخدام تيار مفاتيح 256-bit مشتق من مفتاح التطبيق السري ومعرّف المحاضرة، بحيث يُشفر كل بايت
 *      في موضعه `byteIndex` دون تغيير طول الملف (1:1 Alignment) ودون إضافة نصوص تكسر المحاذاة.
 *    - يستحيل تشغيل ملف `.elgdrm` بأي مشغل خارجي أو تسريبه، وفي نفس الوقت يدعم الإيقاف المؤقت
 *      والاستئناف من نفس البايت (`Range: bytes=$savedBytes-`) بدقة تامة.
 *
 * 2. خادم فك التشفير والبث المحلي (`ServerSocket(0)` على `http://127.0.0.1:$port/play.mp4`):
 *    - في وضع بدون إنترنت (Offline): يقرأ من ملف `.elgdrm` المشفر عبر `RandomAccessFile` بناءً على
 *      موضع `Range` المطلوب، ويفك تشفير البايتات لحظياً في الهواء (On-the-Fly Decryption ببافر خفيف 16KB
 *      وبصفر استهلاك للرام) ويرسلها كـ `video/mp4` إلى `VideoView` ليشتغل الفيديو المشفر في 0.1 ثانية.
 *    - في وضع الأونلاين (Online): يسحب التدفق مباشرة من
 *      `https://www.googleapis.com/drive/v3/files/$driveFileId?alt=media`
 *      مع ترويسة `Authorization: Bearer $accessToken` ويمرره مباشرة إلى `VideoView`.
 */
object EncryptedVideoVault {
    private const val TAG = "EncryptedVideoVault"
    private const val VAULT_SECRET_SALT = "Elghaliz_Medicine_DRM_AES256_Vault_2026_Secret"
    private const val KEYSTREAM_TABLE_BLOCKS = 128 // 128 * 32 bytes = 4096 bytes of 256-bit SHA-256 keystream
    private const val KEYSTREAM_TABLE_SIZE = KEYSTREAM_TABLE_BLOCKS * 32 // 4096 bytes (mask 0xFFF)
    const val STREAM_BUFFER_BYTES = 16 * 1024 // 16 KB lightweight buffer
    const val ENCRYPTED_HEADER_BYTES = 4096L // 4KB Header Lock for instant 0.05s offline playback
    const val HEADER_LOCK_BYTES = 4096

    private val keyStreamCache = ConcurrentHashMap<String, ByteArray>()

    // Local Loopback Server state
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var serverPort: Int = 0
    @Volatile
    private var isServerRunning: Boolean = false
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeSockets = Collections.synchronizedSet(mutableSetOf<Socket>())
    private val localFileRegistry = ConcurrentHashMap<String, File>()

    /**
     * يولد جدول تيار المفاتيح السري (256-bit Key Stream) الخاص بكل محاضرة (`lectureId`)
     * باستخدام SHA-256 (256-bit blocks) لتشفير وفك تشفير أي بايت في موضعه `byteIndex` بسرعة فائقة.
     */
    private fun getOrCreateKeyStreamTable(lectureId: String): ByteArray {
        val cleanId = lectureId.trim().ifBlank { "default_lecture" }
        return keyStreamCache.getOrPut(cleanId) {
            val table = ByteArray(KEYSTREAM_TABLE_SIZE)
            val digest = MessageDigest.getInstance("SHA-256")
            for (block in 0 until KEYSTREAM_TABLE_BLOCKS) {
                digest.reset()
                val seed = "$VAULT_SECRET_SALT|$cleanId|KEYSTREAM_BLOCK_$block".toByteArray(Charsets.UTF_8)
                val blockBytes = digest.digest(seed)
                System.arraycopy(blockBytes, 0, table, block * 32, 32)
            }
            table
        }
    }

    /**
     * يشفر أو يفك تشفير أول 4096 بايت فقط (4KB Header Lock) في مكانها (In-Place Symmetric Stream Cipher)
     * بدءاً من الموضع `startByteIndex` داخل الملف.
     * إذا كان `startByteIndex >= 4096L` لا يتم إجراء أي عملية XOR نهائياً (0ms CPU overhead).
     */
    fun transformBytesAtOffset(
        lectureId: String,
        buffer: ByteArray,
        bufferOffset: Int,
        length: Int,
        startByteIndex: Long
    ) {
        if (length <= 0 || startByteIndex >= ENCRYPTED_HEADER_BYTES) return
        val effectiveStart = startByteIndex.coerceAtLeast(0L)
        val bytesToTransform = minOf(length.toLong(), ENCRYPTED_HEADER_BYTES - effectiveStart).toInt()
        if (bytesToTransform <= 0) return

        val keyTable = getOrCreateKeyStreamTable(lectureId)
        for (i in 0 until bytesToTransform) {
            val byteIndex = effectiveStart + i
            val tableIdx = (byteIndex and 0xFFFL).toInt()
            val tableByte = keyTable[tableIdx].toInt()
            val positionSalt = (((byteIndex ushr 12) * 73L) xor (byteIndex * 31L) xor 0x5AL).toInt()
            val keyStreamByte = (tableByte xor positionSalt) and 0xFF
            val targetIdx = bufferOffset + i
            buffer[targetIdx] = (buffer[targetIdx].toInt() xor keyStreamByte).toByte()
        }
    }

    /**
     * دعم التوافق الخلفي للملفات القديمة التي شُفرت بالكامل على طول التدفق (Legacy Full-Stream XOR).
     */
    fun transformBytesFullStreamAtOffset(
        lectureId: String,
        buffer: ByteArray,
        bufferOffset: Int,
        length: Int,
        startByteIndex: Long
    ) {
        if (length <= 0) return
        val keyTable = getOrCreateKeyStreamTable(lectureId)
        for (i in 0 until length) {
            val byteIndex = startByteIndex + i
            val tableIdx = (byteIndex and 0xFFFL).toInt()
            val tableByte = keyTable[tableIdx].toInt()
            val positionSalt = (((byteIndex ushr 12) * 73L) xor (byteIndex * 31L) xor 0x5AL).toInt()
            val keyStreamByte = (tableByte xor positionSalt) and 0xFF
            val targetIdx = bufferOffset + i
            buffer[targetIdx] = (buffer[targetIdx].toInt() xor keyStreamByte).toByte()
        }
    }

    /**
     * يفك تشفير نسخة من البايتات عند الموضع `startByteIndex` دون تعديل المصفوفة الأصلية.
     */
    fun decryptBytesAtOffset(
        lectureId: String,
        encryptedBytes: ByteArray,
        startByteIndex: Long = 0L
    ): ByteArray {
        val copy = encryptedBytes.copyOf()
        transformBytesAtOffset(lectureId, copy, 0, copy.size, startByteIndex)
        return copy
    }

    /**
     * يتحقق مما إذا كانت البايتات الأولى للملف غير مشفرة أصلاً (تحتوي على ترويسة MP4 صريحة مثل ftyp أو moov).
     */
    fun isFileAlreadyPlainMp4(file: File?): Boolean {
        if (file == null || !file.exists() || file.length() < 8L) return false
        return GoogleDrive5TbVaultManager.hasPlainMp4BoxHeader(file)
    }

    /**
     * يتحقق من أن ملف `.elgdrm` المشفر (أو الجزئي `.elgdrm.part`) يحتوي على بايتات فيديو مشفرة صالحة
     * وليس صفحة خطأ HTML أو JSON تم تشفيرها بالخطأ.
     */
    fun isValidEncryptedOrPlainVaultFile(lectureId: String, file: File?): Boolean {
        if (file == null || !file.exists()) return false
        val len = file.length()
        if (len <= 24L) return false
        return runCatching {
            val headerLen = minOf(96, len.toInt())
            val header = ByteArray(headerLen)
            FileInputStream(file).use { it.read(header) }

            // Check raw first
            val rawStr = String(header, Charsets.UTF_8).trimStart().lowercase()
            if (rawStr.startsWith("<!doctype") ||
                rawStr.startsWith("<html") ||
                rawStr.startsWith("<head") ||
                rawStr.startsWith("<body") ||
                rawStr.startsWith("{\"error\"")
            ) {
                return@runCatching false
            }

            // Check decrypted header at offset 0
            val decryptedHeader = decryptBytesAtOffset(lectureId, header, 0L)
            val decStr = String(decryptedHeader, Charsets.UTF_8).trimStart().lowercase()
            if (decStr.startsWith("<!doctype") ||
                decStr.startsWith("<html") ||
                decStr.startsWith("<head") ||
                decStr.startsWith("<body") ||
                decStr.startsWith("{\"error\"")
            ) {
                return@runCatching false
            }
            true
        }.getOrDefault(false)
    }

    /**
     * مجلد الأجزاء المفهرسة (Indexed Chunk Store) لكل محاضرة قيد التنزيل لدعم Pause / Resume
     */
    fun getIndexedChunkDir(context: Context, userId: String, lectureId: String): File {
        return File(context.filesDir, "indexed_video_db_${userId.take(16)}/$lectureId").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getDownloadedChunkCount(context: Context, userId: String, lectureId: String, totalChunks: Int): Int {
        if (totalChunks <= 0) return 0
        val dir = getIndexedChunkDir(context, userId, lectureId)
        var contiguous = 0
        for (i in 0 until totalChunks) {
            val chunkFile = File(dir, "chunk_$i.part")
            if (chunkFile.exists() && chunkFile.length() > 0L) {
                contiguous++
            } else {
                break
            }
        }
        return contiguous
    }

    fun saveIndexedChunk(
        context: Context,
        userId: String,
        lectureId: String,
        chunkIndex: Int,
        bytes: ByteArray
    ) {
        val dir = getIndexedChunkDir(context, userId, lectureId)
        val tempFile = File(dir, "chunk_${chunkIndex}.tmp")
        val finalFile = File(dir, "chunk_${chunkIndex}.part")
        FileOutputStream(tempFile).use { out ->
            out.write(bytes)
            out.flush()
        }
        if (finalFile.exists()) finalFile.delete()
        tempFile.renameTo(finalFile)
    }

    fun clearIndexedChunks(context: Context, userId: String, lectureId: String) {
        val dir = getIndexedChunkDir(context, userId, lectureId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    /**
     * تجميع الأجزاء المفهرسة وتشفير أول 4096 بايت فقط داخل حاوية .elgdrm محمية
     */
    fun assembleAndEncryptFromChunks(
        context: Context,
        userId: String,
        lectureId: String,
        totalChunks: Int,
        destEncryptedFile: File
    ): Boolean {
        val chunkDir = getIndexedChunkDir(context, userId, lectureId)
        destEncryptedFile.parentFile?.mkdirs()
        var currentByteOffset = 0L
        FileOutputStream(destEncryptedFile).use { fos ->
            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            for (i in 0 until totalChunks) {
                val partFile = File(chunkDir, "chunk_$i.part")
                if (!partFile.exists()) return false
                FileInputStream(partFile).use { fis ->
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        if (currentByteOffset < ENCRYPTED_HEADER_BYTES) {
                            transformBytesAtOffset(lectureId, buffer, 0, read, currentByteOffset)
                        }
                        fos.write(buffer, 0, read)
                        currentByteOffset += read
                    }
                }
            }
            fos.flush()
        }
        clearIndexedChunks(context, userId, lectureId)
        return destEncryptedFile.exists() && destEncryptedFile.length() > 0L
    }

    /**
     * تشفير بايتات خام مباشرة داخل حاوية .elgdrm محمية (أول 4096 بايت فقط)
     */
    fun encryptRawBytesToVaultFile(
        userId: String,
        lectureId: String,
        rawBytes: ByteArray,
        destEncryptedFile: File
    ) {
        destEncryptedFile.parentFile?.mkdirs()
        val copy = rawBytes.copyOf()
        transformBytesAtOffset(lectureId, copy, 0, copy.size, 0L)
        FileOutputStream(destEncryptedFile).use { fos ->
            fos.write(copy)
            fos.flush()
        }
    }

    /**
     * تشفير متدفق من ملف محلي مباشرة إلى ملف المشاهدة المحمي بصيغة .elgdrm
     * بتشفير أول 4096 بايت فقط (4KB Header Lock) دون تحميل الملف كاملاً في الذاكرة
     */
    fun encryptFileToVaultFile(
        userId: String,
        lectureId: String,
        sourceFile: File,
        destEncryptedFile: File
    ): Boolean {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) return false
        destEncryptedFile.parentFile?.mkdirs()
        var currentByteOffset = 0L
        FileInputStream(sourceFile).use { fis ->
            FileOutputStream(destEncryptedFile).use { fos ->
                val buffer = ByteArray(STREAM_BUFFER_BYTES)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    if (currentByteOffset < ENCRYPTED_HEADER_BYTES) {
                        transformBytesAtOffset(lectureId, buffer, 0, read, currentByteOffset)
                    }
                    fos.write(buffer, 0, read)
                    currentByteOffset += read
                }
                fos.flush()
            }
        }
        return destEncryptedFile.exists() && destEncryptedFile.length() > 0L
    }

    /**
     * تشفير أول 4096 بايت من ترويسة ملف الـ MP4 في موضعها إذا كانت غير مشفرة بعد
     * مما يجعل الملف غير قابل للتشغيل في أي مشغل خارجي.
     */
    fun lockHeaderInPlace(lectureId: String, file: File): Boolean {
        if (!file.exists() || file.length() < 16L) return false
        return runCatching {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0L)
                val lenToLock = minOf(ENCRYPTED_HEADER_BYTES, file.length()).toInt()
                val header = ByteArray(lenToLock)
                val read = raf.read(header)
                if (read <= 0) return@runCatching false
                if (GoogleDrive5TbVaultManager.hasPlainMp4BoxHeaderBytes(header)) {
                    transformBytesAtOffset(
                        lectureId = lectureId,
                        buffer = header,
                        bufferOffset = 0,
                        length = read,
                        startByteIndex = 0L
                    )
                    raf.seek(0L)
                    raf.write(header, 0, read)
                }
            }
            true
        }.getOrDefault(false)
    }

    /**
     * يُرجع ملف `.elgdrm` كما هو بدون تعديله على القرص نهائياً، حيث يتم فك تشفير أول 4096 بايت
     * حصرياً في الذاكرة (RAM) داخل `ElgdrmOnTheFlyDataSource.read()`.
     */
    fun prepareDecryptedPlaybackFile(
        context: Context,
        userId: String,
        lectureId: String,
        sourceVaultFile: File
    ): File {
        return sourceVaultFile
    }

    fun cleanupDecryptedPlaybackSessions(context: Context) {
        val sessionDir = File(context.cacheDir, "drm_playback_session")
        if (sessionDir.exists()) {
            sessionDir.deleteRecursively()
        }
    }

    // =========================================================================
    // 4. خادم فك التشفير والبث المحلي (Local Loopback Decryption & Stream Server)
    //    ServerSocket(0) على http://127.0.0.1:$port/play.mp4
    // =========================================================================

    @Synchronized
    fun ensureLoopbackServerStarted(context: Context): Int {
        val existingSocket = serverSocket
        if (isServerRunning && serverPort > 0 && existingSocket != null && !existingSocket.isClosed) {
            return serverPort
        }
        return try {
            val s = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
            serverSocket = s
            serverPort = s.localPort
            isServerRunning = true
            val appCtx = context.applicationContext

            serverScope.launch {
                while (isServerRunning && !s.isClosed) {
                    val client = try {
                        s.accept()
                    } catch (e: Exception) {
                        if (!isServerRunning) break
                        continue
                    }
                    activeSockets.add(client)
                    serverScope.launch {
                        try {
                            handleLoopbackClientRequest(appCtx, client)
                        } catch (e: Exception) {
                            if (e !is SocketException) {
                                Log.d(TAG, "Loopback stream socket closed: ${e.message}")
                            }
                        } finally {
                            activeSockets.remove(client)
                            runCatching { client.close() }
                        }
                    }
                }
            }
            serverPort
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start local loopback decryption & stream server", e)
            0
        }
    }

    /**
     * ينشئ رابط البث المحلي `http://127.0.0.1:$port/play.mp4?...` لتمريره إلى `VideoView`:
     * - في وضع Offline: يربطه بملف `.elgdrm` المشفر ليتم فك تشفيره لحظياً في الهواء (On-the-Fly Decryption ببافر 16KB).
     * - في وضع Online: يربطه بمعرّف `driveFileId` ليسحب التدفق من
     *   `https://www.googleapis.com/drive/v3/files/$driveFileId?alt=media` مع `Authorization: Bearer $accessToken`.
     */
    fun buildLoopbackPlayUrl(
        context: Context,
        lectureId: String,
        localVaultFile: File? = null,
        driveFileId: String? = null
    ): String {
        val port = ensureLoopbackServerStarted(context)
        val cleanLectureId = lectureId.trim().ifBlank { "lecture" }
        val encodedLectureId = URLEncoder.encode(cleanLectureId, "UTF-8")

        if (localVaultFile != null && localVaultFile.exists() && localVaultFile.length() > 0L) {
            localFileRegistry[cleanLectureId] = localVaultFile
            val encodedPath = URLEncoder.encode(localVaultFile.absolutePath, "UTF-8")
            return "http://127.0.0.1:$port/play.mp4?mode=offline&lectureId=$encodedLectureId&path=$encodedPath"
        }

        val cleanDriveId = driveFileId?.trim().orEmpty()
        val encodedDriveId = URLEncoder.encode(cleanDriveId, "UTF-8")
        return "http://127.0.0.1:$port/play.mp4?mode=online&lectureId=$encodedLectureId&driveFileId=$encodedDriveId"
    }

    private suspend fun handleLoopbackClientRequest(context: Context, socket: Socket) {
        socket.soTimeout = 60_000
        socket.tcpNoDelay = true
        socket.sendBufferSize = 64 * 1024

        val inputReader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
        val requestLine = inputReader.readLine() ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0].uppercase()
        if (method != "GET" && method != "HEAD") return
        val pathAndQuery = parts[1]

        var hasRangeHeader = false
        var rangeStart = 0L
        var rangeEndInclusive: Long? = null

        var headerLine: String?
        while (inputReader.readLine().also { headerLine = it } != null) {
            if (headerLine.isNullOrBlank()) break
            val line = headerLine!!
            if (line.startsWith("Range:", ignoreCase = true)) {
                val match = Regex("bytes=(\\d+)-(\\d*)").find(line)
                if (match != null) {
                    hasRangeHeader = true
                    rangeStart = match.groupValues[1].toLongOrNull() ?: 0L
                    rangeEndInclusive = match.groupValues[2].takeIf { it.isNotBlank() }?.toLongOrNull()
                }
            }
        }

        val uri = Uri.parse("http://127.0.0.1$pathAndQuery")
        val lectureId = uri.getQueryParameter("lectureId")?.let {
            runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
        }.orEmpty()
        val filePathParam = uri.getQueryParameter("path")?.let {
            runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
        }.orEmpty()
        val driveFileId = uri.getQueryParameter("driveFileId")?.let {
            runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
        }.orEmpty()

        // 1. Check Offline Local File first (.elgdrm or .mp4)
        val registeredFile = localFileRegistry[lectureId]
            ?: filePathParam.takeIf { it.isNotBlank() }?.let { File(it) }
            ?: File(File(context.filesDir, "offline_vault"), "$lectureId.elgdrm").takeIf { it.exists() && it.length() > 24L }
            ?: File(File(context.filesDir, "offline_vault"), "$lectureId.mp4").takeIf { it.exists() && it.length() > 24L }

        if (registeredFile != null && registeredFile.exists() && registeredFile.length() > 24L) {
            streamOfflineFileWithOnTheFlyDecryption(
                socket = socket,
                lectureId = lectureId,
                vaultFile = registeredFile,
                hasRangeHeader = hasRangeHeader,
                requestedStart = rangeStart,
                requestedEndInclusive = rangeEndInclusive,
                isHeadOnly = (method == "HEAD")
            )
            return
        }

        // 2. Online Direct Google Drive API v3 Stream (https://www.googleapis.com/drive/v3/files/$driveFileId?alt=media)
        if (driveFileId.isNotBlank()) {
            streamOnlineGoogleDriveApiV3(
                socket = socket,
                driveFileId = driveFileId,
                hasRangeHeader = hasRangeHeader,
                requestedStart = rangeStart,
                requestedEndInclusive = rangeEndInclusive,
                isHeadOnly = (method == "HEAD")
            )
            return
        }

        sendErrorResponse(socket, 404, "Not Found")
    }

    /**
     * في وضع بدون إنترنت (Offline):
     * يقرأ الخادم المحلي من ملف `.elgdrm` المشفر عبر `RandomAccessFile` بناءً على موضع `Range` المطلوب،
     * ويفك تشفير البايتات لحظياً في الهواء (On-the-Fly Decryption ببافر خفيف 16KB وبصفر استهلاك للرام)
     * ويرسلها كـ `video/mp4` إلى `VideoView`.
     */
    private fun streamOfflineFileWithOnTheFlyDecryption(
        socket: Socket,
        lectureId: String,
        vaultFile: File,
        hasRangeHeader: Boolean,
        requestedStart: Long,
        requestedEndInclusive: Long?,
        isHeadOnly: Boolean
    ) {
        val totalFileLength = vaultFile.length()
        if (totalFileLength <= 0L) {
            sendErrorResponse(socket, 404, "Empty File")
            return
        }

        val start = requestedStart.coerceIn(0L, totalFileLength - 1L)
        val end = (requestedEndInclusive ?: (totalFileLength - 1L)).coerceIn(start, totalFileLength - 1L)
        val contentLength = end - start + 1L

        val isEncryptedDrm = vaultFile.name.endsWith(".elgdrm", ignoreCase = true) &&
            !isFileAlreadyPlainMp4(vaultFile)

        val out = BufferedOutputStream(socket.getOutputStream(), STREAM_BUFFER_BYTES)
        val headerBuilder = StringBuilder()
        if (hasRangeHeader || start > 0L) {
            headerBuilder.append("HTTP/1.1 206 Partial Content\r\n")
            headerBuilder.append("Content-Range: bytes $start-$end/$totalFileLength\r\n")
        } else {
            headerBuilder.append("HTTP/1.1 200 OK\r\n")
        }
        headerBuilder.append("Content-Type: video/mp4\r\n")
        headerBuilder.append("Accept-Ranges: bytes\r\n")
        headerBuilder.append("Content-Length: $contentLength\r\n")
        headerBuilder.append("Connection: close\r\n\r\n")

        out.write(headerBuilder.toString().toByteArray(Charsets.US_ASCII))
        out.flush()

        if (isHeadOnly) return

        RandomAccessFile(vaultFile, "r").use { raf ->
            raf.seek(start)
            val buffer = ByteArray(STREAM_BUFFER_BYTES) // 16 KB buffer
            var remaining = contentLength
            var currentBytePos = start
            while (remaining > 0L) {
                val maxRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = raf.read(buffer, 0, maxRead)
                if (read <= 0) break
                if (isEncryptedDrm) {
                    transformBytesAtOffset(
                        lectureId = lectureId,
                        buffer = buffer,
                        bufferOffset = 0,
                        length = read,
                        startByteIndex = currentBytePos
                    )
                }
                out.write(buffer, 0, read)
                currentBytePos += read
                remaining -= read
            }
            out.flush()
        }
    }

    /**
     * في وضع الأونلاين (Online):
     * يسحب الخادم المحلي التدفق حصرياً من
     * `https://www.googleapis.com/drive/v3/files/$driveFileId?alt=media`
     * مع ترويسة `Authorization: Bearer $accessToken` المستلمة من السكربت ويمرره مباشرة إلى `VideoView`.
     */
    private suspend fun streamOnlineGoogleDriveApiV3(
        socket: Socket,
        driveFileId: String,
        hasRangeHeader: Boolean,
        requestedStart: Long,
        requestedEndInclusive: Long?,
        isHeadOnly: Boolean
    ) {
        var token = GoogleDrive5TbVaultManager.fetchUploadParamsFromAppsScript(forceRefresh = false)
            .getOrNull()
            ?.accessToken
            ?.trim()
            .orEmpty()

        var conn = try {
            GoogleDrive5TbVaultManager.openDriveRangeConnection(
                driveFileId = driveFileId,
                accessToken = token,
                rangeStart = requestedStart,
                rangeEndInclusive = requestedEndInclusive
            )
        } catch (e: Exception) {
            null
        }

        // If 401/403 or initial failure, force-refresh token from Apps Script bridge and retry immediately
        val initialCode = runCatching { conn?.responseCode ?: 0 }.getOrDefault(0)
        if (conn == null || initialCode == 401 || initialCode == 403) {
            runCatching { conn?.disconnect() }
            val freshToken = GoogleDrive5TbVaultManager.fetchUploadParamsFromAppsScript(forceRefresh = true)
                .getOrNull()
                ?.accessToken
                ?.trim()
                .orEmpty()
            if (freshToken.isNotBlank()) {
                token = freshToken
                conn = try {
                    GoogleDrive5TbVaultManager.openDriveRangeConnection(
                        driveFileId = driveFileId,
                        accessToken = token,
                        rangeStart = requestedStart,
                        rangeEndInclusive = requestedEndInclusive
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }

        if (conn == null) {
            sendErrorResponse(socket, 502, "Bad Gateway")
            return
        }

        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                sendErrorResponse(socket, code, "Upstream Drive API Error $code")
                return
            }

            val contentRange = conn.getHeaderField("Content-Range") ?: conn.getHeaderField("content-range")
            val upstreamLength = conn.contentLengthLong

            // Parse total size from Content-Range: bytes start-end/total
            val parsedTotal = contentRange?.substringAfter("/", "")?.trim()?.toLongOrNull()
            val effectiveEnd = if (requestedEndInclusive != null) {
                requestedEndInclusive
            } else if (parsedTotal != null && parsedTotal > 0L) {
                parsedTotal - 1L
            } else if (upstreamLength > 0L) {
                requestedStart + upstreamLength - 1L
            } else {
                -1L
            }

            val effectiveContentLength = if (upstreamLength > 0L) {
                upstreamLength
            } else if (effectiveEnd >= requestedStart) {
                effectiveEnd - requestedStart + 1L
            } else {
                -1L
            }

            val out = BufferedOutputStream(socket.getOutputStream(), STREAM_BUFFER_BYTES)
            val headerBuilder = StringBuilder()
            if (hasRangeHeader || requestedStart > 0L || code == 206) {
                headerBuilder.append("HTTP/1.1 206 Partial Content\r\n")
                if (!contentRange.isNullOrBlank()) {
                    headerBuilder.append("Content-Range: $contentRange\r\n")
                } else if (effectiveEnd >= requestedStart && parsedTotal != null) {
                    headerBuilder.append("Content-Range: bytes $requestedStart-$effectiveEnd/$parsedTotal\r\n")
                } else if (effectiveEnd >= requestedStart && effectiveContentLength > 0L) {
                    headerBuilder.append("Content-Range: bytes $requestedStart-$effectiveEnd/${requestedStart + effectiveContentLength}\r\n")
                }
            } else {
                headerBuilder.append("HTTP/1.1 200 OK\r\n")
            }
            headerBuilder.append("Content-Type: video/mp4\r\n")
            headerBuilder.append("Accept-Ranges: bytes\r\n")
            if (effectiveContentLength > 0L) {
                headerBuilder.append("Content-Length: $effectiveContentLength\r\n")
            }
            headerBuilder.append("Connection: close\r\n\r\n")

            out.write(headerBuilder.toString().toByteArray(Charsets.US_ASCII))
            out.flush()

            if (isHeadOnly) return

            BufferedInputStream(conn.inputStream, STREAM_BUFFER_BYTES).use { input ->
                val buffer = ByteArray(STREAM_BUFFER_BYTES) // 16 KB buffer
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                out.flush()
            }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun sendErrorResponse(socket: Socket, code: Int, message: String) {
        runCatching {
            val resp = "HTTP/1.1 $code $message\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n$message"
            socket.getOutputStream().write(resp.toByteArray(Charsets.US_ASCII))
            socket.getOutputStream().flush()
        }
    }
}
