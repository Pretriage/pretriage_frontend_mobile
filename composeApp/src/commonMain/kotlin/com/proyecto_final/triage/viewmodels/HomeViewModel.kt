package com.proyecto_final.triage.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proyecto_final.triage.network.estadoConsulta.ConsultaSseClient
import com.proyecto_final.triage.network.estadoConsulta.ConsultaSseEvento
import com.proyecto_final.triage.network.estadoConsulta.EstadoConsultaPacienteDTO
import com.proyecto_final.triage.network.estadoConsulta.obtenerEstadoConsulta
import com.proyecto_final.triage.network.estadoConsulta.obtenerHospitalSeleccionado
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val ESTADOS_COLA_ACTIVA = setOf(
    "EN_COLA", "EN_REVISION", "LLAMADO", "EN_ESPERA", "ATRASADO", "EN_ATENCION"
)

private val ESTADOS_CONSULTA_ACTIVA_SIN_COLA = setOf(
    "HOSPITAL_SELECCIONADO", "PRETRIAGE_EN_PROCESO", "PRETRIAGE_FINALIZADO"
)

private const val POLLING_INTERVAL_MS = 25_000L

class HomeViewModel : ViewModel() {

    var state by mutableStateOf<HomeState>(HomeState.Loading)
        private set

    var showCancelSuccessSnackbar by mutableStateOf(false)
        private set

    fun triggerCancelSuccessSnackbar() {
        showCancelSuccessSnackbar = true
    }

    fun onCancelSuccessSnackbarShown() {
        showCancelSuccessSnackbar = false
    }

    private var suscripcionSseJob: Job? = null
    private var pollingJob: Job? = null

    fun cargarEstadoConsulta() {
        viewModelScope.launch {
            state = HomeState.Loading
            refrescarEstado(mostrarLoading = false)
        }
        iniciarPolling()
    }

    private fun iniciarPolling() {
        if (pollingJob != null) return

        pollingJob = viewModelScope.launch {
            while (true) {
                delay(POLLING_INTERVAL_MS.milliseconds)
                refrescarEstado(mostrarLoading = false)
            }
        }
    }

    private suspend fun refrescarEstado(mostrarLoading: Boolean) {
        if (mostrarLoading) state = HomeState.Loading

        obtenerEstadoConsulta()
            .onSuccess { estado -> aplicarEstado(estado) }
            .onFailure { error ->
                // en polling silencioso no pisamos un Success previo con un Error
                // por un fallo de red puntual; solo si todavía no había nada cargado
                if (state !is HomeState.Success) {
                    state = HomeState.Error(
                        error.message ?: "No se pudo obtener el estado de la consulta."
                    )
                }
            }
    }

    private fun aplicarEstado(estado: EstadoConsultaPacienteDTO) {
        val tieneConsultaActiva =
            estado.estadoEntradaCola in ESTADOS_COLA_ACTIVA ||
                    estado.estadoConsulta in ESTADOS_CONSULTA_ACTIVA_SIN_COLA

        if (!tieneConsultaActiva) {
            detenerSuscripcionSse()
            state = HomeState.SinConsultaActiva(estado)
            return
        }

        viewModelScope.launch {
            val hospital = (state as? HomeState.Success)?.hospital
                ?: obtenerHospitalSeleccionado().getOrNull()
            state = HomeState.Success(estado, hospital)
        }

        if (estado.estadoEntradaCola == "EN_COLA" && estado.consultaId != null) {
            suscribirseSse(estado.consultaId)
        } else {
            detenerSuscripcionSse()
        }
    }

    private fun suscribirseSse(consultaId: Long) {
        if (suscripcionSseJob != null) return

        suscripcionSseJob = viewModelScope.launch {
            ConsultaSseClient.suscribirse(consultaId).collect { evento ->
                if (evento is ConsultaSseEvento.TiempoEstimado) {
                    val actual = state as? HomeState.Success ?: return@collect
                    state = actual.copy(
                        estado = actual.estado.copy(tiempoEstimadoAtencion = evento.data)
                    )
                }
            }
        }
    }

    private fun detenerSuscripcionSse() {
        suscripcionSseJob?.cancel()
        suscripcionSseJob = null
    }

    fun detener() {
        detenerSuscripcionSse()
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onCleared() {
        detener()
        super.onCleared()
    }
}
