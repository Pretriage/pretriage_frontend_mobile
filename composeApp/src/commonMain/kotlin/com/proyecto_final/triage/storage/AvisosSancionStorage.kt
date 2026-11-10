package com.proyecto_final.triage.storage

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal expect object AvisosSancionStorage {
    fun fueVisto(id: String): Boolean
    fun marcarVisto(id: String)
}
