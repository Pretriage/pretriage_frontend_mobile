package com.proyecto_final.triage.notificaciones

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.proyecto_final.triage.MainActivity
import com.proyecto_final.triage.R
import com.proyecto_final.triage.appContext

internal const val CANAL_AVISOS_TURNO = "avisos_turno"
internal const val CANAL_SEGUIMIENTO_TURNO = "seguimiento_turno"
private const val ID_NOTIFICACION_AVISO = 1001

internal fun crearCanalesNotificacion(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    manager.createNotificationChannel(
        NotificationChannel(
            CANAL_AVISOS_TURNO,
            "Avisos del turno",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Cuando te llaman, si no te encontraron o si se cancela tu turno." }
    )
    manager.createNotificationChannel(
        NotificationChannel(
            CANAL_SEGUIMIENTO_TURNO,
            "Seguimiento del turno",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Aviso fijo mientras estás en la fila."
            setShowBadge(false)
        }
    )
}

/** Abre la app donde estaba, como si se tocara su ícono. */
internal fun intentAbrirApp(context: Context): PendingIntent {
    val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent(context, MainActivity::class.java)).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    }
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun tienePermisoNotificaciones(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
actual fun mostrarNotificacionTurno(titulo: String, texto: String) {
    val context = appContext
    if (!tienePermisoNotificaciones(context)) return
    crearCanalesNotificacion(context)
    val notificacion = NotificationCompat.Builder(context, CANAL_AVISOS_TURNO)
        .setSmallIcon(R.drawable.ic_notificacion_turno)
        .setContentTitle(titulo)
        .setContentText(texto)
        .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setAutoCancel(true)
        .setContentIntent(intentAbrirApp(context))
        .build()
    runCatching {
        NotificationManagerCompat.from(context).notify(ID_NOTIFICACION_AVISO, notificacion)
    }
}

actual fun iniciarSeguimientoEnSegundoPlano() {
    val context = appContext
    // Android no deja iniciar el servicio si la app ya está en segundo plano: en ese caso
    // se sigue escuchando mientras el sistema mantenga viva la app.
    runCatching {
        ContextCompat.startForegroundService(context, Intent(context, SeguimientoTurnoService::class.java))
    }
}

actual fun detenerSeguimientoEnSegundoPlano() {
    val context = appContext
    runCatching { context.stopService(Intent(context, SeguimientoTurnoService::class.java)) }
}

@Composable
actual fun rememberSolicitudPermisoNotificaciones(): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    return remember(launcher) {
        {
            if (!tienePermisoNotificaciones(appContext) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
