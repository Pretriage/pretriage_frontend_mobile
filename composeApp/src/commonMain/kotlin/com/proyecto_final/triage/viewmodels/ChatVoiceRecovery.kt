package com.proyecto_final.triage.viewmodels

import com.proyecto_final.triage.network.ChatResponse
import com.proyecto_final.triage.network.CHAT_TEXT_TURN_MAX_DURATION_MS
import com.proyecto_final.triage.network.siguienteEsperaLentaMs
import com.proyecto_final.triage.network.siguienteEsperaMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal fun ChatUiState.aplicarAvisoUrgenteVoz(idChatVoz: Long, mensaje: String): ChatUiState =
    if (chatId == idChatVoz && avisoUrgenteVoz == null) copy(avisoUrgenteVoz = mensaje) else this

internal fun ChatUiState.esperandoResultadoCierre(): Boolean = finalizado &&
    !estadoClasificacion.equals("PARCIAL", ignoreCase = true) &&
    (estadoClasificacion.isNullOrBlank() || estadoClasificacion.equals("PENDIENTE", ignoreCase = true))

/** A GET during voice closure may still see the transaction's earlier history. */
internal fun ChatUiState.aplicarActualizacionTrasVoz(chat: ChatResponse): ChatUiState {
    if (chat.id != null && chatId != null && chat.id != chatId) return this
    if (chat.versionEstado < versionEstado) return this
    val mismaVersion = chat.versionEstado == versionEstado
    if (mismaVersion && solicitudTextoPendienteId != null &&
        chat.solicitudTurnoTextoId != solicitudTextoPendienteId && !chat.finalizado
    ) return this
    val estadoActual = estadoClasificacion?.uppercase()
    val estadoRemoto = chat.estadoClasificacion?.uppercase()
    val estadoActualEsTerminal = estadoActual == "COMPLETADA" || estadoActual == "FALLIDA" || estadoActual == "PARCIAL" ||
        (finalizado && estadoActual == "NO_SOLICITADA")
    val remotoCompleto = estadoRemoto == "COMPLETADA"
    val falloAvanzaACompletada = estadoActual == "FALLIDA" && remotoCompleto
    if (chat.versionEstado == versionEstado && estadoActualEsTerminal && estadoRemoto != estadoActual && !falloAvanzaACompletada) return this
    val terminal = estadoRemoto == "COMPLETADA" || estadoRemoto == "FALLIDA" || estadoRemoto == "PARCIAL" ||
        (chat.finalizado && estadoRemoto == "NO_SOLICITADA")
    // A pre-closure GET can race with the saved closure transaction.
    val preservarCierre = finalizado && !chat.finalizado &&
        estadoRemoto == "NO_SOLICITADA"
    val nuevaSolicitud = chat.versionEstado > versionEstado && chat.solicitudTurnoTextoId != solicitudTextoPendienteId
    return copy(
        chatId = chat.id ?: chatId,
        mensajes = if (terminal || chat.mensajes.size >= mensajes.size) chat.mensajes else mensajes,
        finalizado = finalizado || chat.finalizado || terminal,
        estadoClasificacion = if (preservarCierre) estadoClasificacion else chat.estadoClasificacion,
        atencionEstimada = if (chat.ocultaEstimacionAnterior()) null else atencionEstimada,
        cierreParcial = chat.cierreParcial,
        estadoTurnoTexto = chat.estadoTurnoTexto ?: estadoTurnoTexto,
        estadoRecuperacion = if (terminal) "SIN_RECUPERACION" else chat.estadoRecuperacion,
        proximoReintentoEn = if (terminal) null else chat.proximoReintentoEn,
        intentosTurnoTexto = chat.intentosTurnoTexto,
        versionEstado = maxOf(versionEstado, chat.versionEstado),
        solicitudTextoPendienteId = if (nuevaSolicitud && !terminal) chat.solicitudTurnoTextoId else solicitudTextoPendienteId,
        contenidoSolicitudTextoPendiente = if (nuevaSolicitud || terminal) null else contenidoSolicitudTextoPendiente,
        respuestaPendiente = if (terminal) false else respuestaPendiente,
        estadoVoz = if (terminal) EstadoVoz.INACTIVA else estadoVoz,
        error = if (terminal) null else error,
        errorVoz = if (terminal) false else errorVoz,
        actualizacionCierreDisponible = if (terminal) false else actualizacionCierreDisponible
    )
}

/** A bounded caller can end the loading state if the voice socket never closes. */
internal fun ChatUiState.aplicarLimiteEsperaCierre(ultimaLectura: ChatResponse?): ChatUiState {
    val agotado = copy(
        estadoVoz = EstadoVoz.INACTIVA,
        actualizacionCierreDisponible = true,
        procesandoRespuestaVoz = false,
        progresoVozControladoPorServidor = false,
        asistenteHablandoVoz = false
    )
    val confirmado = ultimaLectura?.let(agotado::aplicarActualizacionTrasVoz) ?: agotado
    return if (confirmado.esperandoResultadoCierre()) confirmado.copy(estadoClasificacion = null) else confirmado
}

/** Only GETs; no patient message or classification request is repeated. */
internal suspend fun seguirResultadoCierre(
    chatId: Long,
    pendiente: () -> Boolean,
    obtener: suspend (Long, Long) -> Result<ChatResponse>,
    aplicar: (ChatResponse) -> Unit,
    esperar: suspend (Long) -> Unit = { delay(it) },
    maxDuracionMs: Long = CHAT_TEXT_TURN_MAX_DURATION_MS,
    maxPolls: Int = 100,
    alPasarAEsperaLenta: () -> Unit = {}
) {
    var elapsed = 0L
    var index = 0
    var faseLenta = false
    while (index < maxPolls && elapsed < maxDuracionMs) {
        if (!pendiente()) return
        if (!faseLenta && elapsed >= CHAT_TEXT_TURN_MAX_DURATION_MS) {
            faseLenta = true
            alPasarAEsperaLenta()
        }
        var lectura: ChatResponse? = null
        obtener(chatId, 2_000L).onSuccess { lectura = it; aplicar(it) }.onFailure {
            if (it is CancellationException) throw it
        }
        if (pendiente()) {
            val proximaPausa = if (faseLenta) siguienteEsperaLentaMs(lectura) else siguienteEsperaMs(lectura, index)
            val limiteDuracion = if (faseLenta) maxDuracionMs else minOf(maxDuracionMs, CHAT_TEXT_TURN_MAX_DURATION_MS)
            val pausa = proximaPausa.coerceAtMost(limiteDuracion - elapsed)
            if (pausa <= 0) return
            esperar(pausa)
            elapsed += pausa
        }
        index++
    }
}
