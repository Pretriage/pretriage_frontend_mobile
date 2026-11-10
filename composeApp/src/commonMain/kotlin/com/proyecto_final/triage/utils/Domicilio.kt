package com.proyecto_final.triage.utils

private const val PROVINCIA_CABA = "Ciudad Autónoma de Buenos Aires"

internal fun provinciaParaSelector(provincia: String, ciudad: String? = null): String =
    if (esCaba(provincia, ciudad)) PROVINCIA_CABA else provincia

internal fun esCaba(provincia: String?, ciudad: String? = null): Boolean {
    val nombreProvincia = provincia.orEmpty().trim().lowercase()
    // Algunos perfiles anteriores guardan CABA en el campo ciudad.
    return esNombreDeCaba(nombreProvincia) ||
        ((nombreProvincia.isEmpty() || nombreProvincia == "buenos aires") &&
            esNombreDeCaba(ciudad.orEmpty()))
}

private fun esNombreDeCaba(nombre: String): Boolean =
    when (nombre.trim().lowercase().replace(".", "").replace('ó', 'o')) {
        "caba", "capital federal", "ciudad autonoma de buenos aires", "ciudad de buenos aires" -> true
        else -> false
    }
