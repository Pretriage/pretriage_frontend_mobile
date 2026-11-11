package com.proyecto_final.triage.network

import kotlinx.serialization.Serializable

@Serializable
data class HospitalCercanoDTO(
    val idHospital: Long,
    val placeId: String?,
    val nombre: String,
    val direccion: String,
    val especialidades: List<EspecialidadMedicaDTO>,
    val tiempoEstimadoArriboMejorRuta: String? = null,
    val pacientesEnCola: Int = 0,
    val disponible: Boolean = true,
    val minutosEsperaEstimados: Long? = null,
    val fechaHoraAtencionEstimada: String? = null,
    val valoracionPromedio: Double? = null,
    val cantidadValoraciones: Int? = null
)
