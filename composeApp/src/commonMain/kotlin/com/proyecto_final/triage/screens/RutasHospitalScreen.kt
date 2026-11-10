package com.proyecto_final.triage.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.proyecto_final.triage.components.CommonHeader
import com.proyecto_final.triage.components.ErrorBanner
import com.proyecto_final.triage.components.Spinner
import com.proyecto_final.triage.network.CombinacionRutasDTO
import com.proyecto_final.triage.network.RutasHospitalState
import com.proyecto_final.triage.network.TiempoEstimadoArriboHospitalResponse
import com.proyecto_final.triage.network.estadoConsulta.HospitalSeleccionadoResponse
import com.proyecto_final.triage.platform.PlatformMap
import com.proyecto_final.triage.theme.Spacing
import com.proyecto_final.triage.viewmodels.RutasHospitalViewModel

class RutasHospitalScreen(
    private val hospital: Hospital,
    private val ubicacion: String
) : Screen {

    @Composable
    override fun Content() {

        val navigator = LocalNavigator.current

        val viewModel = remember {
            RutasHospitalViewModel()
        }

        val state = viewModel.state

        val coordenadas = ubicacion
            .split(",")
            .map { it.trim().toDouble() }

        val latitud = coordenadas[0]
        val longitud = coordenadas[1]

        LaunchedEffect(Unit) {

            viewModel.cargarRutas(
                idHospital = hospital.idHospital,
                transporte = "transporte-publico",
                latitud = latitud,
                longitud = longitud
            )
        }

        when (state) {

            is RutasHospitalState.Loading -> {
                Spinner(color= Color(0xFF5BB8D4))
            }

            is RutasHospitalState.Error -> {

                ErrorBanner(
                    message = state.message,
                    buttonText = "Reintentar",
                    icon = Icons.Filled.ErrorOutline,
                    onButtonClick = {

                        viewModel.cargarRutas(
                            idHospital = hospital.idHospital,
                            transporte = "transporte-publico",
                            latitud = latitud,
                            longitud = longitud
                        )
                    }
                )
            }

            is RutasHospitalState.Success -> {

                RutasHospitalContent(
                    hospital = hospital,
                    rutas = state.rutas,
                    ubicacion = ubicacion,
                    onBack = {
                        navigator?.pop()
                    },
                    onConfirmar = {
                        // POST después
                    }
                )
            }
        }
    }
}


/* ============================================================
   CONTENIDO PRINCIPAL
   ============================================================ */

