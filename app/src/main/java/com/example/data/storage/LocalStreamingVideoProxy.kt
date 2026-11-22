package com.example.data.storage

import android.content.Context
import java.io.File

/**
 * واجهة موحدة لخادم فك التشفير والبث المحلي (Local Loopback Decryption & Stream Server)
 * تعمل على `http://127.0.0.1:$port/play.mp4`.
 */
object LocalStreamingVideoProxy {

    fun ensureStarted(context: Context): Int {
        return EncryptedVideoVault.ensureLoopbackServerStarted(context)
    }

    fun getStreamUrl(
        context: Context,
        lectureId: String,
        localVaultFile: File? = null,
        driveFileId: String? = null
    ): String {
        return EncryptedVideoVault.buildLoopbackPlayUrl(
            context = context,
            lectureId = lectureId,
            localVaultFile = localVaultFile,
            driveFileId = driveFileId
        )
    }
}
