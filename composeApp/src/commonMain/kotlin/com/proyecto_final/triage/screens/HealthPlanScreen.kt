package com.proyecto_final.triage.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.proyecto_final.triage.AppConstants
import com.proyecto_final.triage.components.CommonHeader
import com.proyecto_final.triage.components.ErrorMessage
import com.proyecto_final.triage.components.InputMonthYearField
import com.proyecto_final.triage.components.InputTextField
import com.proyecto_final.triage.components.NextButton
import com.proyecto_final.triage.components.Spinner
import com.proyecto_final.triage.components.SuccessMessage
import com.proyecto_final.triage.theme.AppTheme
import com.proyecto_final.triage.theme.Spacing
import com.proyecto_final.triage.viewmodels.CredencialState
import com.proyecto_final.triage.viewmodels.CredencialesState
import com.proyecto_final.triage.viewmodels.HealthPlanViewModel
import com.proyecto_final.triage.viewmodels.ObrasSocialesState
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource
import triage.composeapp.generated.resources.Res
import triage.composeapp.generated.resources.credential

@Serializable
data class Credencial(
    @SerialName("nombreObraSocial")
    val nombreObraSocial: String,

    @SerialName("numeroAfiliado")
    val numeroAfiliado: String,

    @SerialName("plan")
    val plan: String,

    @SerialName("fechaVencimiento")
    val fechaVencimiento: String,

    @SerialName("id")
    val idCredencial: Long = 0
)

/**
 * Qué operación sobre una credencial está en curso / recién terminó,
 * para saber qué mensaje de éxito mostrar y qué hacer al aceptar.
 */
private sealed class PendingAction {
    object Crear : PendingAction()
    object Editar : PendingAction()
    object Eliminar : PendingAction()
}

class HealthPlanScreen : Screen {

    @Composable
    override fun Content() {

        val navigator = LocalNavigator.current
        val viewModel = remember { HealthPlanViewModel() }

        HealthPlanContent(
            onBack = { navigator?.pop() },
            viewModel = viewModel
        )
    }
}

@Preview
@Composable
fun HealthPlanPreview() {
    AppTheme {
        HealthPlanContent(
            onBack = {},
            viewModel = HealthPlanViewModel()
        )
    }
}

/**
 * Credencial "vacía", usada como base para el formulario de alta
 * y como valor por defecto antes de que carguen los datos.
 */
private fun credencialVacia() = Credencial(
    nombreObraSocial = "",
    numeroAfiliado = "",
    plan = "",
    fechaVencimiento = ""
)

/**
 * Calcula, de forma síncrona, qué credencial corresponde mostrar/editar
 * para una página dada del pager. Al ser una función pura (sin side
 * effects), se puede usar como key de `remember` y así evitar el frame
 * de desfasaje que generaba un `LaunchedEffect` asincrónico.
 */
private fun credencialParaPagina(
    page: Int,
    credenciales: List<Credencial>
): Credencial {

    return if (page < credenciales.size) {

        val actual = credenciales[page]

        actual.copy(
            fechaVencimiento = formatearFechaParaPicker(actual.fechaVencimiento)
        )

    } else {
        credencialVacia()
    }
}

/**
 * Validaciones de cada campo. Se centralizan acá para que tanto
 * `Formulario` (para marcar error en cada campo) como el botón
 * "Guardar" del footer (para habilitarse o no) usen exactamente
 * las mismas reglas.
 */
private fun isNombreValido(credencial: Credencial): Boolean =
    credencial.nombreObraSocial.isNotBlank()

private fun isNumeroValido(credencial: Credencial): Boolean =
    credencial.numeroAfiliado.length >= 3 &&
            credencial.numeroAfiliado.all { it.isDigit() }

private fun isPlanValido(credencial: Credencial): Boolean =
    credencial.plan.isNotBlank()

/**
 * La fecha de vencimiento (formato "MM/aaaa", el que usa el picker)
 * es válida si es un mes/año real y no es anterior al mes/año actual.
 */
