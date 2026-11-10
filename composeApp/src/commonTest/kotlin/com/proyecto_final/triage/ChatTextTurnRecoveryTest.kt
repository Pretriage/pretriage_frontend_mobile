package com.proyecto_final.triage

import com.proyecto_final.triage.network.ChatMensaje
import com.proyecto_final.triage.network.ChatResponse
import com.proyecto_final.triage.network.AtencionEstimada
import com.proyecto_final.triage.network.RespuestaChatResponse
import com.proyecto_final.triage.network.ResultadoEnvioChat
import com.proyecto_final.triage.network.ResultadoPostMensaje
import com.proyecto_final.triage.network.enviarConReconciliacion
import com.proyecto_final.triage.network.seguirTurnoTexto
import com.proyecto_final.triage.storage.ChatConversationCache
import com.proyecto_final.triage.viewmodels.ChatUiState
import com.proyecto_final.triage.viewmodels.ChatClassificationPresentation
import com.proyecto_final.triage.viewmodels.aplicarChatReconciliado
import com.proyecto_final.triage.viewmodels.aplicarEstadoTurnoTexto
import com.proyecto_final.triage.viewmodels.presentacionClasificacionChat
import com.proyecto_final.triage.viewmodels.turnoTextoBloqueaChat
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class ChatTextTurnRecoveryTest {
    private val requestId = "6b0eab0e-7704-4fbf-b548-bcc9691ab201"
    private val patient = ChatMensaje("Me duele la cabeza", "PACIENTE")

    @Test
    fun accepted202UsesCanonicalPatientSnapshotWithoutAddingFakeBotBubble() = runSuspend {
        val accepted = ChatResponse(
            id = 9, mensajes = listOf(patient), estadoTurnoTexto = "PENDIENTE",
            solicitudTurnoTextoId = requestId, intentosTurnoTexto = 1
        )
        var posts = 0
        val result = enviarConReconciliacion(
            chatId = 9, contenido = patient.contenido, solicitudId = requestId, mensajesAntes = emptyList(),
            enviar = { id, text, uuid ->
                assertEquals(9L, id); assertEquals(patient.contenido, text); assertEquals(requestId, uuid)
                posts++
                Result.success(ResultadoPostMensaje.TurnoAceptado(accepted))
            },
            obtener = { _, _ -> error("202 must not trigger reconciliation GET") },
            esperar = { }
        )
        val confirmed = assertIs<ResultadoEnvioChat.Confirmado>(result)
        val state = ChatUiState(chatId = 9, mensajes = listOf(patient), enviando = true)
            .aplicarChatReconciliado(confirmed.chat, confirmed.procesando)

        assertEquals(1, posts)
        assertTrue(state.respuestaPendiente)
        assertEquals(listOf(patient), state.mensajes)
        assertEquals(1, state.mensajes.size)
    }

    @Test
    fun reopenedPendingTurnRestoresProgressAndLocksBothInputs() {
        val cache = ChatConversationCache()
        val pending = ChatResponse(9, listOf(patient), estadoTurnoTexto = "PENDIENTE",
            solicitudTurnoTextoId = requestId, intentosTurnoTexto = 2)
        cache.guardar(pending, null)
        cache.guardarSolicitudTexto(9, requestId, patient.contenido)
        val (snapshot, _) = assertNotNull(cache.recuperar(9))
        val request = assertNotNull(cache.recuperarSolicitudTexto(9))
        val state = ChatUiState(chatId = snapshot.id, mensajes = snapshot.mensajes,
            solicitudTextoPendienteId = request.first, contenidoSolicitudTextoPendiente = request.second,
            estadoTurnoTexto = snapshot.estadoTurnoTexto, respuestaPendiente = true)

        assertTrue(state.respuestaPendiente)
        assertTrue(state.turnoTextoBloqueaChat)
        assertEquals(listOf(patient), state.mensajes)
        assertTrue(state.copy(respuestaPendiente = false, turnoTextoDesconocido = true).turnoTextoBloqueaChat)
    }

    @Test
    fun terminalStatusArrivingWhileAwayReplacesHistoryAndSeparatesInterviewClosure() {
        val local = ChatUiState(chatId = 9, mensajes = listOf(patient), solicitudTextoPendienteId = requestId,
            contenidoSolicitudTextoPendiente = patient.contenido, respuestaPendiente = true)
        val completedHistory = listOf(patient, ChatMensaje("¿Desde cuándo?", "BOT"))
        val completed = local.aplicarEstadoTurnoTexto(ChatResponse(9, completedHistory,
            finalizado = false, estadoClasificacion = "NO_SOLICITADA", estadoTurnoTexto = "COMPLETADA",
            solicitudTurnoTextoId = requestId))
        val unavailable = local.aplicarEstadoTurnoTexto(ChatResponse(9, listOf(patient),
            finalizado = false, estadoClasificacion = "NO_SOLICITADA", estadoTurnoTexto = "NO_DISPONIBLE"))

        assertEquals(completedHistory, completed.mensajes)
        assertFalse(completed.respuestaPendiente)
        assertFalse(completed.finalizado)
        assertFalse(completed.turnoTextoBloqueaChat)
        assertNull(completed.solicitudTextoPendienteId)
        assertFalse(unavailable.respuestaPendiente)
        assertFalse(unavailable.finalizado)
        assertFalse(unavailable.turnoTextoBloqueaChat)
        assertFalse(unavailable.respuestaPendiente)
        assertNull(unavailable.solicitudTextoPendienteId)
        assertEquals("NO_SOLICITADA", unavailable.estadoClasificacion)
    }

    @Test
    fun estadoRecuperacionConVersionMasNuevaReemplazaNoDisponibleCacheado() {
        val cacheado = ChatUiState(chatId = 9, mensajes = listOf(patient),
            solicitudTextoPendienteId = requestId, estadoTurnoTexto = "NO_DISPONIBLE",
            turnoTextoDesconocido = true, versionEstado = 3)
        val remoto = cacheado.aplicarEstadoTurnoTexto(ChatResponse(
            id = 9, mensajes = listOf(patient), estadoTurnoTexto = "PENDIENTE",
            solicitudTurnoTextoId = requestId, estadoRecuperacion = "REINTENTANDO", versionEstado = 4
        ))

        assertEquals("REINTENTANDO", remoto.estadoRecuperacion)
        assertEquals(4L, remoto.versionEstado)
        assertTrue(remoto.respuestaPendiente)
        assertTrue(remoto.turnoTextoBloqueaChat)
        assertNull(remoto.error)
    }

    @Test
    fun versionMasViejaNoReviveNoDisponibleNiRecortaMensajes() {
        val actual = ChatUiState(chatId = 9, mensajes = listOf(patient, ChatMensaje("¿Desde cuándo?", "BOT")),
            estadoTurnoTexto = "COMPLETADA", versionEstado = 7)
        val antigua = actual.aplicarEstadoTurnoTexto(ChatResponse(9, listOf(patient),
            estadoTurnoTexto = "NO_DISPONIBLE", versionEstado = 6))
        assertEquals(actual, antigua)
    }

    @Test
    fun cierreParcialEsTerminalSinPresentarTriageCompletoNiDejarTurnoBloqueado() {
        val estimate = AtencionEstimada(posicionEnCola = 1)
        val pendiente = ChatUiState(chatId = 9, mensajes = listOf(patient),
            solicitudTextoPendienteId = requestId, respuestaPendiente = true,
            estadoTurnoTexto = "COMPLETADA", versionEstado = 2, atencionEstimada = estimate)
        val parcial = pendiente.aplicarEstadoTurnoTexto(ChatResponse(
            id = 9, mensajes = listOf(patient, ChatMensaje("Tus respuestas quedaron guardadas.", "BOT")),
            finalizado = true, estadoClasificacion = "PARCIAL", estadoTurnoTexto = "COMPLETADA",
            cierreParcial = true, solicitudTurnoTextoId = requestId, estadoRecuperacion = "SIN_RECUPERACION",
            versionEstado = 3
        ))
        assertTrue(parcial.finalizado)
        assertTrue(parcial.cierreParcial)
        assertEquals("PARCIAL", parcial.estadoClasificacion)
        assertNull(parcial.atencionEstimada)
        assertFalse(parcial.turnoTextoBloqueaChat)
        assertNull(parcial.solicitudTextoPendienteId)
        assertEquals(ChatClassificationPresentation.PARCIAL,
            presentacionClasificacionChat(parcial.finalizado, parcial.estadoClasificacion))

        val reconciliado = pendiente.aplicarChatReconciliado(
            ChatResponse(9, listOf(patient), finalizado = true, estadoClasificacion = "PARCIAL"),
            procesando = true
        )
        assertNull(reconciliado.atencionEstimada)

        val estimacionValida = ChatUiState(chatId = 9, atencionEstimada = estimate)
            .aplicarEstadoTurnoTexto(ChatResponse(9, listOf(patient), finalizado = true,
                estadoClasificacion = "COMPLETADA", cierreParcial = true))
        assertEquals(estimate, estimacionValida.atencionEstimada)
    }

    @Test
    fun oldCompletedTurnCannotUnlockNewRequestWhileItsPostIsCommitting() {
        val currentId = "f51e3b89-6df2-497b-8e87-80fda0a04c19"
        val oldId = requestId
        val oldHistory = listOf(ChatMensaje("Pregunta anterior", "PACIENTE"), ChatMensaje("Respuesta anterior", "BOT"))
        val inFlight = ChatUiState(chatId = 9, mensajes = listOf(patient),
            solicitudTextoPendienteId = currentId, contenidoSolicitudTextoPendiente = patient.contenido,
            respuestaPendiente = true, siguiendoTurnoTexto = true, estadoTurnoTexto = "PENDIENTE")

        val staleCompleted = inFlight.aplicarEstadoTurnoTexto(ChatResponse(9, oldHistory,
            estadoTurnoTexto = "COMPLETADA", solicitudTurnoTextoId = oldId))
        val matchingPending = staleCompleted.aplicarEstadoTurnoTexto(ChatResponse(9, listOf(patient),
            estadoTurnoTexto = "PENDIENTE", solicitudTurnoTextoId = currentId))
        val matchingCompleted = matchingPending.aplicarEstadoTurnoTexto(ChatResponse(9,
            listOf(patient, ChatMensaje("¿Desde cuándo?", "BOT")), estadoTurnoTexto = "COMPLETADA",
            solicitudTurnoTextoId = currentId))

        assertTrue(staleCompleted.turnoTextoBloqueaChat)
        assertTrue(staleCompleted.turnoTextoDesconocido)
        assertEquals(listOf(patient), staleCompleted.mensajes)
        assertTrue(matchingPending.respuestaPendiente)
        assertTrue(matchingPending.turnoTextoBloqueaChat)
        assertFalse(matchingCompleted.turnoTextoBloqueaChat)
        assertFalse(matchingCompleted.respuestaPendiente)
        assertEquals("COMPLETADA", matchingCompleted.estadoTurnoTexto)
    }

    @Test
    fun completedInterviewAndCanonicalHistorySurviveOlderPendingGet() {
        val canonical = listOf(patient, ChatMensaje("¿Desde cuándo?", "BOT"), ChatMensaje("Desde ayer", "PACIENTE"))
        val completed = ChatUiState(chatId = 9, mensajes = canonical, finalizado = true,
            estadoClasificacion = "COMPLETADA", estadoTurnoTexto = "COMPLETADA")
        val stale = completed.aplicarEstadoTurnoTexto(ChatResponse(9, canonical.take(1), finalizado = true,
            estadoClasificacion = "PENDIENTE", estadoTurnoTexto = "PENDIENTE"))

        assertEquals(completed, stale)
        assertEquals(canonical, stale.mensajes)
        assertTrue(stale.finalizado)
        assertEquals("COMPLETADA", stale.estadoClasificacion)
    }

    @Test
    fun finalClassificationAttemptKeepsAcceptedTextTurnPendingUntilTerminalState() {
        val inFlight = ChatUiState(chatId = 9, mensajes = listOf(patient), finalizado = false,
            estadoClasificacion = "NO_SOLICITADA", estadoTurnoTexto = "PENDIENTE",
            solicitudTextoPendienteId = requestId, respuestaPendiente = true)
        val finalAttempt = inFlight.aplicarEstadoTurnoTexto(ChatResponse(9, listOf(patient),
            finalizado = true, estadoClasificacion = "PENDIENTE", estadoTurnoTexto = "PENDIENTE",
            solicitudTurnoTextoId = requestId, intentosTurnoTexto = 2))
        val failed = finalAttempt.aplicarEstadoTurnoTexto(ChatResponse(9, listOf(patient),
            finalizado = true, estadoClasificacion = "FALLIDA", estadoTurnoTexto = "PENDIENTE",
            solicitudTurnoTextoId = requestId, intentosTurnoTexto = 2))

        assertTrue(finalAttempt.finalizado)
        assertTrue(finalAttempt.respuestaPendiente)
        assertEquals(requestId, finalAttempt.solicitudTextoPendienteId)
        assertFalse(failed.respuestaPendiente)
        assertNull(failed.solicitudTextoPendienteId)
        assertEquals("FALLIDA", failed.estadoClasificacion)
    }

    @Test
    fun uncertainTurnPollHasHardRequestLimit() = runSuspend {
        var reads = 0
        val last = ChatResponse(9, listOf(patient), estadoTurnoTexto = "PENDIENTE", solicitudTurnoTextoId = requestId)
        val result = seguirTurnoTexto(
            chatId = 9,
            siguePendiente = { true },
            obtener = { _, _ -> reads++; Result.success(last) },
            aplicar = { },
            esperar = { }
        )
        assertTrue(reads in 20..100)
        assertEquals(last, result)
    }

    @Test
    fun seguimientoPasaALaFaseLentaYRespetaBackoffDeApi() = runSuspend {
        var slowPhaseStarted = 0
        val pausas = mutableListOf<Long>()
        seguirTurnoTexto(
            chatId = 9,
            siguePendiente = { true },
            obtener = { _, _ -> Result.success(ChatResponse(9, listOf(patient),
                estadoTurnoTexto = "PENDIENTE", estadoRecuperacion = "ESPERANDO_API")) },
            aplicar = { },
            esperar = { pausas += it },
            maxDuracionMs = 215_000L,
            maxPolls = 20,
            alPasarAEsperaLenta = { slowPhaseStarted++ }
        )

        assertEquals(1, slowPhaseStarted)
        assertTrue(pausas.contains(30_000L))
    }

    @Test
    fun legacy200ReplyRemainsSupportedWithoutStatusGet() = runSuspend {
        val reply = RespuestaChatResponse(ChatMensaje("¿Desde cuándo?", "BOT"))
        var gets = 0
        val result = enviarConReconciliacion(
            chatId = 9, contenido = patient.contenido, solicitudId = requestId, mensajesAntes = emptyList(),
            enviar = { _, _, _ -> Result.success(ResultadoPostMensaje.RespuestaInmediata(reply)) },
            obtener = { _, _ -> gets++; Result.failure(IllegalStateException("should not GET")) },
            esperar = { }
        )
        assertEquals(reply, assertIs<ResultadoEnvioChat.Respuesta>(result).respuesta)
        assertEquals(0, gets)
    }

    @Test
    fun legacyChatDtoWithoutTextFieldsStillDecodes() {
        val legacy = Json.decodeFromString<ChatResponse>("""{"id":9,"mensajes":[],"finalizado":false}""")
        assertNull(legacy.estadoTurnoTexto)
        assertNull(legacy.solicitudTurnoTextoId)
        assertEquals(0, legacy.intentosTurnoTexto)
        assertFalse(legacy.cierreParcial)
        assertEquals("SIN_RECUPERACION", legacy.estadoRecuperacion)
        assertEquals(0L, legacy.versionEstado)
    }

    @Test
    fun chatDtoAdditivoDecodificaSnapshotDeRecuperacionYParcial() {
        val chat = Json.decodeFromString<ChatResponse>("""{"id":9,"mensajes":[],"finalizado":true,"estadoClasificacion":"PARCIAL","cierreParcial":true,"estadoRecuperacion":"SIN_RECUPERACION","proximoReintentoEn":null,"versionEstado":11}""")
        assertTrue(chat.cierreParcial)
        assertEquals("SIN_RECUPERACION", chat.estadoRecuperacion)
        assertEquals(11L, chat.versionEstado)
        assertEquals("PARCIAL", chat.estadoClasificacion)
    }

    @Test
    fun snapshotCacheConservaVersionEstadoYFechaDeReintento() {
        val cache = ChatConversationCache()
        val snapshot = ChatResponse(9, listOf(patient), estadoTurnoTexto = "PENDIENTE",
            solicitudTurnoTextoId = requestId, estadoRecuperacion = "ESPERANDO_API",
            proximoReintentoEn = "2026-11-05T12:30:00Z", versionEstado = 42)
        cache.guardar(snapshot, null)

        val (restaurado, _) = assertNotNull(cache.recuperar(9))
        assertEquals(42L, restaurado.versionEstado)
        assertEquals("ESPERANDO_API", restaurado.estadoRecuperacion)
        assertEquals(snapshot.proximoReintentoEn, restaurado.proximoReintentoEn)
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        return result?.getOrThrow() ?: error("Unexpected suspension")
    }
}
