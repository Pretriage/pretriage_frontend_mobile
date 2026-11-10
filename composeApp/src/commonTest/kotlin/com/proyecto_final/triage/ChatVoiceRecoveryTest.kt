package com.proyecto_final.triage

import com.proyecto_final.triage.network.ChatMensaje
import com.proyecto_final.triage.network.ChatResponse
import com.proyecto_final.triage.network.AtencionEstimada
import com.proyecto_final.triage.network.EventoVoz
import com.proyecto_final.triage.network.parsearEventoVoz
import com.proyecto_final.triage.storage.ChatConversationCache
import com.proyecto_final.triage.viewmodels.*
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.*

class ChatVoiceRecoveryTest {
    private val mensajes = listOf(ChatMensaje("Hola", "BOT"), ChatMensaje("Desde ayer", "PACIENTE"),
        ChatMensaje("¿Algo más?", "BOT"), ChatMensaje("No, nada.", "PACIENTE"))
    private fun pendiente() = ChatUiState(chatId = 37, mensajes = mensajes,
        finalizado = true, estadoClasificacion = "PENDIENTE")

    @Test
    fun avisoUrgenteTieneUnEventoPropioYExigeUnMensajeVisible() {
        assertEquals(
            EventoVoz.AvisoUrgente("Andá a una guardia."),
            parsearEventoVoz("""{"tipo":"aviso_urgente","mensaje":"Andá a una guardia."}""")
        )
        assertNull(parsearEventoVoz("""{"tipo":"aviso_urgente"}"""))
        assertNull(parsearEventoVoz("""{"tipo":"aviso_urgente","mensaje":" "}"""))
    }

    @Test
    fun avisoUrgentePermaneceDuranteCierreRecuperacionYFalloDelMismoChat() {
        val mensaje = "Andá a una guardia."
        val activa = ChatUiState(chatId = 37, mensajes = mensajes, estadoVoz = EstadoVoz.ESCUCHANDO)
        assertEquals(activa, activa.aplicarAvisoUrgenteVoz(36, mensaje))

        val avisada = activa.aplicarAvisoUrgenteVoz(37, mensaje)
        assertEquals(EstadoVoz.ESCUCHANDO, avisada.estadoVoz)
        assertEquals(mensajes, avisada.mensajes)
        assertFalse(avisada.finalizado)
        assertEquals(avisada, avisada.aplicarAvisoUrgenteVoz(37, mensaje))

        val pendiente = avisada.aplicarActualizacionTrasVoz(
            ChatResponse(37, mensajes, finalizado = true, estadoClasificacion = "PENDIENTE",
                estadoRecuperacion = "REINTENTANDO", versionEstado = 1)
        )
        val fallida = pendiente.aplicarActualizacionTrasVoz(
            ChatResponse(37, mensajes, finalizado = true, estadoClasificacion = "FALLIDA", versionEstado = 2)
        )
        val reintento = fallida.aplicarActualizacionTrasVoz(
            ChatResponse(37, mensajes, finalizado = true, estadoClasificacion = "PENDIENTE",
                estadoRecuperacion = "ESPERANDO_API", versionEstado = 3)
        )
        val completada = reintento.aplicarActualizacionTrasVoz(
            ChatResponse(37, mensajes, finalizado = true, estadoClasificacion = "COMPLETADA", versionEstado = 4)
        )
        for (estado in listOf(pendiente, fallida, reintento, completada)) {
            assertEquals(mensaje, estado.avisoUrgenteVoz)
        }
    }

    @Test
    fun cacheDeAvisoUrgenteSoloSeRestauraParaElChatActual() {
        val cache = ChatConversationCache()
        cache.guardar(ChatResponse(37, mensajes), null, "Andá a una guardia.")
        assertEquals("Andá a una guardia.", cache.recuperarAvisoUrgenteVoz(37))
        assertNull(cache.recuperarAvisoUrgenteVoz(36))

        cache.guardar(ChatResponse(38, emptyList()), null)
        assertNull(cache.recuperarAvisoUrgenteVoz(37))
        assertNull(cache.recuperarAvisoUrgenteVoz(38))

        cache.guardar(ChatResponse(38, emptyList()), null, "Andá a una guardia.")
        cache.limpiar()
        assertNull(cache.recuperarAvisoUrgenteVoz(38))
    }

