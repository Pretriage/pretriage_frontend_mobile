package com.proyecto_final.triage.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.annotation.ExperimentalVoyagerApi
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.jetpack.navigatorViewModel
import com.proyecto_final.triage.components.AvisoSancionPaciente
import com.proyecto_final.triage.components.ConsultarSancionAlReanudar
import com.proyecto_final.triage.components.RestriccionInscripcionRemota
import com.proyecto_final.triage.components.Spinner
import com.proyecto_final.triage.network.estadoConsulta.EstadoConsultaPacienteDTO
import com.proyecto_final.triage.network.estadoConsulta.HospitalSeleccionadoResponse
import com.proyecto_final.triage.storage.TokenStorageProvider
import com.proyecto_final.triage.theme.AppTheme
import com.proyecto_final.triage.utils.dialEmergency
import com.proyecto_final.triage.viewmodels.HomeState
import com.proyecto_final.triage.viewmodels.HomeViewModel
import com.proyecto_final.triage.viewmodels.SancionPacienteState
import com.proyecto_final.triage.viewmodels.mensajeInscripcionRemota
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import triage.composeapp.generated.resources.Res
import triage.composeapp.generated.resources.image1
import triage.composeapp.generated.resources.image2
import triage.composeapp.generated.resources.image3
import triage.composeapp.generated.resources.image4
import triage.composeapp.generated.resources.logo
import kotlin.time.Duration.Companion.milliseconds


/*
 * ================================================================
 * COLORES
 * ================================================================
 */

private val ACCENT = Color(0xFF5BB8D4)

private val MENU_BACKGROUND = Color(0xFFEAF5FD)
private val MENU_BORDER = Color(0xFFB8DDF4)
private val MENU_BLUE = Color(0xFF0878D1)

private val EMERGENCY_BACKGROUND = Color(0xFFFFF3F3)
private val EMERGENCY_BORDER = Color(0xFFF3CACA)
private val EMERGENCY_RED = Color(0xFFD62828)

private val ACTIVE_CARD_BACKGROUND = Color.White
private val ACTIVE_CARD_BORDER = Color(0xFFE0E0E0)

private val ICON_GRAY = Color(0xFF6B7280)


/*
 * ================================================================
 * HOME SCREEN
 * ================================================================
 */

@OptIn(ExperimentalVoyagerApi::class)
class HomeScreen : Screen {

    @Composable
    override fun Content() {

        val navigator = LocalNavigator.current

        val viewModel =  navigatorViewModel { HomeViewModel() }

        val state = viewModel.state

        /*
         * Si no hay token, volvemos al login.
         */
        if (TokenStorageProvider.instance.getToken() == null) {
            navigator?.replace(SignInScreen())
            return
        }

        val sancion = remember { SancionPacienteState() }
        ConsultarSancionAlReanudar(sancion)

        /*
         * Cargar consulta activa.
         */
        LaunchedEffect(Unit) {
            viewModel.cargarEstadoConsulta()
        }

        /*
         * Detener polling cuando salimos de Home.
         */
        DisposableEffect(Unit) {
            onDispose {
                viewModel.detener()
            }
        }

        HomeContent(
            state = state,
            viewModel = viewModel,
            mensajeSancion = sancion.datos?.mensajeInscripcionRemota(),

            /*
             * Llamada real al 911.
             */
            onEmergency = {
                dialEmergency()
            },

            onSolicitarAtencion = {
                navigator?.push(
                    SelectTypeGuardScreen()
                )
            },

            onChat = {
                navigator?.push(
                    ChatScreen()
                )
            },

            onCargarEstudios = {
                // TODO
            },

            onMiPerfil = {
                navigator?.push(
                    ProfileScreen()
                )
            },

            onConsultaActiva = {
                navigator?.push(
                    ConsultaActivaScreen()
                )
            }
        )
        AvisoSancionPaciente(sancion)
    }
}


/*
 * ================================================================
 * PREVIEW
 * ================================================================
 */

@Preview
@Composable
fun HomePreview() {

    AppTheme {

        HomeContent(
            state = HomeState.Loading,
            viewModel = remember { HomeViewModel() },

            onEmergency = {},

            onSolicitarAtencion = {},

            onChat = {},

            onCargarEstudios = {},

            onMiPerfil = {},

            onConsultaActiva = {}
        )
    }
}


