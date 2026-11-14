package com.proyecto_final.triage.network.estadoConsulta

import com.proyecto_final.triage.config.AppConfig
import com.proyecto_final.triage.network.httpClient
import io.ktor.client.plugins.sse.sse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json

sealed class ConsultaSseEvento {
    data class TiempoEstimado(val data: TiempoEstimadoAtencionResponse) : ConsultaSseEvento()
    data class CambioEstado(val data: CambioEstadoConsultaDTO) : ConsultaSseEvento()
    data object Heartbeat : ConsultaSseEvento()
}

/**
 * Canal en tiempo real de una consulta activa.
 *
 * - "tiempo-estimado": llega mientras la consulta está EN_COLA.
 * - "estado-consulta": llega al conectarse (estado actual) y cada vez que el hospital cambia
 *   el estado (llamado, ausente al llamado, cancelación por vencimiento, inicio y fin de la atención).
 *
 * Se reconecta solo si se corta. Cuando la consulta termina (FINALIZADA o CANCELADA) el flujo se
 * completa y deja de reconectarse.
 */
object ConsultaSseClient {

    private val json = Json { ignoreUnknownKeys = true }
    private const val RECONNECT_DELAY_MS = 3000L

    fun suscribirse(consultaId: Long): Flow<ConsultaSseEvento> = callbackFlow {
        var terminada = false

        while (!terminada) {
            try {
                httpClient.sse(
                    urlString = "${AppConfig.baseUrl}/api/atencion/tiempos/suscribirse/$consultaId"
                ) {
                    incoming.collect { event ->
                        val data = event.data ?: return@collect
                        when (event.event) {
                            "tiempo-estimado" -> runCatching {
                                json.decodeFromString<TiempoEstimadoAtencionResponse>(data)
                            }.onSuccess { trySend(ConsultaSseEvento.TiempoEstimado(it)) }

                            "estado-consulta" -> runCatching {
                                json.decodeFromString<CambioEstadoConsultaDTO>(data)
                            }.onSuccess { cambio ->
                                trySend(ConsultaSseEvento.CambioEstado(cambio))
                                if (cambio.esTerminal) terminada = true
                            }

                            "heartbeat" -> trySend(ConsultaSseEvento.Heartbeat)

                            else -> Unit // evento desconocido, se ignora
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Se cortó la conexión: se reintenta abajo.
            }
            if (!terminada) delay(RECONNECT_DELAY_MS)
        }

        close()
        awaitClose { }
    }
}
