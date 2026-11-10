package com.proyecto_final.triage.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyecto_final.triage.components.NextButton
import com.proyecto_final.triage.components.SelectableOption
import com.proyecto_final.triage.components.SelectableOptionsGrid
import com.proyecto_final.triage.components.ScreenTitleBar
import com.proyecto_final.triage.network.PerfilResponse
import com.proyecto_final.triage.theme.AppTheme
import com.proyecto_final.triage.utils.esCaba
import com.proyecto_final.triage.viewmodels.ProfileState
import com.proyecto_final.triage.viewmodels.ProfileViewModel
import org.jetbrains.compose.resources.painterResource
import triage.composeapp.generated.resources.Res
import triage.composeapp.generated.resources.profile

class ProfileScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel = remember { ProfileViewModel() }
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.cargarPerfil()
        }

        ProfileContent(
            state = state,
            onRetry = { viewModel.cargarPerfil() },
            onEditProfile = { perfil ->
                navigator?.push(
                    EditProfileScreen(
                        perfil = perfil,
                        onProfileUpdated = { viewModel.cargarPerfil() }
                    )
                )
            },
            onHealthPlan = { navigator?.push(HealthPlanScreen()) },
            onMyStudies = { navigator?.push(MyStudiesScreen()) },
            onSettings = { navigator?.push(ConfiguracionScreen()) },
            onBack = { navigator?.pop() }
        )
    }
}

private val perfilDeEjemplo = PerfilResponse(
    nombre = "Rocío",
    apellido = "Pérez",
    tipoDocumento = "DNI",
    numeroDocumento = "42.123.456",
    fechaNacimiento = "2001-05-15",
    generoBiologico = "FEMENINO",
    generoConElQueSeIdentifica = "FEMENINO",
    email = "rperez@gmail.com",
    telefono = "11-1234-5678",
    calle = "Av. Cabildo",
    alturaDireccion = "1234",
    piso = "3",
    codigoPostal = "1428",
    ciudad = "CABA",
    provincia = "Buenos Aires",
    peso = 72.0,
    alturaPersona = 178
)

@Preview
@Composable
fun ProfilePreview() {
    AppTheme {
        ProfileContent(
            state = ProfileState.Success(perfilDeEjemplo),
            onRetry = { },
            onEditProfile = { },
            onHealthPlan = { },
            onMyStudies = { },
            onSettings = { },
            onBack = { }
        )
    }
}

