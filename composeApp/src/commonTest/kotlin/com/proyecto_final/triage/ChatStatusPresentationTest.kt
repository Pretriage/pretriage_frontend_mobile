package com.proyecto_final.triage

import com.proyecto_final.triage.audio.ModoEntradaVoz
import com.proyecto_final.triage.network.EventoVoz
import com.proyecto_final.triage.network.parsearEventoVoz
import com.proyecto_final.triage.viewmodels.CambioProgresoVoz
import com.proyecto_final.triage.viewmodels.ChatClassificationPresentation
import com.proyecto_final.triage.viewmodels.EstadoEntrevistaVozUi
import com.proyecto_final.triage.viewmodels.EstadoVoz
import com.proyecto_final.triage.viewmodels.ProgresoVozUiState
import com.proyecto_final.triage.viewmodels.TransicionCierreVoz
import com.proyecto_final.triage.viewmodels.aplicarCambioProgresoVoz
import com.proyecto_final.triage.viewmodels.aplicarTransicionCierreVoz
import com.proyecto_final.triage.viewmodels.presentarErrorVoz
import com.proyecto_final.triage.viewmodels.presentacionClasificacionChat
import com.proyecto_final.triage.viewmodels.textoPanelVoz
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatStatusPresentationTest {
    @Test
    fun estadosDeClasificacionSeSeparanDelCierreDeEntrevista() {
        assertEquals(ChatClassificationPresentation.NO_SOLICITADA, presentacionClasificacionChat(false, "NO_SOLICITADA"))
        assertEquals(ChatClassificationPresentation.PROCESANDO, presentacionClasificacionChat(true, "PENDIENTE"))
        assertEquals(ChatClassificationPresentation.FALLIDA, presentacionClasificacionChat(true, "FALLIDA"))
        assertEquals(ChatClassificationPresentation.PARCIAL, presentacionClasificacionChat(true, "PARCIAL"))
        assertEquals(ChatClassificationPresentation.COMPLETADA, presentacionClasificacionChat(true, "COMPLETADA"))
    }

    @Test
    fun cierreAdministrativoSinDatosPermaneceComoNoSolicitada() {
        assertEquals(ChatClassificationPresentation.NO_SOLICITADA,
            presentacionClasificacionChat(true, "NO_SOLICITADA"))
    }

    @Test
    fun unaRespuestaLegacySinEstadoNoTomaCierreComoExito() {
        assertEquals(ChatClassificationPresentation.CERRADA_SIN_RESULTADO, presentacionClasificacionChat(true, null))
    }

    @Test
    fun eventoDeProgresoDeRespuestaDecodificaAmbosEstadosYRechazaUnPayloadIncompleto() {
        assertEquals(
            EventoVoz.ProcesandoRespuesta(true),
            parsearEventoVoz("""{"tipo":"procesando_respuesta","activo":true}""")
        )
        assertEquals(
            EventoVoz.ProcesandoRespuesta(false),
            parsearEventoVoz("""{"tipo":"procesando_respuesta","activo":false}""")
        )
        assertNull(parsearEventoVoz("""{"tipo":"procesando_respuesta"}"""))
    }

    @Test
    fun panelVozDistingueProcesamientoPreguntaListaYRespuestaDelAsistente() {
        assertEquals(
            "Procesando tu respuesta...",
            textoPanelVoz(EstadoVoz.ESCUCHANDO, ModoEntradaVoz.POR_TURNOS, true, false)
        )
        assertEquals(
            "Te escuchamos. Podés responder cuando quieras.",
            textoPanelVoz(EstadoVoz.ESCUCHANDO, ModoEntradaVoz.POR_TURNOS, false, false)
        )
        assertEquals(
            "El asistente está hablando. Podés interrumpirlo.",
            textoPanelVoz(EstadoVoz.ESCUCHANDO, ModoEntradaVoz.INTERRUMPIBLE, false, true)
        )
        assertEquals(
            "Guardando tus respuestas...",
            textoPanelVoz(EstadoVoz.PROCESANDO, ModoEntradaVoz.POR_TURNOS, false, false)
        )
    }

    @Test
    fun progresoControladoPorBackendNoSeLimpiaConTranscripcionNiFinDeTurno() {
        var progreso = aplicarCambioProgresoVoz(
            ProgresoVozUiState(),
            CambioProgresoVoz.RESPUESTA_PACIENTE
        )
        progreso = aplicarCambioProgresoVoz(progreso, CambioProgresoVoz.SERVIDOR_PROCESANDO)
        // A second STT fragment must not reset the authority granted by the first backend event.
        progreso = aplicarCambioProgresoVoz(progreso, CambioProgresoVoz.RESPUESTA_PACIENTE)
        progreso = aplicarCambioProgresoVoz(progreso, CambioProgresoVoz.ASISTENTE_HABLANDO)
        progreso = aplicarCambioProgresoVoz(progreso, CambioProgresoVoz.ASISTENTE_TERMINO)

        assertEquals(true, progreso.procesandoRespuesta)
        assertEquals(true, progreso.controladoPorServidor)
        assertEquals(false, progreso.asistenteHablando)
        assertEquals(
            "Procesando tu respuesta...",
            textoPanelVoz(EstadoVoz.ESCUCHANDO, ModoEntradaVoz.POR_TURNOS, progreso.procesandoRespuesta, progreso.asistenteHablando)
        )

        progreso = aplicarCambioProgresoVoz(progreso, CambioProgresoVoz.SERVIDOR_ESPERA_PACIENTE)

        assertEquals(false, progreso.procesandoRespuesta)
        assertEquals(true, progreso.controladoPorServidor)
        assertEquals(
            "Te escuchamos. Podés responder cuando quieras.",
            textoPanelVoz(EstadoVoz.ESCUCHANDO, ModoEntradaVoz.POR_TURNOS, progreso.procesandoRespuesta, progreso.asistenteHablando)
        )
    }

    @Test
    fun cierreAceptadoPermaneceProcesandoYSoloAlTerminarSinResultadoPasaADesconocido() {
        var estado = EstadoEntrevistaVozUi(finalizado = false, estadoClasificacion = "NO_SOLICITADA")

        estado = aplicarTransicionCierreVoz(estado, TransicionCierreVoz.CIERRE_ACEPTADO)

        assertEquals(true, estado.finalizado)
        assertEquals("PENDIENTE", estado.estadoClasificacion)
        assertEquals(
            ChatClassificationPresentation.PROCESANDO,
            presentacionClasificacionChat(estado.finalizado, estado.estadoClasificacion)
        )

        estado = aplicarTransicionCierreVoz(estado, TransicionCierreVoz.RESULTADO_NO_CONFIRMADO)

        assertEquals(true, estado.finalizado)
        assertNull(estado.estadoClasificacion)
        assertEquals(
            ChatClassificationPresentation.CERRADA_SIN_RESULTADO,
            presentacionClasificacionChat(estado.finalizado, estado.estadoClasificacion)
        )
    }

    @Test
    fun errorDeVozOfreceRetomarSoloSiLaEntrevistaSigueAbierta() {
        val abierta = presentarErrorVoz("Se interrumpió la conexión.", entrevistaCerrada = false)
        assertEquals(true, abierta.permiteRetomar)
        assertEquals("Se interrumpió la voz.", abierta.mensaje)

        val cerrada = presentarErrorVoz("No se pudo completar el procesamiento.", entrevistaCerrada = true)
        assertEquals(false, cerrada.permiteRetomar)
        assertEquals("No se pudo completar el procesamiento.", cerrada.mensaje)
    }

    @Test
    fun falloDeEnvioNoEsFalloDeClasificacionHastaConfirmarRespuestasGuardadas() {
        // A failed POST with an open interview remains an ordinary message-send failure.
        assertEquals(ChatClassificationPresentation.NO_SOLICITADA, presentacionClasificacionChat(false, "NO_SOLICITADA"))
        // GET confirming a closed interview and FALLIDA selects the saved-answer retry card.
        assertEquals(ChatClassificationPresentation.FALLIDA, presentacionClasificacionChat(true, "FALLIDA"))
    }
}