private fun isFechaVencimientoValida(fecha: String): Boolean {

    val partes = fecha.split("/")

    if (partes.size != 2) {
        return false
    }

    val mes = partes[0].toIntOrNull() ?: return false
    val anio = partes[1].toIntOrNull() ?: return false

    if (mes !in 1..12) {
        return false
    }

    val hoy = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())

    return anio > hoy.year || (anio == hoy.year && mes >= hoy.month.number)
}

private fun isFechaVencimientoValida(credencial: Credencial): Boolean =
    isFechaVencimientoValida(credencial.fechaVencimiento)

private fun isCredencialValida(credencial: Credencial): Boolean =
    isNombreValido(credencial) &&
            isNumeroValido(credencial) &&
            isPlanValido(credencial) &&
            isFechaVencimientoValida(credencial)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthPlanContent(
    onBack: () -> Unit,
    viewModel: HealthPlanViewModel
) {
    var showErrors by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Si la credencial actual es existente,
    // controla si está bloqueada o en modo edición.
    var isEditingExisting by remember { mutableStateOf(false) }

    // Qué operación disparó el último Success, para saber
    // qué mensaje mostrar y qué hacer al apretar "Aceptar".
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // Estados del ViewModel
    val obrasSocialesState by viewModel.obrasSocialesState.collectAsState()
    val credencialesState by viewModel.credencialesState.collectAsState()
    val credencialState by viewModel.state.collectAsState()

    val isGuardando = credencialState is CredencialState.Loading

    // Obtener datos al entrar a la pantalla.
    LaunchedEffect(Unit) {
        isEditingExisting = false
        viewModel.obtenerObrasSociales()
        viewModel.obtenerCredenciales()
    }

    // Obras sociales disponibles para el selector.
    val obrasSocialesDisponibles = when (val state = obrasSocialesState) {

        is ObrasSocialesState.Loading -> {
            emptyList()
        }

        is ObrasSocialesState.Success -> {
            state.obrasSociales.map { it.nombre }
        }

        is ObrasSocialesState.Error -> {
            emptyList()
        }
    }

    val backendFieldErrors =
        (credencialState as? CredencialState.Error)?.fieldErrors
            ?: emptyMap()

    val errorGeneral =
        (credencialState as? CredencialState.Error)?.message
            ?: (credencialesState as? CredencialesState.Error)?.message

    val credenciales = when (credencialesState) {

        is CredencialesState.Success ->
            (credencialesState as CredencialesState.Success).credenciales

        else ->
            emptyList()
    }

    val pagerState = rememberPagerState(
        pageCount = { credenciales.size + 1 }
    )

    // showForm: derivado en forma síncrona de la página actual, para que
    // no haya un frame donde currentPage ya cambió pero showForm todavía no.
    var showForm by remember(pagerState.currentPage, credenciales) {
        mutableStateOf(pagerState.currentPage >= credenciales.size)
    }

    // credencial: idem, se recalcula en la misma pasada de composición
    // en la que cambia la página o la lista de credenciales, sin pasar
    // por un LaunchedEffect asincrónico. Así se evita el "flash" de un
    // frame con datos de la credencial anterior en la página nueva.
    var credencial by remember(pagerState.currentPage, credenciales) {
        mutableStateOf(credencialParaPagina(pagerState.currentPage, credenciales))
    }

    val scope = rememberCoroutineScope()

    /*
     * Cuando una operación sobre una credencial termina correctamente:
     * - limpiamos el formulario
     * - cerramos diálogos
     * - salimos del modo edición
     * - mostramos el mensaje de éxito correspondiente
     * - reseteamos el estado del ViewModel
     *
     * La navegación puntual (volver a la primera al eliminar, etc.)
     * se resuelve recién cuando el usuario aprieta "Aceptar" en el
     * mensaje de éxito, no acá.
     */
    LaunchedEffect(credencialState) {

        if (credencialState is CredencialState.Success) {

            credencial = credencialVacia()

            showForm = false
            showErrors = false
            showConfirmDialog = false
            showDeleteDialog = false
            isEditingExisting = false

            showSuccessDialog = true

            viewModel.resetState()
        }
    }

    /*
     * Cada vez que cambia la página, solo disparamos los side effects
     * "reales" (limpiar errores, cerrar diálogos, resetear el estado
     * del ViewModel). Los datos que se muestran (credencial, showForm)
     * ya se recalculan arriba de forma síncrona con `remember`.
     */
    LaunchedEffect(pagerState.currentPage, credenciales) {

        showErrors = false
        isEditingExisting = false
        showDeleteDialog = false
        showConfirmDialog = false

        viewModel.resetState()
    }

    val mostrandoVistaPrevia =
        pagerState.currentPage == credenciales.size &&
                (credencial.nombreObraSocial.isNotBlank() ||
                        credencial.numeroAfiliado.isNotBlank() ||
                        credencial.plan.isNotBlank() ||
                        credencial.fechaVencimiento.isNotBlank())

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
        ) {

            // HEADER
            CommonHeader(title = "Mis credenciales", onBack = { onBack() })

            // CONTENIDO DESPLAZABLE
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {

                if (credencialesState is CredencialesState.Loading) {

                    // CARD CON SPINNER MIENTRAS CARGA

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(horizontal = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE0E0E0)
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Spinner(color = Color(0xFF192DAD))
                        }
                    }

                } else {

                    // CARROUSEL
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) { page ->

                        if (page < credenciales.size) {

                            val credencialActual =
                                credenciales[page]

                            // Si estamos editando esta credencial en particular,
                            // mostramos el borrador en vivo en vez del dato
                            // "congelado" que vino del backend.
                            val estaEditandoEstaPagina =
                                isEditingExisting && page == pagerState.currentPage

                            val nombreMostrado =
                                if (estaEditandoEstaPagina)
                                    credencial.nombreObraSocial
                                else
                                    credencialActual.nombreObraSocial

                            val numeroMostrado =
                                if (estaEditandoEstaPagina)
                                    credencial.numeroAfiliado
                                else
                                    credencialActual.numeroAfiliado

                            val planMostrado =
                                if (estaEditandoEstaPagina)
                                    credencial.plan
                                else
                                    credencialActual.plan

                            // OJO: el formato de fecha difiere según el origen.
                            // - credencial.fechaVencimiento (borrador en edición) está en "MM/aaaa"
                            // - credencialActual.fechaVencimiento (backend) está en "aaaa-MM-dd"
                            val vencimientoMostrado =
                                if (estaEditandoEstaPagina)
                                    formatearVencimientoForm(credencial.fechaVencimiento)
                                else
                                    formatearVencimiento(credencialActual.fechaVencimiento)

                            Card(
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(horizontal = 4.dp)
                            ) {

                                Box(
                                    modifier = Modifier.fillMaxSize()
                                ) {

                                    Image(
                                        painter = painterResource(
                                            Res.drawable.credential
                                        ),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(20.dp),
                                        verticalArrangement =
                                            Arrangement.SpaceBetween
                                    ) {

                                        Column {

                                            Text(
                                                text = nombreMostrado.uppercase(),
                                                style = MaterialTheme
                                                    .typography
                                                    .titleLarge,
                                                color = Color.White
                                            )

                                            Text(
                                                text = numeroMostrado,
                                                style = MaterialTheme
                                                    .typography
                                                    .bodyLarge,
                                                color = Color.White
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement =
                                                Arrangement.SpaceBetween,
                                            verticalAlignment =
                                                Alignment.Bottom
                                        ) {

                                            Column {

                                                Text(
                                                    text = "PLAN",
                                                    style = MaterialTheme
                                                        .typography
                                                        .labelSmall,
                                                    color = Color.White.copy(
                                                        alpha = 0.75f
                                                    )
                                                )

                                                Text(
                                                    text = planMostrado,
                                                    style = MaterialTheme
                                                        .typography
                                                        .bodyMedium,
                                                    color = Color.White
                                                )
                                            }

                                            Column(
                                                horizontalAlignment =
                                                    Alignment.End
                                            ) {

                                                Text(
                                                    text = "VENCIMIENTO",
                                                    style = MaterialTheme
                                                        .typography
                                                        .labelSmall,
                                                    color = Color.White.copy(
                                                        alpha = 0.75f
                                                    )
                                                )

                                                Text(
                                                    text = vencimientoMostrado,
                                                    style = MaterialTheme
                                                        .typography
                                                        .bodyMedium,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                        } else {

                            // CARD DE AGREGAR NUEVA

                            Card(
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(horizontal = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor =
                                        if (mostrandoVistaPrevia)
                                            Color.Transparent
                                        else
                                            Color(0xFFE0E0E0)
                                )
                            ) {

                                Box(
                                    modifier = Modifier.fillMaxSize()
                                ) {

                                    if (mostrandoVistaPrevia) {

                                        Image(
                                            painter = painterResource(
                                                Res.drawable.credential
                                            ),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(20.dp),
                                            verticalArrangement =
                                                Arrangement.SpaceBetween
                                        ) {

                                            Column {

                                                Text(
                                                    text = credencial
                                                        .nombreObraSocial
                                                        .uppercase(),
                                                    style = MaterialTheme
                                                        .typography
                                                        .titleLarge,
                                                    color = Color.White
                                                )

                                                Spacer(
                                                    modifier =
                                                        Modifier.height(4.dp)
                                                )

                                                Text(
                                                    text = credencial
                                                        .numeroAfiliado,
                                                    style = MaterialTheme
                                                        .typography
                                                        .bodyLarge,
                                                    color = Color.White.copy(
                                                        alpha = 0.9f
                                                    )
                                                )
                                            }

                                            Row(
                                                modifier =
                                                    Modifier.fillMaxWidth(),
                                                horizontalArrangement =
                                                    Arrangement.SpaceBetween,
                                                verticalAlignment =
                                                    Alignment.Bottom
                                            ) {

                                                Column {

                                                    Text(
                                                        text = "PLAN",
                                                        style = MaterialTheme
                                                            .typography
                                                            .labelSmall,
                                                        color = Color.White.copy(
                                                            alpha = 0.75f
                                                        )
                                                    )

                                                    Text(
                                                        text = credencial.plan,
                                                        style = MaterialTheme
                                                            .typography
                                                            .bodyMedium,
                                                        color = Color.White
                                                    )
                                                }

                                                Column(
                                                    modifier =
                                                        Modifier.wrapContentWidth()
                                                ) {

                                                    Text(
                                                        text = "VENCIMIENTO",
                                                        style = MaterialTheme
                                                            .typography
                                                            .labelSmall,
                                                        color = Color.White.copy(
                                                            alpha = 0.75f
                                                        )
                                                    )

                                                    Text(
                                                        text =
                                                            formatearVencimientoForm(
                                                                credencial
                                                                    .fechaVencimiento
                                                            ),
                                                        style = MaterialTheme
                                                            .typography
                                                            .bodyMedium,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }

                                    } else {

                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment =
                                                Alignment.Center
                                        ) {

                                            Icon(
                                                imageVector =
                                                    Icons.Default.Add,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier =
                                                    Modifier.size(48.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // INDICADOR DE PUNTOS

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(credenciales.size + 1) { index ->

                            val isLast =
                                index == credenciales.size

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            index == pagerState.currentPage && isLast ->
                                                Color(0xFF9E9E9E)

                                            index == pagerState.currentPage ->
                                                Color(0xFF192DAD)

                                            else -> Color(0xFFD0D0D0)
                                        }
                                    )
                                    .clickable {

                                        showErrors = false
                                        showConfirmDialog = false
                                        showDeleteDialog = false

                                        viewModel.resetState()

                                        scope.launch {
                                            pagerState.animateScrollToPage(
                                                index
                                            )
                                        }
                                    }
                            )
                        }
                    }

                    if (showForm) {
                        Formulario(
                            credencial = credencial,
                            onCredencialChange = { credencial = it },
                            obrasSocialesDisponibles = obrasSocialesDisponibles,
                            enabled = true,
                            showErrors = showErrors,
                            backendFieldErrors = backendFieldErrors
                        )
                    } else {

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = "Eliminar credencial",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // ERROR GENERAL
                    errorGeneral?.let { mensaje ->

                        Text(
                            text = mensaje,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.sm)
                        )
                    }
                }
            }

            // BOTÓN GUARDAR / EDITAR — fijo al pie de la pantalla, fuera del
            // scroll, para que ambos ocupen siempre la misma posición.
            if (credencialesState !is CredencialesState.Loading) {

                // Misma validación que usa Formulario para marcar errores.
                val isFormValid = isCredencialValida(credencial)

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (showForm) {

                        NextButton(
                            enabled = !isGuardando && isFormValid,
                            onClick = {
                                if (
                                    credencial.nombreObraSocial.isBlank() &&
                                    credencial.numeroAfiliado.isBlank() &&
                                    credencial.plan.isBlank() &&
                                    credencial.fechaVencimiento.isBlank()
                                ) {
                                    showErrors = true
                                } else if (isEditingExisting) {
                                    pendingAction = PendingAction.Editar
                                    viewModel.actualizarCredencial(
                                        idCredencial = credencial.idCredencial,
                                        nombreObraSocial = credencial.nombreObraSocial,
                                        numeroAfiliado = credencial.numeroAfiliado,
                                        plan = credencial.plan,
                                        fechaVencimiento = credencial.fechaVencimiento
                                    )
                                } else {
                                    pendingAction = PendingAction.Crear
                                    viewModel.cargarCredencial(
                                        nombreObraSocial = credencial.nombreObraSocial,
                                        numeroAfiliado = credencial.numeroAfiliado,
                                        plan = credencial.plan,
                                        fechaVencimiento = credencial.fechaVencimiento
                                    )
                                }
                            },
                            text = "Guardar",
                            isLoading = isGuardando
                        )

                    } else {

                        NextButton(
                            enabled = true,
                            onClick = { showForm = true; isEditingExisting = true },
                            text = "Editar"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ERROR DE OBRAS SOCIALES
        //
        // Lo ponemos fuera del Column para que ErrorMessage
        // pueda ocupar toda la pantalla como overlay.

        if (obrasSocialesState is ObrasSocialesState.Error) {

            val error =
                obrasSocialesState as ObrasSocialesState.Error

            ErrorMessage(
                errorText = error.message,
                buttonText = "Reintentar",
                onClick = {
                    viewModel.obtenerObrasSociales()
                }
            )
        }

        // MENSAJE DE ÉXITO
        //
        // Se muestra al terminar OK una creación, edición o eliminación.
        // Qué hacer al aceptar depende de qué operación fue.

        if (showSuccessDialog) {

            val successText = when (pendingAction) {
                PendingAction.Crear -> "La credencial fue creada con éxito."
                PendingAction.Editar -> "La credencial fue editada con éxito."
                PendingAction.Eliminar -> "La credencial fue eliminada con éxito."
                null -> "La operación se realizó con éxito."
            }

            SuccessMessage(
                successText = successText,
                buttonText = "Aceptar",
                onClick = {

                    when (pendingAction) {

                        PendingAction.Eliminar -> {
                            scope.launch { pagerState.animateScrollToPage(0) }
                        }

                        else -> {
                            // Crear / Editar: no hace falta mover el
                            // carrousel, ya quedas viendo la credencial
                            // correspondiente en su misma posición.
                        }
                    }

                    showSuccessDialog = false
                    pendingAction = null
                }
            )
        }
    }


    // POPUP DE CONFIRMACIÓN

    if (showConfirmDialog) {

        AlertDialog(
            onDismissRequest = {
                showConfirmDialog = false
            },
            title = {
                Text("Confirmar credencial")
            },
            text = {
                Text(
                    "¿Estás seguro de que querés subir esta credencial?"
                )
            },
            confirmButton = {

                TextButton(
                    onClick = {

                        pendingAction = PendingAction.Crear

                        viewModel.cargarCredencial(
                            credencial.nombreObraSocial,
                            credencial.numeroAfiliado,
                            credencial.plan,
                            credencial.fechaVencimiento
                        )

                        showConfirmDialog = false
                    }
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {

                TextButton(
                    onClick = {
                        showConfirmDialog = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }


    // POPUP DE ELIMINACIÓN

    if (showDeleteDialog) {

        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
            },
            title = {
                Text("Eliminar credencial")
            },
            text = {
                Text(
                    "¿Estás seguro de que querés eliminar esta credencial? Esta acción no se puede deshacer."
                )
            },
            confirmButton = {

                TextButton(
                    onClick = {

                        pendingAction = PendingAction.Eliminar

                        viewModel.eliminarCredencial(
                            credencial.idCredencial
                        )

                        showDeleteDialog = false
                    }
                ) {

                    Text(
                        "Eliminar",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {

                TextButton(
                    onClick = {
                        showDeleteDialog = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}


/**
 * Campo de selección con menú desplegable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelectField(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    errorMessage: String = AppConstants.CAMPO_OBLIGATORIO,
    placeholder: String? = null
) {

    var expanded by remember {
        mutableStateOf(false)
    }

    Column {

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = {

                if (enabled) {
                    expanded = it
                }
            }
        ) {

            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                isError = isError,
                trailingIcon = {

                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = Color(0xFFD0D0D0),
                    disabledTextColor = Color(0xFF333333),
                    disabledTrailingIconColor =
                        Color(0xFF9E9E9E)
                ),
                placeholder =
                    if (placeholder != null) {

                        {
                            Text(
                                text = placeholder,
                                style = MaterialTheme
                                    .typography
                                    .bodyLarge
                            )
                        }

                    } else {
                        null
                    },
                supportingText = {

                    if (isError) {

                        Text(
                            text = errorMessage,
                            color = MaterialTheme
                                .colorScheme
                                .error
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        ExposedDropdownMenuAnchorType
                            .PrimaryNotEditable,
                        enabled = enabled
                    )
            )

            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = {
                    expanded = false
                }
            ) {

                options.forEach { option ->

                    DropdownMenuItem(
                        text = {
                            Text(option)
                        },
                        onClick = {

                            onValueChange(option)

                            expanded = false
                        }
                    )
                }
            }
        }
    }
}


private fun formatearVencimiento(
    fecha: String
): String {

    val partes = fecha.split("-")

    if (partes.size < 2) {
        return fecha
    }

    val anio = partes[0].takeLast(2)
    val mes = partes[1]

    return "$mes/$anio"
}


private fun formatearFechaParaPicker(
    fecha: String
): String {

    val partes = fecha.split("-")

    if (partes.size != 3) {
        return fecha
    }

    val anio = partes[0].toIntOrNull() ?: return fecha
    val mes = partes[1].toIntOrNull() ?: return fecha

    return "${mes.toString().padStart(2, '0')}/$anio"
}

private fun formatearVencimientoForm(
    fecha: String
): String {

    val partes = fecha.split("/")

    if (partes.size != 2) {
        return fecha
    }

    return "${partes[0]}/${partes[1].takeLast(2)}"
}

private const val OTRO_PLAN = "Otro plan de la credencial"

@Composable
fun Formulario(
    credencial: Credencial,
    onCredencialChange: (Credencial) -> Unit,
    obrasSocialesDisponibles: List<String>,
    enabled: Boolean,
    showErrors: Boolean,
    backendFieldErrors: Map<String, String>
) {

    // Validaciones de inputs
    val isNombreValid = isNombreValido(credencial)
    val isNumeroValid = isNumeroValido(credencial)
    val isFechaValid = isFechaVencimientoValida(credencial)
    val obraSocial = claveObraSocial(credencial.nombreObraSocial)
    val planesDisponibles = planesDeObraSocial(credencial.nombreObraSocial)
    var seleccionoOtroPlan by remember(credencial.idCredencial, obraSocial) {
        mutableStateOf(false)
    }
    val planPersonalizado = seleccionoOtroPlan ||
        (isPlanValido(credencial) && credencial.plan !in planesDisponibles)
    val isPlanError = (showErrors && !isPlanValido(credencial)) ||
        backendFieldErrors.containsKey("plan")
    val planErrorMessage = backendFieldErrors["plan"] ?: AppConstants.CAMPO_OBLIGATORIO

    Column(modifier = Modifier.fillMaxWidth()) {

        // Obra social
        DropdownSelectField(
            label = "Obra social",
            value = credencial.nombreObraSocial,
            options = obrasSocialesDisponibles,
            enabled = enabled,
            onValueChange = { nombre ->
                onCredencialChange(
                    credencial.copy(
                        nombreObraSocial = nombre,
                        plan = if (claveObraSocial(nombre) == obraSocial) credencial.plan else ""
                    )
                )
            },
            isError =
                (showErrors && !isNombreValid) ||
                        backendFieldErrors.containsKey("nombreObraSocial"),
            errorMessage =
                backendFieldErrors["nombreObraSocial"]
                    ?: "Este campo es obligatorio",
            placeholder = "Seleccioná una obra social"
        )

        // Número de afiliado
        InputTextField(
            label = "N.º de afiliado",
            value = credencial.numeroAfiliado,
            onValueChange = {
                onCredencialChange(
                    credencial.copy(
                        numeroAfiliado = it
                    )
                )
            },
            enabled = enabled,
            keyboardType = KeyboardType.Number,
            placeholder = "Ingresá tu número",
            isError =
                (showErrors && !isNumeroValid) ||
                        backendFieldErrors.containsKey("numeroAfiliado"),
            errorMessage =
                backendFieldErrors["numeroAfiliado"]
                    ?: if (credencial.numeroAfiliado.isBlank())
                        "Este campo es obligatorio"
                    else
                        "Mínimo 6 caracteres"
        )

        // Plan
        if (!isNombreValid || planesDisponibles.isNotEmpty()) {
            DropdownSelectField(
                label = "Plan",
                value = if (planPersonalizado) OTRO_PLAN else credencial.plan,
                options = planesDisponibles + OTRO_PLAN,
                enabled = enabled && planesDisponibles.isNotEmpty(),
                onValueChange = { plan ->
                    seleccionoOtroPlan = plan == OTRO_PLAN
                    onCredencialChange(credencial.copy(plan = if (seleccionoOtroPlan) "" else plan))
                },
                isError = !planPersonalizado && isPlanError,
                errorMessage = planErrorMessage,
                placeholder = if (isNombreValid) "Seleccioná un plan" else "Seleccioná una obra social"
            )
        }

        if (isNombreValid && (planesDisponibles.isEmpty() || planPersonalizado)) {
            InputTextField(
                label = if (planesDisponibles.isEmpty()) "Plan" else "Plan de la credencial",
                value = credencial.plan,
                onValueChange = { onCredencialChange(credencial.copy(plan = it)) },
                enabled = enabled,
                placeholder = "Tal como figura en la credencial",
                isError = isPlanError,
                errorMessage = planErrorMessage
            )
        }

        // Fecha de vencimiento
        InputMonthYearField(
            label = "Fecha de vencimiento",
            value = credencial.fechaVencimiento,
            onValueChange = {
                onCredencialChange(
                    credencial.copy(
                        fechaVencimiento = it
                    )
                )
            },
            enabled = enabled,
            isError =
                (showErrors && !isFechaValid) ||
                        backendFieldErrors.containsKey("fechaVencimiento"),
            errorMessage =
                backendFieldErrors["fechaVencimiento"]
                    ?: if (credencial.fechaVencimiento.isBlank())
                        "Este campo es obligatorio"
                    else
                        "La fecha no puede ser anterior al mes actual",
            placeholder = "MM/AAAA"
        )
    }

}
