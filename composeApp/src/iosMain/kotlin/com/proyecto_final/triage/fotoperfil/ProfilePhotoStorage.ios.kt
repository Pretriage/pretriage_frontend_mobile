package com.proyecto_final.triage.fotoperfil

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual object ProfilePhotoFiles {
    // Application Support (y no Documents): Documents queda visible en la app Archivos
    // porque Info.plist tiene UIFileSharingEnabled.
    @OptIn(ExperimentalForeignApi::class)
    private val directory: String
        get() {
            val base = NSSearchPathForDirectoriesInDomains(
                NSApplicationSupportDirectory, NSUserDomainMask, true
            ).first() as String
            val dir = "$base/foto_perfil"
            NSFileManager.defaultManager.createDirectoryAtPath(
                dir, withIntermediateDirectories = true, attributes = null, error = null
            )
            return dir
        }

    actual fun write(fileName: String, bytes: ByteArray) {
        if (!bytes.toNSData().writeToFile("$directory/$fileName", atomically = true)) {
            error("No se pudo guardar $fileName")
        }
    }

    actual fun read(fileName: String): ByteArray? =
        NSData.dataWithContentsOfFile("$directory/$fileName")?.toByteArray()

    @OptIn(ExperimentalForeignApi::class)
    actual fun delete(fileName: String) {
        NSFileManager.defaultManager.removeItemAtPath("$directory/$fileName", error = null)
    }
}

internal actual fun ImageBitmap.encodeToJpeg(quality: Int): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap())
        .encodeToData(EncodedImageFormat.JPEG, quality)
        ?.bytes
        ?: error("No se pudo codificar la imagen")

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned {
    NSData.create(bytes = it.addressOf(0), length = size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isNotEmpty()) {
        result.usePinned { memcpy(it.addressOf(0), bytes, length) }
    }
    return result
}