    @Test
    fun volverDuranteElCierreConservaTodoElHistorialYNoReabreLaEntrevista() {
        val cache = ChatConversationCache()
        cache.guardar(ChatResponse(37, mensajes, finalizado = true, estadoClasificacion = "PENDIENTE"), null)
        val (snapshot, _) = assertNotNull(cache.recuperar(37))
        val restaurado = ChatUiState(chatId = snapshot.id, mensajes = snapshot.mensajes,
            finalizado = snapshot.finalizado, estadoClasificacion = snapshot.estadoClasificacion)
        val actualizado = restaurado.aplicarActualizacionTrasVoz(
            ChatResponse(37, mensajes.take(1), finalizado = false, estadoClasificacion = "NO_SOLICITADA"))
        assertEquals(mensajes, actualizado.mensajes)
        assertTrue(actualizado.finalizado)
        assertTrue(actualizado.esperandoResultadoCierre())
    }

    @Test
    fun unPendienteDelServidorConMenosMensajesTampocoRecortaElHistorial() {
        val actualizado = pendiente().aplicarActualizacionTrasVoz(
            ChatResponse(37, mensajes.take(1), finalizado = true, estadoClasificacion = "PENDIENTE"))
        assertEquals(mensajes, actualizado.mensajes)
        assertTrue(actualizado.esperandoResultadoCierre())
    }

    @Test
    fun estadoInicialNoSolicitadaPermiteHidratarPendienteOCompletada() {
        val inicial = ChatUiState(chatId = 37, mensajes = mensajes, finalizado = false)
        val pendiente = inicial.aplicarActualizacionTrasVoz(
            ChatResponse(37, mensajes, finalizado = true, estadoClasificacion = "PENDIENTE")
        )
        val completada = inicial.aplicarActualizacionTrasVoz(
            ChatResponse(37, mensajes.take(2), finalizado = true, estadoClasificacion = "COMPLETADA")
        )

        assertEquals("PENDIENTE", pendiente.estadoClasificacion)
        assertTrue(pendiente.esperandoResultadoCierre())
        assertEquals("COMPLETADA", completada.estadoClasificacion)
        assertTrue(completada.finalizado)
        assertEquals(mensajes.take(2), completada.mensajes)
    }

    @Test
    fun cierreAdministrativoNoSolicitadaPermaneceTerminalAnteGetPendienteAntiguo() {
        val administrativa = ChatUiState(
            chatId = 37, mensajes = mensajes, finalizado = true, estadoClasificacion = "NO_SOLICITADA"
        )
        val antigua = administrativa.aplicarActualizacionTrasVoz(
            ChatResponse(37, mensajes.take(1), finalizado = false, estadoClasificacion = "PENDIENTE")
        )
        assertEquals(administrativa, antigua)
        assertFalse(antigua.esperandoResultadoCierre())
    }

    @Test
    fun completadaReemplazaTranscripcionesLocalesYLimpiaAvisosDeVoz() {
        val remoto = mensajes + ChatMensaje("Gracias, registramos tus respuestas.", "BOT")
        val actualizado = pendiente().copy(error = "Se interrumpió la voz.", errorVoz = true)
            .aplicarActualizacionTrasVoz(ChatResponse(37, remoto, finalizado = true, estadoClasificacion = "COMPLETADA"))
        assertEquals(remoto, actualizado.mensajes)
        assertFalse(actualizado.esperandoResultadoCierre())
        assertNull(actualizado.error)
        assertFalse(actualizado.errorVoz)
        assertEquals(actualizado, actualizado.aplicarActualizacionTrasVoz(
            ChatResponse(37, mensajes.take(1), estadoClasificacion = "NO_SOLICITADA")))
    }

