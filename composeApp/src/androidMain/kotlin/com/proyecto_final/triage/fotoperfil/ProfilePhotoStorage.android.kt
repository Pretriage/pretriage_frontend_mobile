package com.proyecto_final.triage.fotoperfil

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.proyecto_final.triage.appContext
import java.io.ByteArrayOutputStream
import java.io.File

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual object ProfilePhotoFiles {
    // filesDir es privado de la app: otras apps no pueden leerlo.
    private val directory: File
        get() = File(appContext.filesDir, "foto_perfil").apply { mkdirs() }

    actual fun write(fileName: String, bytes: ByteArray) {
        // Se escribe a un temporal y se renombra para no dejar un archivo a medias.
        val target = File(directory, fileName)
        val temp = File(directory, "$fileName.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) error("No se pudo guardar $fileName")
        }
    }

    actual fun read(fileName: String): ByteArray? =
        File(directory, fileName).takeIf { it.exists() }?.readBytes()

    actual fun delete(fileName: String) {
        File(directory, fileName).delete()
    }
}

internal actual fun ImageBitmap.encodeToJpeg(quality: Int): ByteArray =
    ByteArrayOutputStream().use { stream ->
        asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, quality, stream)
        stream.toByteArray()
    }
