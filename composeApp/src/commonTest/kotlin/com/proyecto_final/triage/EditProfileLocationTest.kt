package com.proyecto_final.triage

import com.proyecto_final.triage.network.PerfilResponse
import com.proyecto_final.triage.viewmodels.EditProfileViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

class EditProfileLocationTest {
    @Test
    fun cambiarProvinciaLimpiaCiudadYPreservaCodigoPostal() {
        val viewModel = EditProfileViewModel()
        viewModel.prefill(
            PerfilResponse(
                nombre = "Ana",
                apellido = "Pérez",
                tipoDocumento = "DNI",
                numeroDocumento = "12345678",
                codigoPostal = "1900",
                ciudad = "La Plata",
                provincia = "Buenos Aires"
            )
        )

        viewModel.onFieldChange("provincia", "Córdoba")

        assertEquals("Córdoba", viewModel.state.value.provincia)
        assertEquals("", viewModel.state.value.ciudad)
        assertEquals("1900", viewModel.state.value.codigoPostal)
    }

    @Test
    fun reseleccionarProvinciaSinTildeConservaCiudad() {
        val viewModel = EditProfileViewModel()
        viewModel.prefill(
            PerfilResponse(
                nombre = "Ana",
                apellido = "Pérez",
                tipoDocumento = "DNI",
                numeroDocumento = "12345678",
                codigoPostal = "5800",
                ciudad = "Río Cuarto",
                provincia = "Córdoba"
            )
        )

        viewModel.onFieldChange("provincia", "cordoba")

        assertEquals("cordoba", viewModel.state.value.provincia)
        assertEquals("Río Cuarto", viewModel.state.value.ciudad)
    }

    @Test
    fun editarCiudadDeDomicilioLegacyCabaNormalizaProvinciaYConservaPrefillHastaEdicion() {
        val viewModel = EditProfileViewModel()
        viewModel.prefill(
            PerfilResponse(
                nombre = "Ana",
                apellido = "Pérez",
                tipoDocumento = "DNI",
                numeroDocumento = "12345678",
                codigoPostal = "1000",
                ciudad = "CABA",
                provincia = "Buenos Aires"
            )
        )

        assertEquals("Buenos Aires", viewModel.state.value.provincia)
        assertEquals("CABA", viewModel.state.value.ciudad)

        viewModel.onFieldChange("ciudad", "Palermo")

        assertEquals("Ciudad Autónoma de Buenos Aires", viewModel.state.value.provincia)
        assertEquals("Palermo", viewModel.state.value.ciudad)
    }
}