    @Test
    fun getCompletadaReemplazaSegmentacionLocalConHistorialTerminalCanonicoMasCorto() {
        val mensajesLocales = mensajes + ChatMensaje("Despedida parcial", "BOT")
        val estadoLocal = ChatUiState(
            chatId = 37, mensajes = mensajesLocales, finalizado = true, estadoClasificacion = "COMPLETADA",
            estadoVoz = EstadoVoz.PROCESANDO, error = "Se interrumpió la voz."
        )
        val canonicos = mensajes.take(3)
        val actualizado = estadoLocal.aplicarActualizacionTrasVoz(
            ChatResponse(37, canonicos, finalizado = true, estadoClasificacion = "COMPLETADA")
        )
        assertEquals(canonicos, actualizado.mensajes)
        assertEquals(EstadoVoz.INACTIVA, actualizado.estadoVoz)
        assertNull(actualizado.error)
    }

    @Test
    fun falloTerminalNoMantieneSpinnerNiOfreceRecuperacionAlPaciente() {
        val remoto = ChatResponse(37, mensajes, finalizado = true, estadoClasificacion = "FALLIDA")
        val estado = pendiente().copy(estadoVoz = EstadoVoz.PROCESANDO).aplicarActualizacionTrasVoz(remoto)
        assertEquals("FALLIDA", estado.estadoClasificacion)
        assertEquals(ChatClassificationPresentation.FALLIDA,
            presentacionClasificacionChat(estado.finalizado, estado.estadoClasificacion))
        assertEquals(EstadoVoz.INACTIVA, estado.estadoVoz)
        assertFalse(estado.esperandoResultadoCierre())
        assertFalse(estado.actualizacionCierreDisponible)
        assertEquals(mensajes, estado.mensajes)
    }

    @Test
    fun procesamientoFinalizadoParcialCierraVozSinPresentarTriageCompleto() {
        val remoto = ChatResponse(37, mensajes + ChatMensaje("Tus respuestas quedaron guardadas.", "BOT"),
            finalizado = true, estadoClasificacion = "PARCIAL", cierreParcial = true,
            estadoTurnoTexto = "COMPLETADA", estadoRecuperacion = "SIN_RECUPERACION", versionEstado = 8)
        val estado = pendiente().copy(estadoVoz = EstadoVoz.PROCESANDO,
            atencionEstimada = AtencionEstimada(posicionEnCola = 1))
            .aplicarActualizacionTrasVoz(remoto)

        assertEquals("PARCIAL", estado.estadoClasificacion)
        assertTrue(estado.cierreParcial)
        assertFalse(estado.esperandoResultadoCierre())
        assertEquals(EstadoVoz.INACTIVA, estado.estadoVoz)
        assertEquals(ChatClassificationPresentation.PARCIAL,
            presentacionClasificacionChat(estado.finalizado, estado.estadoClasificacion))
        assertNull(estado.atencionEstimada)
    }

    @Test
    fun eventoProcesamientoFinalizadoParseaSnapshotAditivoDelChat() {
        val evento = parsearEventoVoz("""{"tipo":"procesamiento_finalizado","chat":{"id":37,"mensajes":[],"finalizado":true,"estadoClasificacion":"PARCIAL","cierreParcial":true,"estadoRecuperacion":"SIN_RECUPERACION","versionEstado":9}}""")
        val resultado = assertIs<EventoVoz.ProcesamientoFinalizado>(evento)
        assertTrue(resultado.chat.cierreParcial)
        assertEquals(9L, resultado.chat.versionEstado)
        assertEquals("PARCIAL", resultado.chat.estadoClasificacion)
    }

    @Test
    fun eventoEstadoChatParseaSnapshotDeTurnoPendienteSinImplicarCierre() {
        val evento = parsearEventoVoz("""{"tipo":"estado_chat","chat":{"id":37,"mensajes":[],"finalizado":false,"estadoClasificacion":"NO_SOLICITADA","estadoTurnoTexto":"PENDIENTE","estadoRecuperacion":"ESPERANDO_API","solicitudTurnoTextoId":"req-1","versionEstado":10}}""")
        val resultado = assertIs<EventoVoz.EstadoChat>(evento)
        assertFalse(resultado.chat.finalizado)
        assertEquals("ESPERANDO_API", resultado.chat.estadoRecuperacion)
        assertEquals("req-1", resultado.chat.solicitudTurnoTextoId)
        assertEquals(10L, resultado.chat.versionEstado)
    }

