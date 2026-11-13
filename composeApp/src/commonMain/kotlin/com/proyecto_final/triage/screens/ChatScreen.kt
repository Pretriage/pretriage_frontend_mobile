package com.proyecto_final.triage.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.proyecto_final.triage.audio.rememberSolicitudPermisoMicrofono
import com.proyecto_final.triage.audio.ModoEntradaVoz
import com.proyecto_final.triage.components.CommonHeader
import com.proyecto_final.triage.components.ErrorBanner
import com.proyecto_final.triage.components.InfoBanner
import com.proyecto_final.triage.network.AUTOR_BOT
import com.proyecto_final.triage.network.AUTOR_PACIENTE
import com.proyecto_final.triage.network.AtencionEstimada
import com.proyecto_final.triage.network.ChatMensaje
import com.proyecto_final.triage.network.esAutorBot
import com.proyecto_final.triage.theme.AppTheme
import com.proyecto_final.triage.viewmodels.ChatViewModel
import com.proyecto_final.triage.viewmodels.turnoTextoBloqueaChat
import com.proyecto_final.triage.viewmodels.ChatClassificationPresentation
import com.proyecto_final.triage.viewmodels.presentacionClasificacionChat
import com.proyecto_final.triage.viewmodels.textoPanelVoz
import com.proyecto_final.triage.viewmodels.EstadoVoz
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class ChatScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel = remember { ChatViewModel() }
        // El ViewModel se crea con remember (no hay onCleared): se libera el audio al salir.
        DisposableEffect(viewModel) {
            onDispose { viewModel.liberarRecursos() }
        }
        ChatContent(
            onBack = { navigator?.pop() },
            viewModel = viewModel
        )
    }
}

@Preview
@Composable
fun ChatPreview() {
    AppTheme {
        ChatContent(
            onBack = {},
            viewModel = ChatViewModel()
        )
    }
}

