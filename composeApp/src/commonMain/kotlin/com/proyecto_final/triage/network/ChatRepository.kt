package com.proyecto_final.triage.network

import com.proyecto_final.triage.config.AppConfig
import com.proyecto_final.triage.storage.TokenStorage
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val AUTOR_BOT = "BOT"
const val AUTOR_PACIENTE = "PACIENTE"
const val ORIGEN_CIERRE_SIN_DATOS = "CIERRE_SIN_DATOS"

fun esAutorBot(autor: String?): Boolean = autor == AUTOR_BOT

@Serializable
data class ChatMensaje(
    val contenido: String,
    val autor: String? = null,
    val fechaHoraEnvio: String? = null
)

@Serializable
data class ChatResponse(
    val id: Long? = null,
    val mensajes: List<ChatMensaje> = emptyList(),
    val timestamp: String? = null,
    val finalizado: Boolean = false,
    // Null means a legacy server omitted the classification status; finalizado alone is not triage success.
    val estadoClasificacion: String? = null,
    val estadoTurnoTexto: String? = null,
    val solicitudTurnoTextoId: String? = null,
    val intentosTurnoTexto: Int = 0,
    val cierreParcial: Boolean = false,
    val estadoRecuperacion: String = "SIN_RECUPERACION",
    val proximoReintentoEn: String? = null,
    val versionEstado: Long = 0
)

@Serializable
data class EnviarMensajeRequest(
    val contenido: String,
    val solicitudId: String? = null
)

sealed interface ResultadoPostMensaje {
    data class TurnoAceptado(val chat: ChatResponse) : ResultadoPostMensaje
    data class RespuestaInmediata(val respuesta: RespuestaChatResponse) : ResultadoPostMensaje
}

@Serializable
data class AtencionEstimada(
    val consultaId: Long? = null,
    val fechaHoraAtencionEstimada: String? = null,
    val hayMedicosActivos: Boolean = false,
    val medicosActivos: Int = 0,
    val medicosParaEstimacion: Int = 0,
    val posicionEnCola: Int = 0,
    val pacientesAntes: Int = 0,
    val minutosPromedioAtencion: Int = 0,
    val mensaje: String? = null
)

@Serializable
data class RespuestaChatResponse(
    val respuesta: ChatMensaje,
    val atencionEstimada: AtencionEstimada? = null,
    val origenRespuesta: String? = null
)

private val json = Json { ignoreUnknownKeys = true }

/** HTTP failures reached the backend; they must not be labelled as connectivity errors. */
internal class ErrorHttpChat(val estado: Int, mensaje: String) : Exception(mensaje)

internal fun mensajeErrorEnvioChat(error: Throwable): String = when ((error as? ErrorHttpChat)?.estado) {
    401, 403 -> "No pudimos acceder a tu chat. Volvé a iniciar sesión."
    404 -> "No pudimos encontrar este chat. Volvé a abrirlo."
    429 -> "Hay demasiadas solicitudes. Esperá unos segundos y volvé a intentar."
    in 500..599 -> "No pudimos confirmar la respuesta. Consultá el estado del chat antes de continuar."
    null -> "No se pudo enviar el mensaje. Verificá tu conexión e intentá de nuevo."
    else -> "No pudimos procesar el mensaje. Intentá nuevamente."
}

private suspend inline fun <reified T> procesarRespuesta(
    tag: String,
    response: HttpResponse,
    mensajeError: String,
    logBody: Boolean = true
): Result<T> {
    println("$tag STATUS: ${response.status}")
    val bodyText = response.bodyAsText()
    if (logBody) println("$tag BODY: $bodyText")

    if (response.status == HttpStatusCode.OK) {
        return Result.success(json.decodeFromString<T>(bodyText))
    }
    val errorResponse = runCatching {
        json.decodeFromString<ErrorResponse>(bodyText)
    }.getOrNull()
    return Result.failure(ErrorHttpChat(response.status.value, errorResponse?.error ?: mensajeError))
}

suspend fun iniciarChat(): Result<ChatResponse> {
    return try {
        println("CHAT INICIAR")
        val response = httpClient.post("${AppConfig.baseUrl}/api/chat") {
            contentType(ContentType.Application.Json)
        }
        procesarRespuesta("CHAT INICIAR", response, "No se pudo iniciar el chat")
    } catch (e: Exception) {
        println("CHAT INICIAR EXCEPTION: ${e.message}")
        Result.failure(e)
    }
}

suspend fun obtenerChat(id: Long): Result<ChatResponse> {
    return try {
        println("CHAT OBTENER ($id)")
        val response = httpClient.get("${AppConfig.baseUrl}/api/chat/$id") {
        }
        procesarRespuesta("CHAT OBTENER", response, "No se pudo recuperar el chat")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("CHAT OBTENER EXCEPTION: ${e.message}")
        Result.failure(e)
    }
}

internal suspend fun obtenerChatParaReconciliar(id: Long, timeoutMillis: Long): Result<ChatResponse> {
    return try {
        val response = httpClient.get("${AppConfig.baseUrl}/api/chat/$id") {
            timeout {
                requestTimeoutMillis = timeoutMillis
                connectTimeoutMillis = timeoutMillis
                socketTimeoutMillis = timeoutMillis
            }
        }
        procesarRespuesta("CHAT RECONCILIAR", response, "No se pudo verificar el estado del chat", logBody = false)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

suspend fun enviarMensaje(id: Long, contenido: String, solicitudId: String): Result<ResultadoPostMensaje> {
    return try {
        println("CHAT ENVIAR ($id)")
        val response = httpClient.post("${AppConfig.baseUrl}/api/chat/$id/mensajes") {
            contentType(ContentType.Application.Json)
            setBody(EnviarMensajeRequest(contenido = contenido, solicitudId = solicitudId))
            timeout {
                requestTimeoutMillis = 10_000L
                connectTimeoutMillis = 5_000L
                socketTimeoutMillis = 10_000L
            }
        }
        println("CHAT ENVIAR STATUS: ${response.status}")
        val bodyText = response.bodyAsText()
        if (response.status == HttpStatusCode.Accepted) {
            Result.success(ResultadoPostMensaje.TurnoAceptado(json.decodeFromString<ChatResponse>(bodyText)))
        } else if (response.status == HttpStatusCode.OK) {
            Result.success(ResultadoPostMensaje.RespuestaInmediata(json.decodeFromString<RespuestaChatResponse>(bodyText)))
        } else {
            val errorResponse = runCatching { json.decodeFromString<ErrorResponse>(bodyText) }.getOrNull()
            Result.failure(ErrorHttpChat(response.status.value, errorResponse?.error ?: "No se pudo enviar el mensaje"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("CHAT ENVIAR EXCEPTION: ${e.message}")
        Result.failure(e)
    }
}

/** Reprocess saved answers without reopening the interview or sending another message. */
suspend fun reintentarClasificacionChat(id: Long): Result<RespuestaChatResponse> = try {
    val response = httpClient.post("${AppConfig.baseUrl}/api/chat/$id/clasificacion") {
        contentType(ContentType.Application.Json)
    }
    procesarRespuesta("CHAT REINTENTAR", response, "No pudimos procesar tus respuestas. Intentá nuevamente.")
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
