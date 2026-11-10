package com.proyecto_final.triage.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proyecto_final.triage.audio.crearAudioVoz
import com.proyecto_final.triage.audio.ModoEntradaVoz
import com.proyecto_final.triage.network.AUTOR_BOT
import com.proyecto_final.triage.network.AUTOR_PACIENTE
import com.proyecto_final.triage.network.AtencionEstimada
import com.proyecto_final.triage.network.ChatMensaje
import com.proyecto_final.triage.network.ResultadoEnvioChat
import com.proyecto_final.triage.network.enviarConReconciliacion
import com.proyecto_final.triage.network.obtenerChatParaReconciliar
import com.proyecto_final.triage.network.seguirTurnoTexto
import com.proyecto_final.triage.network.turnoTextoPendiente
import com.proyecto_final.triage.network.turnoTextoNoDisponible
import com.proyecto_final.triage.network.EventoVoz
import com.proyecto_final.triage.network.MensajeVozCliente
import com.proyecto_final.triage.network.conectarChatVoz
import com.proyecto_final.triage.storage.ChatStorage
import com.proyecto_final.triage.storage.chatConversationCache
import com.proyecto_final.triage.network.ChatResponse
import com.proyecto_final.triage.network.ErrorHttpChat
import com.proyecto_final.triage.network.enviarMensaje
import com.proyecto_final.triage.network.mensajeErrorEnvioChat
import com.proyecto_final.triage.network.iniciarChat
import com.proyecto_final.triage.network.obtenerChat
import com.proyecto_final.triage.network.ORIGEN_CIERRE_SIN_DATOS
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.random.Random
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class ChatUiState(
    val chatId: Long? = null,
    val iniciando: Boolean = false,
    val enviando: Boolean = false,
    val respuestaPendiente: Boolean = false,
    val solicitudTextoPendienteId: String? = null,
    val contenidoSolicitudTextoPendiente: String? = null,
    val turnoTextoDesconocido: Boolean = false,
    val siguiendoTurnoTexto: Boolean = false,
    val actualizacionTurnoTextoDisponible: Boolean = false,
    val estadoTurnoTexto: String? = null,
    val intentosTurnoTexto: Int = 0,
    val cierreParcial: Boolean = false,
    val estadoRecuperacion: String = "SIN_RECUPERACION",
    val proximoReintentoEn: String? = null,
    val versionEstado: Long = 0,
    val verificandoRespuesta: Boolean = false,
    val siguiendoCierre: Boolean = false,
    val actualizacionCierreDisponible: Boolean = false,
    val mensajes: List<ChatMensaje> = emptyList(),
    val mensajesNoEnviados: Set<String> = emptySet(),
    val finalizado: Boolean = false,
    val estadoClasificacion: String? = "NO_SOLICITADA",
    val borrador: String = "",
    val atencionEstimada: AtencionEstimada? = null,
    val error: String? = null,
    val envioExitoso: Boolean? = null,
    // Chat de voz
    val vozDisponible: Boolean = false,
    val estadoVoz: EstadoVoz = EstadoVoz.INACTIVA,
    val modoEntradaVoz: ModoEntradaVoz = ModoEntradaVoz.POR_TURNOS,
    val procesandoRespuestaVoz: Boolean = false,
    val progresoVozControladoPorServidor: Boolean = false,
    val asistenteHablandoVoz: Boolean = false,
    val errorVoz: Boolean = false,
    val avisoUrgenteVoz: String? = null,
    // Transcripción en curso (todavía no confirmada como burbuja)
    val transcripcionPaciente: String = "",
    val transcripcionBot: String = ""
)

internal fun ChatUiState.aplicarChatReconciliado(chat: com.proyecto_final.triage.network.ChatResponse, procesando: Boolean): ChatUiState = copy(
    enviando = false,
    respuestaPendiente = (procesando || chat.turnoTextoPendiente()) && !chat.finalizado,
    solicitudTextoPendienteId = if (procesando) solicitudTextoPendienteId ?: chat.solicitudTurnoTextoId else null,
    contenidoSolicitudTextoPendiente = if (procesando) contenidoSolicitudTextoPendiente else null,
    turnoTextoDesconocido = false,
    estadoTurnoTexto = chat.estadoTurnoTexto,
    intentosTurnoTexto = chat.intentosTurnoTexto,
    cierreParcial = chat.cierreParcial,
    estadoRecuperacion = if (chat.estadoClasificacion.equals("NO_SOLICITADA", true) && chat.finalizado ||
        chat.estadoClasificacion.equals("PARCIAL", true) || chat.estadoClasificacion.equals("COMPLETADA", true) ||
        chat.estadoClasificacion.equals("FALLIDA", true)) {
        "SIN_RECUPERACION"
    } else chat.estadoRecuperacion,
    proximoReintentoEn = if (chat.estadoClasificacion.equals("NO_SOLICITADA", true) && chat.finalizado ||
        chat.estadoClasificacion.equals("PARCIAL", true) || chat.estadoClasificacion.equals("COMPLETADA", true) ||
        chat.estadoClasificacion.equals("FALLIDA", true)) null else chat.proximoReintentoEn,
    versionEstado = maxOf(versionEstado, chat.versionEstado),
    mensajes = chat.mensajes,
    mensajesNoEnviados = emptySet(),
    borrador = "",
    atencionEstimada = if (procesando && !chat.ocultaEstimacionAnterior()) atencionEstimada else null,
    finalizado = chat.finalizado || chat.estadoClasificacion.equals("FALLIDA", ignoreCase = true) ||
        chat.estadoClasificacion.equals("PARCIAL", ignoreCase = true) || chat.cierreParcial,
    estadoClasificacion = chat.estadoClasificacion,
    envioExitoso = null,
    error = null
)

internal fun ChatResponse.ocultaEstimacionAnterior(): Boolean =
    estadoClasificacion.equals("PARCIAL", ignoreCase = true) ||
        (cierreParcial && estadoClasificacion.equals("PENDIENTE", ignoreCase = true))

