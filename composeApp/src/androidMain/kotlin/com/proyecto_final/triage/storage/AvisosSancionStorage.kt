package com.proyecto_final.triage.storage

import android.content.Context
import androidx.core.content.edit
import com.proyecto_final.triage.appContext

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual object AvisosSancionStorage {
    private val preferencias by lazy {
        appContext.getSharedPreferences("avisos_sancion", Context.MODE_PRIVATE)
    }

    actual fun fueVisto(id: String): Boolean = preferencias.getBoolean(id, false)

    actual fun marcarVisto(id: String) {
        preferencias.edit { putBoolean(id, true) }
    }
}
