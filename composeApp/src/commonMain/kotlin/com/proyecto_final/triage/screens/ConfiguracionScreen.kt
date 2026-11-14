package com.proyecto_final.triage.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.proyecto_final.triage.components.CommonHeader
import com.proyecto_final.triage.components.NextButton
import com.proyecto_final.triage.storage.TokenStorageProvider
import com.proyecto_final.triage.theme.AppTheme
import com.proyecto_final.triage.theme.Spacing

class ConfiguracionScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        ConfiguracionContent(
            onBack = { navigator?.pop() },
            onCambiarContrasenia = { navigator?.push(CambiarContraseniaScreen()) },
            onLogOut = {
                TokenStorageProvider.instance.clearTokens()
                com.proyecto_final.triage.notificaciones.SeguimientoConsulta.detener()
                navigator?.popUntilRoot()
                navigator?.replace(SignInScreen())
            }
        )
    }
}

@Preview
@Composable
fun ConfiguracionPreview() {
    AppTheme {
        ConfiguracionContent(
            onBack = { },
            onCambiarContrasenia = { },
            onLogOut = { }
        )
    }
}

/* ============================================================
   MODELO DE OPCIÓN
   ============================================================ */

data class OpcionConfiguracion(
    val titulo: String,
    val icono: ImageVector? = null,
    val destructiva: Boolean = false,   // texto en rojo (ej: "Eliminar cuenta")
    val onClick: () -> Unit
)

/* ============================================================
   CONTENIDO
   ============================================================ */

@Composable
fun ConfiguracionContent(
    onBack: () -> Unit,
    onCambiarContrasenia: () -> Unit,
    onLogOut: () -> Unit
) {
    // Cada lista es un grupo (una card). Para sumar opciones,
    // agregalas acá o creá otro grupo.
    val grupos = listOf(
        listOf(
            OpcionConfiguracion(
                titulo = "Cambiar contraseña",
                icono = Icons.Filled.Lock,
                onClick = onCambiarContrasenia
            )
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        CommonHeader(
            title = "Configuración",
            onBack = onBack
        )

        grupos.forEach { grupo ->
            GrupoOpciones(opciones = grupo)
            Spacer(modifier = Modifier.height(Spacing.md))
        }

        // Empuja el botón al fondo
        Spacer(modifier = Modifier.weight(1f))

        NextButton(
            onClick = onLogOut,
            text = "Cerrar sesión"
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/* ============================================================
   GRUPO DE OPCIONES (una card con filas separadas por líneas)
   ============================================================ */

@Composable
private fun GrupoOpciones(opciones: List<OpcionConfiguracion>) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, Color(0xFFD6D6D6)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            opciones.forEachIndexed { index, opcion ->
                FilaOpcion(opcion = opcion)

                if (index < opciones.lastIndex) {
                    HorizontalDivider(
                        color = Color(0xFFE0E0E0),
                        thickness = 1.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaOpcion(opcion: OpcionConfiguracion) {
    val colorTexto = if (opcion.destructiva) {
        Color(0xFFE53935)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val colorFlecha = if (opcion.destructiva) {
        Color(0xFFE53935)
    } else {
        Color(0xFF5BB8D4)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { opcion.onClick() }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        opcion.icono?.let { icono ->
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = colorTexto,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Text(
            text = opcion.titulo,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = colorTexto,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colorFlecha
        )
    }
}