@Composable
fun ChatContent(
    onBack: () -> Unit,
    viewModel: ChatViewModel
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    var mostrarExito by remember { mutableStateOf(false) }
    val listaState = rememberLazyListState()
    val vozActiva = state.estadoVoz != EstadoVoz.INACTIVA
    val presentacionClasificacion = presentacionClasificacionChat(
        finalizado = state.finalizado,
        estadoClasificacion = state.estadoClasificacion
    )

    val solicitarMicrofono = rememberSolicitudPermisoMicrofono { concedido ->
        if (concedido) viewModel.iniciarVoz() else viewModel.permisoMicrofonoDenegado()
    }

    // El InfoBanner se muestra hasta que el usuario envía su primer mensaje.
    val mostrarInfo = !state.iniciando && state.mensajes.none { !esAutorBot(it.autor) }
    // Offset de items fijos antes de los mensajes: header + (banner cuando está visible)
    val offsetMensajes = if (mostrarInfo) 2 else 1

    LaunchedEffect(Unit) {
        viewModel.iniciar()
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.actualizarEstadoEnForeground()
            if (event == Lifecycle.Event.ON_STOP) viewModel.suspenderSeguimientosEnBackground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Al enviar un mensaje: llevo la vista al mensaje recién agregado.
    LaunchedEffect(state.enviando) {
        if (state.enviando && state.mensajes.isNotEmpty()) {
            scrollAlFinal(listaState, offsetMensajes + state.mensajes.size - 1)
        }
    }

    // Al recibir mensajes: solo bajo al final si el usuario ya está cerca del fondo
    // (o en la primera carga del chat). Si está leyendo histórico, la vista no se mueve.
    var scrollInicialHecho by remember { mutableStateOf(false) }
    LaunchedEffect(state.mensajes.size) {
        if (state.mensajes.isNotEmpty()) {
            val ultimoVisible = listaState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val cercaDelFinal = ultimoVisible >= listaState.layoutInfo.totalItemsCount - 2
            if (!scrollInicialHecho || cercaDelFinal) {
                scrollInicialHecho = true
                scrollAlFinal(listaState, offsetMensajes + state.mensajes.size - 1)
            }
        }
    }

    // Transcripción de voz en vivo: se sigue el final de la lista mientras el usuario esté abajo.
    val hayTranscripcion = state.transcripcionPaciente.isNotBlank() || state.transcripcionBot.isNotBlank()
    LaunchedEffect(hayTranscripcion, state.transcripcionPaciente.length / 40, state.transcripcionBot.length / 40) {
        val total = listaState.layoutInfo.totalItemsCount
        val ultimoVisible = listaState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (hayTranscripcion && total > 0 && ultimoVisible >= total - 3) {
            listaState.animateScrollToItem(total - 1)
        }
    }

    // Feedback transitorio de éxito del envío
    LaunchedEffect(state.envioExitoso) {
        if (state.envioExitoso == true) {
            mostrarExito = true
            delay(2500.milliseconds)
            mostrarExito = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        // Error al iniciar el chat
        if (state.error != null && state.chatId == null) {
            ErrorBanner(
                message = state.error ?: "No se pudo iniciar el chat",
                buttonText = "Reintentar",
                icon = Icons.Filled.Backup,
                onButtonClick = { viewModel.iniciar() }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Lista de mensajes
        LazyColumn(
            state = listaState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                CommonHeader(title = "Chat interactivo", onBack = { onBack() })
            }

            if (mostrarInfo) {
                item {
                    InfoBanner(
                        text = if (state.vozDisponible)
                            "Describí tus síntomas por escrito o tocá el micrófono para hablar con el asistente. Él estimará una prioridad de atención."
                        else
                            "Describí tus síntomas por escrito y el asistente estimará una prioridad de atención."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (state.mensajes.isEmpty() && state.iniciando) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Cargando chat...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            itemsIndexed(state.mensajes) { index, mensaje ->
                BurbujaDeMensaje(
                    mensaje = mensaje,
                    noEnviado = state.mensajesNoEnviados.contains(mensaje.contenido)
                )
                if (index == state.mensajes.size - 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            if (state.enviando || (state.respuestaPendiente && !state.turnoTextoDesconocido) ||
                (state.siguiendoTurnoTexto && !state.turnoTextoDesconocido)
            ) {
                item {
                    BurbujaDeMensaje(
                        ChatMensaje(
                            contenido = if (state.turnoTextoDesconocido) "Consultando estado..." else "Escribiendo...",
                            autor = "BOT"
                        ),
                        escribiendo = true
                    )
                }
            }

            // Transcripciones de voz en curso
            if (state.transcripcionPaciente.isNotBlank()) {
                item {
                    BurbujaDeMensaje(ChatMensaje(state.transcripcionPaciente.trim(), AUTOR_PACIENTE))
                }
            }
            if (state.transcripcionBot.isNotBlank()) {
                item {
                    BurbujaDeMensaje(ChatMensaje(state.transcripcionBot.trim(), AUTOR_BOT))
                }
            }

            if (state.estadoVoz == EstadoVoz.PROCESANDO &&
                presentacionClasificacion == ChatClassificationPresentation.NO_SOLICITADA
            ) {
                item {
                    BurbujaDeMensaje(
                        ChatMensaje(
                            contenido = "Registrando tus respuestas...",
                            autor = AUTOR_BOT
                        ),
                        escribiendo = true
                    )
                }
            }
        }

        state.avisoUrgenteVoz?.let { mensaje ->
            Surface(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Assertive
                },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Buscá atención médica urgente ahora",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(mensaje, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Card de atención estimada / prioridad
        state.atencionEstimada?.let { atencion ->
            CardAtencionEstimada(atencion)
        }

        // Cerrar la entrevista no significa que el triage haya terminado correctamente.
        when (presentacionClasificacion) {
            ChatClassificationPresentation.COMPLETADA -> {
                InfoBanner(
                    text = "Entrevista completada. Podés consultar el estado de tu atención.",
                    icon = Icons.Filled.Check
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            ChatClassificationPresentation.PARCIAL -> {
                InfoBanner(
                    text = "Tus respuestas quedaron guardadas para el equipo médico.",
                    icon = Icons.Filled.Check
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            ChatClassificationPresentation.PROCESANDO -> {
                InfoBanner(text = "Estamos procesando tus respuestas...")
                if (state.error != null) {
                    Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            ChatClassificationPresentation.FALLIDA -> {
                InfoBanner(text = "No pudimos procesar tus respuestas. Tus respuestas quedaron guardadas.")
                Spacer(modifier = Modifier.height(12.dp))
            }
            ChatClassificationPresentation.CERRADA_SIN_RESULTADO -> {
                InfoBanner(text = if (state.siguiendoCierre) "Estamos confirmando el resultado..." else "Todavía no pudimos confirmar el resultado.")
                if (state.error != null) {
                    Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            ChatClassificationPresentation.NO_SOLICITADA -> {
                if (state.finalizado) {
                    InfoBanner(text = "La entrevista finalizó. Tus respuestas quedaron registradas.", icon = Icons.Filled.Check)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        if (state.turnoTextoDesconocido && state.chatId != null &&
            !state.estadoRecuperacion.equals("REINTENTANDO", true) &&
            !state.estadoRecuperacion.equals("ESPERANDO_API", true)
        ) {
            InfoBanner(text = if (state.solicitudTextoPendienteId != null) {
                "Seguimos confirmando tu mensaje automáticamente. No hace falta volver a enviarlo."
            } else {
                "Seguimos verificando el estado de tu conversación automáticamente."
            })
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (state.estadoRecuperacion.equals("REINTENTANDO", ignoreCase = true)) {
            InfoBanner(text = "Estamos procesando tu respuesta. Se va a actualizar automáticamente.")
            Spacer(modifier = Modifier.height(12.dp))
        } else if (state.estadoRecuperacion.equals("ESPERANDO_API", ignoreCase = true)) {
            InfoBanner(text = "Tu respuesta está guardada. Estamos intentando conectar de nuevo.")
            Spacer(modifier = Modifier.height(12.dp))
        }
        // Error al enviar (texto conservado en el campo). Once closed, classification status is authoritative.
        if (state.error != null && state.chatId != null && !state.finalizado) {
            if (state.errorVoz && state.estadoVoz == EstadoVoz.INACTIVA) {
                ErrorBanner(
                    message = state.error ?: "Se interrumpió la conexión de voz.",
                    buttonText = "Retomar voz",
                    icon = Icons.Filled.Mic,
                    onButtonClick = viewModel::iniciarVoz
                )
            } else {
                Text(
                    text = state.error ?: "Error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }

        if (mostrarExito && !state.finalizado && state.error == null) {
            Text(
                text = "Mensaje enviado",
                color = Color(0xFF2E7D32),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
        }

        // Conversación por voz en curso: reemplaza la entrada de texto
        if (vozActiva && state.estadoVoz != EstadoVoz.PROCESANDO) {
            PanelVoz(
                estado = state.estadoVoz,
                modoEntrada = state.modoEntradaVoz,
                procesandoRespuesta = state.procesandoRespuestaVoz || state.avisoUrgenteVoz != null,
                asistenteHablando = state.asistenteHablandoVoz,
                onDetener = viewModel::detenerVoz
            )
            return@Column
        }

        if (state.finalizado) return@Column

        // Entrada de mensaje
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = state.borrador,
                onValueChange = viewModel::actualizarBorrador,
                enabled = state.chatId != null && !state.iniciando && !state.finalizado && !state.enviando && !state.turnoTextoBloqueaChat && !state.verificandoRespuesta && !state.estadoClasificacion.equals("PENDIENTE", ignoreCase = true),
                placeholder = {
                    Text("Escribí tus síntomas...")
                },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            val puedeHablar = state.vozDisponible &&
                state.chatId != null &&
                !state.iniciando &&
                !state.finalizado &&
                !state.enviando &&
                !state.turnoTextoBloqueaChat &&
                !state.verificandoRespuesta && !state.estadoClasificacion.equals("PENDIENTE", ignoreCase = true)

            // Micrófono: solo si todavía no escribió nada, para no competir con el botón de enviar.
            if (state.vozDisponible && state.borrador.isBlank()) {
                IconButton(
                    onClick = { if (puedeHablar) solicitarMicrofono() },
                    enabled = puedeHablar,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (puedeHablar) Color(0xFF5BB8D4) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Hablar con el asistente",
                        tint = if (puedeHablar) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Row
            }

            val puedeEnviar = state.borrador.isNotBlank() &&
                state.chatId != null &&
                !state.iniciando &&
                !state.finalizado &&
                !state.enviando &&
                !state.turnoTextoBloqueaChat &&
                !state.verificandoRespuesta && !state.estadoClasificacion.equals("PENDIENTE", ignoreCase = true)

            if (state.enviando) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp
                )
            } else {
                IconButton(
                    onClick = {
                        if (puedeEnviar) {
                            viewModel.enviarBorrador()
                        }
                    },
                    enabled = puedeEnviar,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (puedeEnviar) Color(0xFF5BB8D4) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                        tint = if (puedeEnviar) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

}

@Composable
private fun BurbujaDeMensaje(
    mensaje: ChatMensaje,
    escribiendo: Boolean = false,
    noEnviado: Boolean = false
) {
    val esDelBot = esAutorBot(mensaje.autor) || escribiendo

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (esDelBot) Alignment.Start else Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (esDelBot)
                MaterialTheme.colorScheme.surfaceVariant
            else
                Color(0xFF5BB8D4),
            shadowElevation = 2.dp
        ) {
            if (escribiendo) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = mensaje.contenido,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = mensaje.contenido,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (esDelBot)
                        MaterialTheme.colorScheme.onSurface
                    else
                        Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        mensaje.fechaHoraEnvio?.takeIf { it.isNotBlank() }?.let { hora ->
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatearFechaHora(hora),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (noEnviado) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "No enviado",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun PanelVoz(
    estado: EstadoVoz,
    modoEntrada: ModoEntradaVoz,
    procesandoRespuesta: Boolean,
    asistenteHablando: Boolean,
    onDetener: () -> Unit
) {
    val texto = textoPanelVoz(estado, modoEntrada, procesandoRespuesta, asistenteHablando)
    val puedeDetener = estado == EstadoVoz.CONECTANDO || estado == EstadoVoz.ESCUCHANDO

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFE3F2FD),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (estado == EstadoVoz.ESCUCHANDO) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = Color(0xFF5BB8D4),
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = texto,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (puedeDetener) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDetener,
                        modifier = Modifier
                            .size(48.dp)
                            .background(color = MaterialTheme.colorScheme.error, shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Terminar conversación por voz",
                            tint = Color.White
                        )
                    }
                }
            }

            // Sin auriculares el celular capta más ruido ambiente y el propio parlante.
            if (estado == EstadoVoz.ESCUCHANDO && modoEntrada == ModoEntradaVoz.POR_TURNOS) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Si estás en un lugar ruidoso, usá auriculares y hablá cerca del micrófono.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CardAtencionEstimada(atencion: AtencionEstimada) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.PriorityHigh,
                    contentDescription = null,
                    tint = Color(0xFF5BB8D4),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tu atención",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            atencion.mensaje?.takeIf { it.isNotBlank() }?.let { mensaje ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = mensaje,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (atencion.posicionEnCola > 0 || atencion.pacientesAntes > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    MetricColumn(label = "Posición en cola", valor = "${atencion.posicionEnCola}")
                    MetricColumn(label = "Pacientes antes", valor = "${atencion.pacientesAntes}")
                    MetricColumn(label = "Tiempo estimado", valor = "${atencion.minutosPromedioAtencion} min")
                }
            }

            if (!atencion.hayMedicosActivos) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "No hay médicos activos para estimar la atención",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9E9E9E)
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.MetricColumn(label: String, valor: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

// Baja al final de la lista. Con mucha distancia usa scroll instantáneo para
// evitar animaciones largas que interfieren con el gesto del usuario.
private suspend fun scrollAlFinal(listaState: LazyListState, target: Int) {
    val distancia = target - listaState.firstVisibleItemIndex
    if (distancia > 20) {
        listaState.scrollToItem(target)
    } else {
        listaState.animateScrollToItem(target)
    }
}

// Formateo simple: "2024-06-15T14:30:00Z" -> "15/06/2024 14:30"
private fun formatearFechaHora(fechaHora: String): String {
    val partes = fechaHora.replace("T", " ").substringBeforeLast(":").split("-")
    
    val anio= partes[0]
    val mes = partes[1]
    val diaHora = partes[2]
    val partesDiaHora = diaHora.split(" ")

    return "${partesDiaHora[0]}/${mes}/${anio} ${partesDiaHora[1]}"
}
