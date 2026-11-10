package com.proyecto_final.triage.viewmodels

import com.proyecto_final.triage.audio.ModoEntradaVoz

/** Classification state for display; interview closure is tracked separately. */
enum class ChatClassificationPresentation {
    NO_SOLICITADA,
    PROCESANDO,
    FALLIDA,
    COMPLETADA,
    PARCIAL,
    CERRADA_SIN_RESULTADO
}

/**
 * Maps the backend classification status to UI. Legacy responses can omit the status;
 * an interview closure or queue estimate alone cannot establish triage success.
 */
fun presentacionClasificacionChat(
    finalizado: Boolean,
    estadoClasificacion: String?
): ChatClassificationPresentation = when (estadoClasificacion?.uppercase()) {
    "NO_SOLICITADA" -> ChatClassificationPresentation.NO_SOLICITADA
    "PENDIENTE" -> ChatClassificationPresentation.PROCESANDO
    "FALLIDA" -> ChatClassificationPresentation.FALLIDA
    "PARCIAL" -> ChatClassificationPresentation.PARCIAL
    "COMPLETADA" -> ChatClassificationPresentation.COMPLETADA
    else -> if (finalizado) {
        ChatClassificationPresentation.CERRADA_SIN_RESULTADO
    } else {
        ChatClassificationPresentation.NO_SOLICITADA
    }
}

/** Copy shown while the microphone remains active during a voice interview. */
fun textoPanelVoz(
    estado: EstadoVoz,
    modoEntrada: ModoEntradaVoz,
    procesandoRespuesta: Boolean,
    asistenteHablando: Boolean
): String = when (estado) {
    EstadoVoz.INACTIVA -> ""
    EstadoVoz.CONECTANDO -> "Conectando con el asistente..."
    EstadoVoz.ESCUCHANDO -> when {
        procesandoRespuesta -> "Procesando tu respuesta..."
        asistenteHablando && modoEntrada == ModoEntradaVoz.INTERRUMPIBLE ->
            "El asistente está hablando. Podés interrumpirlo."
        asistenteHablando -> "El asistente está respondiendo. Enseguida te escucha."
        modoEntrada == ModoEntradaVoz.INTERRUMPIBLE ->
            "El micrófono está activo. Podés responder cuando quieras."
        else -> "Te escuchamos. Podés responder cuando quieras."
    }
    EstadoVoz.PROCESANDO -> "Guardando tus respuestas..."
    EstadoVoz.DETENIENDO -> "Terminando la conversación de voz..."
}

/** Backend-owned progress wins over acknowledgements/transcripts from the assistant. */
data class ProgresoVozUiState(
    val procesandoRespuesta: Boolean = false,
    val controladoPorServidor: Boolean = false,
    val asistenteHablando: Boolean = false
)

enum class CambioProgresoVoz {
    RESPUESTA_PACIENTE,
    SERVIDOR_PROCESANDO,
    SERVIDOR_ESPERA_PACIENTE,
    ASISTENTE_HABLANDO,
    ASISTENTE_TERMINO,
    CIERRE
}

/** Reduces voice events without letting a transcript acknowledgement clear backend-owned work. */
fun aplicarCambioProgresoVoz(
    actual: ProgresoVozUiState,
    cambio: CambioProgresoVoz
): ProgresoVozUiState = when (cambio) {
    CambioProgresoVoz.RESPUESTA_PACIENTE -> actual.copy(
        procesandoRespuesta = true,
        asistenteHablando = false
    )
    CambioProgresoVoz.SERVIDOR_PROCESANDO -> ProgresoVozUiState(
        procesandoRespuesta = true,
        controladoPorServidor = true
    )
    CambioProgresoVoz.SERVIDOR_ESPERA_PACIENTE -> ProgresoVozUiState(
        procesandoRespuesta = false,
        controladoPorServidor = true
    )
    CambioProgresoVoz.ASISTENTE_HABLANDO -> actual.copy(
        procesandoRespuesta = if (actual.controladoPorServidor) actual.procesandoRespuesta else false,
        asistenteHablando = true
    )
    CambioProgresoVoz.ASISTENTE_TERMINO -> actual.copy(
        procesandoRespuesta = if (actual.controladoPorServidor) actual.procesandoRespuesta else false,
        asistenteHablando = false
    )
    CambioProgresoVoz.CIERRE -> ProgresoVozUiState()
}

data class EstadoEntrevistaVozUi(
    val finalizado: Boolean,
    val estadoClasificacion: String?
)

enum class TransicionCierreVoz {
    CIERRE_ACEPTADO,
    RESULTADO_NO_CONFIRMADO
}

fun aplicarTransicionCierreVoz(
    actual: EstadoEntrevistaVozUi,
    transicion: TransicionCierreVoz
): EstadoEntrevistaVozUi = when (transicion) {
    TransicionCierreVoz.CIERRE_ACEPTADO -> actual.copy(
        finalizado = true,
        estadoClasificacion = "PENDIENTE"
    )
    TransicionCierreVoz.RESULTADO_NO_CONFIRMADO -> actual.copy(
        finalizado = true,
        estadoClasificacion = null
    )
}

data class ErrorVozUi(
    val mensaje: String,
    val permiteRetomar: Boolean
)

fun presentarErrorVoz(mensaje: String, entrevistaCerrada: Boolean): ErrorVozUi =
    if (entrevistaCerrada) {
        ErrorVozUi(mensaje, permiteRetomar = false)
    } else {
        ErrorVozUi("Se interrumpió la voz.", permiteRetomar = true)
    }