internal fun ChatUiState.aplicarEstadoTurnoTexto(chat: ChatResponse): ChatUiState {
    if (chat.id != null && chatId != null && chat.id != chatId) return this
    if (chat.versionEstado < versionEstado) return this
    val mismaVersion = chat.versionEstado == versionEstado
    if (mismaVersion && solicitudTextoPendienteId != null &&
        chat.solicitudTurnoTextoId != solicitudTextoPendienteId && !chat.turnoTextoNoDisponible() && !chat.finalizado
    ) return copy(
        siguiendoTurnoTexto = false,
        respuestaPendiente = false,
        turnoTextoDesconocido = true,
        actualizacionTurnoTextoDisponible = true,
        verificandoRespuesta = false
    )
    val estadoActualClasificacion = estadoClasificacion?.uppercase()
    val estadoRemotoClasificacion = chat.estadoClasificacion?.uppercase()
    val clasificacionActualTerminal = estadoActualClasificacion == "COMPLETADA" ||
        estadoActualClasificacion == "FALLIDA" || estadoActualClasificacion == "PARCIAL" ||
        (finalizado && estadoActualClasificacion == "NO_SOLICITADA")
    val falloAvanzaACompletada = estadoActualClasificacion == "FALLIDA" &&
        estadoRemotoClasificacion == "COMPLETADA"
    if (mismaVersion && clasificacionActualTerminal && estadoRemotoClasificacion != estadoActualClasificacion && !falloAvanzaACompletada) {
        return this
    }
    val preservarCierre = finalizado && estadoActualClasificacion == "PENDIENTE" &&
        !chat.finalizado && estadoRemotoClasificacion == "NO_SOLICITADA"
    val pendiente = chat.turnoTextoPendiente()
    val noDisponible = chat.turnoTextoNoDisponible()
    val completado = chat.estadoTurnoTexto.equals("COMPLETADA", ignoreCase = true)
    val solicitudCoincide = solicitudTextoPendienteId == null || chat.solicitudTurnoTextoId == solicitudTextoPendienteId
    val completadoActual = completado && solicitudCoincide
    val cierreAdministrativo = chat.finalizado && estadoRemotoClasificacion == "NO_SOLICITADA"
    val clasificacionTerminal = estadoRemotoClasificacion in setOf("COMPLETADA", "FALLIDA", "PARCIAL")
    val terminal = completadoActual || cierreAdministrativo || clasificacionTerminal
    val recuperacionActiva = chat.estadoRecuperacion.equals("REINTENTANDO", true) ||
        chat.estadoRecuperacion.equals("ESPERANDO_API", true)
    val noDisponibleLegacySinRecuperacion = noDisponible && !terminal && !recuperacionActiva
    val turnoPendiente = pendiente || recuperacionActiva
    val respuestaAntigua = solicitudTextoPendienteId != null && chat.solicitudTurnoTextoId != solicitudTextoPendienteId
    val versionAvanza = chat.versionEstado > versionEstado
    val solicitudAplicada = when {
        terminal || noDisponibleLegacySinRecuperacion -> null
        versionAvanza -> chat.solicitudTurnoTextoId
        else -> solicitudTextoPendienteId ?: chat.solicitudTurnoTextoId
    }
    val contenidoSolicitudAplicado = if (terminal || noDisponibleLegacySinRecuperacion ||
        (versionAvanza && chat.solicitudTurnoTextoId != solicitudTextoPendienteId)
    ) null else contenidoSolicitudTextoPendiente
    val mensajesCanonicos = when {
        terminal -> chat.mensajes
        !respuestaAntigua && chat.mensajes.size >= mensajes.size -> chat.mensajes
        else -> mensajes
    }
    return copy(
        mensajes = mensajesCanonicos,
        finalizado = chat.finalizado || finalizado || chat.estadoClasificacion.equals("COMPLETADA", true) ||
            chat.estadoClasificacion.equals("FALLIDA", true) || chat.estadoClasificacion.equals("PARCIAL", true) || chat.cierreParcial,
        estadoClasificacion = if (preservarCierre) estadoClasificacion else chat.estadoClasificacion ?: estadoClasificacion,
        atencionEstimada = if (chat.ocultaEstimacionAnterior()) null else atencionEstimada,
        estadoTurnoTexto = chat.estadoTurnoTexto,
        intentosTurnoTexto = chat.intentosTurnoTexto,
        cierreParcial = chat.cierreParcial,
        estadoRecuperacion = if (terminal) "SIN_RECUPERACION" else chat.estadoRecuperacion,
        proximoReintentoEn = if (terminal) null else chat.proximoReintentoEn,
        versionEstado = maxOf(versionEstado, chat.versionEstado),
        solicitudTextoPendienteId = solicitudAplicada,
        contenidoSolicitudTextoPendiente = contenidoSolicitudAplicado,
        respuestaPendiente = turnoPendiente && !terminal,
        turnoTextoDesconocido = when {
            terminal || noDisponibleLegacySinRecuperacion -> false
            turnoPendiente -> turnoTextoDesconocido
            solicitudTextoPendienteId != null -> !solicitudCoincide || turnoTextoDesconocido || chat.solicitudTurnoTextoId == null
            else -> turnoTextoDesconocido && chat.estadoTurnoTexto == null
        },
        actualizacionTurnoTextoDisponible = !terminal && !turnoPendiente && solicitudAplicada != null && !siguiendoTurnoTexto,
        mensajesNoEnviados = if (terminal || turnoPendiente) emptySet() else mensajesNoEnviados,
        borrador = if (terminal || turnoPendiente) "" else borrador,
        enviando = false,
        verificandoRespuesta = false,
        envioExitoso = null,
        error = null
    )
}

internal val ChatUiState.turnoTextoBloqueaChat: Boolean
    get() = !finalizado && (respuestaPendiente || turnoTextoDesconocido ||
        estadoRecuperacion.equals("REINTENTANDO", true) || estadoRecuperacion.equals("ESPERANDO_API", true) ||
        solicitudTextoPendienteId != null)
internal fun ChatUiState.aplicarChatVerificadoPendiente(
    chat: com.proyecto_final.triage.network.ChatResponse,
    respuestaRecibida: Boolean
): ChatUiState {
    val estadoTerminal = chat.estadoClasificacion.equals("COMPLETADA", ignoreCase = true) ||
        chat.estadoClasificacion.equals("FALLIDA", ignoreCase = true) ||
        chat.estadoClasificacion.equals("PARCIAL", ignoreCase = true)
    val entrevistaCerrada = chat.finalizado || estadoTerminal
    return copy(
        verificandoRespuesta = false,
        respuestaPendiente = !(respuestaRecibida || entrevistaCerrada),
        mensajes = chat.mensajes,
        mensajesNoEnviados = emptySet(),
        borrador = "",
        atencionEstimada = if (chat.ocultaEstimacionAnterior()) null else atencionEstimada,
        finalizado = entrevistaCerrada,
        estadoClasificacion = chat.estadoClasificacion,
        cierreParcial = chat.cierreParcial,
        estadoRecuperacion = chat.estadoRecuperacion,
        proximoReintentoEn = chat.proximoReintentoEn,
        versionEstado = maxOf(versionEstado, chat.versionEstado),
        envioExitoso = null,
        error = null
    )
}
internal fun ChatUiState.aplicarFalloEnvioNoConfirmado(contenido: String, error: Throwable): ChatUiState = copy(
    enviando = false,
    respuestaPendiente = false,
    envioExitoso = false,
    error = mensajeErrorEnvioChat(error),
    mensajesNoEnviados = mensajesNoEnviados + contenido,
    borrador = contenido
)

private fun ChatUiState.cambiarProgresoVoz(cambio: CambioProgresoVoz): ChatUiState {
    val progreso = aplicarCambioProgresoVoz(
        ProgresoVozUiState(
            procesandoRespuesta = procesandoRespuestaVoz,
            controladoPorServidor = progresoVozControladoPorServidor,
            asistenteHablando = asistenteHablandoVoz
        ),
        cambio
    )
    return copy(
        procesandoRespuestaVoz = progreso.procesandoRespuesta,
        progresoVozControladoPorServidor = progreso.controladoPorServidor,
        asistenteHablandoVoz = progreso.asistenteHablando
    )
}

enum class EstadoVoz {
    INACTIVA,
    CONECTANDO,
    // Gemini listo: se envía el audio del micrófono
    ESCUCHANDO,
    // Cierre solicitado: el micrófono está detenido y se espera cerrar el socket.
    DETENIENDO,
    // Entrevista cerrada por Gemini: se espera la preclasificación
    PROCESANDO
}

