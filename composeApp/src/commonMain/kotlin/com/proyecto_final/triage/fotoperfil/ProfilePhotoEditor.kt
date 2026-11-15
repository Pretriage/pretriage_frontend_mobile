package com.proyecto_final.triage.fotoperfil

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Estado de la foto de perfil del usuario [userKey]. La foto se lee del almacenamiento
 * local apenas se conoce el usuario.
 */
@Stable
class ProfilePhotoState internal constructor(val userKey: String?) {
    var photo by mutableStateOf<ImageBitmap?>(null)
        internal set

    internal var showOptions by mutableStateOf(false)
    internal var imageToCrop by mutableStateOf<ImageBitmap?>(null)
    internal var errorMessage by mutableStateOf<String?>(null)

    /** Muestra las opciones: galería, cámara y, si ya hay foto, eliminarla. */
    fun openOptions() {
        if (userKey != null) showOptions = true
    }
}

@Composable
fun rememberProfilePhotoState(userKey: String?): ProfilePhotoState {
    val state = remember(userKey) { ProfilePhotoState(userKey) }
    LaunchedEffect(state) {
        if (userKey != null) state.photo = ProfilePhotoStorage.load(userKey)
    }
    return state
}

/**
 * Hojas y diálogos para cambiar la foto. Tiene que estar siempre en composición
 * (no dentro de un `if`) para recibir el resultado de la galería o la cámara.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePhotoDialogs(state: ProfilePhotoState) {
    val scope = rememberCoroutineScope()
    val picker = rememberProfilePhotoPicker(
        onImagePicked = { state.imageToCrop = it },
        onError = { state.errorMessage = it }
    )

    if (state.showOptions) {
        ModalBottomSheet(onDismissRequest = { state.showOptions = false }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "Foto de perfil",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                PhotoOption(Icons.Filled.PhotoLibrary, "Elegir de la galería") {
                    state.showOptions = false
                    picker.pickFromGallery()
                }
                PhotoOption(Icons.Filled.PhotoCamera, "Sacar una foto") {
                    state.showOptions = false
                    picker.takePhoto()
                }
                if (state.photo != null) {
                    PhotoOption(Icons.Filled.Delete, "Eliminar foto", tint = Color(0xFFB3261E)) {
                        state.showOptions = false
                        val key = state.userKey ?: return@PhotoOption
                        state.photo = null
                        scope.launch {
                            runCatching { ProfilePhotoStorage.delete(key) }
                                .onFailure { state.errorMessage = "No se pudo eliminar la foto." }
                        }
                    }
                }
            }
        }
    }

    state.imageToCrop?.let { image ->
        SquareImageCropper(
            image = image,
            onCancel = { state.imageToCrop = null },
            onCropped = { cropped ->
                state.imageToCrop = null
                val key = state.userKey ?: return@SquareImageCropper
                val previous = state.photo
                state.photo = cropped
                scope.launch {
                    runCatching { ProfilePhotoStorage.save(key, cropped) }
                        .onFailure {
                            state.photo = previous
                            state.errorMessage = "No se pudo guardar la foto."
                        }
                }
            }
        )
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { state.errorMessage = null },
            confirmButton = {
                TextButton(onClick = { state.errorMessage = null }) { Text("Aceptar") }
            },
            title = { Text("Foto de perfil") },
            text = { Text(message) }
        )
    }
}

@Composable
private fun PhotoOption(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