    @Test
    fun unGetAntiguoPosteriorAlFalloNoLoReviveNiRecortaElHistorial() {
        val fallida = pendiente().aplicarActualizacionTrasVoz(
            ChatResponse(37, mensajes, finalizado = true, estadoClasificacion = "FALLIDA")
        )
        val antigua = fallida.aplicarActualizacionTrasVoz(
            ChatResponse(37, mensajes.take(1), finalizado = true, estadoClasificacion = "PENDIENTE")
        )
        assertEquals("FALLIDA", antigua.estadoClasificacion)
        assertFalse(antigua.esperandoResultadoCierre())
        assertEquals(mensajes, antigua.mensajes)
    }

    @Test
    fun completadaAutoritativaPuedeCorregirUnFalloPersistido() {
        val fallida = pendiente().aplicarActualizacionTrasVoz(
            ChatResponse(37, mensajes, finalizado = true, estadoClasificacion = "FALLIDA")
        )
        val canonica = mensajes.take(2)
        val completada = fallida.aplicarActualizacionTrasVoz(
            ChatResponse(37, canonica, finalizado = true, estadoClasificacion = "COMPLETADA")
        )
        assertEquals("COMPLETADA", completada.estadoClasificacion)
        assertEquals(canonica, completada.mensajes)
        assertFalse(completada.esperandoResultadoCierre())
    }

    @Test
    fun limiteSinResultadoSaleDelSpinnerYSiguePermitiendoSoloConsultarEstado() {
        val enCurso = pendiente().copy(estadoVoz = EstadoVoz.PROCESANDO, procesandoRespuestaVoz = true)
        for (remoto in listOf(
            null,
            ChatResponse(37, mensajes.take(1), finalizado = true, estadoClasificacion = "PENDIENTE")
        )) {
            val estado = enCurso.aplicarLimiteEsperaCierre(remoto)
            assertNull(estado.estadoClasificacion)
            assertEquals(EstadoVoz.INACTIVA, estado.estadoVoz)
            assertTrue(estado.finalizado)
            assertTrue(estado.actualizacionCierreDisponible)
            assertFalse(estado.procesandoRespuestaVoz)
            assertEquals(mensajes, estado.mensajes)
            assertEquals(ChatClassificationPresentation.CERRADA_SIN_RESULTADO,
                presentacionClasificacionChat(estado.finalizado, estado.estadoClasificacion))
        }
    }

    @Test
    fun cacheSoloRecuperaElMismoChatYSeBorraAlLimpiarSesion() {
        val cache = ChatConversationCache()
        cache.guardar(ChatResponse(37, mensajes), null)
        assertNull(cache.recuperar(36))
        cache.limpiar()
        assertNull(cache.recuperar(37))
        assertEquals(pendiente(), pendiente().aplicarActualizacionTrasVoz(ChatResponse(36, emptyList())))
    }

    @Test
    fun seguimientoMantienePendienteDuranteReintentosDelServidorYRecuperaCompletadaSinReenviar() {
        var estado = pendiente()
        var lecturas = 0
        var pausas = 0
        runSuspend {
            seguirResultadoCierre(37, { estado.esperandoResultadoCierre() },
                obtener = { id, timeout ->
                    assertEquals(37, id)
                    assertEquals(2_000L, timeout)
                    when (++lecturas) {
                        1 -> Result.success(ChatResponse(37, mensajes.take(1), finalizado = true, estadoClasificacion = "PENDIENTE"))
                        2 -> Result.success(ChatResponse(37, mensajes, finalizado = true, estadoClasificacion = "PENDIENTE"))
                        3 -> Result.failure(IllegalStateException("sin conexión"))
                        4 -> Result.success(ChatResponse(37, mensajes, finalizado = true, estadoClasificacion = "PENDIENTE"))
                        else -> Result.success(ChatResponse(37, mensajes + ChatMensaje("Listo", "BOT"), finalizado = true, estadoClasificacion = "COMPLETADA"))
                    }
                }, aplicar = { estado = estado.aplicarActualizacionTrasVoz(it) },
                esperar = { pausas++ })
        }
        assertEquals(5, lecturas)
        assertEquals(4, pausas)
        assertEquals("COMPLETADA", estado.estadoClasificacion)
        assertEquals(mensajes + ChatMensaje("Listo", "BOT"), estado.mensajes)
    }

