package com.proyecto_final.triage.viewmodels

import com.proyecto_final.triage.utils.formatHora
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proyecto_final.triage.network.estadoConsulta.ConsultaSseClient
import com.proyecto_final.triage.network.estadoConsulta.ConsultaSseEvento
import com.proyecto_final.triage.network.estadoConsulta.EstadoConsultaPacienteDTO
import com.proyecto_final.triage.network.estadoConsulta.EstadoEntradaCola
import com.proyecto_final.triage.network.estadoConsulta.HospitalSeleccionadoResponse
import com.proyecto_final.triage.network.estadoConsulta.TiempoEstimadoAtencionResponse
import com.proyecto_final.triage.network.estadoConsulta.TipoPausaCola
import com.proyecto_final.triage.network.estadoConsulta.obtenerEstadoConsulta
import com.proyecto_final.triage.network.estadoConsulta.obtenerHospitalSeleccionado
import com.proyecto_final.triage.network.estadoConsulta.ausentarme as ausentarmeApi
import com.proyecto_final.triage.network.estadoConsulta.estoyAtrasado as estoyAtrasadoApi
import com.proyecto_final.triage.network.estadoConsulta.sigoAsistiendo as sigoAsistiendoApi
import com.proyecto_final.triage.network.estadoConsulta.llegue as llegueApi
import com.proyecto_final.triage.network.estadoConsulta.cancelarSeleccionHospital as cancelarSeleccionHospitalApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface ConsultaActivaState {
    object Loading : ConsultaActivaState

    data class Success(
        val estado: EstadoConsultaPacienteDTO
    ) : ConsultaActivaState

    data class Error(
        val message: String
    ) : ConsultaActivaState
}

fun EstadoConsultaPacienteDTO.estadoColaEnum(): EstadoEntradaCola? =
    estadoEntradaCola?.let {
        runCatching {
            EstadoEntradaCola.valueOf(it)
        }.getOrNull()
    }

fun EstadoConsultaPacienteDTO.tipoPausaEnum(): TipoPausaCola? =
    tipoPausa?.let {
        runCatching {
            TipoPausaCola.valueOf(it)
        }.getOrNull()
    }

const val MENSAJE_ERROR = "No se pudo procesar la acción."

class ConsultaActivaViewModel : ViewModel() {

    var state by mutableStateOf<ConsultaActivaState>(
        ConsultaActivaState.Loading
    )
        private set

    var actionError by mutableStateOf<String?>(null)
        private set

    /** Confirmación visible de la última acción (por ejemplo, cuánto tiempo se sumó). */
    var actionMessage by mutableStateOf<String?>(null)
        private set

    var hospital by mutableStateOf<HospitalSeleccionadoResponse?>(null)
        private set

    var isAusentarmeLoading by mutableStateOf(false)
        private set

    var isEstoyAtrasadoLoading by mutableStateOf(false)
        private set

    var isSigoAsistiendoLoading by mutableStateOf(false)
        private set

    var isLlegueLoading by mutableStateOf(false)
        private set

    var isCancelarSeleccionLoading by mutableStateOf(false)
        private set

    var cancelacionExitosa by mutableStateOf(false)
        private set

    private var sseJob: Job? = null
    private var sseConsultaId: Long? = null

    fun cargarEstado(mostrarLoading: Boolean = true) {

        viewModelScope.launch {

            if (mostrarLoading) {
                state = ConsultaActivaState.Loading
            }

            cancelacionExitosa = false

            obtenerEstadoConsulta()
                .onSuccess { estado ->

                    // Guardamos el estado de la consulta
                    state = ConsultaActivaState.Success(estado)

                    // Obtenemos el hospital seleccionado
                    obtenerHospitalSeleccionado()
                        .onSuccess { hospitalSeleccionado ->
                            hospital = hospitalSeleccionado
                        }

                    // Sincronizamos SSE
                    sincronizarSse(estado)
                }
                .onFailure { error ->

                    if (
                        mostrarLoading ||
                        state !is ConsultaActivaState.Success
                    ) {
                        state = ConsultaActivaState.Error(
                            error.message
                                ?: "No se pudo obtener el estado de la consulta."
                        )
                    }
                }
        }
    }

