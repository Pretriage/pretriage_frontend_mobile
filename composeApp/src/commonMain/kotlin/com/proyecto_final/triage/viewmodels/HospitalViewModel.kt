package com.proyecto_final.triage.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proyecto_final.triage.network.HospitalCercanoDTO
import com.proyecto_final.triage.network.IngresoRemotoSancionado
import com.proyecto_final.triage.network.elegirHospital
import com.proyecto_final.triage.network.obtenerHospitalesCercanos
import com.proyecto_final.triage.screens.Hospital
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class OrdenHospital(val apiValue: String?, val label: String) {
    DISTANCIA(null, "Distancia"),
    TIEMPO_ATENCION("tiempo-atencion", "Menor espera estimada"),
    MEJOR_VALORADOS("valoracion", "Mejor valorados")
}

class HospitalesViewModel : ViewModel() {

    var state by mutableStateOf<HospitalesState>(
        HospitalesState.Loading
    )
        private set

    var hospitalSeleccionado by mutableStateOf(false)
        private set

    var errorSeleccion by mutableStateOf<String?>(null)
        private set

    var seleccionEnCurso by mutableStateOf(false)
        private set

    internal val sancionPaciente = SancionPacienteState()

    var ordenSeleccionado by mutableStateOf(OrdenHospital.DISTANCIA)
        private set

    // Búsqueda en curso. Se cancela si arranca otra, para que una
    // respuesta vieja (por ej. de otro orden) no pise a la nueva.
    private var busquedaJob: Job? = null

    fun buscarHospitalesCercanos(
        latitud: Double,
        longitud: Double,
        codigoEspecialidad: String,
        transporte: String = "transporte-publico",
        orden: OrdenHospital = ordenSeleccionado
    ) {
        ordenSeleccionado = orden

        busquedaJob?.cancel()
        busquedaJob = viewModelScope.launch {

            state = HospitalesState.Loading

            val resultado = obtenerHospitalesCercanos(
                latitud = latitud,
                longitud = longitud,
                codigoEspecialidad = codigoEspecialidad,
                transporte = transporte,
                ordenarPor = orden.apiValue
            )

            resultado
                .onSuccess { hospitales ->
                    state = HospitalesState.Success(hospitales)
                }
                .onFailure {
                    state = HospitalesState.Error("No pudimos cargar los hospitales cercanos.")
                }
        }
    }

    fun cambiarOrden(
        nuevoOrden: OrdenHospital,
        latitud: Double,
        longitud: Double,
        codigoEspecialidad: String,
        transporte: String = "transporte-publico"
    ) {
        if (nuevoOrden == ordenSeleccionado) return
        buscarHospitalesCercanos(
            latitud = latitud,
            longitud = longitud,
            codigoEspecialidad = codigoEspecialidad,
            transporte = transporte,
            orden = nuevoOrden
        )
    }

    fun seleccionarHospital(
        hospital: Hospital,
        codigoEspecialidad: String
    ) {
        val placeId = hospital.placeId ?: return
        if (seleccionEnCurso || sancionPaciente.datos?.vigente == true) return

        seleccionEnCurso = true
        errorSeleccion = null
        viewModelScope.launch {
            try {
                elegirHospital(placeId, codigoEspecialidad)
                    .onSuccess { hospitalSeleccionado = true }
                    .onFailure { error ->
                        if (error is IngresoRemotoSancionado) {
                            sancionPaciente.registrar(error.sancion)
                        } else {
                            errorSeleccion = error.message ?: "No pudimos confirmar el hospital seleccionado."
                        }
                    }
            } finally {
                seleccionEnCurso = false
            }
        }
    }

    fun descartarErrorSeleccion() {
        errorSeleccion = null
    }
}