@Composable
fun RutasHospitalContent(
    hospital: Hospital,
    rutas: List<TiempoEstimadoArriboHospitalResponse>,
    ubicacion: String,
    onBack: () -> Unit,
    onConfirmar: () -> Unit
) {

    /*
     * Ruta seleccionada.
     *
     * Por defecto seleccionamos la primera ruta.
     */
    var rutaSeleccionada by remember(rutas) {
        mutableStateOf(0)
    }

    val rutaActual = rutas.getOrNull(rutaSeleccionada)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
            )
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(horizontal = 16.dp)
        ) {

            CommonHeader(
                title = "Cómo llegar",
                subtitle = "Revisá las opciones para llegar al hospital",
                onBack = onBack
            )

            Spacer(
                modifier = Modifier.height(Spacing.lg)
            )

            Text(
                text = hospital.nombre,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = hospital.direccion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            /*
             * MAPA
             *
             * Mostramos la ruta seleccionada.
             */

            PlatformMap(
                hospital = HospitalSeleccionadoResponse(
                    idHospital = hospital.idHospital,
                    placeId = hospital.placeId,
                    nombre = hospital.nombre,
                    direccion = hospital.direccion,
                    sectorId = 0,//no se usa aca
                    nombreSector = ""// no se usa aca
                ),
                ubicacion = ubicacion,
                polylineCode = rutaActual?.polylineCode
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            /*
             * CANTIDAD DE RUTAS
             */

            Text(
                text = if (rutas.size == 1) {
                    "1 ruta disponible"
                } else {
                    "${rutas.size} rutas disponibles"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            /*
             * RUTAS
             *
             * Al tocar una ruta se actualiza el MiniMap.
             */

            rutas.forEachIndexed { index, ruta ->

                RutaCard(
                    ruta = ruta,
                    seleccionada = index == rutaSeleccionada,
                    onClick = {
                        rutaSeleccionada = index
                    }
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )
        }

    }
}


/* ============================================================
   MODELO VISUAL DE UN TRAMO
   ============================================================ */

data class TramoRuta(
    val tipoTransporte: String,
    val nombreLinea: String?,
    val indicaciones: List<String>
)


/* ============================================================
   AGRUPAR TRAMOS
   ============================================================ */

fun agruparTramos(
    combinaciones: List<CombinacionRutasDTO>
): List<TramoRuta> {

    val resultado = mutableListOf<TramoRuta>()

    combinaciones.forEach { combinacion ->

        val esCaminar = combinacion.tipoTransporte
            .equals(
                "caminar",
                ignoreCase = true
            )

        /*
         * Si ya tenemos un tramo caminando
         * y el nuevo también es caminar,
         * los juntamos.
         */

        if (
            esCaminar &&
            resultado.lastOrNull()
                ?.tipoTransporte
                ?.equals(
                    "caminar",
                    ignoreCase = true
                ) == true
        ) {

            val ultimo = resultado.removeAt(
                resultado.lastIndex
            )

            resultado.add(
                ultimo.copy(
                    indicaciones =
                        ultimo.indicaciones +
                                combinacion.indicaciones
                )
            )

        } else {

            resultado.add(
                TramoRuta(
                    tipoTransporte =
                        combinacion.tipoTransporte,

                    nombreLinea =
                        combinacion.nombreLinea,

                    indicaciones =
                        listOf(
                            combinacion.indicaciones
                        )
                )
            )
        }
    }

    return resultado
}


/* ============================================================
   CARD DE UNA RUTA COMPLETA
   ============================================================ */

@Composable
fun RutaCard(
    ruta: TiempoEstimadoArriboHospitalResponse,
    seleccionada: Boolean,
    onClick: () -> Unit
) {

    var expanded by remember {
        mutableStateOf(false)
    }

    /*
     * Agrupamos los caminar consecutivos.
     */

    val tramos = remember(ruta) {

        agruparTramos(
            ruta.combinacionesLineas
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
                expanded = !expanded
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                if (seleccionada) {
                    Color(0xFFE3F2FD)
                } else {
                    MaterialTheme.colorScheme.surface
                }
        ),
        border = BorderStroke(
            width =
                if (seleccionada) {
                    2.dp
                } else {
                    1.dp
                },
            color =
                if (seleccionada) {
                    Color(0xFF5BB8D4)
                } else {
                    Color(0xFFD6D6D6)
                }
        )
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            /*
             * CABECERA
             */

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Icon(
                            imageVector =
                                Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = Color(0xFF5BB8D4),
                            modifier = Modifier.size(19.dp)
                        )

                        Spacer(
                            modifier = Modifier.width(5.dp)
                        )

                        Text(
                            text = formatearTiempo(
                                ruta.tiempoEstimadoArribo
                            ),
                            style =
                                MaterialTheme.typography.titleMedium,
                            color =
                                MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(
                            modifier = Modifier.width(10.dp)
                        )

                        Text(
                            text = "· ${
                                formatearDistancia(
                                    ruta.distanciaMetros
                                )
                            }",
                            style =
                                MaterialTheme.typography.bodyMedium,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector =
                        if (expanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                    contentDescription = null,
                    tint =
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            /*
             * RESUMEN DE LA RUTA
             */

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                tramos.forEachIndexed { index, tramo ->

                    if (index > 0) {

                        Text(
                            text = "›",
                            style =
                                MaterialTheme.typography.bodyLarge,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    RutaChip(
                        tramo = tramo
                    )
                }
            }

            /*
             * DETALLE DE LA RUTA
             */

            if (expanded) {

                HorizontalDivider(
                    modifier = Modifier.padding(
                        vertical = 14.dp
                    ),
                    color = Color(0xFFE0E0E0)
                )

                tramos.forEachIndexed { index, tramo ->

                    RutaIndicacion(
                        tramo = tramo
                    )

                    if (index < tramos.lastIndex) {

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )
                    }
                }
            }
        }
    }
}


/* ============================================================
   CHIP DEL TRAMO
   ============================================================ */

@Composable
fun RutaChip(
    tramo: TramoRuta
) {

    val esCaminar = tramo.tipoTransporte
        .equals(
            "caminar",
            ignoreCase = true
        )

    val esBicicleta = tramo.tipoTransporte
        .equals(
            "bicicleta",
            ignoreCase = true
        )

    val icono = when {

        esCaminar ->
            Icons.AutoMirrored.Filled.DirectionsWalk

        esBicicleta ->
            Icons.AutoMirrored.Filled.DirectionsBike

        else ->
            Icons.Filled.DirectionsBus
    }

    val tieneLinea =
        !tramo.nombreLinea.isNullOrBlank()

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE3F2FD)
        )
    ) {

        Row(
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 6.dp
            ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = Color(0xFF5BB8D4),
                modifier = Modifier.size(18.dp)
            )

            if (tieneLinea) {

                Spacer(
                    modifier = Modifier.width(4.dp)
                )

                Text(
                    text = tramo.nombreLinea,
                    style =
                        MaterialTheme.typography.labelMedium,
                    color =
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}


/* ============================================================
   INDICACIONES DE UN TRAMO
   ============================================================ */

@Composable
fun RutaIndicacion(
    tramo: TramoRuta
) {

    val esCaminar = tramo.tipoTransporte
        .equals(
            "caminar",
            ignoreCase = true
        )

    val esBicicleta = tramo.tipoTransporte
        .equals(
            "bicicleta",
            ignoreCase = true
        )

    val icono = when {

        esCaminar ->
            Icons.AutoMirrored.Filled.DirectionsWalk

        esBicicleta ->
            Icons.AutoMirrored.Filled.DirectionsBike

        else ->
            Icons.Filled.DirectionsBus
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {

        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = Color(0xFF5BB8D4),
            modifier = Modifier.size(20.dp)
        )

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            if (
                !esCaminar &&
                !tramo.nombreLinea.isNullOrBlank()
            ) {

                Text(
                    text = tramo.nombreLinea,
                    style =
                        MaterialTheme.typography.titleSmall,
                    color =
                        MaterialTheme.colorScheme.onSurface
                )

                Spacer(
                    modifier = Modifier.height(3.dp)
                )
            }

            tramo.indicaciones.filter { it.isNotBlank() }.forEach { indicacion ->

                Text(
                    text = indicacion,
                    style =
                        MaterialTheme.typography.bodyMedium,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )
            }
        }
    }
}


/* ============================================================
   FORMATEAR TIEMPO
   ============================================================ */

fun formatearTiempo(
    tiempo: String?
): String {

    if (tiempo == null) {
        return "--"
    }

    val partes = tiempo.split(":")

    if (partes.size != 3) {
        return tiempo
    }

    val horas =
        partes[0].toIntOrNull() ?: 0

    val minutos =
        partes[1].toIntOrNull() ?: 0

    val segundos =
        partes[2].toIntOrNull() ?: 0

    return when {

        horas > 0 -> {

            if (minutos > 0) {
                "$horas h $minutos min"
            } else {
                "$horas h"
            }
        }

        minutos > 0 -> {

            if (segundos >= 30) {
                "${minutos + 1} min"
            } else {
                "$minutos min"
            }
        }

        else -> {
            "Menos de 1 min"
        }
    }
}


/* ============================================================
   FORMATEAR DISTANCIA
   ============================================================ */

fun formatearDistancia(
    metros: Int
): String {

    return if (metros >= 1000) {

        val kilometros =
            metros / 1000.0

        "${(kilometros * 10).toInt() / 10.0} km"

    } else {

        "$metros m"
    }
}


/* ============================================================
   FORMATEAR TRANSPORTE
   ============================================================ */

fun formatearTipoTransporte(
    tipo: String
): String {

    return tipo
        .replace("-", " ")
        .replaceFirstChar {
            it.uppercase()
        }
}
