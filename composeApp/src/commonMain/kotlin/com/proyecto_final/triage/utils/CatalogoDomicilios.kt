package com.proyecto_final.triage.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import triage.composeapp.generated.resources.Res

@Serializable
internal data class CatalogoDomicilios(val provinces: List<Provincia>)

@Serializable
internal data class Provincia(val name: String, val cities: List<String>)

private val jsonCatalogoDomicilios = Json { ignoreUnknownKeys = true }

/** Catálogo de Georef; regenerar desde el script del frontend web al actualizarlo. */
@OptIn(ExperimentalResourceApi::class)
internal suspend fun cargarCatalogoDomicilios(): CatalogoDomicilios {
    val bytes = Res.readBytes("files/argentina-locations.json")
    return jsonCatalogoDomicilios.decodeFromString(bytes.decodeToString())
}

/** Normaliza mayúsculas, tildes y marcas combinadas para comparar y buscar ubicaciones. */
internal fun claveUbicacion(nombre: String): String = buildString(nombre.length) {
    for (caracter in nombre.trim().lowercase()) {
        when (caracter) {
            'á', 'à', 'ä', 'â', 'ã', 'å' -> append('a')
            'é', 'è', 'ë', 'ê' -> append('e')
            'í', 'ì', 'ï', 'î' -> append('i')
            'ó', 'ò', 'ö', 'ô', 'õ' -> append('o')
            'ú', 'ù', 'ü', 'û' -> append('u')
            'ñ' -> append('n')
            in '\u0300'..'\u036f' -> Unit
            else -> append(caracter)
        }
    }
}
