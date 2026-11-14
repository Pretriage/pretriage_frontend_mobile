package com.proyecto_final.triage.notificaciones

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.proyecto_final.triage.R

private const val ID_NOTIFICACION_SEGUIMIENTO = 1002

/**
 * Servicio en primer plano mientras el paciente está en la fila. No hace trabajo propio: su
 * notificación fija evita que Android corte la conexión con el hospital cuando la app está
 * minimizada o el celular bloqueado. Lo inicia y lo detiene [SeguimientoConsulta].
 */
class SeguimientoTurnoService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalesNotificacion(this)
        val notificacion = NotificationCompat.Builder(this, CANAL_SEGUIMIENTO_TURNO)
            .setSmallIcon(R.drawable.ic_notificacion_turno)
            .setContentTitle("Seguís en la fila")
            .setContentText("Te avisamos cuando te llamen, aunque minimices la app.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(intentAbrirApp(this))
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                ID_NOTIFICACION_SEGUIMIENTO,
                notificacion,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(ID_NOTIFICACION_SEGUIMIENTO, notificacion)
        }
        return START_NOT_STICKY
    }
}
