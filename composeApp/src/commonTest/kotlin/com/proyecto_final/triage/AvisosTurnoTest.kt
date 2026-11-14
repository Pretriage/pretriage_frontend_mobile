package com.proyecto_final.triage

import com.proyecto_final.triage.network.estadoConsulta.CambioEstadoConsultaDTO
import com.proyecto_final.triage.notificaciones.avisoPara
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvisosTurnoTest {

    @Test
    fun avisaDondePresentarseCuandoLoLlaman() {
        val aviso = assertNotNull(
            avisoPara(
                CambioEstadoConsultaDTO(
                    estado = "LLAMADO",
                    nombreSector = "Guardia de Adultos",
                    nombreSala = "Consultorio 2"
                )
            )
        )
        assertEquals("Te están llamando", aviso.titulo)
        assertEquals("Acercate al Consultorio 2 de Guardia de Adultos.", aviso.texto)
    }

    @Test
    fun avisaElPlazoCuandoNoLoEncontraron() {
        val aviso = assertNotNull(
            avisoPara(
                CambioEstadoConsultaDTO(
                    estado = "EN_ESPERA",
                    tipoPausa = "AUSENTE_AL_LLAMADO",
                    fechaHoraLimiteRespuesta = "2026-11-14T15:40:00"
                )
            )
        )
        assertEquals("No te encontramos cuando te llamaron", aviso.titulo)
        assertTrue(aviso.texto.contains("15:40"))
    }

    @Test
    fun avisaCuandoSeCancelaElTurno() {
        val aviso = assertNotNull(
            avisoPara(CambioEstadoConsultaDTO(estado = "CANCELADA", nombreHospital = "Hospital Fernández"))
        )
        assertEquals("Tu turno se canceló", aviso.titulo)
        assertTrue(aviso.texto.contains("Hospital Fernández"))
    }

    @Test
    fun noNotificaLosDemasCambios() {
        assertNull(avisoPara(CambioEstadoConsultaDTO(estado = "EN_COLA")))
        assertNull(avisoPara(CambioEstadoConsultaDTO(estado = "EN_ATENCION")))
        assertNull(avisoPara(CambioEstadoConsultaDTO(estado = "FINALIZADA")))
        assertNull(avisoPara(CambioEstadoConsultaDTO(estado = "EN_ESPERA", tipoPausa = "ESPERA_MANUAL")))
    }
}