    @Test
    fun marcaCierreParcialNoDetienePollingMientrasClasificacionSiguePendiente() {
        var estado = pendiente().copy(versionEstado = 23)
        var lecturas = 0
        runSuspend {
            seguirResultadoCierre(
                37,
                pendiente = { estado.esperandoResultadoCierre() },
                obtener = { _, _ ->
                    when (++lecturas) {
                        1 -> Result.success(ChatResponse(37, mensajes, finalizado = true,
                            estadoClasificacion = "PENDIENTE", cierreParcial = true,
                            estadoTurnoTexto = "COMPLETADA", estadoRecuperacion = "REINTENTANDO",
                            versionEstado = 24))
                        else -> Result.success(ChatResponse(37, mensajes + ChatMensaje("Tus respuestas quedaron guardadas.", "BOT"),
                            finalizado = true, estadoClasificacion = "PARCIAL", cierreParcial = true,
                            estadoTurnoTexto = "COMPLETADA", estadoRecuperacion = "SIN_RECUPERACION",
                            versionEstado = 25))
                    }
                },
                aplicar = { chat ->
                    estado = estado.aplicarActualizacionTrasVoz(chat).aplicarEstadoTurnoTexto(chat)
                },
                esperar = {}
            )
        }
        assertEquals(2, lecturas)
        assertEquals("PARCIAL", estado.estadoClasificacion)
        assertEquals(25L, estado.versionEstado)
        assertFalse(estado.esperandoResultadoCierre())
        assertTrue(estado.finalizado)
    }

    @Test
    fun seguimientoCierreApagaConfirmacionRapidaPeroSigueConsultandoEnFaseLenta() {
        var lecturas = 0
        var inicioFaseLenta = 0
        val pausas = mutableListOf<Long>()
        runSuspend {
            seguirResultadoCierre(
                37,
                pendiente = { true },
                obtener = { _, _ ->
                    lecturas++
                    Result.success(ChatResponse(37, mensajes, finalizado = true,
                        estadoClasificacion = "PENDIENTE", estadoRecuperacion = "ESPERANDO_API"))
                },
                aplicar = {},
                esperar = { pausas += it },
                maxDuracionMs = 215_000L,
                maxPolls = 20,
                alPasarAEsperaLenta = { inicioFaseLenta++ }
            )
        }
        assertEquals(1, inicioFaseLenta)
        assertTrue(lecturas > 1)
        assertTrue(pausas.contains(30_000L))
        assertTrue(pausas.contains(15_000L))
    }

    @Test
    fun seguimientoSeAcotaCuandoElServidorNoConfirmaElResultado() {
        var lecturas = 0
        runSuspend {
            seguirResultadoCierre(37, { true }, obtener = { _, _ ->
                lecturas++
                Result.failure(IllegalStateException("sin conexión"))
            }, aplicar = { error("No hay resultado para aplicar") }, esperar = {})
        }
        assertTrue(lecturas in 20..100)
    }

    @Test
    fun cancelarAlSalirNoSeConvierteEnErrorDeVozNiContinuaConsultando() {
        var lecturas = 0
        assertFailsWith<CancellationException> {
            runSuspend {
                seguirResultadoCierre(37, { true }, obtener = { _, _ ->
                    lecturas++
                    Result.failure(CancellationException("salir"))
                }, aplicar = { error("No debe aplicar") }, esperar = { error("No debe esperar") })
            }
        }
        assertEquals(1, lecturas)
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