/*
 * ================================================================
 * HOME CONTENT
 * ================================================================
 */

@Composable
fun HomeContent(

    state: HomeState,

    viewModel: HomeViewModel,
    mensajeSancion: String? = null,

    onEmergency: () -> Unit,

    onSolicitarAtencion: () -> Unit,

    onChat: () -> Unit,

    onCargarEstudios: () -> Unit,

    onMiPerfil: () -> Unit,

    onConsultaActiva: () -> Unit

) {

    val snackbarHostState = remember { SnackbarHostState() }

    // Mostrar snackbar de éxito al cancelar selección
    LaunchedEffect(viewModel.showCancelSuccessSnackbar) {
        if (viewModel.showCancelSuccessSnackbar) {
            snackbarHostState.showSnackbar(
                message = "Selección cancelada con éxito",
                duration = SnackbarDuration.Short
            )
            viewModel.onCancelSuccessSnackbarShown()
        }
    }

    /*
     * Carrusel existente.
     */
    val carouselImages = listOf(
        Res.drawable.image1,
        Res.drawable.image2,
        Res.drawable.image3,
        Res.drawable.image4
    )

    Column(

        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
            )
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                horizontal = 16.dp
            )

    ) {

        /*
         * ============================================================
         * HEADER
         * ============================================================
         */

        Spacer(
            modifier = Modifier.height(36.dp)
        )

        Row(

            modifier = Modifier.fillMaxWidth(),

            verticalAlignment =
                Alignment.CenterVertically

        ) {

            /*
             * LOGO
             */

            Image(

                painter =
                    painterResource(
                        Res.drawable.logo
                    ),

                contentDescription =
                    "Logo",

                modifier =
                    Modifier.size(52.dp)
            )

            Spacer(
                modifier = Modifier.width(12.dp)
            )

            /*
             * SALUDO
             */

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(

                    text =
                        "Hola!",

                    style =
                        MaterialTheme.typography
                            .titleLarge,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        MaterialTheme.colorScheme
                            .onBackground
                )

                Spacer(
                    modifier =
                        Modifier.height(2.dp)
                )

                Text(

                    text =
                        "¿Como podemos ayudarte?",

                    style =
                        MaterialTheme.typography
                            .bodyMedium,

                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }

            /*
             * PERFIL
             */

            Icon(

                imageVector =
                    Icons.Filled.Person,

                contentDescription =
                    "Perfil",

                tint =
                    Color.Black,

                modifier =
                    Modifier
                        .size(30.dp)
                        .clickable {
                            onMiPerfil()
                        }
            )
        }

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        /*
         * ============================================================
         * CARRUSEL
         * ============================================================
         */

        Carousel(
            carouselImages
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        /*
         * ============================================================
         * MENÚ
         * ============================================================
         */

        Text(

            text =
                "Menú",

            style =
                MaterialTheme.typography
                    .titleLarge,

            fontWeight =
                FontWeight.Bold,

            color =
                MaterialTheme.colorScheme
                    .onBackground
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        /*
         * Menú contextual: Chat si hay consulta activa, sino Solicitar atención
         */

        if (mensajeSancion != null) {
            RestriccionInscripcionRemota(mensajeSancion)
            Spacer(modifier = Modifier.height(12.dp))
        }

        when (state) {

            is HomeState.Loading -> {
                Spinner(color = MENU_BLUE)
            }

            is HomeState.Success -> {
                MenuCard(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    titulo = "Chat interactivo",
                    descripcion = "Consultá al asistente sobre tu atención",
                    onClick = onChat,
                    accentColor = MaterialTheme.colorScheme.tertiary
                )
            }

            else -> Unit
        }

        if (state !is HomeState.Loading && (state !is HomeState.Success || mensajeSancion != null)) {
            if (state is HomeState.Success) Spacer(modifier = Modifier.height(12.dp))
            MenuCard(
                icon = Icons.Filled.LocalHospital,
                titulo = if (mensajeSancion == null) "Solicitar atención de guardia" else "Consultar hospitales",
                descripcion = if (mensajeSancion == null) {
                    "Elegí el hospital según su tiempo de espera y describí tus síntomas"
                } else {
                    "Consultá hospitales cercanos y sus tiempos estimados"
                },
                onClick = onSolicitarAtencion
            )
        }

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )


        /*
         * ============================================================
         * EMERGENCIAS
         * ============================================================
         */

        EmergencyCard(
            onClick =
                onEmergency
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        /*
         * ============================================================
         * ATENCIÓN EN CURSO
         * ============================================================
         */

        Text(

            text =
                "Atención en curso",

            style =
                MaterialTheme.typography
                    .titleLarge,

            fontWeight =
                FontWeight.Bold,

            color =
                MaterialTheme.colorScheme
                    .onBackground
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        /*
         * ============================================================
         * ESTADO
         * ============================================================
         */

        when (state) {

            is HomeState.Loading -> {
                Spinner(MENU_BLUE, 24.dp)
            }

            is HomeState.Success -> {

                ConsultaActivaCard(

                    estado =
                        state.estado,

                    hospital =
                        state.hospital,

                    onClick =
                        onConsultaActiva
                )
            }

            is HomeState.SinConsultaActiva -> {

                SinConsultaCard()
            }

            is HomeState.Error -> {

                SinConsultaCard()
            }
        }

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        // SnackbarHost para mostrar mensajes
        SnackbarHost(
            hostState = snackbarHostState,
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF3AA76D),
                    contentColor = Color.White
                )
            }
        )

    }

}


