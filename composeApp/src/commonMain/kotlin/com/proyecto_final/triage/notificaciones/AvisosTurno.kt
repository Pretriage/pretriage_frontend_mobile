package com.proyecto_final.triage.notificaciones

import com.proyecto_final.triage.network.estadoConsulta.CambioEstadoConsultaDTO
import com.proyecto_final.triage.utils.formatHora

data class AvisoTurno(val titulo: String, val texto: String)

/**
 * Las tres notificaciones que el paciente necesita sí o sí. Cualquier otro cambio de estado
 * solo actualiza la pantalla, sin notificar.
 */
fun avisoPara(cambio: CambioEstadoConsultaDTO): AvisoTurno? = when (cambio.estado) {
    "LLAMADO" -> AvisoTurno(
        titulo = "Te están llamando",
        texto = "Acercate ${dondePresentarse(cambio)}."
    )

    "EN_ESPERA" -> if (cambio.tipoPausa == "AUSENTE_AL_LLAMADO") {
        AvisoTurno(
            titulo = "No te encontramos cuando te llamaron",
            texto = plazoParaVolver(cambio.fechaHoraLimiteRespuesta)
        )
    } else null

    "CANCELADA" -> AvisoTurno(
        titulo = "Tu turno se canceló",
        texto = buildString {
            append("Venció el tiempo de espera")
            cambio.nombreHospital?.takeIf { it.isNotBlank() }?.let { append(" en $it") }
            append(". Si todavía necesitás atención, podés iniciar una nueva consulta.")
        }
    )

    else -> null
}

private fun dondePresentarse(cambio: CambioEstadoConsultaDTO): String {
    val sala = cambio.nombreSala?.takeIf { it.isNotBlank() }
    val sector = cambio.nombreSector?.takeIf { it.isNotBlank() }
    return when {
        sala != null && sector != null -> "al $sala de $sector"
        sala != null -> "al $sala"
        sector != null -> "a $sector"
        else -> "a la guardia"
    }
}

private fun plazoParaVolver(fechaHoraLimite: String?): String {
    val hora = formatHora(fechaHoraLimite)
    return if (hora == "--:--") {
        "Volvé a la guardia lo antes posible o tu turno se va a cancelar."
    } else {
        "Tenés hasta las $hora para volver. Si no, tu turno se va a cancelar."
    }
}
