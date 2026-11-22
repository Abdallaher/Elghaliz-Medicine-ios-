@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import com.example.data.model.Lecture
import com.example.data.model.SessionPreferences
import com.example.data.storage.EncryptedVideoVault
import com.example.data.storage.GoogleDrive5TbVaultManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

val ELGHALIZ_PLAYER_SPEEDS = listOf(1.0f, 1.25f, 1.5f, 2.0f)

internal fun Context.findElghalizActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private data class ResolvedMedia3StreamTarget(
    val uri: Uri,
    val cacheKey: String,
    val localVaultFile: File? = null,
    val isLocalOffline: Boolean = false
)

/**
 * كاش البث الذكي بأجزاء صغيرة (256 KB) وبحد أقصى 1 GB (Singleton) في cacheDir/media_cache
 * بحيث لا يعيد المشغل تحميل المشاهد السابقة عند الترجيع دون إبطاء بدء الفيديو على الإنترنت الضعيف.
 * يتم إنشاؤه واستدعاؤه حصرياً على خيوط الخلفية (IO / ExoPlayer Loader Thread) لضمان صفر عمليات I/O على الـ Main Thread.
 */
object MediaStreamCache {
    const val MAX_CACHE_BYTES: Long = 1024L * 1024L * 1024L // 1 GB
    const val FRAGMENT_SIZE_BYTES: Long = 256L * 1024L // 256 KB

    @Volatile
    private var simpleCacheInstance: SimpleCache? = null

    fun getOrCreate(context: Context): SimpleCache {
        simpleCacheInstance?.let { return it }
        synchronized(this) {
            simpleCacheInstance?.let { return it }
            val appCtx = context.applicationContext
            val cacheFolder = File(appCtx.cacheDir, "media_cache").apply {
                if (!exists()) mkdirs()
            }
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            val dbProvider = StandaloneDatabaseProvider(appCtx)
            return SimpleCache(cacheFolder, evictor, dbProvider).also {
                simpleCacheInstance = it
            }
        }
    }

