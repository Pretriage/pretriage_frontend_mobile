package com.proyecto_final.triage.notificaciones

import androidx.compose.runtime.Composable

/**
 * Muestra (o reemplaza) la notificación del sistema con el último aviso del turno.
 * Si el usuario no dio permiso de notificaciones, no hace nada.
 */
expect fun mostrarNotificacionTurno(titulo: String, texto: String)

/**
 * Mantiene viva la conexión con el hospital mientras la app está minimizada.
 * En Android levanta un servicio en primer plano con la notificación fija "Seguís en la fila".
 * En iOS no hace nada: el sistema suspende la app en segundo plano.
 */
expect fun iniciarSeguimientoEnSegundoPlano()

expect fun detenerSeguimientoEnSegundoPlano()

/** Devuelve una acción que pide el permiso de notificaciones si todavía no se concedió. */
@Composable
expect fun rememberSolicitudPermisoNotificaciones(): () -> Unit