    fun ausentarme() {
        viewModelScope.launch {

            actionError = null
            actionMessage = null
            isAusentarmeLoading = true

            ausentarmeApi()
                .onSuccess { estado ->
                    aplicarNuevoEstado(estado)
                }
                .onFailure { error ->
                    actionError =
                        error.message
                            ?: MENSAJE_ERROR
                }

            isAusentarmeLoading = false
        }
    }

    fun estoyAtrasado() {
        viewModelScope.launch {

            actionError = null
            actionMessage = null
            isEstoyAtrasadoLoading = true

            estoyAtrasadoApi()
                .onSuccess { estado ->
                    aplicarNuevoEstado(estado)
                    actionMessage = mensajeTiempoExtendido(estado)
                }
                .onFailure { error ->
                    actionError =
                        error.message
                            ?: MENSAJE_ERROR
                }

            isEstoyAtrasadoLoading = false
        }
    }

    fun sigoAsistiendo() {
        viewModelScope.launch {

            actionError = null
            actionMessage = null
            isSigoAsistiendoLoading = true

            sigoAsistiendoApi()
                .onSuccess { estado ->
                    aplicarNuevoEstado(estado)
                    actionMessage = mensajeTiempoExtendido(estado)
                }
                .onFailure { error ->
                    actionError =
                        error.message
                            ?: MENSAJE_ERROR
                }

            isSigoAsistiendoLoading = false
        }
    }

    fun llegue() {
        viewModelScope.launch {

            actionError = null
            actionMessage = null
            isLlegueLoading = true

            llegueApi()
                .onSuccess { estado ->
                    aplicarNuevoEstado(estado)
                    actionMessage = "Listo, volviste a la cola. Como ya te habían llamado, vas primero entre los pacientes con tu misma prioridad."
                }
                .onFailure { error ->
                    actionError =
                        error.message
                            ?: MENSAJE_ERROR
                }

            isLlegueLoading = false
        }
    }

    fun cancelarSeleccion() {
        viewModelScope.launch {

            actionError = null
            actionMessage = null
            isCancelarSeleccionLoading = true
            cancelacionExitosa = false

            cancelarSeleccionHospitalApi()
                .onSuccess { estado ->
                    aplicarNuevoEstado(estado)
                    cancelacionExitosa = true
                }
                .onFailure { error ->
                    actionError =
                        error.message
                            ?: MENSAJE_ERROR
                }

            isCancelarSeleccionLoading = false
        }
    }

    private fun mensajeTiempoExtendido(estado: EstadoConsultaPacienteDTO): String {
        val hora = formatHora(estado.fechaHoraLimiteRespuesta)
        return "Listo, sumamos ${estado.minutosPorExtension} minutos. Ahora tenés hasta las $hora para llegar."
    }

    private fun aplicarNuevoEstado(
        estado: EstadoConsultaPacienteDTO
    ) {
        state = ConsultaActivaState.Success(estado)

        sincronizarSse(estado)
    }

    /**
     * Abre o mantiene la conexión SSE cuando la consulta está EN_COLA.
     *
     * Cuando deja de estar en cola, se corta la conexión.
     */
    private fun sincronizarSse(
        estado: EstadoConsultaPacienteDTO
    ) {
        val consultaId = estado.consultaId

        val enCola =
            estado.estadoColaEnum() == EstadoEntradaCola.EN_COLA

        if (!enCola || consultaId == null) {
            detenerSse()
            return
        }

        if (
            sseJob?.isActive == true &&
            sseConsultaId == consultaId
        ) {
            return
        }

        detenerSse()

        sseConsultaId = consultaId

        sseJob = viewModelScope.launch {

            ConsultaSseClient
                .suscribirse(consultaId)
                .collect { evento ->

                    if (
                        evento is ConsultaSseEvento.TiempoEstimado
                    ) {
                        actualizarEstimacion(evento.data)
                    }
                }
        }
    }

    private fun detenerSse() {

        sseJob?.cancel()

        sseJob = null

        sseConsultaId = null
    }

    fun detenerActualizaciones() {
        detenerSse()
    }

    private fun actualizarEstimacion(
        estimacion: TiempoEstimadoAtencionResponse
    ) {
        val actual = state

        if (actual is ConsultaActivaState.Success) {

            state = ConsultaActivaState.Success(
                actual.estado.copy(
                    tiempoEstimadoAtencion = estimacion
                )
            )
        }
    }

    override fun onCleared() {

        detenerSse()

        super.onCleared()
    }
}