@Composable
fun ProfileContent(
    state: ProfileState,
    onRetry: () -> Unit,
    onEditProfile: (PerfilResponse) -> Unit,
    onHealthPlan: () -> Unit,
    onMyStudies: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit
) {
    val opciones = listOf(
        SelectableOption("Mis estudios clínicos", Icons.Default.BarChart, onClick = onMyStudies),
        SelectableOption("Mis credenciales", Icons.Default.CreditCard, onClick = onHealthPlan)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Header con título y acceso a configuración
        ScreenTitleBar(
            title = "Mi perfil",
            onBack = onBack
        ) {
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Configuración",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is ProfileState.Loading -> ProfileLoading()

            is ProfileState.Error -> ProfileError(
                message = state.message,
                onRetry = onRetry
            )

            is ProfileState.Success -> {
                val perfil = state.perfil
                val placeHolderText = "Sin especificar"

                ProfileHeaderCard(
                    nombreCompleto = "${perfil.nombre} ${perfil.apellido}"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Grid de opciones
                SelectableOptionsGrid(
                    options = opciones,
                    selectedOption = null,
                    onOptionSelected = { }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Campos de información del perfil
                ProfileField(icon = Icons.Default.Person, label = "Nombre", value = perfil.nombre)
                ProfileField(icon = Icons.Default.Person, label = "Apellido", value = perfil.apellido)
                ProfileField(label = "Tipo de documento", value = perfil.tipoDocumento, showArrow = true)
                ProfileField(icon = Icons.Default.Badge, label = "N.º de documento", value = perfil.numeroDocumento)
                ProfileField(
                    icon = Icons.Default.CalendarMonth,
                    label = "Fecha de nacimiento",
                    value = formatearFechaNacimiento(perfil.fechaNacimiento ?: placeHolderText)
                )

                ProfileField(label = "Sexo", value = perfil.generoBiologico ?: placeHolderText, showArrow = true)
                ProfileField(label = "Género", value = perfil.generoConElQueSeIdentifica ?: placeHolderText, showArrow = true)
                ProfileField(
                    icon =  Icons.Filled.Email,
                    label = "Correo electrónico",
                    value = perfil.email ?: placeHolderText
                )
                ProfileField(
                    icon =  Icons.Filled.Phone,
                    label = "Teléfono",
                    value = perfil.telefono ?: placeHolderText
                )
                ProfileField(
                    icon =  Icons.Filled.Home,
                    label = "Calle",
                    value = perfil.calle ?: placeHolderText
                )
                ProfileField(
                    icon =  Icons.Filled.LocationCity,
                    label = "Altura",
                    value = perfil.alturaDireccion ?: placeHolderText
                )

                perfil.piso?.takeIf { it.isNotBlank() }?.let { piso ->
                    ProfileField(
                        icon = Icons.Filled.LocationCity,
                        label = "Piso",
                        value = piso
                    )
                }

                ProfileField(
                    icon =  Icons.Filled.Map,
                    label = "Provincia",
                    value = perfil.provincia ?: placeHolderText
                )
                ProfileField(
                    icon =  Icons.Filled.Place,
                    label = if (esCaba(perfil.provincia, perfil.ciudad)) "Barrio" else "Ciudad",
                    value = perfil.ciudad ?: placeHolderText
                )
                ProfileField(
                    icon =  Icons.Filled.LocationCity,
                    label = "Código postal",
                    value = perfil.codigoPostal ?: placeHolderText
                )


                ProfileField(
                    icon = Icons.Default.FitnessCenter,
                    label = "Peso",
                    value = perfil.peso?.let {
                        if (it % 1.0 == 0.0) "${it.toInt()} kg" else "$it kg"
                    } ?: placeHolderText
                )

                ProfileField(
                    icon = Icons.Default.Height,
                    label = "Altura",
                    value = perfil.alturaPersona?.let { "$it cm" } ?: placeHolderText
                )

                Spacer(modifier = Modifier.height(24.dp))

                NextButton(onClick = { onEditProfile(perfil) }, text = "Editar")

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ProfileLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ProfileError(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE5E5)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFB3261E)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No se pudo cargar el perfil",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFB3261E)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB3261E)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Reintentar", color = Color(0xFFB3261E))
                }
            }
        }
    }
}

@Composable
private fun ProfileHeaderCard(nombreCompleto: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFD6EAF8)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Res.drawable.profile),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF5BB8D4)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = nombreCompleto,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

private fun formatearFechaNacimiento(fecha: String): String {
    val partes = fecha.split("-")
    if (partes.size != 3) return fecha

    return try {
        val anio = partes[0].toInt()
        val mes = partes[1].toInt()
        val dia = partes[2].toInt()
        "${dia.toString().padStart(2, '0')}/${mes.toString().padStart(2, '0')}/$anio"
    } catch (e: NumberFormatException) {
        fecha
    }
}

@Composable
private fun ProfileField(
    icon: ImageVector? = null,
    label: String,
    value: String,
    showArrow: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            val useStackedLayout = maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.5f

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = if (useStackedLayout) Alignment.Top else Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(32.dp)) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (useStackedLayout) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ProfileFieldValue(value, showArrow, Modifier.fillMaxWidth())
                    }
                } else {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.4f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ProfileFieldValue(value, showArrow, Modifier.weight(0.6f))
                }
            }
        }
    }
}

@Composable
private fun ProfileFieldValue(
    value: String,
    showArrow: Boolean,
    modifier: Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (showArrow) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
