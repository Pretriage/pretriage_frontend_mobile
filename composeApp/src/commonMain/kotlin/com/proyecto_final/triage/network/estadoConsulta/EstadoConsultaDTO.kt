package com.proyecto_final.triage.network.estadoConsulta

import kotlinx.serialization.Serializable

@Serializable
data class EstadoConsultaPacienteDTO(
    val consultaId: Long? = null,
    val estadoConsulta: String? = null,
    val estadoEntradaCola: String? = null,
    val tipoPausa: String? = null,
    val fechaHoraLimiteRespuesta: String? = null,
    val tiempoEstimadoAtencion: TiempoEstimadoAtencionResponse? = null,
    /** Veces que todavía se puede pedir más tiempo para llegar después de no presentarse al llamado. */
    val extensionesRestantes: Int = 0,
    /** Minutos que suma cada pedido de más tiempo al final del plazo vigente. */
    val minutosPorExtension: Int = 30
)

@Serializable
data class TiempoEstimadoAtencionResponse(
    val consultaId: Long? = null,
    val fechaHoraAtencionEstimada: String? = null,
    val hayMedicosActivos: Boolean = false,
    val medicosActivos: Int = 0,
    val medicosParaEstimacion: Int = 0,
    val posicionEnCola: Int = 0,
    val pacientesAntes: Int = 0,
    val minutosPromedioAtencion: Int = 0,
    val codigoSala: String? = null,
    val mensaje: String? = null
)

@Serializable
data class HospitalSeleccionadoResponse(
    val idHospital: Long,
    val placeId: String? = null,
    val nombre: String,
    val direccion: String? = null,
    val sectorId: Long,
    val nombreSector: String
)

@Serializable
enum class EstadoConsulta {
    PENDIENTE,
    HOSPITAL_SELECCIONADO,
    PRETRIAGE_FINALIZADO,
    PRETRIAGE_EN_PROCESO,
    EN_COLA,
    EN_REVISION,
    LLAMADO,
    EN_ESPERA,
    ATRASADO,
    EN_ATENCION,
    FINALIZADA,
    CANCELADA
}

@Serializable
enum class EstadoEntradaCola {
    EN_COLA,
    EN_REVISION,
    LLAMADO,
    EN_ESPERA,
    ATRASADO,
    EN_ATENCION,
    FINALIZADA,
    CANCELADA
}

@Serializable
enum class TipoPausaCola {
    AUSENTE_AL_LLAMADO,
    ESPERA_MANUAL,
    ATRASADO_CONFIRMADO
}
