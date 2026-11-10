package com.proyecto_final.triage.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.proyecto_final.triage.components.AvisoSancionPaciente
import com.proyecto_final.triage.components.CommonHeader
import com.proyecto_final.triage.components.ConsultarSancionAlReanudar
import com.proyecto_final.triage.components.ErrorBanner
import com.proyecto_final.triage.components.ProgressBar
import com.proyecto_final.triage.components.RestriccionInscripcionRemota
import com.proyecto_final.triage.components.Spinner
import com.proyecto_final.triage.network.EspecialidadMedicaDTO
import com.proyecto_final.triage.network.HospitalCercanoDTO
import com.proyecto_final.triage.theme.AppTheme
import com.proyecto_final.triage.theme.Spacing
import com.proyecto_final.triage.viewmodels.HospitalesState
import com.proyecto_final.triage.viewmodels.HospitalesViewModel
import com.proyecto_final.triage.viewmodels.OrdenHospital
import com.proyecto_final.triage.viewmodels.mensajeInscripcionRemota

class HospitalesScreen(private val type: String, private val ubicacion: String) : Screen {
    @Composable
    override fun Content() {

        val navigator = LocalNavigator.current
        val viewModel = remember { HospitalesViewModel() }
        val state = viewModel.state
        val ordenSeleccionado = viewModel.ordenSeleccionado
        val sancionPaciente = viewModel.sancionPaciente

        ConsultarSancionAlReanudar(sancionPaciente)

        var selectedHospital by remember { mutableStateOf<Hospital?>(null) }

        // Parseo la ubicación una sola vez
        val (lat, lon) = remember(ubicacion) {
            ubicacion.split(",").map { it.trim().toDouble() }
        }

        val buscar = {
            viewModel.buscarHospitalesCercanos(
                latitud = lat,
                longitud = lon,
                codigoEspecialidad = type,
                orden = ordenSeleccionado
            )
        }

        // ENVIO LA REQUEST PARA BUSCAR HOSPITALES
        // El cambio de orden NO va como key: cambiarOrden() ya hace la búsqueda.
        // Si estuviera, cada cambio de orden dispararía dos requests.
        LaunchedEffect(ubicacion, type) {
            buscar()
        }

        LaunchedEffect(viewModel.hospitalSeleccionado) {
            if (viewModel.hospitalSeleccionado) {
                navigator?.items?.firstOrNull()?.let { root ->
                    navigator.replaceAll(listOf(root, ChatScreen()))
                }
            }
        }

        HospitalesContent(
            state = state,
            selectedHospital = selectedHospital,
            ordenSeleccionado = ordenSeleccionado,
            mensajeSancion = sancionPaciente.datos?.mensajeInscripcionRemota(),
            seleccionEnCurso = viewModel.seleccionEnCurso,
            onRetry = { buscar() },
            onOrdenChange = { nuevoOrden ->
                selectedHospital = null
                viewModel.cambiarOrden(
                    nuevoOrden = nuevoOrden,
                    latitud = lat,
                    longitud = lon,
                    codigoEspecialidad = type
                )
            },
            onBack = { navigator?.pop() },
            onHospitalSelected = { hospital -> selectedHospital = hospital },
            onContinue = {
                selectedHospital?.let { hospital ->
                    viewModel.seleccionarHospital(
                        hospital = hospital,
                        codigoEspecialidad = type
                    )
                }
            }
        )

        AvisoSancionPaciente(sancionPaciente)

        viewModel.errorSeleccion?.let { mensaje ->
            AlertDialog(
                onDismissRequest = viewModel::descartarErrorSeleccion,
                title = { Text("No se pudo solicitar atención") },
                text = { Text(mensaje) },
                confirmButton = {
                    TextButton(onClick = viewModel::descartarErrorSeleccion) {
                        Text("Entendido")
                    }
                }
            )
        }
    }
}

