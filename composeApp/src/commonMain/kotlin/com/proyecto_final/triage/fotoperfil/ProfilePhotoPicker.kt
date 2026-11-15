package com.proyecto_final.triage.fotoperfil

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Abre la galería o la cámara del dispositivo para elegir una foto.
 * Se obtiene con [rememberProfilePhotoPicker].
 */
class ProfilePhotoPicker(
    val pickFromGallery: () -> Unit,
    val takePhoto: () -> Unit
)

/**
 * La imagen que se entrega en [onImagePicked] ya viene con la orientación corregida
 * y reducida a un tamaño razonable (lado mayor de ~2048 px) para poder encuadrarla.
 * [onError] recibe un mensaje listo para mostrar al usuario.
 * Si el usuario cancela, no se llama a ninguno de los dos.
 */
@Composable
expect fun rememberProfilePhotoPicker(
    onImagePicked: (ImageBitmap) -> Unit,
    onError: (String) -> Unit
): ProfilePhotoPicker
