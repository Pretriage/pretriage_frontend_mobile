package com.proyecto_final.triage.network

import com.proyecto_final.triage.config.AppConfig
import com.proyecto_final.triage.storage.ChatStorage
import com.proyecto_final.triage.utils.parsearMensajeError
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

suspend fun obtenerHospitalesCercanos(
    latitud: Double,
    longitud: Double,
    codigoEspecialidad: String,
    transporte: String = "transporte-publico",
    ordenarPor: String? = null
): Result<List<HospitalCercanoDTO>> {
    return try {

        val response = httpClient.get("${AppConfig.baseUrl}/api/hospitales/cercanos") {
            parameter("latitud", latitud)
            parameter("longitud", longitud)
            parameter("codigoEspecialidad", codigoEspecialidad)
            parameter("transporte", transporte)
            ordenarPor?.let { parameter("ordenarPor", it) }
        }

        if (response.status == HttpStatusCode.OK) {
            val hospitales = response.body<List<HospitalCercanoDTO>>()
            Result.success(hospitales)
        } else {
            val errorBody = response.bodyAsText()
            Result.failure(Exception(parsearMensajeError(errorBody)))
        }

    } catch (e: Exception) {
        Result.failure(e)
    }
}

suspend fun elegirHospital(
    placeId: String,
    codigoEspecialidad: String
): Result<Unit> {
    return try {
        val sancion = consultarSancionPaciente().getOrNull()
        if (sancion?.vigente == true) {
            return Result.failure(IngresoRemotoSancionado(sancion))
        }

        val response = httpClient.post("${AppConfig.baseUrl}/api/atencion/hospital") {
            contentType(ContentType.Application.Json)
            setBody(SeleccionHospitalRequest(placeId = placeId, codigoEspecialidad = codigoEspecialidad))
        }

        if (response.status == HttpStatusCode.NoContent) {
            // Una seleccion confirmada inicia otra consulta, incluso en el mismo hospital.
            // El proximo ChatViewModel debe crear un chat nuevo, no recuperar el anterior.
            ChatStorage.clearChatId()
            Result.success(Unit)
        } else {
            val errorBody = response.bodyAsText()
            val rechazo = leerRechazoPorSancion(response.status, errorBody)
            Result.failure(
                if (rechazo == null) Exception(parsearMensajeError(errorBody))
                else IngresoRemotoSancionado(rechazo)
            )
        }

    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