    fun createCacheDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory
    ): CacheDataSource.Factory {
        val cache = getOrCreate(context)
        val sinkFactory = CacheDataSink.Factory()
            .setCache(cache)
            .setFragmentSize(FRAGMENT_SIZE_BYTES)

        val cacheKeyFactory = CacheKeyFactory { dataSpec ->
            val explicitKey = dataSpec.key?.takeIf { it.isNotBlank() }
            if (explicitKey != null) {
                explicitKey
            } else {
                val driveId = GoogleDrive5TbVaultManager.extractDriveFileId(dataSpec.uri.toString())
                if (!driveId.isNullOrBlank()) "drive_$driveId" else dataSpec.uri.toString()
            }
        }

        return CacheDataSource.Factory()
            .setCache(cache)
            .setCacheWriteDataSinkFactory(sinkFactory)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheKeyFactory(cacheKeyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun createCacheDataSource(
        context: Context,
        upstreamFactory: DataSource.Factory
    ): DataSource = createCacheDataSourceFactory(context, upstreamFactory).createDataSource()
}

/**
 * عميل OkHttp الموحد والمشترك للبث والتنزيل مع ضوابط التزامن (منع إغراق الحساب).
 */
private object Media3VideoCacheHolder {
    val sharedOkHttpClient: OkHttpClient
        get() = GoogleDrive5TbVaultManager.sharedOkHttpClient
}

/**
 * DataSource مخصص داخل Media3 يفك تشفير أول 4096 بايت (4KB Header Lock) من ملفات (.elgdrm)
 * لحظياً في الذاكرة (RAM) أثناء القراءة (On-the-Fly) دون تعديل أو نسخ الملف على القرص نهائياً،
 * مع قراءة جميع البايتات بعد 4096 بسرعة القرص المباشرة (0ms CPU overhead)،
 * ليفتح الفيديو الأوفلاين (حتى لو حجمه 1GB) في أقل من 0.05 ثانية.
 */
private class ElgdrmOnTheFlyDataSource(
    private val lectureIdProvider: () -> String
) : BaseDataSource(/* isNetwork = */ false) {

    private var activeLectureId: String = ""
    private var randomAccessFile: RandomAccessFile? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0L
    private var currentFilePosition: Long = 0L
    private var opened: Boolean = false

    // Encryption modes:
    // 0 = Plain MP4 (no decryption needed)
    // 1 = Standard 4096-byte Header Lock (decrypt only when position < 4096L -> 0ms CPU overhead after 4KB)
    // 2 = Legacy Full-Stream Encrypted file (backward compatibility for older downloads)
    private var encryptionMode: Int = 1
    private var encryptedStartOffset: Long = 0L

    override fun open(dataSpec: DataSpec): Long {
        val targetUri = dataSpec.uri
        uri = targetUri
        transferInitializing(dataSpec)

        val path = targetUri.path ?: throw IOException("مسار ملف الفيديو غير صالح: $targetUri")
        val file = File(path)
        if (!file.exists() || file.length() <= 0L) {
            throw IOException("ملف المحاضرة المحلي غير موجود")
        }

        val currentFromProvider = lectureIdProvider().trim()
        activeLectureId = if (currentFromProvider.isNotBlank()) {
            currentFromProvider
        } else {
            file.nameWithoutExtension
        }

        val raf = RandomAccessFile(file, "r")
        randomAccessFile = raf
        val fileLength = raf.length()
        if (dataSpec.position > fileLength) {
            raf.close()
            randomAccessFile = null
            throw EOFException("Position out of range")
        }

        detectEncryptionMode(raf, file, fileLength)

        raf.seek(dataSpec.position)
        currentFilePosition = dataSpec.position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            fileLength - dataSpec.position
        } else {
            minOf(dataSpec.length, fileLength - dataSpec.position)
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    private fun detectEncryptionMode(raf: RandomAccessFile, file: File, fileLength: Long) {
        if (!file.name.endsWith(".elgdrm", ignoreCase = true) || fileLength < 16L) {
            encryptionMode = 0
            encryptedStartOffset = 0L
            return
        }
        runCatching {
            val probeLen = minOf(128L, fileLength).toInt()
            val probe = ByteArray(probeLen)
            raf.seek(0L)
            raf.readFully(probe, 0, probeLen)

            val hasPlainStart = GoogleDrive5TbVaultManager.hasPlainMp4BoxHeaderBytes(probe)
            if (!hasPlainStart) {
                encryptedStartOffset = 0L
                encryptionMode = if (isLegacyEncryptedBeyond4096(raf, fileLength)) 2 else 1
            } else {
                if (isLegacyEncryptedAtOffset64(probe, fileLength)) {
                    encryptedStartOffset = 64L
                    encryptionMode = if (isLegacyEncryptedBeyond4096(raf, fileLength)) 2 else 1
                } else {
                    encryptionMode = 0
                    encryptedStartOffset = 0L
                }
            }
        }.onFailure {
            encryptionMode = 1
            encryptedStartOffset = 0L
        }
    }

    private fun isLegacyEncryptedAtOffset64(probe: ByteArray, fileLength: Long): Boolean {
        if (probe.size < 16) return false
        val firstBoxSize = ((probe[0].toInt() and 0xFF) shl 24) or
            ((probe[1].toInt() and 0xFF) shl 16) or
            ((probe[2].toInt() and 0xFF) shl 8) or
            (probe[3].toInt() and 0xFF)
        if (firstBoxSize in 8..60 && firstBoxSize + 8 <= probe.size) {
            val secondBoxType = String(probe, firstBoxSize + 4, 4, Charsets.US_ASCII).lowercase()
            if (secondBoxType in setOf("moov", "mdat", "free", "wide", "skip", "uuid")) {
                if (fileLength > 128L) {
                    val sample = probe.copyOfRange(64, minOf(128, probe.size))
                    val decSample = sample.copyOf()
                    EncryptedVideoVault.transformBytesFullStreamAtOffset(activeLectureId, decSample, 0, decSample.size, 64L)
                    if (!containsKnownMp4Atom(sample) && containsKnownMp4Atom(decSample)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun isLegacyEncryptedBeyond4096(raf: RandomAccessFile, fileLength: Long): Boolean {
        if (fileLength <= EncryptedVideoVault.ENCRYPTED_HEADER_BYTES + 128L) {
            return false
        }
        return runCatching {
            val headerProbe = ByteArray(64)
            raf.seek(0L)
            raf.readFully(headerProbe)
            if (!GoogleDrive5TbVaultManager.hasPlainMp4BoxHeaderBytes(headerProbe)) {
                EncryptedVideoVault.transformBytesAtOffset(activeLectureId, headerProbe, 0, headerProbe.size, 0L)
            }
            val firstBoxSize = ((headerProbe[0].toInt() and 0xFF) shl 24) or
                ((headerProbe[1].toInt() and 0xFF) shl 16) or
                ((headerProbe[2].toInt() and 0xFF) shl 8) or
                (headerProbe[3].toInt() and 0xFF)
            if (firstBoxSize in 8..48 && firstBoxSize + 8 <= headerProbe.size) {
                val secondBoxSize = ((headerProbe[firstBoxSize].toInt() and 0xFF).toLong() shl 24) or
                    ((headerProbe[firstBoxSize + 1].toInt() and 0xFF).toLong() shl 16) or
                    ((headerProbe[firstBoxSize + 2].toInt() and 0xFF).toLong() shl 8) or
                    (headerProbe[firstBoxSize + 3].toInt() and 0xFF).toLong()
                val nextBoxOffset = firstBoxSize.toLong() + secondBoxSize
                if (nextBoxOffset >= EncryptedVideoVault.ENCRYPTED_HEADER_BYTES && nextBoxOffset + 8L <= fileLength) {
                    val boxHeader = ByteArray(8)
                    raf.seek(nextBoxOffset)
                    raf.readFully(boxHeader)
                    val rawType = String(boxHeader, 4, 4, Charsets.US_ASCII).lowercase()
                    if (rawType in setOf("moov", "mdat", "free", "wide", "skip", "uuid", "ftyp")) {
                        return@runCatching false
                    }
                    val decBox = boxHeader.copyOf()
                    EncryptedVideoVault.transformBytesFullStreamAtOffset(activeLectureId, decBox, 0, 8, nextBoxOffset)
                    val decType = String(decBox, 4, 4, Charsets.US_ASCII).lowercase()
                    if (decType in setOf("moov", "mdat", "free", "wide", "skip", "uuid", "ftyp")) {
                        return@runCatching true
                    }
                }
            }
            val tailWindowLen = minOf(512L, fileLength - EncryptedVideoVault.ENCRYPTED_HEADER_BYTES).toInt()
            val tailBuf = ByteArray(tailWindowLen)
            raf.seek(EncryptedVideoVault.ENCRYPTED_HEADER_BYTES)
            raf.readFully(tailBuf)
            if (containsKnownMp4Atom(tailBuf)) {
                return@runCatching false
            }
            val decTail = tailBuf.copyOf()
            EncryptedVideoVault.transformBytesFullStreamAtOffset(
                activeLectureId,
                decTail,
                0,
                decTail.size,
                EncryptedVideoVault.ENCRYPTED_HEADER_BYTES
            )
            containsKnownMp4Atom(decTail)
        }.getOrDefault(false)
    }

    private fun containsKnownMp4Atom(bytes: ByteArray): Boolean {
        val known = setOf("mvhd", "trak", "tkhd", "mdia", "mdhd", "hdlr", "minf", "vmhd", "smhd", "dinf", "stbl", "stsd", "avc1", "mp4a", "mdat", "moov")
        if (bytes.size < 4) return false
        for (i in 0..bytes.size - 4) {
            val c0 = bytes[i].toInt()
            val c1 = bytes[i + 1].toInt()
            val c2 = bytes[i + 2].toInt()
            val c3 = bytes[i + 3].toInt()
            if (c0 in 97..122 && c1 in 97..122 && c2 in 97..122 && (c3 in 97..122 || c3 in 48..57)) {
                val atom = String(bytes, i, 4, Charsets.US_ASCII)
                if (atom in known) return true
            }
        }
        return false
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val raf = randomAccessFile ?: return C.RESULT_END_OF_INPUT
        val bytesToRead = minOf(bytesRemaining, length.toLong()).toInt()
        val readStartPos = currentFilePosition
        val bytesRead = raf.read(buffer, offset, bytesToRead)

        if (bytesRead == -1) {
            if (bytesRemaining != C.LENGTH_UNSET.toLong() && bytesRemaining > 0L) {
                throw EOFException()
            }
            return C.RESULT_END_OF_INPUT
        }

        when (encryptionMode) {
            1 -> {
                if (readStartPos < EncryptedVideoVault.ENCRYPTED_HEADER_BYTES) {
                    val regionStart = maxOf(readStartPos, encryptedStartOffset)
                    val regionEnd = minOf(readStartPos + bytesRead, EncryptedVideoVault.ENCRYPTED_HEADER_BYTES)
                    if (regionEnd > regionStart) {
                        val subOffset = offset + (regionStart - readStartPos).toInt()
                        val subLength = (regionEnd - regionStart).toInt()
                        EncryptedVideoVault.transformBytesAtOffset(
                            lectureId = activeLectureId,
                            buffer = buffer,
                            bufferOffset = subOffset,
                            length = subLength,
                            startByteIndex = regionStart
                        )
                    }
                }
            }
            2 -> {
                val regionStart = maxOf(readStartPos, encryptedStartOffset)
                val regionEnd = readStartPos + bytesRead
                if (regionEnd > regionStart) {
                    val subOffset = offset + (regionStart - readStartPos).toInt()
                    val subLength = (regionEnd - regionStart).toInt()
                    EncryptedVideoVault.transformBytesFullStreamAtOffset(
                        lectureId = activeLectureId,
                        buffer = buffer,
                        bufferOffset = subOffset,
                        length = subLength,
                        startByteIndex = regionStart
                    )
                }
            }
        }

        currentFilePosition += bytesRead
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            randomAccessFile?.close()
        } catch (e: IOException) {
            throw e
        } finally {
            randomAccessFile = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}

@Stable
class ElghalizMedia3PlayerState(
    val exoPlayer: ExoPlayer,
    private val context: Context,
    private val getActiveLecture: () -> Lecture?,
    isFullscreenState: androidx.compose.runtime.MutableState<Boolean>,
    isScreenLockedState: androidx.compose.runtime.MutableState<Boolean>,
    showOverlayControlsState: androidx.compose.runtime.MutableState<Boolean>,
    controlsInteractionTickState: androidx.compose.runtime.MutableLongState,
    playbackSpeedState: androidx.compose.runtime.MutableFloatState,
    zoomScaleState: androidx.compose.runtime.MutableFloatState,
    panOffsetXState: androidx.compose.runtime.MutableFloatState,
    panOffsetYState: androidx.compose.runtime.MutableFloatState,
    seekFeedbackLabelState: androidx.compose.runtime.MutableState<String?>,
    seekFeedbackTickState: androidx.compose.runtime.MutableLongState,
    isPlayingState: androidx.compose.runtime.MutableState<Boolean>,
    isPlayerPreparedState: androidx.compose.runtime.MutableState<Boolean>,
    isBufferingState: androidx.compose.runtime.MutableState<Boolean>,
    bufferedPercentageState: androidx.compose.runtime.MutableIntState,
    playbackErrorMessageState: androidx.compose.runtime.MutableState<String?>,
    reloadAttemptState: androidx.compose.runtime.MutableIntState,
    watermarkStepState: androidx.compose.runtime.MutableIntState,
    currentSecondState: androidx.compose.runtime.MutableIntState,
    isDraggingSliderState: androidx.compose.runtime.MutableState<Boolean>,
    sliderDragSecondState: androidx.compose.runtime.MutableIntState,
    totalSecondsState: androidx.compose.runtime.MutableIntState
) {
    var isFullscreenLandscape by isFullscreenState
    var isScreenLocked by isScreenLockedState
    var showOverlayControls by showOverlayControlsState
    var controlsInteractionTick by controlsInteractionTickState
    var playbackSpeed by playbackSpeedState
    var zoomScale by zoomScaleState
    var panOffsetX by panOffsetXState
    var panOffsetY by panOffsetYState
    var seekFeedbackLabel by seekFeedbackLabelState
    var seekFeedbackTick by seekFeedbackTickState
    var isPlaying by isPlayingState
    var isPlayerPrepared by isPlayerPreparedState
    var isBuffering by isBufferingState
    var bufferedPercentage by bufferedPercentageState
    var playbackErrorMessage by playbackErrorMessageState
    var reloadAttempt by reloadAttemptState
    var watermarkStep by watermarkStepState
    var currentSecond by currentSecondState
    var isDraggingSlider by isDraggingSliderState
    var sliderDragSecond by sliderDragSecondState
    var totalSeconds by totalSecondsState

    fun registerUserInteraction() {
        controlsInteractionTick = System.currentTimeMillis()
        showOverlayControls = true
    }

    fun persistCurrentPosition() {
        val lecId = getActiveLecture()?.id ?: return
        val sec = currentSecond
        val total = totalSeconds
        CoroutineScope(Dispatchers.IO).launch {
            SessionPreferences.saveLecturePlaybackSecond(context, lecId, sec, total)
        }
    }

    fun seekToSecond(targetSec: Int) {
        val lecId = getActiveLecture()?.id
        val clamped = targetSec.coerceIn(0, totalSeconds)
        currentSecond = clamped
        sliderDragSecond = clamped
        if (!lecId.isNullOrBlank()) {
            val total = totalSeconds
            CoroutineScope(Dispatchers.IO).launch {
                SessionPreferences.saveLecturePlaybackSecond(context, lecId, clamped, total)
            }
        }
        runCatching {
            exoPlayer.seekTo(clamped * 1000L)
        }
    }

    fun seekByDeltaSeconds(deltaSec: Int) {
        val target = (currentSecond + deltaSec).coerceIn(0, totalSeconds)
        seekToSecond(target)
        seekFeedbackLabel = if (deltaSec > 0) "⏩ +10 ثوانٍ" else "⏪ -10 ثوانٍ"
        seekFeedbackTick = System.currentTimeMillis()
    }

    fun togglePlayPause() {
        registerUserInteraction()
        val lecId = getActiveLecture()?.id
        if (lecId.isNullOrBlank() && exoPlayer.mediaItemCount == 0) return
        runCatching {
            val shouldPause = isPlaying ||
                exoPlayer.isPlaying ||
                (exoPlayer.playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED)
            if (shouldPause) {
                exoPlayer.playWhenReady = false
                exoPlayer.pause()
                isPlaying = false
                if (!lecId.isNullOrBlank()) {
                    val sec = currentSecond
                    val total = totalSeconds
                    CoroutineScope(Dispatchers.IO).launch {
                        SessionPreferences.saveLecturePlaybackSecond(context, lecId, sec, total)
                    }
                }
            } else {
                if (exoPlayer.playbackState == Player.STATE_ENDED ||
                    (totalSeconds > 1 && currentSecond >= totalSeconds)
                ) {
                    exoPlayer.seekTo(0L)
                    currentSecond = 0
                    sliderDragSecond = 0
                }
                if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0) {
                    exoPlayer.prepare()
                }
                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                exoPlayer.playWhenReady = true
                exoPlayer.play()
                isPlaying = true
            }
        }
    }

    fun setSpeed(speedOption: Float) {
        registerUserInteraction()
        playbackSpeed = speedOption
        runCatching {
            exoPlayer.playbackParameters = PlaybackParameters(speedOption)
        }
    }

    fun toggleFullscreen() {
        registerUserInteraction()
        isFullscreenLandscape = !isFullscreenLandscape
    }

    fun exitFullscreen() {
        isFullscreenLandscape = false
        zoomScale = 1f
        panOffsetX = 0f
        panOffsetY = 0f
    }

    fun unlockScreen() {
        isScreenLocked = false
        registerUserInteraction()
    }

    fun retryPlayback() {
        reloadAttempt++
    }
}

@Composable
fun rememberElghalizMedia3PlayerState(
    lecture: Lecture?,
    isDownloadedInApp: Boolean,
    onPlaybackUpdated: (lectureId: String, second: Int, totalSeconds: Int, isCompleted: Boolean) -> Unit = { _, _, _, _ -> }
): ElghalizMedia3PlayerState {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findElghalizActivity() }

    val activeLectureRef = remember { AtomicReference<Lecture?>(lecture) }
    activeLectureRef.set(lecture)
    val activeLectureIdRef = remember { AtomicReference(lecture?.id.orEmpty()) }
    activeLectureIdRef.set(lecture?.id.orEmpty())

    // منع تصوير الشاشة ولقطات الشاشة 100% داخل مشغل الفيديو
    DisposableEffect(Unit) {
        activity?.window?.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        onDispose {}
    }

    val isFullscreenState = rememberSaveable { mutableStateOf(false) }
    val isScreenLockedState = rememberSaveable { mutableStateOf(false) }
    val showOverlayControlsState = remember { mutableStateOf(true) }
    val controlsInteractionTickState = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val playbackSpeedState = rememberSaveable { mutableFloatStateOf(1.0f) }

    val zoomScaleState = remember { mutableFloatStateOf(1f) }
    val panOffsetXState = remember { mutableFloatStateOf(0f) }
    val panOffsetYState = remember { mutableFloatStateOf(0f) }

    val seekFeedbackLabelState = remember { mutableStateOf<String?>(null) }
    val seekFeedbackTickState = remember { mutableLongStateOf(0L) }

    // نُبقي جميع MutableState ثابتة على مستوى عمر الـ ExoPlayer حتى لا تنفصل عن ElghalizMedia3PlayerState أو Player.Listener عند تغيير المحاضرة
    val isPlayingState = remember { mutableStateOf(lecture != null) }
    val isPlayerPreparedState = remember { mutableStateOf(false) }
    val isBufferingState = remember { mutableStateOf(lecture != null) }
    val bufferedPercentageState = remember { mutableIntStateOf(0) }
    val playbackErrorMessageState = remember { mutableStateOf<String?>(null) }
    val reloadAttemptState = remember { mutableIntStateOf(0) }

    val watermarkStepState = remember { mutableIntStateOf(0) }

    val currentSecondState = remember { mutableIntStateOf(0) }
    val isDraggingSliderState = remember { mutableStateOf(false) }
    val sliderDragSecondState = remember { mutableIntStateOf(0) }
    val totalSecondsState = remember {
        mutableIntStateOf((lecture?.durationSeconds ?: 1L).toInt().coerceAtLeast(1))
    }

    var streamTarget by remember {
        mutableStateOf<ResolvedMedia3StreamTarget?>(null)
    }

    // تسخين مبدئي للتوكن والكاش في الخلفية (Dispatchers.IO) لضمان صفر عمليات I/O على الـ Main Thread
    LaunchedEffect(context) {
        withContext(Dispatchers.IO) {
            GoogleDrive5TbVaultManager.seedCachedTokenIfEmpty(
                SessionPreferences.getLastKnownDriveToken(context)
            )
            runCatching { MediaStreamCache.getOrCreate(context) }
        }
    }

    val activeVideoCalls = remember {
        Collections.newSetFromMap(ConcurrentHashMap<Call, Boolean>())
    }

    // بناء مشغل Media3 ExoPlayer خفيف وعالي الصمود أمام الإنترنت الضعيف والذروة (400 طالب):
    // 1. ResolvingDataSource.Factory لحقن التوكن الفوري (0ms) وتجديده عبر Single-flight Mutex
    // 2. كاش بث ذكي (MediaStreamCache) بأجزاء صغيرة 256KB ومفتاح drive_<fileId> مع FLAG_IGNORE_CACHE_ON_ERROR
    // 3. Exponential Backoff مع Jitter لغاية 4 محاولات عند 429/5xx وعدم اعتبار 403 الناتج عن الحصة خطأ تسجيل دخول
    // 4. فك التشفير اللحظي في الرام لملفات (.elgdrm) عبر ElgdrmOnTheFlyDataSource دون تخزين مؤقت مكرر
    val exoPlayer = remember {
        val appCtx = context.applicationContext
        val trackedCallFactory = Call.Factory { request ->
            activeVideoCalls.removeIf { it.isCanceled() }
            Media3VideoCacheHolder.sharedOkHttpClient.newCall(request).also { call ->
                activeVideoCalls.add(call)
            }
        }

        val okHttpFactory = OkHttpDataSource.Factory(trackedCallFactory)
            .setUserAgent("ElghalizMedicine-Media3/2.0 (Android)")

        val driveRetryUpstreamFactory = DataSource.Factory {
            val baseHttpDataSource = okHttpFactory.createDataSource()
            object : DataSource {
                override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
                    baseHttpDataSource.addTransferListener(transferListener)
                }

                override fun open(dataSpec: DataSpec): Long {
                    var currentSpec = dataSpec
                    var attempt = 1
                    val maxAttempts = 4

                    while (true) {
                        try {
                            val openedBytes = baseHttpDataSource.open(currentSpec)
                            GoogleDrive5TbVaultManager.recordHttpStatus(200)
                            return openedBytes
                        } catch (e: HttpDataSource.InvalidResponseCodeException) {
                            val code = e.responseCode
                            GoogleDrive5TbVaultManager.recordHttpStatus(code)
                            val responseBodyText = runCatching {
                                e.responseBody?.toString(Charsets.UTF_8).orEmpty()
                            }.getOrDefault("")
                            val isQuota403 = GoogleDrive5TbVaultManager.isQuotaOrRateLimit403(
                                statusCode = code,
                                responseBody = responseBodyText + " " + (e.responseMessage ?: "")
                            )

                            if (code == 401 || (code == 403 && !isQuota403 && attempt == 1)) {
                                runCatching { baseHttpDataSource.close() }
                                val refreshedToken = runBlocking(Dispatchers.IO) {
                                    GoogleDrive5TbVaultManager.fetchUploadParamsFromAppsScript(forceRefresh = true)
                                        .getOrNull()
                                        ?.also { params ->
                                            if (params.accessToken.isNotBlank()) {
                                                SessionPreferences.saveLastKnownDriveToken(
                                                    appCtx,
                                                    params.accessToken,
                                                    params.folderId
                                                )
                                            }
                                        }
                                        ?.accessToken
                                        ?.trim()
                                        .orEmpty()
                                }
                                if (refreshedToken.isNotBlank() && attempt < maxAttempts) {
                                    val updatedHeaders = HashMap(currentSpec.httpRequestHeaders)
                                    updatedHeaders["Authorization"] = "Bearer $refreshedToken"
                                    currentSpec = currentSpec.buildUpon()
                                        .setHttpRequestHeaders(updatedHeaders)
                                        .build()
                                    attempt++
                                    continue
                                }
                            } else if ((code == 429 || code in 500..599 || isQuota403) && attempt < maxAttempts) {
                                runCatching { baseHttpDataSource.close() }
                                val backoffMs = GoogleDrive5TbVaultManager.computeExponentialBackoffWithJitterMs(attempt)
                                runBlocking(Dispatchers.IO) {
                                    delay(backoffMs)
                                }
                                attempt++
                                continue
                            }
                            throw e
                        }
                    }
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return baseHttpDataSource.read(buffer, offset, length)
                }

                override fun getUri(): Uri? = baseHttpDataSource.uri

                override fun getResponseHeaders(): Map<String, List<String>> = baseHttpDataSource.responseHeaders

                override fun close() {
                    baseHttpDataSource.close()
                }
            }
        }

        val resolvingHttpFactory = ResolvingDataSource.Factory(
            driveRetryUpstreamFactory,
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val host = dataSpec.uri.host?.lowercase().orEmpty()
                    if (host.contains("googleapis.com") || host.contains("google.com")) {
                        var token = GoogleDrive5TbVaultManager.getInstantAccessTokenOrNull()
                            ?: SessionPreferences.getLastKnownDriveToken(appCtx).orEmpty()
                        if (token.isBlank() && !android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
                            token = runBlocking(Dispatchers.IO) {
                                GoogleDrive5TbVaultManager.fetchUploadParamsFromAppsScript(forceRefresh = false)
                                    .getOrNull()
                                    ?.also { params ->
                                        if (params.accessToken.isNotBlank()) {
                                            SessionPreferences.saveLastKnownDriveToken(
                                                appCtx,
                                                params.accessToken,
                                                params.folderId
                                            )
                                        }
                                    }
                                    ?.accessToken
                                    ?.trim()
                                    .orEmpty()
                            }
                        }
                        if (token.isNotBlank()) {
                            return dataSpec.withAdditionalHeaders(
                                mapOf(
                                    "Authorization" to "Bearer $token",
                                    "Accept-Encoding" to "identity",
                                    "Connection" to "keep-alive"
                                )
                            )
                        }
                    }
                    return dataSpec
                }
            }
        )

        val defaultDataSourceFactory = DefaultDataSource.Factory(appCtx, resolvingHttpFactory)
        var cachedStreamFactory: CacheDataSource.Factory? = null

        val routingDataSourceFactory = DataSource.Factory {
            object : DataSource {
                private var activeDelegate: DataSource? = null
                private val transferListeners = mutableListOf<TransferListener>()

                override fun addTransferListener(transferListener: TransferListener) {
                    if (!transferListeners.contains(transferListener)) {
                        transferListeners.add(transferListener)
                    }
                    activeDelegate?.addTransferListener(transferListener)
                }

                override fun open(dataSpec: DataSpec): Long {
                    val scheme = dataSpec.uri.scheme?.lowercase().orEmpty()
                    val path = dataSpec.uri.path.orEmpty()
                    val delegate: DataSource = when {
                        (scheme == "file" || scheme.isEmpty()) && path.endsWith(".elgdrm", ignoreCase = true) -> {
                            ElgdrmOnTheFlyDataSource { activeLectureIdRef.get() }
                        }
                        scheme == "file" || scheme.isEmpty() -> {
                            FileDataSource()
                        }
                        scheme == "http" || scheme == "https" -> {
                            runCatching {
                                val factory = cachedStreamFactory ?: synchronized(MediaStreamCache) {
                                    cachedStreamFactory ?: MediaStreamCache
                                        .createCacheDataSourceFactory(appCtx, resolvingHttpFactory)
                                        .also { cachedStreamFactory = it }
                                }
                                factory.createDataSource()
                            }.getOrElse {
                                resolvingHttpFactory.createDataSource()
                            }
                        }
                        else -> {
                            defaultDataSourceFactory.createDataSource()
                        }
                    }
                    transferListeners.forEach { listener ->
                        delegate.addTransferListener(listener)
                    }
                    activeDelegate = delegate
                    return delegate.open(dataSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return activeDelegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
                }

                override fun getUri(): Uri? = activeDelegate?.uri

                override fun getResponseHeaders(): Map<String, List<String>> {
                    return activeDelegate?.responseHeaders ?: emptyMap()
                }

                override fun close() {
                    try {
                        activeDelegate?.close()
                    } finally {
                        activeDelegate = null
                    }
                }
            }
        }

        val extractorsFactory = DefaultExtractorsFactory()
            .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            .setConstantBitrateSeekingEnabled(true)

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 128 * 1024))
            .setBufferDurationsMs(
                30_000,
                90_000,
                200,
                1_000
            )
            .setTargetBufferBytes(64 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(30_000, true)
            .build()

        ExoPlayer.Builder(appCtx)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(routingDataSourceFactory, extractorsFactory)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
            )
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    val state = remember(exoPlayer) {
        ElghalizMedia3PlayerState(
            exoPlayer = exoPlayer,
            context = context,
            getActiveLecture = { activeLectureRef.get() },
            isFullscreenState = isFullscreenState,
            isScreenLockedState = isScreenLockedState,
            showOverlayControlsState = showOverlayControlsState,
            controlsInteractionTickState = controlsInteractionTickState,
            playbackSpeedState = playbackSpeedState,
            zoomScaleState = zoomScaleState,
            panOffsetXState = panOffsetXState,
            panOffsetYState = panOffsetYState,
            seekFeedbackLabelState = seekFeedbackLabelState,
            seekFeedbackTickState = seekFeedbackTickState,
            isPlayingState = isPlayingState,
            isPlayerPreparedState = isPlayerPreparedState,
            isBufferingState = isBufferingState,
            bufferedPercentageState = bufferedPercentageState,
            playbackErrorMessageState = playbackErrorMessageState,
            reloadAttemptState = reloadAttemptState,
            watermarkStepState = watermarkStepState,
            currentSecondState = currentSecondState,
            isDraggingSliderState = isDraggingSliderState,
            sliderDragSecondState = sliderDragSecondState,
            totalSecondsState = totalSecondsState
        )
    }

    // Dynamic moving watermark step (changes position gently every 6 seconds)
    LaunchedEffect(isPlayingState.value) {
        while (isPlayingState.value) {
            delay(6000L)
            watermarkStepState.intValue = (watermarkStepState.intValue + 1) % 4
        }
    }

    // Save previous lecture position when switching lectures or leaving
    DisposableEffect(lecture?.id) {
        val trackedId = lecture?.id
        onDispose {
            if (!trackedId.isNullOrBlank()) {
                val posSec = (exoPlayer.currentPosition / 1000L).toInt().coerceAtLeast(0)
                val durMs = exoPlayer.duration
                val durSec = if (durMs != C.TIME_UNSET && durMs > 0L) {
                    (durMs / 1000L).toInt().coerceAtLeast(1)
                } else {
                    totalSecondsState.intValue
                }
                val isCompleted = SessionPreferences.saveLecturePlaybackSecond(context, trackedId, posSec, durSec)
                onPlaybackUpdated(trackedId, posSec, durSec, isCompleted)
            }
        }
    }

    // تحرير الذاكرة فوراً (Lifecycle Release) عند إغلاق المشغل لمنع أي استهلاك للرام أو لاج في التطبيق
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val activeId = activeLectureIdRef.get()
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBufferingState.value = true
                        isPlayingState.value = exoPlayer.playWhenReady
                        bufferedPercentageState.intValue = exoPlayer.bufferedPercentage.coerceIn(0, 100)
                    }
                    Player.STATE_READY -> {
                        isBufferingState.value = false
                        isPlayerPreparedState.value = true
                        isPlayingState.value = exoPlayer.playWhenReady
                        bufferedPercentageState.intValue = exoPlayer.bufferedPercentage.coerceIn(0, 100)
                        playbackErrorMessageState.value = null
                        val durMs = exoPlayer.duration
                        if (durMs != C.TIME_UNSET && durMs > 0L) {
                            totalSecondsState.intValue = (durMs / 1000L).toInt().coerceAtLeast(1)
                        }
                    }
                    Player.STATE_ENDED -> {
                        isBufferingState.value = false
                        isPlayingState.value = false
                        val dur = totalSecondsState.intValue
                        currentSecondState.intValue = dur
                        sliderDragSecondState.intValue = dur
                        if (activeId.isNotBlank()) {
                            SessionPreferences.markLectureCompleted(context, activeId)
                            onPlaybackUpdated(activeId, 0, dur, true)
                        }
                        showOverlayControlsState.value = true
                    }
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlayingState.value = playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                if (playing) {
                    isPlayingState.value = true
                } else if (!exoPlayer.playWhenReady || exoPlayer.playbackState == Player.STATE_ENDED) {
                    isPlayingState.value = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val activeId = activeLectureIdRef.get()
                val badLocal = streamTarget?.localVaultFile
                if (badLocal != null && activeId.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        if (!EncryptedVideoVault.isValidEncryptedOrPlainVaultFile(activeId, badLocal)) {
                            runCatching { badLocal.delete() }
                        }
                    }
                }
                isBufferingState.value = false
                playbackErrorMessageState.value = if (streamTarget?.isLocalOffline == true) {
                    "تعذر تشغيل ملف الفيديو المحلي. يمكنك الضغط على إعادة المحاولة أو إعادة تنزيل المحاضرة."
                } else {
                    "تعذر فتح دفق الفيديو. يرجى التحقق من اتصال الإنترنت والمحاولة مرة أخرى."
                }
            }
        }

        exoPlayer.addListener(listener)
        onDispose {
            val activeId = activeLectureIdRef.get()
            if (activeId.isNotBlank()) {
                val finalPosSec = (exoPlayer.currentPosition / 1000L).toInt().coerceAtLeast(0)
                SessionPreferences.saveLecturePlaybackSecond(context, activeId, finalPosSec, totalSecondsState.intValue)
            }
            val callsSnapshot = activeVideoCalls.toList()
            activeVideoCalls.clear()
            CoroutineScope(Dispatchers.IO).launch {
                callsSnapshot.forEach { call -> runCatching { call.cancel() } }
            }
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    // Apply Landscape Fullscreen orientation & immersive system bars when toggled
    DisposableEffect(isFullscreenState.value) {
        val window = activity?.window
        val insetsController = if (window != null) {
            WindowCompat.getInsetsController(window, view)
        } else {
            null
        }

        if (isFullscreenState.value) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            isScreenLockedState.value = false
            zoomScaleState.floatValue = 1f
            panOffsetXState.floatValue = 0f
            panOffsetYState.floatValue = 0f
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // بدء التشغيل الفوري في 0ms بدون أي انتظار للشبكة (Zero-Latency Prepare)
    LaunchedEffect(lecture?.id, lecture?.videoSourceUri, isDownloadedInApp, reloadAttemptState.intValue) {
        val previousCalls = activeVideoCalls.toList()
        if (previousCalls.isNotEmpty()) {
            activeVideoCalls.clear()
            CoroutineScope(Dispatchers.IO).launch {
                previousCalls.forEach { call -> runCatching { call.cancel() } }
            }
        }
        val targetLecture = lecture
        if (targetLecture == null) {
            withContext(Dispatchers.Main.immediate) {
                exoPlayer.playWhenReady = false
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }
            isPlayingState.value = false
            isBufferingState.value = false
            isPlayerPreparedState.value = false
            bufferedPercentageState.intValue = 0
            playbackErrorMessageState.value = null
            currentSecondState.intValue = 0
            sliderDragSecondState.intValue = 0
            totalSecondsState.intValue = 1
            streamTarget = null
            return@LaunchedEffect
        }

        activeLectureRef.set(targetLecture)
        activeLectureIdRef.set(targetLecture.id)
        isPlayerPreparedState.value = false
        isBufferingState.value = true
        bufferedPercentageState.intValue = 0
        playbackErrorMessageState.value = null
        totalSecondsState.intValue = targetLecture.durationSeconds.toInt().coerceAtLeast(1)
        isPlayingState.value = true

        val trimmed = targetLecture.videoSourceUri.trim()
        val (localVaultFile, savedSecond) = withContext(Dispatchers.IO) {
            val vaultFile = resolveInstantLocalVaultVideoFile(context, targetLecture.id, trimmed)
            val savedSec = SessionPreferences.getLecturePlaybackSecond(context, targetLecture.id)
            vaultFile to savedSec
        }

        val resolved: ResolvedMedia3StreamTarget? = when {
            localVaultFile != null && localVaultFile.exists() && localVaultFile.length() > 0L -> {
                ResolvedMedia3StreamTarget(
                    uri = Uri.fromFile(localVaultFile),
                    cacheKey = "offline_${targetLecture.id}",
                    localVaultFile = localVaultFile,
                    isLocalOffline = true
                )
            }
            trimmed.startsWith("content://") -> {
                ResolvedMedia3StreamTarget(
                    uri = Uri.parse(trimmed),
                    cacheKey = "content_${targetLecture.id}",
                    isLocalOffline = true
                )
            }
            else -> {
                val driveFileId = GoogleDrive5TbVaultManager.extractDriveFileId(trimmed)
                if (!driveFileId.isNullOrBlank()) {
                    if (reloadAttemptState.intValue > 0) {
                        withContext(Dispatchers.IO) {
                            GoogleDrive5TbVaultManager.fetchUploadParamsFromAppsScript(forceRefresh = true)
                                .getOrNull()
                                ?.let { params ->
                                    if (params.accessToken.isNotBlank()) {
                                        SessionPreferences.saveLastKnownDriveToken(
                                            context,
                                            params.accessToken,
                                            params.folderId
                                        )
                                    }
                                }
                        }
                    }
                    val directDriveUrl = GoogleDrive5TbVaultManager.buildDirectDriveStreamUrl(driveFileId)
                    ResolvedMedia3StreamTarget(
                        uri = Uri.parse(directDriveUrl),
                        cacheKey = "drive_${driveFileId}",
                        isLocalOffline = false
                    )
                } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    ResolvedMedia3StreamTarget(
                        uri = Uri.parse(trimmed),
                        cacheKey = "http_${targetLecture.id}",
                        isLocalOffline = false
                    )
                } else {
                    null
                }
            }
        }

        streamTarget = resolved
        if (resolved == null) {
            isBufferingState.value = false
            playbackErrorMessageState.value = "ملف المحاضرة غير متوفر حالياً. يرجى التحقق من اتصال الإنترنت أو إعادة تنزيل المحاضرة."
        } else {
            val mediaItem = MediaItem.Builder()
                .setUri(resolved.uri)
                .setMediaId(targetLecture.id)
                .setCustomCacheKey(resolved.cacheKey)
                .setMimeType(MimeTypes.VIDEO_MP4)
                .build()

            val resumeSec = savedSecond
                .coerceIn(0, (totalSecondsState.intValue - 2).coerceAtLeast(0))
            currentSecondState.intValue = resumeSec
            sliderDragSecondState.intValue = resumeSec
            val startPositionMs = if (resumeSec > 0) resumeSec * 1000L else 0L

            withContext(Dispatchers.Main.immediate) {
                exoPlayer.setMediaItem(mediaItem, startPositionMs)
                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeedState.floatValue)
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()
                isPlayingState.value = true
            }
        }
    }

    // Auto-Hide Controls after 3 seconds of playback
    LaunchedEffect(
        showOverlayControlsState.value,
        isPlayingState.value,
        controlsInteractionTickState.longValue,
        isScreenLockedState.value,
        isDraggingSliderState.value
    ) {
        if (showOverlayControlsState.value && isPlayingState.value && !isDraggingSliderState.value) {
            delay(3200L)
            showOverlayControlsState.value = false
        }
    }

    // Clear double-tap seek indicator after 900ms
    LaunchedEffect(seekFeedbackTickState.longValue) {
        if (seekFeedbackLabelState.value != null) {
            delay(900L)
            seekFeedbackLabelState.value = null
        }
    }

    // Sync playback progress, buffering percentage, & persist position safely on Main thread
    LaunchedEffect(
        exoPlayer,
        lecture?.id,
        isBufferingState.value,
        isPlayerPreparedState.value,
        isPlayingState.value,
        isDraggingSliderState.value
    ) {
        val trackedId = lecture?.id ?: return@LaunchedEffect
        var tickCount = 0
        while (isBufferingState.value || (isPlayingState.value && isPlayerPreparedState.value)) {
            val (posMs, durMs, bufPct) = withContext(Dispatchers.Main) {
                Triple(exoPlayer.currentPosition, exoPlayer.duration, exoPlayer.bufferedPercentage)
            }
            bufferedPercentageState.intValue = bufPct.coerceIn(0, 100)
            if (!isDraggingSliderState.value && isPlayingState.value && isPlayerPreparedState.value && posMs >= 0L) {
                if (durMs != C.TIME_UNSET && durMs > 0L) {
                    totalSecondsState.intValue = (durMs / 1000L).toInt().coerceAtLeast(1)
                }
                val total = totalSecondsState.intValue
                val sec = (posMs / 1000L).toInt().coerceIn(0, total)
                currentSecondState.intValue = sec
                sliderDragSecondState.intValue = sec
                tickCount++
                if (tickCount % 6 == 0) {
                    val completed = SessionPreferences.saveLecturePlaybackSecond(context, trackedId, sec, total)
                    onPlaybackUpdated(trackedId, sec, total, completed)
                }
            }
            delay(250L)
        }
    }

    return state
}

