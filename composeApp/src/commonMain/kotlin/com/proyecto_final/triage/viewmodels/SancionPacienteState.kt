package com.proyecto_final.triage.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.proyecto_final.triage.network.SancionPacienteDTO
import com.proyecto_final.triage.network.consultarSancionPaciente
import com.proyecto_final.triage.storage.AvisosSancionStorage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal class SancionPacienteState {
    var datos by mutableStateOf<SancionPacienteDTO?>(null)
        private set

    var aviso by mutableStateOf<SancionPacienteDTO?>(null)
        private set

    private var revision = 0L

    suspend fun actualizar() {
        val revisionSolicitada = ++revision
        consultarSancionPaciente().onSuccess { estado ->
            if (revisionSolicitada == revision) registrar(estado)
        }
    }

    fun registrar(estado: SancionPacienteDTO) {
        revision++
        datos = estado
        aviso = estado.takeIf {
            val id = it.avisoId
            id != null && it.tipoAviso != null && !AvisosSancionStorage.fueVisto(id)
        }
    }

    fun descartarAviso() {
        aviso?.avisoId?.let(AvisosSancionStorage::marcarVisto)
        aviso = null
    }
}

internal fun SancionPacienteDTO.mensajeInscripcionRemota(): String? {
    if (!vigente) return null
    val fecha = fechaFin?.takeUnless { permanente }?.let { fin ->
        runCatching {
            Instant.parse(fin)
                .toLocalDateTime(TimeZone.of("America/Argentina/Buenos_Aires")).date
        }.getOrNull()
    }
    val hasta = if (fecha == null) "" else {
        val dia = fecha.day.toString().padStart(2, '0')
        val mes = fecha.month.number.toString().padStart(2, '0')
        " hasta el $dia/$mes/${fecha.year}"
    }
    return "Por declaraciones de síntomas inconsistentes, no podés anotarte en la cola desde la app$hasta. " +
        "Podés atenderte igualmente acercándote a la recepción del hospital."
}
