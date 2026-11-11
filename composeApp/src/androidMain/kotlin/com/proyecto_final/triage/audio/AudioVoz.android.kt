package com.proyecto_final.triage.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import com.proyecto_final.triage.appContext
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.thread

private const val FRECUENCIA_GRABACION = 16_000
private const val FRECUENCIA_REPRODUCCION = 24_000
// 20 ms de PCM 16 bits mono a 16 kHz: evita agregar latencia al barge-in.
private const val BYTES_POR_FRAGMENTO = 640

actual fun crearAudioVoz(): AudioVoz = AudioVozAndroid()

private fun tienePermisoMicrofono(): Boolean =
    ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

@Composable
actual fun rememberSolicitudPermisoMicrofono(onResultado: (Boolean) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onResultado)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido -> callback(concedido) }
    return remember(launcher) {
        {
            if (tienePermisoMicrofono()) callback(true)
            else launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

private class AudioVozAndroid : AudioVoz {
    override val disponible = true
    private val _modoEntrada = MutableStateFlow(ModoEntradaVoz.POR_TURNOS)
    override val modoEntrada: StateFlow<ModoEntradaVoz> = _modoEntrada.asStateFlow()
    private val _errorAudio = MutableStateFlow<String?>(null)
    override val errorAudio: StateFlow<String?> = _errorAudio.asStateFlow()
    @Volatile private var bloqueadoPorFallo = false
    @Volatile private var forzarPorTurnos = false
    @Volatile private var sesionComunicacionActiva = false
    @Volatile private var focoObtenido = false
    @Volatile private var ecoHabilitado = false
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handlerAudio = Handler(Looper.getMainLooper())
    private var modoAnterior = AudioManager.MODE_NORMAL
    private var cambioModo = false
    private var solicitudFoco: AudioFocusRequest? = null
    private var dispositivoAnterior: AudioDeviceInfo? = null
    private var dispositivoElegidoId: Int? = null
    private var parlanteAnterior = false
    private var parlanteAsignado: Boolean? = null

    private val cambioFoco = AudioManager.OnAudioFocusChangeListener { cambio ->
        synchronized(this) {
            if (sesionComunicacionActiva) {
                if (cambio == AudioManager.AUDIOFOCUS_GAIN) {
                    focoObtenido = true
                    synchronized(bloqueoReproduccion) { actualizarModoEntrada() }
                } else {
                    // No dejar una entrevista aparentemente activa con micrófono mudo.
                    fallarAudio("Otra aplicación necesita el audio. Volvé a iniciar la conversación de voz.")
                }
            }
        }
    }
    private val cambioDispositivos = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = actualizarRuta()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = actualizarRuta()
    }
    private val cambioRuta = AudioRouting.OnRoutingChangedListener {
        synchronized(bloqueoReproduccion) { actualizarModoEntrada() }
    }

    @Volatile private var grabando = false
    private var grabador: AudioRecord? = null
    private var hiloGrabacion: Thread? = null
    private var cancelacionEco: AcousticEchoCanceler? = null
    // Supresor de ruido del dispositivo: VOICE_COMMUNICATION no lo garantiza en todos los fabricantes.
    private var supresionRuido: NoiseSuppressor? = null

    @Volatile private var liberado = false
    private var reproductor: AudioTrack? = null
    private var hiloReproduccion: Thread? = null
    private data class Fragmento(val generacion: Long, val pcm: ByteArray)
    private val pendientes = LinkedBlockingQueue<Fragmento>()
    private val bloqueoReproduccion = Any()
    private val controlEco = ControlEcoVoz()
    private var generacionReproduccion = 0L

    @SuppressLint("MissingPermission")
    @Synchronized
    override fun iniciarGrabacion(onFragmento: (ByteArray) -> Unit): Boolean {
        if (liberado || !tienePermisoMicrofono()) return false
        if (grabando) return true
        bloqueadoPorFallo = false
        _errorAudio.value = null
        if (!iniciarSesionComunicacion()) return false

        val minimo = AudioRecord.getMinBufferSize(
            FRECUENCIA_GRABACION, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minimo <= 0) {
            finalizarSesionSiInactiva()
            return false
        }

        val nuevoGrabador = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                FRECUENCIA_GRABACION,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimo, BYTES_POR_FRAGMENTO * 2)
            )
        } catch (_: Exception) {
            finalizarSesionSiInactiva()
            return false
        }
        if (nuevoGrabador.state != AudioRecord.STATE_INITIALIZED) {
            nuevoGrabador.release()
            finalizarSesionSiInactiva()
            return false
        }
        // AEC es una ayuda del dispositivo; no garantiza eliminar el parlante.
        cancelacionEco = runCatching {
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(nuevoGrabador.audioSessionId)
            } else null
        }.getOrNull()
        ecoHabilitado = cancelacionEco?.let { efecto ->
            runCatching {
                efecto.setEnabled(true) == AudioEffect.SUCCESS && efecto.enabled
            }.getOrDefault(false)
        } ?: false
        if (!ecoHabilitado) {
            runCatching { cancelacionEco?.release() }
            cancelacionEco = null
        }
        // Reduce el ruido ambiente (guardias, salas de espera) antes de mandar el audio.
        // No se activa el control automático de ganancia: en lugares ruidosos también sube el ruido.
        supresionRuido = runCatching {
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(nuevoGrabador.audioSessionId)?.also { efecto ->
                    if (efecto.setEnabled(true) != AudioEffect.SUCCESS) {
                        efecto.release()
                        throw IllegalStateException("No se pudo activar el supresor de ruido")
                    }
                }
            } else null
        }.getOrNull()

        try {
            nuevoGrabador.startRecording()
        } catch (_: IllegalStateException) {
            cancelacionEco?.release()
            cancelacionEco = null
            ecoHabilitado = false
            runCatching { supresionRuido?.release() }
            supresionRuido = null
            nuevoGrabador.release()
            finalizarSesionSiInactiva()
            return false
        }
        grabador = nuevoGrabador
        grabando = true
        synchronized(bloqueoReproduccion) { actualizarModoEntrada() }
        hiloGrabacion = thread(name = "chat-voz-grabacion") {
            val buffer = ByteArray(BYTES_POR_FRAGMENTO)
            while (grabando && !liberado) {
                val silenciarAntes = synchronized(bloqueoReproduccion) { debeSilenciarMicrofono() }
                val leidos = runCatching { nuevoGrabador.read(buffer, 0, buffer.size) }
                    .getOrDefault(AudioRecord.ERROR_INVALID_OPERATION)
                if (leidos < 0) {
                    handlerAudio.post {
                        synchronized(this) {
                            if (grabando && grabador === nuevoGrabador) {
                                fallarAudio("No se pudo continuar con el micrófono. Volvé a iniciar la conversación de voz.")
                            }
                        }
                    }
                    break
                }
                if (leidos == 0) continue
                synchronized(bloqueoReproduccion) {
                    if (grabando && !liberado) {
                        val silenciarDespues = debeSilenciarMicrofono()
                        // También descartar lecturas que cruzan el fin del audio/eco.
                        // PCM cero mantiene el flujo y el VAD sin enviar el audio del bot.
                        onFragmento(
                            if (silenciarAntes || silenciarDespues) ByteArray(leidos)
                            else buffer.copyOf(leidos)
                        )
                    }
                }
            }
        }
        return true
    }

    // Sólo bajo bloqueoReproduccion: la cola vacía no implica que el parlante terminó.
    private fun debeSilenciarMicrofono(): Boolean {
        val reproducidos = reproductor?.playbackHeadPosition?.toLong()?.and(0xffff_ffffL) ?: 0L
        // Actualizar siempre el drenaje, incluso en dúplex: cambiar a protección por
        // turnos durante una respuesta debe conservar el estado real del buffer.
        val ecoPendiente = controlEco.debeSilenciar(reproducidos, SystemClock.elapsedRealtime())
        return !focoObtenido || (_modoEntrada.value == ModoEntradaVoz.POR_TURNOS && ecoPendiente)
    }

    @Synchronized
    override fun detenerGrabacion() {
        grabando = false
        val actual = grabador
        grabador = null
        runCatching { actual?.stop() }
        hiloGrabacion?.join(500)
        hiloGrabacion = null
        runCatching { cancelacionEco?.release() }
        cancelacionEco = null
        ecoHabilitado = false
        runCatching { supresionRuido?.release() }
        supresionRuido = null
        actual?.release()
        synchronized(bloqueoReproduccion) { actualizarModoEntrada() }
        // La despedida puede seguir sonando: no restaurar ruta/modo antes de su fin.
        finalizarSesionSiInactiva()
    }

    @Synchronized
    override fun reproducir(pcm: ByteArray) {
        if (liberado || bloqueadoPorFallo || pcm.isEmpty() || pcm.size % 2 != 0) return
        if (!iniciarSesionComunicacion() || !focoObtenido) return
        asegurarReproductor() ?: run { finalizarSesionSiInactiva(); return }
        synchronized(bloqueoReproduccion) {
            controlEco.encolar(pcm.size / 2L)
            pendientes.offer(Fragmento(generacionReproduccion, pcm))
        }
    }

    @Synchronized
    private fun asegurarReproductor(): AudioTrack? {
        if (liberado) return null
        reproductor?.let { return it }
        val minimo = AudioTrack.getMinBufferSize(
            FRECUENCIA_REPRODUCCION, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minimo <= 0) return null
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(FRECUENCIA_REPRODUCCION)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minimo, FRECUENCIA_REPRODUCCION / 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            return null
        }
        if (runCatching { track.play() }.isFailure) {
            track.release()
            return null
        }
        track.addOnRoutingChangedListener(cambioRuta, handlerAudio)
        synchronized(bloqueoReproduccion) { reproductor = track; actualizarModoEntrada() }
        hiloReproduccion = thread(name = "chat-voz-reproduccion") {
            try {
                while (!liberado) {
                    val fragmento = pendientes.poll(20, TimeUnit.MILLISECONDS)
                    if (fragmento == null) {
                        synchronized(this) { finalizarSesionSiInactiva() }
                        continue
                    }
                    var posicion = 0
                    while (!liberado && posicion < fragmento.pcm.size) {
                        val escritos = synchronized(bloqueoReproduccion) {
                            if (liberado || fragmento.generacion != generacionReproduccion) {
                                Int.MIN_VALUE
                            } else {
                                // No bloquear el control de eco mientras el buffer está lleno.
                                // El mismo lock impide escribir audio viejo después de un flush.
                                runCatching {
                                    track.write(fragmento.pcm, posicion,
                                        fragmento.pcm.size - posicion, AudioTrack.WRITE_NON_BLOCKING)
                                }.getOrDefault(AudioTrack.ERROR_INVALID_OPERATION)
                            }
                        }
                        when {
                            escritos == Int.MIN_VALUE -> break
                            escritos < 0 -> {
                                synchronized(bloqueoReproduccion) {
                                    if (!liberado && fragmento.generacion == generacionReproduccion) {
                                        vaciarReproduccion(track)
                                    }
                                }
                                handlerAudio.post {
                                    synchronized(this) {
                                        if (!liberado && !bloqueadoPorFallo) {
                                            fallarAudio("No se pudo reproducir el audio. Volvé a iniciar la conversación de voz.")
                                        }
                                    }
                                }
                                break
                            }
                            escritos == 0 -> Thread.sleep(10)
                            else -> posicion += escritos
                        }
                    }
                }
            } catch (_: InterruptedException) {
                // liberar() despierta la espera de la cola o del buffer.
            }
        }
        return track
    }

    // Sólo bajo bloqueoReproduccion. flush() reinicia playbackHeadPosition a cero.
    private fun vaciarReproduccion(track: AudioTrack?) {
        generacionReproduccion++
        pendientes.clear()
        track?.let {
            it.pause()
            it.flush()
            if (!liberado) it.play()
        }
        controlEco.vaciar(SystemClock.elapsedRealtime())
    }

    @Synchronized
    override fun interrumpirReproduccion() {
        synchronized(bloqueoReproduccion) {
            vaciarReproduccion(reproductor)
        }
        finalizarSesionSiInactiva()
    }

    @Synchronized
    override fun liberar() {
        if (liberado) return
        liberado = true
        detenerGrabacion()
        hiloReproduccion?.interrupt()
        synchronized(bloqueoReproduccion) {
            runCatching { vaciarReproduccion(reproductor) }
            reproductor?.let { track ->
                track.removeOnRoutingChangedListener(cambioRuta)
                runCatching { track.stop() }
                track.release()
            }
            reproductor = null
        }
        hiloReproduccion?.join(500)
        hiloReproduccion = null
        finalizarSesionComunicacion()
    }

    // Se llama con this adquirido. No volver a pedir foco por fragmentos tardíos.
    private fun fallarAudio(mensaje: String) {
        bloqueadoPorFallo = true
        focoObtenido = false
        runCatching { interrumpirReproduccion() }
        detenerGrabacion()
        finalizarSesionComunicacion()
        _errorAudio.value = mensaje
    }

    @Synchronized
    override fun configurarModoPorTurnos(activar: Boolean) {
        forzarPorTurnos = activar
        synchronized(bloqueoReproduccion) { actualizarModoEntrada() }
    }

    // Sólo bajo bloqueoReproduccion. Consultar la ruta real, no sólo dispositivos presentes.
    private fun actualizarModoEntrada() {
        val ruta = runCatching {
            reproductor?.routedDevice ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.communicationDevice
            } else null
        }.getOrNull()
        val tipo = ruta?.type
        val auriculares = tipo != null && esAuricular(tipo)
        val salidaConocida = tipo == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
            tipo == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        _modoEntrada.value = modoEntradaVoz(
            forzarPorTurnos, sesionComunicacionActiva, focoObtenido,
            auriculares, salidaConocida, ecoHabilitado
        )
    }

    @Suppress("DEPRECATION")
    private fun iniciarSesionComunicacion(): Boolean {
        if (sesionComunicacionActiva) return focoObtenido
        // No apropiarse del audio de otra llamada o aplicación de comunicación.
        modoAnterior = audioManager.mode
        if (modoAnterior != AudioManager.MODE_NORMAL) return false
        parlanteAnterior = audioManager.isSpeakerphoneOn
        dispositivoAnterior = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice
        } else null
        val atributos = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        return try {
            val resultado = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val solicitud = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(atributos)
                    .setOnAudioFocusChangeListener(cambioFoco, handlerAudio)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true).build()
                solicitudFoco = solicitud
                audioManager.requestAudioFocus(solicitud)
            } else {
                audioManager.requestAudioFocus(cambioFoco, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
            if (resultado != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                abandonarFoco()
                false
            } else {
                focoObtenido = true
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                cambioModo = true
                sesionComunicacionActiva = audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
                if (!sesionComunicacionActiva) {
                    finalizarSesionComunicacion()
                    false
                } else {
                    seleccionarRuta()
                    audioManager.registerAudioDeviceCallback(cambioDispositivos, handlerAudio)
                    true
                }
            }
        } catch (_: Exception) {
            finalizarSesionComunicacion()
            false
        }
    }

    @Synchronized
    private fun actualizarRuta() {
        if (!sesionComunicacionActiva || liberado) return
        runCatching { seleccionarRuta() }
        ecoHabilitado = runCatching { cancelacionEco?.enabled == true }.getOrDefault(false)
        synchronized(bloqueoReproduccion) { actualizarModoEntrada() }
    }

    @Suppress("DEPRECATION")
    private fun seleccionarRuta() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val actual = audioManager.communicationDevice
            val disponibles = audioManager.availableCommunicationDevices
            // Respetar una selección de auriculares ya vigente; al enchufar otros,
            // preferirlos al override de parlante que pidió esta instancia.
            val preferido = actual?.takeIf { esAuricular(it.type) }
                ?: disponibles.firstOrNull { esAuricular(it.type) }
                ?: actual?.takeIf { it.id != dispositivoElegidoId &&
                    it.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                ?: disponibles.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (preferido != null && preferido.id != actual?.id &&
                audioManager.setCommunicationDevice(preferido)) {
                dispositivoElegidoId = preferido.id
            }
        } else {
            // No iniciar SCO ni pedir BLUETOOTH_CONNECT. Mantener las rutas que el
            // sistema ya activó y usar el parlante sólo cuando no hay auriculares.
            val hayAuricular = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any { esAuricular(it.type) }
            val usarParlante = !hayAuricular && !audioManager.isBluetoothScoOn
            if (audioManager.isSpeakerphoneOn != usarParlante) {
                audioManager.isSpeakerphoneOn = usarParlante
                parlanteAsignado = usarParlante
            }
        }
    }

    private fun esAuricular(tipo: Int): Boolean = when (tipo) {
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET -> true
        else -> false // A2DP/BLE_SPEAKER/USB genérico podrían ser un parlante externo.
    }

    // Se llama con this adquirido, fuera de bloqueoReproduccion.
    private fun finalizarSesionSiInactiva() {
        if (!sesionComunicacionActiva || grabando) return
        val audioPendiente = synchronized(bloqueoReproduccion) {
            val reproducidos = reproductor?.playbackHeadPosition?.toLong()?.and(0xffff_ffffL) ?: 0L
            controlEco.debeSilenciar(reproducidos, SystemClock.elapsedRealtime())
        }
        if (!audioPendiente) finalizarSesionComunicacion()
    }

    @Suppress("DEPRECATION")
    private fun abandonarFoco() {
        solicitudFoco?.let { solicitud ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { audioManager.abandonAudioFocusRequest(solicitud) }
            }
        } ?: runCatching { audioManager.abandonAudioFocus(cambioFoco) }
        solicitudFoco = null
        focoObtenido = false
    }

    @Suppress("DEPRECATION")
    private fun finalizarSesionComunicacion() {
        sesionComunicacionActiva = false
        runCatching { audioManager.unregisterAudioDeviceCallback(cambioDispositivos) }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dispositivoElegidoId != null &&
                    audioManager.communicationDevice?.id == dispositivoElegidoId) {
                    // Sólo restaurar nuestra selección; no pisar cambios del usuario.
                    val anterior = dispositivoAnterior
                    val disponibles = audioManager.availableCommunicationDevices
                    if (anterior != null && disponibles.any { it.id == anterior.id } &&
                        (esAuricular(anterior.type) || disponibles.none { esAuricular(it.type) })) {
                        audioManager.setCommunicationDevice(anterior)
                    } else audioManager.clearCommunicationDevice()
                }
            } else if (parlanteAsignado != null &&
                audioManager.isSpeakerphoneOn == parlanteAsignado) {
                audioManager.isSpeakerphoneOn = parlanteAnterior
            }
        }
        dispositivoElegidoId = null
        dispositivoAnterior = null
        parlanteAsignado = null
        if (cambioModo && audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
            runCatching { audioManager.mode = modoAnterior }
        }
        cambioModo = false
        abandonarFoco()
        synchronized(bloqueoReproduccion) { actualizarModoEntrada() }
    }
}
