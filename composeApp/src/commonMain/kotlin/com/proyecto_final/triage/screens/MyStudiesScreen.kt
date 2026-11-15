package com.proyecto_final.triage.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.proyecto_final.triage.theme.AppTheme
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.proyecto_final.triage.components.CommonHeader
import com.proyecto_final.triage.theme.Spacing
import com.proyecto_final.triage.viewmodels.StudiesViewModel
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.proyecto_final.triage.network.EstudioClinicoDTO
import com.proyecto_final.triage.viewmodels.EstudiosState
import com.proyecto_final.triage.viewmodels.LocalStudiesViewModel
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Description
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.filterNotNull

class MyStudiesScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel = LocalStudiesViewModel.current

        MyStudiesContent(
            onBack = { navigator?.pop() },
            onAddStudy = { navigator?.push(AddStudyScreen()) },
            onStudyClick = { estudio -> navigator?.push(MyStudyScreen(estudio)) },
            viewModel = viewModel
        )
    }

}

@Composable
fun MyStudiesContent(   onBack: () -> Unit,
                        onAddStudy: () -> Unit,
                        onStudyClick: (EstudioClinicoDTO) -> Unit,
                        viewModel: StudiesViewModel
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.cargarEstudios()
    }

    LaunchedEffect(Unit) {
        viewModel.mensajeExito.filterNotNull().collect { mensaje ->
            // Se limpia ANTES de mostrarlo: showSnackbar espera hasta que se oculta,
            // y si el usuario sale de la pantalla en ese lapso el efecto se cancela.
            // Limpiando después, el mensaje quedaba en el ViewModel (compartido por
            // toda la app) y volvía a aparecer al regresar, aunque el estudio ya no estuviera.
            // Se usa collect (y no LaunchedEffect(mensajeExito)) para que limpiar no
            // reinicie el efecto y corte el snackbar recién mostrado.
            viewModel.limpiarMensajeExito()
            snackbarHostState.showSnackbar(
                message = mensaje,
                duration = SnackbarDuration.Short
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

            Column( modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                // Título de la pantalla
                CommonHeader(title = "Mis estudios", onBack = { onBack() })

                Spacer(modifier = Modifier.height(Spacing.lg))

                when (state) {

                    is EstudiosState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }

                    is EstudiosState.Error -> {
                        Text((state as EstudiosState.Error).message)
                    }

                    is EstudiosState.Success -> {
                        val estudios = (state as EstudiosState.Success).estudios

                        if (estudios.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().height(130.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F2)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No hay estudios disponibles",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.Gray
                                    )

                                }
                            }
                        } else {
                            estudios.forEach { estudio ->
                                StudyCard(
                                    estudio = estudio,
                                    onClick = { onStudyClick(estudio) }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onAddStudy,
                        shape = RoundedCornerShape(25)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Agregar estudio")
                    }
                }
            }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp, start = 16.dp, end = 16.dp)
        ) { data ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE3F2FD))
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = Color(0xFF5BB8D4),
                    modifier = Modifier.size(Spacing.lg)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF5BB8D4)
                )
            }
        }
    }
}

private fun obtenerIconoPorTipo(tipoArchivo: String): ImageVector {
    return when (tipoArchivo) {
        "Análisis de laboratorio"   -> Icons.Default.Bloodtype
        "Radiografía"               -> Icons.Default.Radar
        "Resonancia"                -> Icons.Default.GraphicEq
        "Tomografía"                -> Icons.Default.Science
        "Ecografía"                 -> Icons.Default.Waves
        "Otro"                      -> Icons.Default.Description
        else                        -> Icons.Default.Description
    }
}

@Composable
fun StudyCard(estudio: EstudioClinicoDTO, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF5BB8D4), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = obtenerIconoPorTipo(estudio.tipoArchivo),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = estudio.tipoArchivo, style = MaterialTheme.typography.titleMedium)

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                    val fecha = estudio.fechaSubida?.let { LocalDateTime.parse(it) }

                    Text(
                        text = fecha?.let {
                            "${it.day.toString().padStart(2, '0')}/" +
                                    "${it.month.number.toString().padStart(2, '0')}/" +
                                    it.year
                        } ?: "",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }
}

@Preview
@Composable
fun MyStudiesPreview() {
    AppTheme {
        MyStudiesContent(onBack = {},
            onAddStudy = {},
            onStudyClick = {},
            viewModel = StudiesViewModel()
        )
    }
}