/*
 * ================================================================
 * CARD DE MENÚ
 * ================================================================
 */

@Composable
private fun MenuCard(

    icon: ImageVector,

    titulo: String,

    descripcion: String,

    onClick:() -> Unit,

    accentColor: Color = MENU_BLUE

) {

    Card(

        shape =
            RoundedCornerShape(16.dp),

        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                },

        colors =
            CardDefaults.cardColors(
                containerColor =
                    MENU_BACKGROUND
            ),

        border =
            BorderStroke(
                1.dp,
                MENU_BORDER
            ),

        elevation =
            CardDefaults.cardElevation(
                defaultElevation =
                    2.dp
            )
    ) {

        Row(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 14.dp
                    ),

            verticalAlignment =
                Alignment.CenterVertically

        ) {

            /*
             * ÍCONO
             */

            Box(

                modifier =
                    Modifier
                        .size(58.dp)
                        .clip(
                            RoundedCornerShape(14.dp)
                        )
                        .background(
                            accentColor
                        ),

                contentAlignment =
                    Alignment.Center

            ) {

                Icon(

                    imageVector =
                        icon,

                    contentDescription =
                        null,

                    tint =
                        Color.White,

                    modifier =
                        Modifier.size(34.dp)
                )
            }

            Spacer(
                modifier =
                    Modifier.width(14.dp)
            )

            /*
             * TEXTO
             */

            Column(

                modifier =
                    Modifier.weight(1f)

            ) {

                Text(

                    text =
                        titulo,

                    style =
                        MaterialTheme.typography
                            .titleMedium,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        accentColor
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(

                    text =
                        descripcion,

                    style =
                        MaterialTheme.typography
                            .bodySmall,

                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }

            Spacer(
                modifier =
                    Modifier.width(8.dp)
            )

            /*
             * FLECHA
             */

            Icon(

                imageVector =
                    Icons.Filled.ChevronRight,

                contentDescription =
                    null,

                tint =
                    accentColor,

                modifier =
                    Modifier.size(30.dp)
            )
        }
    }
}


/*
 * ================================================================
 * CARD EMERGENCIA
 * ================================================================
 */

