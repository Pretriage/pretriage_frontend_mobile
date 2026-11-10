package com.proyecto_final.triage.screens

// Mantener sincronizado con pretriage_frontend_web/features/reception/coverage-plans.ts.
private val obrasSociales = listOf(
    "OSDE" to listOf("210", "310", "410", "450", "510"),
    "Swiss Medical" to listOf("SMG02", "SMG20", "SMG30", "SMG40"),
    "Galeno" to listOf("220", "330", "440", "550"),
    "Medifé" to listOf("Bronce", "Bronce Classic", "Plata", "Plata Classic", "Oro", "Platinum", "Juntos", "Medife+"),
    "Omint" to listOf("1500", "2500", "2600", "4021", "4500", "6500", "8500"),
    "SanCor Salud" to listOf("F700", "F800", "F801", "S1000", "S1500", "S3000", "S3500", "S4000", "S4500", "S5000", "S6000"),
    "Accord Salud" to listOf("Accord 1.5", "Accord 2.2", "Accord 3.2", "Accord 4.2", "Accord 110", "Accord 210", "Accord 310"),
    "Unión Personal" to listOf("Classic", "Familiar", "UP10", "PMO", "Monotributista"),
    "OSECAC" to listOf("Cobertura general PMO"),
    "OSDEPYM" to listOf("800", "Staff", "1000", "2000", "2500", "3000", "4000", "Personal"),
    "OSPE" to listOf("A-401", "A-608", "A-706"),
    "OSUTHGRA" to listOf("Cobertura general PMO"),
    "OSPERYH" to listOf("Programa Médico OSPERYH"),
    "IOMA" to listOf("Cobertura general"),
    "PAMI" to listOf("Cobertura general"),
    "DOSUBA" to listOf("General", "Estudiantes y Graduados"),
)

private val espacios = Regex("\\s+")
private val acentosCombinados = Regex("[\u0300-\u036f]")
private val letrasSinAcento = mapOf(
    'Á' to 'A', 'É' to 'E', 'Í' to 'I', 'Ó' to 'O', 'Ú' to 'U', 'Ü' to 'U', 'Ñ' to 'N'
)

internal fun claveObraSocial(nombre: String): String {
    val normalizado = nombre.uppercase().replace(acentosCombinados, "").trim().replace(espacios, " ")
    return buildString {
        normalizado.forEach { append(letrasSinAcento[it] ?: it) }
    }
}

private val planesPorObraSocial = obrasSociales.associate { (nombre, planes) ->
    claveObraSocial(nombre) to planes
}

internal fun planesDeObraSocial(nombre: String): List<String> =
    planesPorObraSocial[claveObraSocial(nombre)] ?: emptyList()