class ChatViewModel : ViewModel() {

    private val audio = crearAudioVoz()

    private val _state = MutableStateFlow(ChatUiState(
        vozDisponible = audio.disponible, modoEntradaVoz = audio.modoEntrada.value
    ))
    val state: StateFlow<ChatUiState> = _state

    private var sesionVoz: Job? = null
    private var seguimientoCierre: Job? = null
    private var envioTextoJob: Job? = null
    private var seguimientoTurnoTextoJob: Job? = null
    private var actualizacionTurnoTextoJob: Job? = null
    private var refreshEnForegroundJob: Job? = null
    private var generacionTurnoTexto = 0
    private var pantallaLiberada = false
    private var appEnPrimerPlano = true
    private var salidaVoz: Channel<MensajeVozCliente>? = null
    // El audio nativo puede cambiar de modo al iniciar o al cambiar la salida.
    // Colectar en viewModelScope evita escribir el estado UI desde hilos de audio.
    private val observacionModoAudio = viewModelScope.launch {
        audio.modoEntrada.collect { modo ->
            _state.update { it.copy(modoEntradaVoz = modo) }
        }
    }

    private val observacionErrorAudio = viewModelScope.launch {
        audio.errorAudio.collect { mensaje ->
            val estado = _state.value.estadoVoz
            if (mensaje != null &&
                (estado == EstadoVoz.CONECTANDO || estado == EstadoVoz.ESCUCHANDO)
            ) {
                detenerVoz()
                _state.update { it.copy(error = mensaje, errorVoz = true) }
            }
        }
    }

    fun iniciar() {
        if (_state.value.iniciando || _state.value.chatId != null) return
        pantallaLiberada = false

        val chatIdGuardado = ChatStorage.getChatId()

        val snapshot = chatIdGuardado?.let { chatConversationCache.recuperar(it) }
        val solicitudTextoGuardada = chatIdGuardado?.let { chatConversationCache.recuperarSolicitudTexto(it) }
        _state.value = snapshot?.let { (chat, atencion) ->
            _state.value.copy(
                chatId = chat.id, mensajes = chat.mensajes, finalizado = chat.finalizado,
                estadoClasificacion = chat.estadoClasificacion, atencionEstimada = atencion,
                avisoUrgenteVoz = chatIdGuardado?.let(chatConversationCache::recuperarAvisoUrgenteVoz),
                solicitudTextoPendienteId = solicitudTextoGuardada?.first
                    ?: chat.solicitudTurnoTextoId.takeIf { chat.turnoTextoPendiente() },
                contenidoSolicitudTextoPendiente = solicitudTextoGuardada?.second,
                estadoTurnoTexto = chat.estadoTurnoTexto,
                intentosTurnoTexto = chat.intentosTurnoTexto,
                cierreParcial = chat.cierreParcial,
                estadoRecuperacion = chat.estadoRecuperacion,
                proximoReintentoEn = chat.proximoReintentoEn,
                versionEstado = chat.versionEstado,
                respuestaPendiente = chat.turnoTextoPendiente(),
                turnoTextoDesconocido = chat.estadoRecuperacion.equals("REINTENTANDO", true) ||
                    chat.estadoRecuperacion.equals("ESPERANDO_API", true) ||
                    (solicitudTextoGuardada != null && !chat.turnoTextoPendiente()),
                iniciando = true, error = null
            )
        } ?: _state.value.copy(
            chatId = chatIdGuardado,
            solicitudTextoPendienteId = solicitudTextoGuardada?.first,
            contenidoSolicitudTextoPendiente = solicitudTextoGuardada?.second,
            turnoTextoDesconocido = solicitudTextoGuardada != null,
            iniciando = true,
            error = null
        )

        viewModelScope.launch {
            if (chatIdGuardado != null) {
                recuperarChat(chatIdGuardado)
            } else {
                crearChat()
            }
        }
    }

    private suspend fun recuperarChat(id: Long) {
        println("CHAT RECUPERAR (id=$id)")
        obtenerChat(id)
            .onSuccess { chat ->
                println("CHAT RECUPERADO: id=${chat.id} finalizado=${chat.finalizado}")
                _state.update { it.aplicarActualizacionTrasVoz(chat).aplicarEstadoTurnoTexto(chat).copy(iniciando = false) }
                guardarEstadoActualEnCache()
                iniciarSeguimientoCierre()
                iniciarSeguimientoTurnoTexto()
            }
            .onFailure { error ->
                println("CHAT ERROR AL RECUPERAR: ${error.message}")
                // Only a confirmed missing chat permits creation; a network failure does not.
                if (error is ErrorHttpChat && error.estado == 404) {
                    ChatStorage.clearChatId()
                    crearChat()
                } else {
                    _state.value = _state.value.copy(
                        iniciando = false,
                        turnoTextoDesconocido = true,
                        actualizacionTurnoTextoDisponible = true,
                        error = "No pudimos cargar la conversación. Consultá el estado antes de continuar."
                    )
                    iniciarSeguimientoCierre()
                    iniciarSeguimientoTurnoTexto()
                }
            }
    }

    private suspend fun crearChat() {
        iniciarChat()
            .onSuccess { chat ->
                println("CHAT INICIADO: id=${chat.id} finalizado=${chat.finalizado}")
                chat.id?.let { ChatStorage.saveChatId(it) }
                _state.value = _state.value.copy(
                    chatId = chat.id,
                    mensajes = chat.mensajes,
                    avisoUrgenteVoz = null,
                    finalizado = chat.finalizado || chat.estadoClasificacion.equals("FALLIDA", ignoreCase = true) ||
                        chat.estadoClasificacion.equals("PARCIAL", ignoreCase = true) || chat.cierreParcial,
                    estadoClasificacion = chat.estadoClasificacion,
                    estadoTurnoTexto = chat.estadoTurnoTexto,
                    intentosTurnoTexto = chat.intentosTurnoTexto,
                    cierreParcial = chat.cierreParcial,
                    estadoRecuperacion = chat.estadoRecuperacion,
                    proximoReintentoEn = chat.proximoReintentoEn,
                    versionEstado = chat.versionEstado,
                    respuestaPendiente = chat.turnoTextoPendiente(),
                    iniciando = false
                )
                guardarEstadoActualEnCache()
            }
            .onFailure { error ->
                println("CHAT ERROR AL INICIAR: ${error.message}")
                _state.value = _state.value.copy(
                    iniciando = false,
                    error = error.message ?: "No se pudo iniciar el chat"
                )
            }
    }

    fun actualizarBorrador(texto: String) {
        if (_state.value.iniciando || _state.value.turnoTextoBloqueaChat || _state.value.verificandoRespuesta || _state.value.estadoClasificacion.equals("PENDIENTE", ignoreCase = true)) return
        _state.value = _state.value.copy(
            borrador = texto,
            envioExitoso = null
        )
    }

