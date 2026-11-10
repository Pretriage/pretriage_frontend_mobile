package com.proyecto_final.triage.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Instant

internal const val CHAT_TEXT_TURN_POLL_INTERVAL_MS = 1_500L
internal const val CHAT_TEXT_TURN_MAX_POLLS = 100
internal const val CHAT_TEXT_TURN_MAX_DURATION_MS = 200_000L

/** Polls durable turn state using GET only, long enough for both backend recovery attempts. */
internal suspend fun seguirTurnoTexto(
    chatId: Long,
    siguePendiente: () -> Boolean,
    obtener: suspend (Long, Long) -> Result<ChatResponse>,
    aplicar: (ChatResponse) -> Unit,
    esperar: suspend (Long) -> Unit = { delay(it) },
    maxDuracionMs: Long = CHAT_TEXT_TURN_MAX_DURATION_MS,
    maxPolls: Int = CHAT_TEXT_TURN_MAX_POLLS,
    alPasarAEsperaLenta: () -> Unit = {}
): ChatResponse? {
    var ultimaLectura: ChatResponse? = null
    var elapsed = 0L
    var index = 0
    var faseLenta = false
    while (index < maxPolls && elapsed < maxDuracionMs) {
        if (!siguePendiente()) return ultimaLectura
        if (!faseLenta && elapsed >= CHAT_TEXT_TURN_MAX_DURATION_MS) {
            faseLenta = true
            alPasarAEsperaLenta()
        }
        val resultado = try {
            obtener(chatId, 2_000L)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        resultado?.onSuccess { chat ->
            ultimaLectura = chat
            aplicar(chat)
        }?.onFailure { error -> if (error is CancellationException) throw error }
        if (!siguePendiente()) return ultimaLectura
        val ultimo = ultimaLectura
        val proximaEspera = if (faseLenta) siguienteEsperaLentaMs(ultimo) else siguienteEsperaMs(ultimo, index)
        val limiteDuracion = if (faseLenta) maxDuracionMs else minOf(maxDuracionMs, CHAT_TEXT_TURN_MAX_DURATION_MS)
        val espera = proximaEspera.coerceAtMost(limiteDuracion - elapsed)
        if (espera <= 0) break
        esperar(espera)
        elapsed += espera
        index++
    }
    return ultimaLectura
}

@OptIn(kotlin.time.ExperimentalTime::class)
internal fun siguienteEsperaLentaMs(chat: ChatResponse?): Long {
    val reintento = chat?.proximoReintentoEn?.let { texto ->
        runCatching {
            val instante = Instant.parse(texto)
            (instante - Clock.System.now()).inWholeMilliseconds
        }.getOrNull()
    }
    return if (chat?.estadoRecuperacion.equals("ESPERANDO_API", true) && reintento != null && reintento > 0) {
        minOf(reintento, 30_000L).coerceAtLeast(1_000L)
    } else if (chat?.estadoRecuperacion.equals("ESPERANDO_API", true)) {
        30_000L
    } else 15_000L
}

@OptIn(kotlin.time.ExperimentalTime::class)
internal fun siguienteEsperaMs(chat: ChatResponse?, pollIndex: Int): Long {
    val local = when (pollIndex) {
        0, 1 -> CHAT_TEXT_TURN_POLL_INTERVAL_MS
        2 -> 3_000L
        3 -> 5_000L
        else -> 8_000L
    }
    val reintento = chat?.proximoReintentoEn?.let { texto ->
        runCatching {
            val instante = Instant.parse(texto)
            (instante - Clock.System.now()).inWholeMilliseconds
        }.getOrNull()
    }
    if (chat?.estadoRecuperacion.equals("ESPERANDO_API", true)) {
        return if (reintento != null && reintento > 0) {
            minOf(reintento, 30_000L).coerceAtLeast(1_000L)
        } else maxOf(local, 30_000L)
    }
    return local
}

internal fun ChatResponse.turnoTextoPendiente(): Boolean =
    estadoTurnoTexto.equals("PENDIENTE", ignoreCase = true) ||
        estadoRecuperacion.equals("REINTENTANDO", ignoreCase = true) ||
        estadoRecuperacion.equals("ESPERANDO_API", ignoreCase = true)

internal fun ChatResponse.turnoTextoNoDisponible(): Boolean =
    estadoTurnoTexto.equals("NO_DISPONIBLE", ignoreCase = true)