@Composable
private fun EmergencyCard(

    onClick:
        () -> Unit

) {

    Card(

        shape =
            RoundedCornerShape(16.dp),

        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                },

        colors =
            CardDefaults.cardColors(
                containerColor =
                    EMERGENCY_BACKGROUND
            ),

        border =
            BorderStroke(
                1.dp,
                EMERGENCY_BORDER
            ),

        elevation =
            CardDefaults.cardElevation(
                defaultElevation =
                    2.dp
            )
    ) {

        Row(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 14.dp
                    ),

            verticalAlignment =
                Alignment.CenterVertically

        ) {

            /*
             * ÍCONO 911
             */

            Box(

                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(
                            RoundedCornerShape(13.dp)
                        )
                        .background(
                            EMERGENCY_RED
                        ),

                contentAlignment =
                    Alignment.Center

            ) {

                Text(

                    text =
                        "911",

                    color =
                        Color.White,

                    style =
                        MaterialTheme.typography
                            .titleMedium,

                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                modifier =
                    Modifier.width(14.dp)
            )

            /*
             * TEXTO
             */

            Column(

                modifier =
                    Modifier.weight(1f)

            ) {

                Text(

                    text =
                        "¿Urgencia? Llamá al 911",

                    style =
                        MaterialTheme.typography
                            .titleMedium,

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        EMERGENCY_RED,

                    maxLines =
                        1
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(

                    text =
                        "Ante una emergencia médica, llamá inmediatamente",

                    style =
                        MaterialTheme.typography
                            .bodySmall,

                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }

            Spacer(
                modifier =
                    Modifier.width(8.dp)
            )

            Icon(

                imageVector =
                    Icons.Filled.ChevronRight,

                contentDescription =
                    "Llamar al 911",

                tint =
                    EMERGENCY_RED,

                modifier =
                    Modifier.size(28.dp)
            )
        }
    }
}


/*
 * ================================================================
 * CARRUSEL
 * ================================================================
 */

@OptIn(
    ExperimentalFoundationApi::class
)
@Composable
fun Carousel(

    carouselImages:
    List<DrawableResource>

) {

    val pagerState =
        rememberPagerState(
            pageCount = {
                carouselImages.size
            }
        )

    /*
     * 5 segundos por imagen.
     * Antes eran 3 segundos.
     */
    LaunchedEffect(Unit) {

        while (true) {

            delay(5000.milliseconds)

            val nextPage =
                (
                        pagerState.currentPage + 1
                        ) % carouselImages.size

            pagerState.animateScrollToPage(
                nextPage
            )
        }
    }

    Column {

        HorizontalPager(

            state =
                pagerState,

            modifier =
                Modifier.fillMaxWidth()

        ) { page ->

            Card(

                shape =
                    RoundedCornerShape(16.dp),

                modifier =
                    Modifier.fillMaxWidth()

            ) {

                Image(

                    painter =
                        painterResource(
                            carouselImages[page]
                        ),

                    contentDescription =
                        null,

                    contentScale =
                        ContentScale.Crop,

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Row(

            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.Center

        ) {

            repeat(
                carouselImages.size
            ) { index ->

                Box(

                    modifier =
                        Modifier
                            .padding(
                                horizontal = 4.dp
                            )
                            .size(
                                if (
                                    index ==
                                    pagerState.currentPage
                                ) {
                                    10.dp
                                } else {
                                    8.dp
                                }
                            )
                            .clip(
                                CircleShape
                            )
                            .background(

                                if (
                                    index ==
                                    pagerState.currentPage
                                ) {
                                    ACCENT
                                } else {
                                    Color(0xFFD0D0D0)
                                }
                            )
                )
            }
        }
    }
}


/*
 * ================================================================
 * CONSULTA ACTIVA
 * ================================================================
 */
@Composable
fun ConsultaActivaCard(

    estado:  EstadoConsultaPacienteDTO,

    hospital: HospitalSeleccionadoResponse?,

    onClick:
        () -> Unit
) {

    val (
        etiqueta,
        colorEstado
    ) = estadoVisual(

        estado.estadoEntradaCola,

        estado.tipoPausa,

        estado.estadoConsulta
    )

    val estimacion =
        estado.tiempoEstimadoAtencion

    val minutosRestantes =
        estimacion?.let {

            minutosHasta(
                it.fechaHoraAtencionEstimada
            )
        }

    Card(

        shape =
            RoundedCornerShape(16.dp),

        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                },

        colors =
            CardDefaults.cardColors(
                containerColor =
                    ACTIVE_CARD_BACKGROUND
            ),

        border =
            BorderStroke(
                1.dp,
                ACTIVE_CARD_BORDER
            ),

        elevation =
            CardDefaults.cardElevation(
                defaultElevation =
                    4.dp
            )
    ) {

        Column(
            modifier =
                Modifier.padding(16.dp)
        ) {

            /*
             * ========================================================
             * INFORMACIÓN DEL HOSPITAL
             * ========================================================
             */

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically

            ) {

                /*
                 * ÍCONO CELESTE
                 */

                Box(

                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(
                                RoundedCornerShape(14.dp)
                            )
                            .background(
                                Color(0xFFE8F4FD)
                            ),

                    contentAlignment =
                        Alignment.Center

                ) {

                    Icon(

                        imageVector =
                            Icons.Filled.People,

                        contentDescription =
                            null,

                        tint =
                            MENU_BLUE,

                        modifier =
                            Modifier.size(32.dp)
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(12.dp)
                )

                /*
                 * INFORMACIÓN
                 */

                Column(

                    modifier =
                        Modifier.weight(1f)

                ) {

                    Text(

                        text =
                            hospital?.nombre
                                ?: "Tu consulta",

                        style =
                            MaterialTheme.typography
                                .titleMedium,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            Color(0xFF3AA76D)
                    )

                    Text(

                        text =
                            hospital?.direccion
                                ?.substringBefore(",")
                                ?: "Dirección no disponible",

                        style =
                            MaterialTheme.typography
                                .bodyMedium,

                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )

                    Spacer(
                        modifier =
                            Modifier.height(5.dp)
                    )

                    Row(

                        verticalAlignment =
                            Alignment.CenterVertically

                    ) {

                        Box(

                            modifier =
                                Modifier
                                    .size(8.dp)
                                    .clip(
                                        CircleShape
                                    )
                                    .background(
                                        colorEstado
                                    )
                        )

                        Spacer(
                            modifier =
                                Modifier.width(6.dp)
                        )

                        Text(

                            text =
                                etiqueta,

                            style =
                                MaterialTheme.typography
                                    .bodySmall,

                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )
                    }

                    if (hospital?.nombreSector?.isNotBlank() == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = MENU_BLUE,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sector: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text=hospital.nombreSector,
                                style= MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            /*
             * ========================================================
             * DIVISOR HORIZONTAL
             * ========================================================
             */

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            HorizontalDivider(
                color =
                    Color(0xFFE0E0E0)
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            /*
             * ========================================================
             * INFORMACIÓN DE COLA
             * ========================================================
             */

            if (
                estimacion != null &&
                estimacion.hayMedicosActivos
            ) {

                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    verticalAlignment =
                        Alignment.CenterVertically

                ) {

                    /*
                     * =================================================
                     * PACIENTES ADELANTE
                     * =================================================
                     */

                    Row(

                        modifier =
                            Modifier.weight(1f),

                        verticalAlignment =
                            Alignment.CenterVertically

                    ) {

                        Icon(

                            imageVector =
                                Icons.Filled.People,

                            contentDescription =
                                null,

                            tint =
                                MENU_BLUE,

                            modifier =
                                Modifier.size(28.dp)
                        )

                        Spacer(
                            modifier =
                                Modifier.width(8.dp)
                        )

                        Text(

                            text =
                                "${estimacion.pacientesAntes} adelante",

                            style =
                                MaterialTheme.typography
                                    .bodyMedium,

                            color =
                                MaterialTheme.colorScheme
                                    .onSurface
                        )
                    }

                    /*
                     * =================================================
                     * DIVISOR VERTICAL
                     * =================================================
                     */

                    Box(

                        modifier =
                            Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(
                                    Color(0xFFE0E0E0)
                                )
                    )

                    /*
                     * =================================================
                     * TIEMPO
                     *
                     * Alineado a la izquierda dentro de su mitad.
                     * =================================================
                     */

                    Row(

                        modifier =
                            Modifier.weight(1f),

                        verticalAlignment =
                            Alignment.CenterVertically,

                        horizontalArrangement =
                            Arrangement.Start

                    ) {

                        Spacer(
                            modifier =
                                Modifier.width(16.dp)
                        )

                        Icon(

                            imageVector =
                                Icons.Filled.Schedule,

                            contentDescription =
                                null,

                            tint =
                                MENU_BLUE,

                            modifier =
                                Modifier.size(28.dp)
                        )

                        Spacer(
                            modifier =
                                Modifier.width(8.dp)
                        )

                        Text(

                            text =
                                if (
                                    minutosRestantes != null
                                ) {
                                    "$minutosRestantes min"
                                } else {
                                    "-- min"
                                },

                            style =
                                MaterialTheme.typography
                                    .bodyMedium,

                            color =
                                MaterialTheme.colorScheme
                                    .onSurface
                        )
                    }
                }

            } else if (
                estimacion != null
            ) {

                Row(

                    verticalAlignment =
                        Alignment.CenterVertically

                ) {

                    Icon(

                        imageVector =
                            Icons.Filled.Warning,

                        contentDescription =
                            null,

                        tint =
                            Color(0xFFE8A33D),

                        modifier =
                            Modifier.size(20.dp)
                    )

                    Spacer(
                        modifier =
                            Modifier.width(6.dp)
                    )

                    Text(

                        text =
                            estimacion.mensaje
                                ?: "Sin médicos disponibles por el momento",

                        style =
                            MaterialTheme.typography
                                .bodySmall,

                        color =
                            Color(0xFFE8A33D)
                    )
                }
            }
        }
    }
}


/*
 * ================================================================
 * SIN CONSULTA
 * ================================================================
 */

@Composable
fun SinConsultaCard() {

    Box(

        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(
                    RoundedCornerShape(16.dp)
                )
                .background(
                    Color(0xFFD6D9DE)
                ),

        contentAlignment =
            Alignment.Center

    ) {

        Text(

            text =
                "No tenés consultas activas",

            style =
                MaterialTheme.typography
                    .bodyMedium,

            color =
                ICON_GRAY
        )
    }
}


/*
 * ================================================================
 * ESTADO VISUAL
 * ================================================================
 */

private fun estadoVisual(

    estadoEntradaCola:
    String?,

    tipoPausa:
    String?,

    estadoConsulta:
    String?

): Pair<String, Color> = when {

    estadoEntradaCola ==
            "EN_COLA" ->

        "En cola" to
                Color(0xFF3AA76D)

    estadoEntradaCola == "EN_REVISION" ->
        "En revisión" to ACCENT

    estadoEntradaCola ==
            "LLAMADO" ->

        "Te están llamando" to
                ACCENT

    estadoEntradaCola ==
            "EN_ATENCION" ->

        "En atención" to
                Color(0xFF3AA76D)

    estadoEntradaCola ==
            "EN_ESPERA" &&
            tipoPausa ==
            "ESPERA_MANUAL" ->

        "Te ausentaste" to
                Color(0xFFE8A33D)

    estadoEntradaCola ==
            "EN_ESPERA" &&
            tipoPausa ==
            "AUSENTE_AL_LLAMADO" ->

        "Confirmá tu llegada" to
                Color(0xFFD9534F)

    estadoEntradaCola ==
            "ATRASADO" ->

        "Atrasado, confirmá" to
                Color(0xFFD9534F)

    estadoConsulta ==
            "HOSPITAL_SELECCIONADO" ->

        "Completá el pretriage" to
                ACCENT

    estadoConsulta ==
            "PRETRIAGE_EN_PROCESO" ->

        "Pretriage en curso" to
                ACCENT

    estadoConsulta ==
            "PRETRIAGE_FINALIZADO" ->

        "Preparando tu turno" to
                ACCENT

    else ->

        "Consulta activa" to
                ACCENT
}


/*
 * ================================================================
 * MINUTOS HASTA LA ATENCIÓN
 * ================================================================
 */

fun minutosHasta(

    fechaHoraIso:
    String?

): Int? {

    if (
        fechaHoraIso.isNullOrBlank()
    ) {
        return null
    }

    return runCatching {

        val objetivo =

            LocalDateTime
                .parse(
                    fechaHoraIso
                )
                .toInstant(
                    TimeZone
                        .currentSystemDefault()
                )

        val ahora =
            kotlin.time.Clock.System.now()

        val minutos =

            (
                    objetivo - ahora
                    ).inWholeMinutes

        if (
            minutos < 0
        ) {

            0

        } else {

            minutos.toInt()
        }

    }.getOrNull()
}