@Composable
fun HospitalesContent(
    state: HospitalesState,
    selectedHospital: Hospital?,
    ordenSeleccionado: OrdenHospital,
    mensajeSancion: String?,
    seleccionEnCurso: Boolean,
    onRetry: () -> Unit,
    onOrdenChange: (OrdenHospital) -> Unit,
    onBack: () -> Unit,
    onHospitalSelected: (Hospital) -> Unit,
    onContinue: () -> Unit
) {
    val puedeSeleccionar = mensajeSancion == null && !seleccionEnCurso
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {

        // SIEMPRE VISIBLE
        CommonHeader(
            title = "3. Elegí el hospital",
            subtitle = "Calculamos las mejores opciones para vos",
            showLogo = true,
            onBack = onBack
        )

        ProgressBar(
            currentStep = 3,
            totalSteps = 3
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        OrdenHospitalSelector(
            ordenSeleccionado = ordenSeleccionado,
            onOrdenChange = onOrdenChange
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        val hayHospitales = state is HospitalesState.Success && state.hospitales.isNotEmpty()
        if (mensajeSancion != null && !hayHospitales) {
            RestriccionInscripcionRemota(mensajeSancion)
            Spacer(modifier = Modifier.height(Spacing.md))
        }

        // SOLO ESTA ZONA CAMBIA SEGÚN EL ESTADO
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (state) {

                is HospitalesState.Loading -> {
                    Spinner(color = Color(0xFF5BB8D4))
                }

                is HospitalesState.Error -> {
                    ErrorBanner(
                        message = state.mensaje,
                        buttonText = "Reintentar",
                        icon = Icons.Filled.ErrorOutline,
                        onButtonClick = onRetry
                    )
                }

                is HospitalesState.Success -> {
                    val hospitales = remember(state.hospitales) {
                        state.hospitales.map { it.toHospital() }
                    }

                    if (hospitales.isEmpty()) {
                        Text(
                            text = "No hay hospitales disponibles para atención en este momento.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        // SOLO ESTA PARTE SCROLLEA
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (mensajeSancion != null) {
                                item { RestriccionInscripcionRemota(mensajeSancion) }
                            }
                            items(
                                items = hospitales,
                                key = { it.idHospital }
                            ) { hospital ->
                                HospitalCard(
                                    hospital = hospital,
                                    selected = mensajeSancion == null && selectedHospital?.idHospital == hospital.idHospital,
                                    enabled = puedeSeleccionar,
                                    onClick = { onHospitalSelected(hospital) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // STICKY
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onContinue,
            enabled = puedeSeleccionar && selectedHospital != null && state is HospitalesState.Success,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text("Continuar")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview
@Composable
fun OrdenHospitalSelectorPreview() {
    var orden by remember { mutableStateOf(OrdenHospital.DISTANCIA) }
    AppTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            OrdenHospitalSelector(ordenSeleccionado = orden, onOrdenChange = { orden = it })
        }
    }
}

@Composable
private fun OrdenHospitalSelector(
    ordenSeleccionado: OrdenHospital,
    onOrdenChange: (OrdenHospital) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Ordenar por",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = ordenSeleccionado.label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
            Spacer(Modifier.width(Spacing.xs))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OrdenHospital.entries.forEach { orden ->
                DropdownMenuItem(
                    text = { Text(orden.label) },
                    onClick = {
                        expanded = false
                        if (orden != ordenSeleccionado) onOrdenChange(orden)
                    },
                    trailingIcon = if (orden == ordenSeleccionado) {
                        { Icon(Icons.Filled.Done, contentDescription = "Seleccionado") }
                    } else null
                )
            }
        }
    }
}

@Composable
fun HospitalCard(
    hospital: Hospital,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                Color(0xFF5BB8D4)
            } else {
                Color(0xFFD6D6D6)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            // Nombre del hospital
            Text(
                text = hospital.nombre,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Valoración (solo si el hospital tiene)
            hospital.valoracionPromedio?.let { promedio ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatearValoracion(promedio),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    hospital.cantidadValoraciones?.let { cantidad ->
                        Text(
                            text = " ($cantidad)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Línea divisora
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = Color(0xFFE0E0E0),
                thickness = 1.dp
            )

            val direccionCorta = hospital.direccion.substringBefore(",")

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Row(
                    modifier = Modifier.weight(1.4f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = direccionCorta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }

                // Tiempo estimado arribo - derecha
                hospital.tiempoEstimadoArriboMejorRuta?.let { tiempo ->

                    Row(
                        modifier = Modifier.weight(0.8f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.width(5.dp))

                        Text(
                            text = formatearTiempoArribo(tiempo),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            // --- Info de atención: cola, espera y fecha estimada ---
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Pacientes en cola
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.People,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${hospital.pacientesEnCola} en cola",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Minutos espera estimados
                hospital.minutosEsperaEstimados?.let { minutos ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.HourglassEmpty,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatearMinutosEspera(minutos),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Fecha/hora atención estimada
            hospital.fechaHoraAtencionEstimada?.let { fechaHora ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Atención estimada: ${formatearFechaHoraHospital(fechaHora)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Especialidades
            Text(
                text = "Especialidades",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                hospital.especialidades.forEach { especialidad ->

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFA8D5A2)
                    ) {
                        Text(
                            text = especialidad.nombre,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 6.dp
                            )
                        )
                    }
                }
            }
        }
    }
}

data class Hospital(
    val idHospital: Long,
    val placeId: String?,
    val nombre: String,
    val direccion: String,
    val especialidades: List<EspecialidadMedicaDTO>,
    val tiempoEstimadoArriboMejorRuta: String?,
    val pacientesEnCola: Int = 0,
    val minutosEsperaEstimados: Long? = null,
    val fechaHoraAtencionEstimada: String? = null,
    val valoracionPromedio: Double? = null,
    val cantidadValoraciones: Int? = null
)

private fun HospitalCercanoDTO.toHospital(): Hospital {
    return Hospital(
        idHospital = idHospital,
        placeId = placeId,
        nombre = nombre,
        direccion = direccion,
        especialidades = especialidades,
        tiempoEstimadoArriboMejorRuta = tiempoEstimadoArriboMejorRuta,
        pacientesEnCola = pacientesEnCola,
        minutosEsperaEstimados = minutosEsperaEstimados,
        fechaHoraAtencionEstimada = fechaHoraAtencionEstimada,
        valoracionPromedio = valoracionPromedio,
        cantidadValoraciones = cantidadValoraciones
    )
}

// Helpers de formateo — reutilizan lógica existente en el proyecto

// 4.36 -> "4,4"
private fun formatearValoracion(valor: Double): String {
    val redondeado = kotlin.math.round(valor * 10) / 10
    return redondeado.toString().replace(".", ",")
}

private fun formatearTiempoArribo(tiempo: String): String {
    // Backend devuelve LocalTime como "HH:mm:ss" o "HH:mm"
    val partes = tiempo.split(":")
    if (partes.size < 2) return tiempo
    val horas = partes[0].toIntOrNull() ?: 0
    val minutos = partes[1].toIntOrNull() ?: 0
    val segundos = partes.getOrNull(2)?.toIntOrNull() ?: 0
    return when {
        horas > 0 -> if (minutos > 0) "$horas h $minutos min" else "$horas h"
        minutos > 0 -> if (segundos >= 30) "${minutos + 1} min" else "$minutos min"
        else -> "Menos de 1 min"
    }
}

private fun formatearMinutosEspera(minutos: Long): String {
    return when {
        minutos <= 0 -> "Sin espera"
        minutos < 60 -> "$minutos min espera"
        else -> {
            val h = minutos / 60
            val m = minutos % 60
            if (m > 0) "$h h $m min espera" else "$h h espera"
        }
    }
}

// "2024-06-15T14:30:00" -> "15/06/2024 14:30" (similar a ChatScreen.kt:498)
private fun formatearFechaHoraHospital(fechaHora: String): String {
    return try {
        val sinZona = fechaHora.substringBefore("Z").substringBefore("+")
        val partes = sinZona.replace("T", " ").substringBeforeLast(":").split("-")
        if (partes.size != 3) return fechaHora
        val anio = partes[0]
        val mes = partes[1]
        val diaHora = partes[2]
        val partesDiaHora = diaHora.split(" ")
        if (partesDiaHora.size != 2) return fechaHora
        "${partesDiaHora[0]}/${mes}/${anio} ${partesDiaHora[1]}"
    } catch (_: Exception) {
        fechaHora
    }
}
