package com.proyecto_final.triage

import com.proyecto_final.triage.network.SancionPacienteDTO
import com.proyecto_final.triage.network.TipoAvisoSancion
import com.proyecto_final.triage.network.leerRechazoPorSancion
import com.proyecto_final.triage.viewmodels.mensajeInscripcionRemota
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SancionPacienteTest {
    @Test
    fun reconoceLaSancionAplicadaEntreLaConsultaYElIngreso() {
        val sancion = leerRechazoPorSancion(
            HttpStatusCode.Forbidden,
            """{"error":"Inscripción restringida","codigo":"PATIENT_SANCTIONED","vigente":true,"permanente":false,"fechaFin":"2027-05-10T01:00:00Z","avisoId":"nuevo","tipoAviso":"APLICADA"}"""
        )

        assertNotNull(sancion)
        assertEquals("nuevo", sancion.avisoId)
        assertEquals(TipoAvisoSancion.APLICADA, sancion.tipoAviso)
        val mensaje = assertNotNull(sancion.mensajeInscripcionRemota())
        assertContains(mensaje, "hasta el 09/05/2027")
        assertContains(mensaje, "recepción del hospital")
    }

    @Test
    fun distingueUnRechazoAjenoALaSancion() {
        assertNull(leerRechazoPorSancion(HttpStatusCode.Forbidden, """{"error":"Sin acceso"}"""))
        assertNull(leerRechazoPorSancion(
            HttpStatusCode.BadRequest,
            """{"codigo":"PATIENT_SANCTIONED","vigente":true}"""
        ))
    }

    @Test
    fun sancionPermanenteNoTieneFechaYLevantadaNoRestringeLaInscripcion() {
        assertEquals(
            "Por declaraciones de síntomas inconsistentes, no podés anotarte en la cola desde la app. " +
                "Podés atenderte igualmente acercándote a la recepción del hospital.",
            SancionPacienteDTO(vigente = true, permanente = true).mensajeInscripcionRemota()
        )
        assertNull(
            SancionPacienteDTO(vigente = false, tipoAviso = TipoAvisoSancion.LEVANTADA)
                .mensajeInscripcionRemota()
        )
    }
}