    fun enviarBorrador() {
        val estadoActual = _state.value
        val chatId = estadoActual.chatId
        val contenido = estadoActual.borrador
        if (chatId == null || contenido.isBlank() || estadoActual.iniciando || pantallaLiberada || estadoActual.enviando || estadoActual.finalizado || estadoActual.turnoTextoBloqueaChat || estadoActual.verificandoRespuesta || estadoActual.estadoClasificacion.equals("PENDIENTE", ignoreCase = true)) return
        if (estadoActual.estadoVoz != EstadoVoz.INACTIVA) return

        val esReintento = estadoActual.mensajesNoEnviados.contains(contenido)
        // Exclude the last optimistic bubble from the ordered backend snapshot on a manual retry.
        val mensajesAntes = if (esReintento && estadoActual.mensajes.lastOrNull()?.let {
                it.autor == AUTOR_PACIENTE && it.contenido == contenido
            } == true
        ) estadoActual.mensajes.dropLast(1) else estadoActual.mensajes
        val hora = fechaHoraLocal()
        val solicitudId = nuevoUuidSolicitudTexto()
        val mensajesActualizados = if (esReintento) {
            estadoActual.mensajes.mapIndexed { index, mensaje ->
                if (index == estadoActual.mensajes.lastIndex && mensaje.contenido == contenido) {
                    mensaje.copy(fechaHoraEnvio = hora)
                } else {
                    mensaje
                }
            }
        } else {
            estadoActual.mensajes + ChatMensaje(contenido = contenido, autor = AUTOR_PACIENTE, fechaHoraEnvio = hora)
        }

        _state.value = estadoActual.copy(
            enviando = true,
            respuestaPendiente = true,
            error = null,
            envioExitoso = null,
            borrador = "",
            mensajesNoEnviados = estadoActual.mensajesNoEnviados - contenido,
            mensajes = mensajesActualizados,
            solicitudTextoPendienteId = solicitudId,
            contenidoSolicitudTextoPendiente = contenido,
            turnoTextoDesconocido = false,
            estadoTurnoTexto = "PENDIENTE"
        )
        chatConversationCache.guardarSolicitudTexto(chatId, solicitudId, contenido)
        guardarEstadoActualEnCache()

        val generacion = generacionTurnoTexto
        envioTextoJob = viewModelScope.launch {
            val resultado = enviarConReconciliacion(
                chatId = chatId,
                contenido = contenido,
                solicitudId = solicitudId,
                mensajesAntes = mensajesAntes,
                enviar = ::enviarMensaje,
                obtener = ::obtenerChatParaReconciliar
            )
            if (!trabajoTextoActivo(generacion)) return@launch
            when (resultado) {
                is ResultadoEnvioChat.Respuesta -> {
                    val respuesta = resultado.respuesta
                    println("CHAT RESPUESTA: ${respuesta.respuesta}")
                    if (respuesta.origenRespuesta == ORIGEN_CIERRE_SIN_DATOS) {
                        sincronizarCierreSinDatos(chatId, respuesta.respuesta)
                    } else {
                        _state.value = _state.value.copy(
                            enviando = false,
                            respuestaPendiente = false,
                            envioExitoso = true,
                            error = null,
                            mensajes = _state.value.mensajes + respuesta.respuesta,
                            atencionEstimada = respuesta.atencionEstimada ?: _state.value.atencionEstimada,
                            finalizado = respuesta.atencionEstimada != null || _state.value.finalizado,
                            estadoClasificacion = if (respuesta.atencionEstimada != null) "COMPLETADA" else _state.value.estadoClasificacion
                        )
                    }
                    _state.update { it.copy(solicitudTextoPendienteId = null,
                        contenidoSolicitudTextoPendiente = null, respuestaPendiente = false,
                        estadoTurnoTexto = "COMPLETADA", turnoTextoDesconocido = false) }
                    chatConversationCache.guardarSolicitudTexto(chatId, null, null)
                    guardarEstadoActualEnCache()
                }
                is ResultadoEnvioChat.Confirmado -> {
                    _state.value = _state.value.aplicarChatReconciliado(
                        resultado.chat,
                        procesando = resultado.procesando
                    )
                    chatConversationCache.guardarSolicitudTexto(
                        chatId, _state.value.solicitudTextoPendienteId,
                        _state.value.contenidoSolicitudTextoPendiente
                    )
                    guardarEstadoActualEnCache()
                    iniciarSeguimientoCierre()
                    iniciarSeguimientoTurnoTexto()
                }
                is ResultadoEnvioChat.Desconocido -> {
                    _state.value = _state.value.copy(
                        enviando = false,
                        respuestaPendiente = false,
                        solicitudTextoPendienteId = resultado.solicitudId,
                        contenidoSolicitudTextoPendiente = contenido,
                        turnoTextoDesconocido = true,
                        estadoTurnoTexto = resultado.chat?.estadoTurnoTexto,
                        estadoRecuperacion = resultado.chat?.estadoRecuperacion ?: _state.value.estadoRecuperacion,
                        proximoReintentoEn = resultado.chat?.proximoReintentoEn ?: _state.value.proximoReintentoEn,
                        cierreParcial = resultado.chat?.cierreParcial ?: _state.value.cierreParcial,
                        versionEstado = maxOf(_state.value.versionEstado, resultado.chat?.versionEstado ?: 0),
                        siguiendoTurnoTexto = false,
                        actualizacionTurnoTextoDisponible = true,
                        mensajesNoEnviados = emptySet(),
                        borrador = "",
                        error = null
                    )
                    chatConversationCache.guardarSolicitudTexto(chatId, resultado.solicitudId, contenido)
                    guardarEstadoActualEnCache()
                    iniciarSeguimientoTurnoTexto()
                }
                is ResultadoEnvioChat.NoConfirmado -> {
                    val error = resultado.error
                    println("CHAT ERROR AL ENVIAR: ${error.message}")
                    _state.value = _state.value.aplicarFalloEnvioNoConfirmado(contenido, error).copy(
                        solicitudTextoPendienteId = null,
                        contenidoSolicitudTextoPendiente = null,
                        estadoTurnoTexto = "SIN_SOLICITAR"
                    )
                    chatConversationCache.guardarSolicitudTexto(chatId, null, null)
                    guardarEstadoActualEnCache()
                }
            }
        }
    }

    /** GET-only recovery for a durable text turn; an unknown request is never POSTed again. */
    fun actualizarEstadoTurnoTexto() {
        val actual = _state.value
        val chatId = actual.chatId ?: return
        if (pantallaLiberada || actual.verificandoRespuesta || actual.siguiendoTurnoTexto ||
            (actual.solicitudTextoPendienteId == null && !actual.turnoTextoDesconocido &&
                !actual.estadoRecuperacion.equals("REINTENTANDO", true) &&
                !actual.estadoRecuperacion.equals("ESPERANDO_API", true))
        ) return
        _state.update { it.copy(verificandoRespuesta = true, actualizacionTurnoTextoDisponible = false, error = null) }
        val generacion = generacionTurnoTexto
        actualizacionTurnoTextoJob = viewModelScope.launch {
            obtenerChatParaReconciliar(chatId, 2_000L)
                .onSuccess { chat ->
                    if (!trabajoTextoActivo(generacion)) return@onSuccess
                    _state.update { it.aplicarEstadoTurnoTexto(chat) }
                    guardarEstadoActualEnCache()
                    iniciarSeguimientoTurnoTexto()
                    iniciarSeguimientoCierre()
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    if (!trabajoTextoActivo(generacion)) return@onFailure
                    _state.update { it.copy(verificandoRespuesta = false, actualizacionTurnoTextoDisponible = true,
                        turnoTextoDesconocido = true,
                        error = "No pudimos confirmar la conexión. Seguimos verificando automáticamente; no reenvíes el mensaje.") }
                    iniciarSeguimientoTurnoTexto()
                }
        }
    }

