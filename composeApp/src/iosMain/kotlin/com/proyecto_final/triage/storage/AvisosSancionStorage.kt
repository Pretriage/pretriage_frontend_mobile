package com.proyecto_final.triage.storage

import platform.Foundation.NSUserDefaults

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal actual object AvisosSancionStorage {
    private val defaults get() = NSUserDefaults.Companion.standardUserDefaults

    actual fun fueVisto(id: String): Boolean = defaults.boolForKey("aviso_sancion_$id")

    actual fun marcarVisto(id: String) {
        defaults.setBool(true, forKey = "aviso_sancion_$id")
    }
}
