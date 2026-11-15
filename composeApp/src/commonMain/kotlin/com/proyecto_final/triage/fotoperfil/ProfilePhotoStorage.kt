package com.proyecto_final.triage.fotoperfil

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Lectura/escritura de archivos en el almacenamiento privado de la app
 * (no visible para otras apps ni para el usuario, y no se sube al servidor).
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal expect object ProfilePhotoFiles {
    fun write(fileName: String, bytes: ByteArray)
    fun read(fileName: String): ByteArray?
    fun delete(fileName: String)
}

/** Codifica la imagen como JPEG con calidad 0..100. */
internal expect fun ImageBitmap.encodeToJpeg(quality: Int): ByteArray

/**
 * Guarda la foto de perfil como archivo local, una por usuario, para que si otra
 * persona inicia sesión en el mismo dispositivo no vea la foto de la anterior.
 */
object ProfilePhotoStorage {

    suspend fun save(userKey: String, photo: ImageBitmap) = withContext(Dispatchers.IO) {
        ProfilePhotoFiles.write(fileNameFor(userKey), photo.encodeToJpeg(quality = 90))
    }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(userKey: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val bytes = ProfilePhotoFiles.read(fileNameFor(userKey)) ?: return@withContext null
        runCatching { bytes.decodeToImageBitmap() }.getOrNull()
    }

    suspend fun delete(userKey: String) = withContext(Dispatchers.IO) {
        ProfilePhotoFiles.delete(fileNameFor(userKey))
    }

    private fun fileNameFor(userKey: String): String {
        val safeKey = userKey.replace(Regex("[^A-Za-z0-9]"), "_")
        return "foto_perfil_$safeKey.jpg"
    }
}
