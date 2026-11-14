package com.proyecto_final.triage.notificaciones

import com.proyecto_final.triage.network.estadoConsulta.CambioEstadoConsultaDTO
import com.proyecto_final.triage.network.estadoConsulta.ConsultaSseClient
import com.proyecto_final.triage.network.estadoConsulta.ConsultaSseEvento
import com.proyecto_final.triage.network.estadoConsulta.EstadoConsultaPacienteDTO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

private val ESTADOS_SEGUIDOS = setOf(
    "EN_COLA", "EN_REVISION", "LLAMADO", "EN_ESPERA", "ATRASADO", "EN_ATENCION"
)

/**
 * Sigue la consulta activa del paciente a nivel de app (no de pantalla), para que los avisos
 * lleguen una sola vez aunque el paciente cambie de pantalla o minimice la app.
 *
 * Las pantallas le informan el estado que obtienen con [sincronizar]; mientras la consulta esté
 * en la fila (o llamada, en pausa o en atención) se mantiene conectado al hospital por SSE.
 */
object SeguimientoConsulta {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var consultaSeguida: Long? = null
    private val avisosMostrados = mutableSetOf<String>()

    private val _cambios = MutableSharedFlow<CambioEstadoConsultaDTO>(extraBufferCapacity = 8)

    /** Cambios de estado que llegan del hospital, para que la pantalla se actualice al instante. */
    val cambios: SharedFlow<CambioEstadoConsultaDTO> = _cambios.asSharedFlow()

    fun sincronizar(estado: EstadoConsultaPacienteDTO) {
        val consultaId = estado.consultaId
        if (consultaId != null && estado.estadoEntradaCola in ESTADOS_SEGUIDOS) {
            seguir(consultaId)
        } else {
            detener()
        }
    }

    /** Deja de seguir la consulta (por ejemplo, porque el paciente la cancela él mismo). */
    fun detener() {
        job?.cancel()
        job = null
        if (consultaSeguida != null) {
            consultaSeguida = null
            detenerSeguimientoEnSegundoPlano()
        }
    }

    private fun seguir(consultaId: Long) {
        if (job?.isActive == true && consultaSeguida == consultaId) return
        job?.cancel()
        consultaSeguida = consultaId
        iniciarSeguimientoEnSegundoPlano()
        job = scope.launch {
            ConsultaSseClient.suscribirse(consultaId).collect { evento ->
                if (evento is ConsultaSseEvento.CambioEstado) procesar(evento.data)
            }
            // La consulta terminó (finalizada o cancelada): ya no hay nada que seguir.
            if (consultaSeguida == consultaId) {
                consultaSeguida = null
                detenerSeguimientoEnSegundoPlano()
            }
        }
    }

    private fun procesar(cambio: CambioEstadoConsultaDTO) {
        _cambios.tryEmit(cambio)
        val id = cambio.id ?: return
        val aviso = avisoPara(cambio) ?: return
        if (!avisosMostrados.add(id)) return
        mostrarNotificacionTurno(aviso.titulo, aviso.texto)
    }
}
