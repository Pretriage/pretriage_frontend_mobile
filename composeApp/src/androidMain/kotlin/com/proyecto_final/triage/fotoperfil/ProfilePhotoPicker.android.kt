package com.proyecto_final.triage.fotoperfil

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Lado mayor máximo de la imagen decodificada: suficiente para encuadrar sin agotar memoria. */
private const val MAX_DIMENSION = 2048
private const val CAMERA_DIR = "camara"

@Composable
actual fun rememberProfilePhotoPicker(
    onImagePicked: (ImageBitmap) -> Unit,
    onError: (String) -> Unit
): ProfilePhotoPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnImagePicked by rememberUpdatedState(onImagePicked)
    val currentOnError by rememberUpdatedState(onError)
    // Se guarda en un Saveable para no perder la foto si Android cierra la app mientras la cámara está abierta.
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun deliver(uri: Uri, tempFile: File? = null) {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val decoded = runCatching { decodeImage(context, uri) }.getOrNull()
                tempFile?.delete()
                decoded
            }
            if (bitmap != null) {
                currentOnImagePicked(bitmap.asImageBitmap())
            } else {
                currentOnError("No se pudo leer la imagen seleccionada.")
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) deliver(uri) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraPath ?: return@rememberLauncherForActivityResult
        pendingCameraPath = null
        val file = File(path)
        if (success) deliver(Uri.fromFile(file), tempFile = file) else file.delete()
    }

    fun launchCamera() {
        try {
            val dir = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
            val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCameraPath = file.absolutePath
            cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pendingCameraPath = null
            currentOnError("No se encontró una app de cámara en el dispositivo.")
        } catch (e: Exception) {
            pendingCameraPath = null
            currentOnError("No se pudo abrir la cámara.")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            currentOnError(
                "Necesitamos permiso para usar la cámara. Podés habilitarlo desde los ajustes del dispositivo."
            )
        }
    }

    return ProfilePhotoPicker(
        pickFromGallery = {
            try {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } catch (e: ActivityNotFoundException) {
                currentOnError("No se encontró una galería en el dispositivo.")
            }
        },
        takePhoto = {
            // Si la app declara el permiso CAMERA (lo agrega una dependencia), Android exige
            // que esté concedido incluso para abrir la app de cámara del sistema.
            val needsPermission = context.declaresPermission(Manifest.permission.CAMERA) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                launchCamera()
            }
        }
    )
}

private fun Context.declaresPermission(permission: String): Boolean = try {
    @Suppress("DEPRECATION")
    packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions?.contains(permission) == true
} catch (e: Exception) {
    false
}

/** Decodifica la imagen reducida y con la orientación EXIF aplicada. */
private fun decodeImage(context: Context, uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder ya aplica la orientación EXIF y soporta HEIC.
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Bitmap en software: los de hardware no se pueden leer para recortar.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largest = max(info.size.width, info.size.height)
            if (largest > MAX_DIMENSION) {
                val ratio = MAX_DIMENSION.toFloat() / largest
                decoder.setTargetSize(
                    (info.size.width * ratio).roundToInt().coerceAtLeast(1),
                    (info.size.height * ratio).roundToInt().coerceAtLeast(1)
                )
            }
        }
    }

    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_DIMENSION) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: return null

    val orientation = resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    return bitmap.applyExifOrientation(orientation)
}

private fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        else -> return this
    }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (rotated != this) recycle()
    return rotated
}
