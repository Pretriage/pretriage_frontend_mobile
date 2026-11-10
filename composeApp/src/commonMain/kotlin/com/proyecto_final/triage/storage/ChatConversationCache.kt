package com.proyecto_final.triage.storage

import com.proyecto_final.triage.network.AtencionEstimada
import com.proyecto_final.triage.network.ChatResponse

/** One in-memory navigation snapshot. Never writes transcripts to preferences or disk. */
internal class ChatConversationCache {
    private var chat: ChatResponse? = null
    private var atencion: AtencionEstimada? = null
    private var avisoUrgenteVoz: String? = null
    private var solicitudChatId: Long? = null
    private var solicitudTexto: Pair<String, String>? = null

    fun guardar(snapshot: ChatResponse, estimacion: AtencionEstimada?, avisoUrgenteVoz: String? = null) {
        chat = snapshot
        atencion = estimacion
        this.avisoUrgenteVoz = avisoUrgenteVoz
    }

    fun recuperar(id: Long): Pair<ChatResponse, AtencionEstimada?>? =
        chat?.takeIf { it.id == id }?.let { it to atencion }

    fun recuperarAvisoUrgenteVoz(id: Long): String? =
        avisoUrgenteVoz?.takeIf { chat?.id == id }

    fun guardarSolicitudTexto(chatId: Long, solicitudId: String?, contenido: String?) {
        solicitudChatId = chatId
        solicitudTexto = if (solicitudId != null && contenido != null) solicitudId to contenido else null
    }

    fun recuperarSolicitudTexto(chatId: Long): Pair<String, String>? =
        solicitudTexto?.takeIf { solicitudChatId == chatId }

    fun limpiar() {
        chat = null
        atencion = null
        avisoUrgenteVoz = null
        solicitudChatId = null
        solicitudTexto = null
    }
}

internal val chatConversationCache = ChatConversationCache()
