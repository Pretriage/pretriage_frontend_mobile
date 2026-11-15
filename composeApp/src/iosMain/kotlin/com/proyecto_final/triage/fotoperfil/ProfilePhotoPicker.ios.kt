package com.proyecto_final.triage.fotoperfil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.math.max
import kotlin.math.min

/** Lado mayor máximo (en píxeles) de la imagen que se entrega para encuadrar. */
private const val MAX_DIMENSION = 2048.0

@Composable
actual fun rememberProfilePhotoPicker(
    onImagePicked: (ImageBitmap) -> Unit,
    onError: (String) -> Unit
): ProfilePhotoPicker {
    val scope = rememberCoroutineScope()
    val currentOnImagePicked by rememberUpdatedState(onImagePicked)
    val currentOnError by rememberUpdatedState(onError)

    // UIImagePickerController guarda el delegate como referencia débil: hay que retenerlo acá.
    val delegate = remember {
        ImagePickerDelegate { uiImage ->
            scope.launch {
                val bitmap = withContext(Dispatchers.Default) {
                    runCatching { uiImage.toNormalizedImageBitmap() }.getOrNull()
                }
                if (bitmap != null) {
                    currentOnImagePicked(bitmap)
                } else {
                    currentOnError("No se pudo leer la imagen seleccionada.")
                }
            }
        }
    }

    fun present(source: UIImagePickerControllerSourceType, unavailableMessage: String) {
        if (!UIImagePickerController.isSourceTypeAvailable(source)) {
            currentOnError(unavailableMessage)
            return
        }
        val picker = UIImagePickerController()
        picker.sourceType = source
        picker.allowsEditing = false
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    return ProfilePhotoPicker(
        pickFromGallery = {
            // La galería de UIImagePickerController corre en otro proceso: no requiere permiso.
            present(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary,
                "No se pudo abrir la galería."
            )
        },
        takePhoto = {
            @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
            val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo!!)
            when (status) {
                AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> currentOnError(
                    "Necesitamos permiso para usar la cámara. Podés habilitarlo desde Ajustes."
                )
                // Si todavía no se pidió, el propio UIImagePickerController muestra el pedido de permiso.
                else -> present(
                    UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
                    "Este dispositivo no tiene cámara disponible."
                )
            }
        }
    )
}

private class ImagePickerDelegate(
    private val onImage: (UIImage) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, null)
        if (image != null) onImage(image)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
    }
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}

/**
 * Redibuja la imagen con orientación "arriba" (las fotos de la cámara vienen rotadas
 * por metadatos) y reducida a [MAX_DIMENSION], y la convierte a ImageBitmap.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalResourceApi::class)
private fun UIImage.toNormalizedImageBitmap(): ImageBitmap? {
    val pointWidth = size.useContents { width }
    val pointHeight = size.useContents { height }
    if (pointWidth <= 0.0 || pointHeight <= 0.0) return null

    val pixelWidth = pointWidth * scale
    val pixelHeight = pointHeight * scale
    val ratio = min(1.0, MAX_DIMENSION / max(pixelWidth, pixelHeight))
    val targetWidth = pixelWidth * ratio
    val targetHeight = pixelHeight * ratio

    val format = UIGraphicsImageRendererFormat.defaultFormat()
    format.scale = 1.0
    format.opaque = true
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(targetWidth, targetHeight), format = format)
    val image = this
    val rendered = renderer.imageWithActions { _ ->
        image.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    }

    val jpeg = UIImageJPEGRepresentation(rendered, 0.95) ?: return null
    return jpeg.toByteArray().decodeToImageBitmap()
}
