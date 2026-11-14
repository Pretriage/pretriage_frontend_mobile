package com.proyecto_final.triage.notificaciones

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

private const val ID_AVISO_TURNO = "aviso-turno"

/** Permite mostrar el aviso aunque la app esté abierta (por defecto iOS lo oculta). */
private class DelegadoNotificaciones : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit
    ) {
        withCompletionHandler(
            UNNotificationPresentationOptionBanner or
                UNNotificationPresentationOptionList or
                UNNotificationPresentationOptionSound
        )
    }
}

// El centro de notificaciones guarda el delegado como referencia débil: se retiene acá.
private val delegado = DelegadoNotificaciones()

private fun centroDeNotificaciones(): UNUserNotificationCenter {
    val centro = UNUserNotificationCenter.currentNotificationCenter()
    centro.delegate = delegado
    return centro
}

actual fun mostrarNotificacionTurno(titulo: String, texto: String) {
    val contenido = UNMutableNotificationContent()
    contenido.setTitle(titulo)
    contenido.setBody(texto)
    val pedido = UNNotificationRequest.requestWithIdentifier(ID_AVISO_TURNO, contenido, null)
    centroDeNotificaciones().addNotificationRequest(pedido, null)
}

// iOS suspende la app en segundo plano: los avisos llegan con la app abierta.
actual fun iniciarSeguimientoEnSegundoPlano() = Unit

actual fun detenerSeguimientoEnSegundoPlano() = Unit

@Composable
actual fun rememberSolicitudPermisoNotificaciones(): () -> Unit = remember {
    {
        centroDeNotificaciones().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { _, _ -> }
    }
}