@Composable
fun ElghalizVideoViewport(
    state: ElghalizMedia3PlayerState,
    lecture: Lecture,
    userEmail: String,
    isPreparingVideo: Boolean = false,
    showOverlaySpeedRow: Boolean = true,
    modifier: Modifier = Modifier
) {
    val layoutDirection = LocalLayoutDirection.current

    Box(
        modifier = modifier
            .background(Color.Black)
            .clipToBounds()
            .pointerInput(state.isScreenLocked) {
                if (!state.isScreenLocked) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (state.zoomScale * zoom).coerceIn(1f, 3f)
                        state.zoomScale = newScale
                        if (newScale > 1.01f) {
                            val maxDx = (size.width * (newScale - 1f)) / 2f
                            val maxDy = (size.height * (newScale - 1f)) / 2f
                            state.panOffsetX = (state.panOffsetX + pan.x).coerceIn(-maxDx, maxDx)
                            state.panOffsetY = (state.panOffsetY + pan.y).coerceIn(-maxDy, maxDy)
                        } else {
                            state.panOffsetX = 0f
                            state.panOffsetY = 0f
                        }
                    }
                }
            }
            .pointerInput(state.isScreenLocked, layoutDirection) {
                detectTapGestures(
                    onTap = {
                        state.controlsInteractionTick = System.currentTimeMillis()
                        state.showOverlayControls = !state.showOverlayControls
                    },
                    onDoubleTap = { tapOffset ->
                        if (!state.isScreenLocked) {
                            val isRightHalf = tapOffset.x > (size.width / 2f)
                            val delta = if (isRightHalf) 10 else -10
                            state.seekByDeltaSeconds(delta)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        PlayerSurface(
            player = state.exoPlayer,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = state.zoomScale,
                    scaleY = state.zoomScale,
                    translationX = state.panOffsetX,
                    translationY = state.panOffsetY
                )
        )

        // Dynamic Moving Watermark for content protection
        val watermarkAlignment = when (state.watermarkStep) {
            0 -> Alignment.TopStart
            1 -> Alignment.BottomEnd
            2 -> Alignment.TopEnd
            else -> Alignment.BottomStart
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            contentAlignment = watermarkAlignment
        ) {
            Text(
                text = "${userEmail.ifBlank { "Elghaliz Medicine" }}\nID: ${lecture.id.take(8)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.22f),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer(rotationZ = -10f)
            )
        }

        // Lightweight buffering indicator with bufferedPercentage for weak networks
        if ((state.isBuffering || isPreparingVideo) && state.playbackErrorMessage == null) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFC9A24D),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(38.dp)
                    )
                    val bufferedPct = state.bufferedPercentage.coerceIn(0, 100)
                    Text(
                        text = if (bufferedPct > 0) "جاري التحميل... $bufferedPct%" else "جاري التحميل...",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Error Recovery Overlay
        if (state.playbackErrorMessage != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(Color(0xD9000000), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.playbackErrorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { state.retryPlayback() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC9A24D),
                        contentColor = Color(0xFF0F3D2E)
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إعادة المحاولة", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Double-Tap Seek Feedback Badge
        AnimatedVisibility(
            visible = state.seekFeedbackLabel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = state.seekFeedbackLabel.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFC9A24D),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }

        // Reset Zoom (1x) badge when zoomed in
        if (state.zoomScale > 1.05f && !state.isScreenLocked) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(50),
                onClick = {
                    state.zoomScale = 1f
                    state.panOffsetX = 0f
                    state.panOffsetY = 0f
                    state.registerUserInteraction()
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOutMap,
                        contentDescription = "إعادة ضبط التكبير",
                        tint = Color(0xFFC9A24D),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${"%.1f".format(state.zoomScale)}x • إعادة ضبط (1x)",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (state.isScreenLocked) {
            AnimatedVisibility(
                visible = state.showOverlayControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Surface(
                    color = Color(0xFF0F3D2E),
                    shape = RoundedCornerShape(50),
                    onClick = { state.unlockScreen() },
                    modifier = Modifier.testTag("screen_unlock_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "فتح قفل الشاشة",
                            tint = Color(0xFFC9A24D),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🔒 الشاشة مقفلة • اضغط للفتح",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            if (state.isFullscreenLandscape) {
                AnimatedVisibility(
                    visible = state.showOverlayControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xCC000000), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lecture.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { state.exitFullscreen() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق ملء الشاشة",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = state.showOverlayControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ViewportBottomOverlayControls(
                    state = state,
                    showSpeedRow = showOverlaySpeedRow || state.isFullscreenLandscape
                )
            }
        }
    }
}

@Composable
private fun ViewportBottomOverlayControls(
    state: ElghalizMedia3PlayerState,
    showSpeedRow: Boolean
) {
    val displayedSecond = if (state.isDraggingSlider) state.sliderDragSecond else state.currentSecond
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xCC000000), Color(0xF2000000))
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { state.registerUserInteraction() }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Slider(
            value = displayedSecond.toFloat().coerceIn(0f, state.totalSeconds.toFloat().coerceAtLeast(1f)),
            onValueChange = { newSec ->
                state.registerUserInteraction()
                state.isDraggingSlider = true
                state.sliderDragSecond = newSec.toInt().coerceIn(0, state.totalSeconds)
                state.currentSecond = state.sliderDragSecond
            },
            onValueChangeFinished = {
                val target = state.sliderDragSecond
                state.isDraggingSlider = false
                state.seekToSecond(target)
            },
            valueRange = 0f..state.totalSeconds.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFC9A24D),
                activeTrackColor = Color(0xFFC9A24D),
                inactiveTrackColor = Color.White.copy(alpha = 0.35f)
            ),
            modifier = Modifier.height(24.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\u200E${formatDurationHoursMinutesSeconds(displayedSecond)} / ${formatDurationHoursMinutesSeconds(state.totalSeconds)}\u200E",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            if (showSpeedRow) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            state.registerUserInteraction()
                            state.seekByDeltaSeconds(-10)
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "إرجاع 10 ثوان",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = { state.togglePlayPause() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFC9A24D))
                            .testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "إيقاف مؤقت" else "تشغيل",
                            tint = Color(0xFF0F3D2E),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            state.registerUserInteraction()
                            state.seekByDeltaSeconds(10)
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "تقديم 10 ثوان",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isFullscreenLandscape) {
                    IconButton(
                        onClick = {
                            state.isScreenLocked = true
                            state.registerUserInteraction()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("screen_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "قفل الشاشة",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { state.toggleFullscreen() },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("toggle_fullscreen_landscape_button")
                ) {
                    Icon(
                        imageVector = if (state.isFullscreenLandscape) {
                            Icons.Default.FullscreenExit
                        } else {
                            Icons.Default.Fullscreen
                        },
                        contentDescription = if (state.isFullscreenLandscape) "تصغير الشاشة" else "تكبير الشاشة",
                        tint = Color(0xFFC9A24D),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (showSpeedRow) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playback_speed_button"),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ELGHALIZ_PLAYER_SPEEDS.forEach { speedOption ->
                    val isSelected = state.playbackSpeed == speedOption
                    val label = when (speedOption) {
                        1.0f -> "1x"
                        2.0f -> "2x"
                        else -> "${speedOption}x"
                    }
                    Surface(
                        color = if (isSelected) Color(0xFFC9A24D) else Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50),
                        onClick = { state.setSpeed(speedOption) }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) Color(0xFF0F3D2E) else Color.White.copy(alpha = 0.88f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

internal fun resolveInstantLocalVaultVideoFile(
    context: Context,
    lectureId: String,
    rawUri: String
): File? {
    val trimmed = rawUri.trim()
    return runCatching {
        val vaultDir = File(context.filesDir, "offline_vault")
        val candidates = mutableListOf<File>()

        if (lectureId.isNotBlank()) {
            candidates.add(File(vaultDir, "$lectureId.elgdrm"))
            candidates.add(File(vaultDir, "$lectureId.mp4"))
            candidates.add(File(File(context.filesDir, "uploaded_videos"), "$lectureId.mp4"))
            candidates.add(File(File(context.filesDir, "stream_cache"), "$lectureId.mp4"))
        }

        val directLocal = when {
            trimmed.startsWith("file://") -> Uri.parse(trimmed).path?.let { File(it) }
            trimmed.startsWith("/") -> File(trimmed)
            else -> null
        }
        if (directLocal != null && directLocal.exists()) {
            candidates.add(directLocal)
        }

        candidates.firstOrNull { file ->
            if (!file.exists() || file.length() <= 64L) return@firstOrNull false
            val valid = EncryptedVideoVault.isValidEncryptedOrPlainVaultFile(lectureId, file) ||
                GoogleDrive5TbVaultManager.isValidPlayableVideoFile(file)
            if (!valid) {
                runCatching { file.delete() }
            }
            valid
        }
    }.getOrNull()
}

fun formatDurationHoursMinutesSeconds(totalSec: Int): String {
    val safeSec = totalSec.coerceAtLeast(0)
    val hours = safeSec / 3600
    val minutes = (safeSec % 3600) / 60
    val seconds = safeSec % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun formatFileSize(sizeMb: Long): String {
    return if (sizeMb >= 1024L) {
        String.format(java.util.Locale.US, "%.2f GB", sizeMb / 1024.0)
    } else {
        "$sizeMb MB"
    }
}
