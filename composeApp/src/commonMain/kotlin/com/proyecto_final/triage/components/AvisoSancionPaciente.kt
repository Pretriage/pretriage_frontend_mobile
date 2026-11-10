package com.proyecto_final.triage.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.proyecto_final.triage.network.TipoAvisoSancion
import com.proyecto_final.triage.utils.dialEmergency
import com.proyecto_final.triage.viewmodels.SancionPacienteState
import com.proyecto_final.triage.viewmodels.mensajeInscripcionRemota
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
internal fun ConsultarSancionAlReanudar(sancion: SancionPacienteState) {
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(sancion) {
        val job = scope.launch {
            sancion.actualizar()
            snapshotFlow { sancion.datos?.vigente == true }.collectLatest { vigente ->
                if (vigente) {
                    while (true) {
                        delay(30_000)
                        sancion.actualizar()
                    }
                }
            }
        }
        onPauseOrDispose { job.cancel() }
    }
}

@Composable
internal fun AvisoSancionPaciente(sancion: SancionPacienteState) {
    val aviso = sancion.aviso ?: return
    val (titulo, mensaje) = when (aviso.tipoAviso) {
        TipoAvisoSancion.APLICADA -> "Inscripción remota restringida" to
            (aviso.mensajeInscripcionRemota() ?: return)
        TipoAvisoSancion.LEVANTADA -> "Inscripción remota habilitada" to
            "Se levantó la sanción. Ya podés volver a anotarte en la cola desde la app."
        null -> return
    }
    AlertDialog(
        onDismissRequest = sancion::descartarAviso,
        title = { Text(titulo) },
        text = { Text(mensaje) },
        confirmButton = {
            TextButton(onClick = sancion::descartarAviso) { Text("Entendido") }
        }
    )
}

@Composable
internal fun RestriccionInscripcionRemota(mensaje: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(mensaje, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "Si tenés síntomas críticos, acudí de inmediato a la guardia o llamá al 107.",
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = { dialEmergency("107") }) { Text("Llamar al 107") }
        }
    }
}