    /** Reconcile automatically whenever the chat returns to the foreground; GET only. */
    fun actualizarEstadoEnForeground() {
        appEnPrimerPlano = true
        val actual = _state.value
        val chatId = actual.chatId ?: return
        if (pantallaLiberada || actual.iniciando || actual.verificandoRespuesta || refreshEnForegroundJob?.isActive == true) return
        if (actual.finalizado && actual.esperandoResultadoCierre()) {
            actualizarConversacion()
            return
        }
        val hayRecuperacion = actual.solicitudTextoPendienteId != null || actual.turnoTextoDesconocido ||
            actual.estadoRecuperacion.equals("REINTENTANDO", true) ||
            actual.estadoRecuperacion.equals("ESPERANDO_API", true)
        if (!hayRecuperacion) return
        refreshEnForegroundJob = viewModelScope.launch {
            obtenerChatParaReconciliar(chatId, 2_000L)
                .onSuccess { chat ->
                    _state.update { it.aplicarActualizacionTrasVoz(chat).aplicarEstadoTurnoTexto(chat) }
                    guardarEstadoActualEnCache()
                    iniciarSeguimientoTurnoTexto()
                    iniciarSeguimientoCierre()
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    iniciarSeguimientoTurnoTexto()
                    iniciarSeguimientoCierre()
                }
        }
    }

    fun suspenderSeguimientosEnBackground() {
        appEnPrimerPlano = false
        seguimientoTurnoTextoJob?.cancel()
        seguimientoTurnoTextoJob = null
        seguimientoCierre?.cancel()
        seguimientoCierre = null
        actualizacionTurnoTextoJob?.cancel()
        actualizacionTurnoTextoJob = null
        refreshEnForegroundJob?.cancel()
        refreshEnForegroundJob = null
        _state.update { actual ->
            val habiaTurno = actual.respuestaPendiente || actual.solicitudTextoPendienteId != null ||
                actual.estadoRecuperacion.equals("REINTENTANDO", true) ||
                actual.estadoRecuperacion.equals("ESPERANDO_API", true)
            actual.copy(
                respuestaPendiente = false,
                siguiendoTurnoTexto = false,
                siguiendoCierre = false,
                verificandoRespuesta = false,
                turnoTextoDesconocido = actual.turnoTextoDesconocido || habiaTurno,
                actualizacionTurnoTextoDisponible = false,
                actualizacionCierreDisponible = false
            )
        }
    }

    private fun iniciarSeguimientoTurnoTexto() {
        val id = _state.value.chatId ?: return
        val actual = _state.value
        val recuperacionActiva = actual.estadoRecuperacion.equals("REINTENTANDO", true) ||
            actual.estadoRecuperacion.equals("ESPERANDO_API", true)
        if (pantallaLiberada || (actual.solicitudTextoPendienteId == null && !recuperacionActiva &&
                !actual.respuestaPendiente && !actual.turnoTextoDesconocido) ||
            (!actual.respuestaPendiente && !actual.turnoTextoDesconocido && !recuperacionActiva) ||
            actual.siguiendoTurnoTexto || seguimientoTurnoTextoJob?.isActive == true
        ) return
        _state.update { it.copy(siguiendoTurnoTexto = true, actualizacionTurnoTextoDisponible = false) }
        val generacion = generacionTurnoTexto
        seguimientoTurnoTextoJob = viewModelScope.launch {
            try {
                seguirTurnoTexto(
                        chatId = id,
                        siguePendiente = { trabajoTextoActivo(generacion) && appEnPrimerPlano && (_state.value.respuestaPendiente || _state.value.turnoTextoDesconocido ||
                            _state.value.estadoRecuperacion.equals("REINTENTANDO", true) || _state.value.estadoRecuperacion.equals("ESPERANDO_API", true)) },
                        obtener = ::obtenerChatParaReconciliar,
                        aplicar = { chat ->
                            if (trabajoTextoActivo(generacion)) _state.update { actual ->
                                val actualizado = actual.aplicarEstadoTurnoTexto(chat)
                                if (!actual.siguiendoTurnoTexto && !actualizado.finalizado &&
                                    (actualizado.respuestaPendiente || actualizado.estadoRecuperacion.equals("REINTENTANDO", true) ||
                                        actualizado.estadoRecuperacion.equals("ESPERANDO_API", true))
                                ) actualizado.copy(respuestaPendiente = false, turnoTextoDesconocido = true,
                                    actualizacionTurnoTextoDisponible = false, error = null) else actualizado
                            }
                        },
                        maxDuracionMs = Long.MAX_VALUE,
                        maxPolls = Int.MAX_VALUE,
                        alPasarAEsperaLenta = {
                            if (trabajoTextoActivo(generacion)) _state.update { actual ->
                                if (actual.finalizado) actual else actual.copy(
                                    respuestaPendiente = false,
                                    siguiendoTurnoTexto = false,
                                    turnoTextoDesconocido = true,
                                    actualizacionTurnoTextoDisponible = false,
                                    error = null
                                )
                            }
                        }
                    )
                if (trabajoTextoActivo(generacion)) guardarEstadoActualEnCache()
                if (trabajoTextoActivo(generacion)) iniciarSeguimientoCierre()
            } finally {
                if (trabajoTextoActivo(generacion)) {
                    _state.update { if (it.siguiendoTurnoTexto) it.copy(siguiendoTurnoTexto = false) else it }
                }
            }
        }
    }

    private fun guardarEstadoActualEnCache() {
        val actual = _state.value
        val id = actual.chatId ?: return
        chatConversationCache.guardar(
            ChatResponse(id = id, mensajes = actual.mensajes, finalizado = actual.finalizado,
                estadoClasificacion = actual.estadoClasificacion, estadoTurnoTexto = actual.estadoTurnoTexto,
                solicitudTurnoTextoId = actual.solicitudTextoPendienteId, intentosTurnoTexto = actual.intentosTurnoTexto,
                cierreParcial = actual.cierreParcial, estadoRecuperacion = actual.estadoRecuperacion,
                proximoReintentoEn = actual.proximoReintentoEn, versionEstado = actual.versionEstado),
            actual.atencionEstimada,
            actual.avisoUrgenteVoz
        )
        chatConversationCache.guardarSolicitudTexto(id, actual.solicitudTextoPendienteId, actual.contenidoSolicitudTextoPendiente)
    }

    private fun trabajoTextoActivo(generacion: Int): Boolean =
        !pantallaLiberada && generacion == generacionTurnoTexto

