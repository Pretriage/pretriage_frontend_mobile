package com.proyecto_final.triage.network

import com.proyecto_final.triage.config.AppConfig
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val CODIGO_SANCION = "PATIENT_SANCTIONED"
private val jsonSancion = Json { ignoreUnknownKeys = true }

@Serializable
internal enum class TipoAvisoSancion {
    APLICADA,
    LEVANTADA
}

@Serializable
internal data class SancionPacienteDTO(
    val vigente: Boolean,
    val permanente: Boolean = false,
    val fechaFin: String? = null,
    val avisoId: String? = null,
    val tipoAviso: TipoAvisoSancion? = null,
    val codigo: String? = null
)

internal class IngresoRemotoSancionado(val sancion: SancionPacienteDTO) : Exception()

internal suspend fun consultarSancionPaciente(): Result<SancionPacienteDTO> {
    return try {
        val respuesta = httpClient.get("${AppConfig.baseUrl}/api/paciente/sancion")
        if (respuesta.status == HttpStatusCode.OK) {
            Result.success(respuesta.body<SancionPacienteDTO>())
        } else {
            Result.failure(IllegalStateException("No pudimos consultar el estado de inscripción remota."))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal fun leerRechazoPorSancion(estado: HttpStatusCode, cuerpo: String): SancionPacienteDTO? {
    if (estado != HttpStatusCode.Forbidden) return null
    return runCatching { jsonSancion.decodeFromString<SancionPacienteDTO>(cuerpo) }
        .getOrNull()
        ?.takeIf { it.codigo == CODIGO_SANCION && it.vigente }
}