    private fun nuevoUuidSolicitudTexto(): String {
        val bytes = Random.nextBytes(16)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        val hex = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
    /** Refreshes a backend-accepted patient message without sending it again. */
    fun actualizarConversacion() {
        val actual = _state.value
        if (actual.solicitudTextoPendienteId != null) {
            actualizarEstadoTurnoTexto()
            return
        }
        val chatId = actual.chatId ?: return
        val entrevistaCerrada = actual.finalizado
        val cierreSinResultado = entrevistaCerrada && actual.estadoClasificacion.isNullOrBlank()
        if ((!actual.respuestaPendiente && !actual.estadoClasificacion.equals("PENDIENTE", ignoreCase = true) && !cierreSinResultado) || actual.verificandoRespuesta) return
        _state.value = actual.copy(verificandoRespuesta = true, error = null)
        viewModelScope.launch {
            try {
                obtenerChatParaReconciliar(chatId, 1_200L)
                    .onSuccess { chat ->
                        val estadoActual = _state.value
                        _state.value = if (estadoActual.finalizado) {
                            estadoActual.aplicarActualizacionTrasVoz(chat).aplicarEstadoTurnoTexto(chat).copy(verificandoRespuesta = false)
                        } else estadoActual.aplicarEstadoTurnoTexto(chat)
                        guardarEstadoActualEnCache()
                        iniciarSeguimientoTurnoTexto()
                        iniciarSeguimientoCierre()
                    }
                    .onFailure { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        _state.value = _state.value.copy(
                            verificandoRespuesta = false,
                            error = if (entrevistaCerrada) {
                                "No pudimos actualizar el estado de la entrevista. Seguimos verificando automáticamente."
                            } else {
                                "El mensaje quedó guardado, pero no pudimos actualizar la conversación. Seguimos verificando automáticamente; no lo reenvíes."
                            }
                        )
                        iniciarSeguimientoTurnoTexto()
                        iniciarSeguimientoCierre()
                    }
            } finally {
                _state.update { if (it.verificandoRespuesta) it.copy(verificandoRespuesta = false) else it }
            }
        }
    }
    /** A no-data closure ends the interview without representing a successful classification. */
    private suspend fun sincronizarCierreSinDatos(chatId: Long, respuesta: ChatMensaje) {
        val actual = _state.value
        _state.value = actual.copy(
            mensajes = agregarRespuestaSiNueva(actual.mensajes, respuesta),
            mensajesNoEnviados = emptySet(),
            borrador = "",
            atencionEstimada = null,
            finalizado = true,
            estadoClasificacion = "NO_SOLICITADA",
            enviando = false,
            error = null,
            envioExitoso = null
        )
        obtenerChat(chatId).onSuccess { chat ->
            if (chat.finalizado) {
                _state.value = _state.value.copy(
                    mensajes = chat.mensajes,
                    atencionEstimada = null,
                    finalizado = true,
                    estadoClasificacion = "NO_SOLICITADA",
                    mensajesNoEnviados = emptySet(),
                    borrador = "",
                    error = null,
                    envioExitoso = null
                )
            }
        }
    }

    private fun agregarRespuestaSiNueva(
        mensajes: List<ChatMensaje>,
        respuesta: ChatMensaje
    ): List<ChatMensaje> = if (mensajes.lastOrNull() == respuesta) mensajes else mensajes + respuesta

    fun limpiarError() {
        _state.value = _state.value.copy(error = null)
    }

    // ---------------------------------------------------------------------
    // Chat de voz (WebSocket /api/chat/{id}/voz, entrevista con Gemini Live)
    // ---------------------------------------------------------------------

    fun permisoMicrofonoDenegado() {
        _state.value = _state.value.copy(
            error = "Necesitamos permiso para usar el micrófono. Podés seguir por texto."
        )
    }

    fun iniciarVoz() {
        val actual = _state.value
        val chatId = actual.chatId ?: return
        if (pantallaLiberada || actual.iniciando || !actual.vozDisponible || actual.estadoVoz != EstadoVoz.INACTIVA ||
            actual.finalizado || actual.enviando || actual.turnoTextoBloqueaChat || actual.verificandoRespuesta || actual.estadoClasificacion.equals("PENDIENTE", ignoreCase = true)
        ) return

        _state.value = actual.copy(
            estadoVoz = EstadoVoz.CONECTANDO,
            error = null,
            envioExitoso = null,
            procesandoRespuestaVoz = false,
            progresoVozControladoPorServidor = false,
            asistenteHablandoVoz = false,
            errorVoz = false
        )

        // Cola breve: con captura de 20 ms limita a ~160 ms el audio pendiente.
        // Si el socket se atrasa se descarta lo más viejo para no demorar una interrupción.
        val salida = Channel<MensajeVozCliente>(
            capacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        salidaVoz = salida

        sesionVoz = viewModelScope.launch {
            var triageRecibido = false
            var cierreSinDatosRecibido = false
            var entrevistaFinalizadaRecibida = false
            var estadoChatRecibido = false
            try {
                // Consulta previa por HTTP: renueva el token si venció (el handshake no reintenta)
                // y evita abrir la voz sobre un chat ya finalizado (el backend respondería 409).
                val chat = obtenerChat(chatId).getOrNull()
                if (chat?.finalizado == true) {
                    _state.update { it.aplicarActualizacionTrasVoz(chat).aplicarEstadoTurnoTexto(chat) }
                    guardarEstadoActualEnCache()
                    return@launch
                }

                val resultado = conectarChatVoz(chatId, salida) { evento ->
                    if (pantallaLiberada || salidaVoz !== salida || _state.value.chatId != chatId) {
                        return@conectarChatVoz
                    }
                    if (evento is EventoVoz.TriageFinalizado) {
                        triageRecibido = true
                        cierreSinDatosRecibido = evento.origenRespuesta == ORIGEN_CIERRE_SIN_DATOS
                    }
                    if (evento is EventoVoz.ProcesamientoFinalizado) triageRecibido = true
                    if (evento is EventoVoz.EstadoChat) estadoChatRecibido = true
                    if (evento is EventoVoz.EntrevistaFinalizada) entrevistaFinalizadaRecibida = true
                    procesarEventoVoz(evento, salida, chatId)
                }
                resultado.onFailure {
                    // The final server result takes precedence over a later socket failure.
                    if (!triageRecibido && !entrevistaFinalizadaRecibida && !estadoChatRecibido && !_state.value.finalizado) {
                        _state.update { it.copy(error = "Se interrumpió la voz.", errorVoz = true) }
                    }
                }
            } finally {
                val detencionSolicitada = _state.value.estadoVoz == EstadoVoz.DETENIENDO
                audio.detenerGrabacion()
                confirmarTranscripciones()
                salida.close()
                if (salidaVoz === salida) salidaVoz = null
                _state.update { actual ->
                    val cierreSinResultado = entrevistaFinalizadaRecibida && !triageRecibido && !cierreSinDatosRecibido
                    val estadoCierre = if (cierreSinResultado && actual.esperandoResultadoCierre() && !actual.actualizacionCierreDisponible) {
                        EstadoEntrevistaVozUi(true, "PENDIENTE")
                    } else null
                    val desconexionInesperada = !triageRecibido && !entrevistaFinalizadaRecibida &&
                        !detencionSolicitada && !actual.finalizado && !actual.errorVoz
                    actual.copy(
                        estadoVoz = EstadoVoz.INACTIVA,
                        procesandoRespuestaVoz = false,
                        progresoVozControladoPorServidor = false,
                        asistenteHablandoVoz = false,
                        finalizado = estadoCierre?.finalizado ?: actual.finalizado,
                        estadoClasificacion = estadoCierre?.estadoClasificacion ?: actual.estadoClasificacion,
                        errorVoz = actual.errorVoz || desconexionInesperada,
                        error = if (desconexionInesperada) {
                            "Se interrumpió la voz."
                        } else actual.error
                    )
                }
            }
            sincronizarChatTrasVoz(chatId, triageRecibido, cierreSinDatosRecibido)
        }
    }

    /** El paciente corta la conversación de voz. */
    fun detenerVoz() {
        val estado = _state.value.estadoVoz
        // Tras entrevista_finalizada el backend clasifica igual: se espera el resultado.
        if (estado == EstadoVoz.INACTIVA || estado == EstadoVoz.PROCESANDO ||
            estado == EstadoVoz.DETENIENDO
        ) return

        _state.update { it.copy(estadoVoz = EstadoVoz.DETENIENDO) }
        audio.detenerGrabacion()
        audio.interrumpirReproduccion()
        salidaVoz?.trySend(MensajeVozCliente.Cerrar)

        // El backend cierra el socket al recibir "cerrar"; si no lo hace, se corta desde acá.
        val sesion = sesionVoz
        viewModelScope.launch {
            delay(5_000)
            if (sesion?.isActive == true) sesion.cancel()
        }
    }

    private fun procesarEventoVoz(evento: EventoVoz, salida: Channel<MensajeVozCliente>, chatId: Long) {
        when (evento) {
            is EventoVoz.Audio -> {
                if (_state.value.estadoVoz != EstadoVoz.DETENIENDO &&
                    _state.value.estadoVoz != EstadoVoz.INACTIVA
                ) {
                    _state.update { it.cambiarProgresoVoz(CambioProgresoVoz.ASISTENTE_HABLANDO) }
                    audio.reproducir(evento.pcm)
                }
            }

            EventoVoz.Listo -> {
                // Un listo tardío no vuelve a abrir el micrófono tras solicitar cierre.
                if (_state.value.estadoVoz != EstadoVoz.CONECTANDO) return
                val grabando = audio.iniciarGrabacion { pcm ->
                    salida.trySend(MensajeVozCliente.Audio(pcm))
                }
                if (grabando) {
                    _state.value = _state.value.copy(estadoVoz = EstadoVoz.ESCUCHANDO)
                } else {
                    _state.value = _state.value.copy(
                        error = "No se pudo acceder al micrófono."
                    )
                    salida.trySend(MensajeVozCliente.Cerrar)
                }
            }

            is EventoVoz.TranscripcionPaciente -> {
                // Si el paciente empieza a hablar, el turno del asistente terminó.
                if (_state.value.transcripcionBot.isNotBlank()) confirmarTranscripcionBot()
                _state.value = _state.value.copy(
                    transcripcionPaciente = _state.value.transcripcionPaciente + evento.texto
                ).cambiarProgresoVoz(CambioProgresoVoz.RESPUESTA_PACIENTE)
            }

            is EventoVoz.TranscripcionBot -> {
                if (_state.value.transcripcionPaciente.isNotBlank()) confirmarTranscripcionPaciente()
                _state.value = _state.value.copy(
                    transcripcionBot = _state.value.transcripcionBot + evento.texto
                ).cambiarProgresoVoz(CambioProgresoVoz.ASISTENTE_HABLANDO)
            }

            EventoVoz.Interrumpido -> {
                audio.interrumpirReproduccion()
                confirmarTranscripcionBot()
                _state.update { it.cambiarProgresoVoz(CambioProgresoVoz.ASISTENTE_TERMINO) }
            }

            is EventoVoz.ProcesandoRespuesta -> _state.update {
                it.cambiarProgresoVoz(
                    if (evento.activo) CambioProgresoVoz.SERVIDOR_PROCESANDO
                    else CambioProgresoVoz.SERVIDOR_ESPERA_PACIENTE
                )
            }

            EventoVoz.TurnoCompleto -> {
                confirmarTranscripciones()
                _state.update { it.cambiarProgresoVoz(CambioProgresoVoz.ASISTENTE_TERMINO) }
            }

            is EventoVoz.AvisoUrgente -> {
                _state.update { it.aplicarAvisoUrgenteVoz(chatId, evento.mensaje) }
                guardarEstadoActualEnCache()
            }

            EventoVoz.EntrevistaFinalizada -> {
                // El audio de despedida que ya llegó se sigue reproduciendo.
                audio.detenerGrabacion()
                confirmarTranscripciones()
                val estadoCierre = aplicarTransicionCierreVoz(
                    EstadoEntrevistaVozUi(_state.value.finalizado, _state.value.estadoClasificacion),
                    TransicionCierreVoz.CIERRE_ACEPTADO
                )
                _state.value = _state.value.copy(
                    estadoVoz = EstadoVoz.PROCESANDO,
                    finalizado = estadoCierre.finalizado,
                    estadoClasificacion = estadoCierre.estadoClasificacion
                ).cambiarProgresoVoz(CambioProgresoVoz.CIERRE)
                iniciarSeguimientoCierre()
            }

            is EventoVoz.EstadoChat -> procesarSnapshotChatVoz(evento.chat, salida)

            is EventoVoz.ProcesamientoFinalizado -> {
                procesarSnapshotChatVoz(evento.chat, salida)
            }

            is EventoVoz.TriageFinalizado -> {
                confirmarTranscripciones()
                _state.update { it.copy(estadoVoz = EstadoVoz.INACTIVA, error = null, actualizacionCierreDisponible = false) }
                if (evento.origenRespuesta == ORIGEN_CIERRE_SIN_DATOS) {
                    _state.value = _state.value.copy(
                        mensajes = evento.respuesta?.let { agregarRespuestaSiNueva(_state.value.mensajes, it) }
                            ?: _state.value.mensajes,
                        atencionEstimada = null,
                        finalizado = true,
                        estadoClasificacion = "NO_SOLICITADA",
                        cierreParcial = false,
                        estadoRecuperacion = "SIN_RECUPERACION",
                        proximoReintentoEn = null,
                        error = null,
                        errorVoz = false,
                        envioExitoso = null
                    ).cambiarProgresoVoz(CambioProgresoVoz.CIERRE)
                } else {
                    _state.value = _state.value.copy(
                        mensajes = evento.respuesta?.let { agregarRespuestaSiNueva(_state.value.mensajes, it) }
                            ?: _state.value.mensajes,
                        atencionEstimada = evento.atencionEstimada ?: _state.value.atencionEstimada,
                        finalizado = true,
                        estadoClasificacion = "COMPLETADA",
                        cierreParcial = false,
                        estadoRecuperacion = "SIN_RECUPERACION",
                        proximoReintentoEn = null,
                        errorVoz = false
                    ).cambiarProgresoVoz(CambioProgresoVoz.CIERRE)
                }
            }

            EventoVoz.SesionPorExpirar -> _state.value = _state.value.copy(
                error = "La sesión de voz está por terminar."
            )

            is EventoVoz.Error -> {
                if (_state.value.estadoClasificacion.equals("COMPLETADA", true)) return
                val actual = _state.value
                val entrevistaCerrada = actual.finalizado ||
                    actual.estadoClasificacion.equals("COMPLETADA", ignoreCase = true) ||
                    actual.estadoClasificacion.equals("FALLIDA", ignoreCase = true)
                val presentacionError = presentarErrorVoz(evento.mensaje, entrevistaCerrada)
                _state.update {
                    it.copy(
                        error = presentacionError.mensaje,
                        errorVoz = presentacionError.permiteRetomar,
                        procesandoRespuestaVoz = false
                    ).cambiarProgresoVoz(CambioProgresoVoz.CIERRE)
                }
                detenerVoz()
            }

            // El backend cierra el socket después de "fin"; el cierre termina conectarChatVoz.
            EventoVoz.Fin -> Unit
        }
    }

    private fun procesarSnapshotChatVoz(chat: ChatResponse, salida: Channel<MensajeVozCliente>) {
        val turnoPendiente = chat.turnoTextoPendiente() ||
            chat.estadoRecuperacion.equals("REINTENTANDO", true) ||
            chat.estadoRecuperacion.equals("ESPERANDO_API", true)
        val cerrarVoz = chat.finalizado || turnoPendiente
        if (cerrarVoz) {
            audio.detenerGrabacion()
            audio.interrumpirReproduccion()
            confirmarTranscripciones()
            salida.trySend(MensajeVozCliente.Cerrar)
        }
        _state.update { actual ->
            actual.aplicarActualizacionTrasVoz(chat).aplicarEstadoTurnoTexto(chat).copy(
                estadoVoz = if (cerrarVoz) EstadoVoz.INACTIVA else actual.estadoVoz,
                procesandoRespuestaVoz = if (cerrarVoz) false else actual.procesandoRespuestaVoz,
                progresoVozControladoPorServidor = if (cerrarVoz) false else actual.progresoVozControladoPorServidor,
                asistenteHablandoVoz = if (cerrarVoz) false else actual.asistenteHablandoVoz,
                actualizacionCierreDisponible = if (chat.finalizado) false else actual.actualizacionCierreDisponible,
                error = null,
                errorVoz = false
            )
        }
        guardarEstadoActualEnCache()
        if (turnoPendiente) iniciarSeguimientoTurnoTexto()
        iniciarSeguimientoCierre()
    }

    private fun confirmarTranscripciones() {
        confirmarTranscripcionPaciente()
        confirmarTranscripcionBot()
    }

    private fun confirmarTranscripcionPaciente() {
        val texto = _state.value.transcripcionPaciente.trim()
        _state.value = _state.value.copy(
            transcripcionPaciente = "",
            mensajes = if (texto.isEmpty()) _state.value.mensajes else
                _state.value.mensajes + ChatMensaje(texto, AUTOR_PACIENTE, fechaHoraLocal())
        )
    }

    private fun confirmarTranscripcionBot() {
        val texto = _state.value.transcripcionBot.trim()
        _state.value = _state.value.copy(
            transcripcionBot = "",
            mensajes = if (texto.isEmpty()) _state.value.mensajes else
                _state.value.mensajes + ChatMensaje(texto, AUTOR_BOT, fechaHoraLocal())
        )
    }

    private suspend fun sincronizarChatTrasVoz(
        chatId: Long,
        triageRecibido: Boolean,
        cierreSinDatosRecibido: Boolean
    ) {
        if (!triageRecibido) delay(1_500)
        obtenerChat(chatId).onSuccess { chat ->
            _state.update { actual ->
                actual.aplicarActualizacionTrasVoz(chat).aplicarEstadoTurnoTexto(chat).let {
                    if (cierreSinDatosRecibido) it.copy(atencionEstimada = null) else it
                }
            }
            guardarEstadoActualEnCache()
        }
        iniciarSeguimientoCierre()
    }

    private fun iniciarSeguimientoCierre() {
        val id = _state.value.chatId ?: return
        if (_state.value.estadoTurnoTexto.equals("PENDIENTE", true) && _state.value.solicitudTextoPendienteId != null) return
        if (!appEnPrimerPlano || !_state.value.esperandoResultadoCierre() || seguimientoCierre?.isActive == true) return
        _state.update { it.copy(siguiendoCierre = true, actualizacionCierreDisponible = false) }
        seguimientoCierre = viewModelScope.launch {
            try {
                // Poll quickly for the two backend attempts, then continue slowly while foregrounded.
                seguirResultadoCierre(
                        chatId = id,
                        pendiente = { appEnPrimerPlano && _state.value.esperandoResultadoCierre() },
                        obtener = ::obtenerChatParaReconciliar,
                        aplicar = { chat ->
                            val anterior = _state.value
                            val actualizado = anterior.aplicarActualizacionTrasVoz(chat).aplicarEstadoTurnoTexto(chat)
                            if (actualizado.finalizado && !actualizado.esperandoResultadoCierre() &&
                                anterior.estadoVoz != EstadoVoz.INACTIVA
                            ) {
                                audio.detenerGrabacion()
                                audio.interrumpirReproduccion()
                                salidaVoz?.trySend(MensajeVozCliente.Cerrar)
                            }
                            _state.value = actualizado.copy(
                                estadoVoz = if (actualizado.finalizado && !actualizado.esperandoResultadoCierre()) {
                                    EstadoVoz.INACTIVA
                                } else actualizado.estadoVoz,
                                procesandoRespuestaVoz = if (actualizado.finalizado && !actualizado.esperandoResultadoCierre()) false
                                    else actualizado.procesandoRespuestaVoz,
                                progresoVozControladoPorServidor = if (actualizado.finalizado && !actualizado.esperandoResultadoCierre()) false
                                    else actualizado.progresoVozControladoPorServidor,
                                asistenteHablandoVoz = if (actualizado.finalizado && !actualizado.esperandoResultadoCierre()) false
                                    else actualizado.asistenteHablandoVoz
                            )
                            guardarEstadoActualEnCache()
                        },
                        maxDuracionMs = Long.MAX_VALUE,
                        maxPolls = Int.MAX_VALUE,
                        alPasarAEsperaLenta = {
                            _state.update { actual ->
                                if (actual.esperandoResultadoCierre()) actual.copy(
                                    siguiendoCierre = false,
                                    actualizacionCierreDisponible = false,
                                    error = null
                                ) else actual
                            }
                        }
                    )
            } finally {
                _state.update { it.copy(siguiendoCierre = false) }
            }
        }
    }

    /** Corta la voz y libera el audio. Llamar al salir de la pantalla. */
    fun liberarRecursos() {
        confirmarTranscripciones()
        if (_state.value.chatId == ChatStorage.getChatId()) {
            guardarEstadoActualEnCache()
        }
        pantallaLiberada = true
        generacionTurnoTexto++
        envioTextoJob?.cancel()
        envioTextoJob = null
        seguimientoTurnoTextoJob?.cancel()
        seguimientoTurnoTextoJob = null
        actualizacionTurnoTextoJob?.cancel()
        actualizacionTurnoTextoJob = null
        seguimientoCierre?.cancel()
        seguimientoCierre = null
        observacionModoAudio.cancel()
        observacionErrorAudio.cancel()
        sesionVoz?.cancel()
        sesionVoz = null
        audio.liberar()
    }

    override fun onCleared() {
        liberarRecursos()
        super.onCleared()
    }

    // Hora local con el mismo formato que envía el backend ("2026-08-06T14:30:00")
    // para que formatearFechaHora la renderice sin cambios.
    @OptIn(ExperimentalTime::class)
    private fun fechaHoraLocal(): String {
        val ahora = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val hora = ahora.hour.toString().padStart(2, '0')
        val minuto = ahora.minute.toString().padStart(2, '0')
        return "${ahora.date}T$hora:$minuto:00"
    }
}
