package com.proyecto_final.triage.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proyecto_final.triage.network.PerfilResponse
import com.proyecto_final.triage.network.PerfilUpdateResult
import com.proyecto_final.triage.network.PerfilUsuarioRequest
import com.proyecto_final.triage.network.actualizarPerfil
import com.proyecto_final.triage.screens.isValidEmail
import com.proyecto_final.triage.utils.claveUbicacion
import com.proyecto_final.triage.utils.provinciaParaSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.proyecto_final.triage.AppConstants

class EditProfileViewModel : ViewModel() {

    private val _state = MutableStateFlow(EditProfileState())
    val state: StateFlow<EditProfileState> = _state

    fun prefill(perfil: PerfilResponse) {
        _state.value = EditProfileState(
            nombre = perfil.nombre,
            apellido = perfil.apellido,
            tipoDocumento = tipoDocumentoParaUi(perfil.tipoDocumento),
            numeroDocumento = perfil.numeroDocumento,

            fechaNacimiento = perfil.fechaNacimiento?.let { formatearFechaParaPicker(it) } ?: "",

            generoBiologico = perfil.generoBiologico.orEmpty(),
            generoConElQueSeIdentifica = perfil.generoConElQueSeIdentifica.orEmpty(),

            email = perfil.email.orEmpty(),
            telefono = perfil.telefono.orEmpty(),

            calle = perfil.calle.orEmpty(),
            alturaDireccion = perfil.alturaDireccion.orEmpty(),
            piso = perfil.piso.orEmpty(),
            codigoPostal = perfil.codigoPostal.orEmpty(),

            ciudad = perfil.ciudad.orEmpty(),
            provincia = perfil.provincia.orEmpty(),

            peso = extraerNumero(perfil.peso?.toString(), esAltura = false),
            alturaPersona = extraerNumero(perfil.alturaPersona?.toString(), esAltura = true)
        )
    }

    fun onFieldChange(campo: String, valor: String) {
        _state.update { estado ->
            when (campo) {
                "nombre" -> estado.copy(nombre = valor)
                "apellido" -> estado.copy(apellido = valor)
                "tipoDocumento" -> estado.copy(tipoDocumento = valor)
                "numeroDocumento" -> estado.copy(numeroDocumento = valor)
                "fechaNacimiento" -> estado.copy(fechaNacimiento = valor)

                "generoBiologico" -> estado.copy(generoBiologico = valor.uppercase())
                "generoConElQueSeIdentifica" -> if (valor == "Otro") {
                    estado.copy(generoConElQueSeIdentifica = "X")
                } else {
                    estado.copy(generoConElQueSeIdentifica = valor.uppercase())
                }

                "email" -> estado.copy(email = valor)
                "telefono" -> estado.copy(telefono = valor)

                "calle" -> estado.copy(calle = valor)
                "alturaDireccion" -> estado.copy(alturaDireccion = valor)
                "piso" -> estado.copy(piso = valor)
                "codigoPostal" -> estado.copy(codigoPostal = valor)
                "ciudad" -> estado.copy(
                    ciudad = valor,
                    provincia = provinciaParaSelector(estado.provincia, estado.ciudad),
                    errors = estado.errors - "ciudad"
                )
                "provincia" -> {
                    val provincia = provinciaParaSelector(valor)
                    val actual = provinciaParaSelector(estado.provincia, estado.ciudad)
                    val cambioProvincia = claveUbicacion(provincia) != claveUbicacion(actual)
                    estado.copy(
                        provincia = provincia,
                        ciudad = if (cambioProvincia) "" else estado.ciudad,
                        errors = if (cambioProvincia) {
                            estado.errors - "provincia" - "ciudad"
                        } else {
                            estado.errors - "provincia"
                        }
                    )
                }

                "peso" -> estado.copy(peso = valor)
                "alturaPersona" -> estado.copy(alturaPersona = valor)
                else -> estado
            }
        }
    }

    fun guardar() {
        val errores = validar()
        if (errores.isNotEmpty()) {
            _state.update { it.copy(errors = errores) }
            return
        }

        println("Guardando perfil con datos (antes de update): ${_state.value}")

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errors = emptyMap(), generalError = null) }

            println("Guardando perfil con datos (despues de update): ${_state.value}")
            when (val result = actualizarPerfil(buildRequest())) {
                is PerfilUpdateResult.Success ->
                    _state.update { it.copy(isSaving = false, saved = true) }

                is PerfilUpdateResult.FieldErrors ->
                    _state.update { it.copy(isSaving = false, errors = result.errors) }

                is PerfilUpdateResult.Error ->
                    _state.update { it.copy(isSaving = false, generalError = result.message) }
            }
        }
    }

    private fun validar(): Map<String, String> {
        val s = _state.value
        val errores = mutableMapOf<String, String>()

        validarCamposObligatorios(errores, s)
        validarEmail(errores, s)
        validarTelefono(errores, s)
        validarPeso(errores, s)
        validarAlturaPersona(errores, s)
        validarAlturaDireccion(errores, s)
        validarPiso(errores, s)
        validarCodigoPostal(errores, s)
        validarCiudadProvincia(errores, s)

        return errores
    }

    private fun validarCamposObligatorios(errores: MutableMap<String, String>, s: EditProfileState) {
        if (s.nombre.isBlank()) errores["nombre"] = AppConstants.CAMPO_OBLIGATORIO
        if (s.apellido.isBlank()) errores["apellido"] = AppConstants.CAMPO_OBLIGATORIO
        if (s.tipoDocumento.isBlank()) errores["tipoDocumento"] = AppConstants.CAMPO_OBLIGATORIO
        if (s.numeroDocumento.isBlank()) errores["numeroDocumento"] = AppConstants.CAMPO_OBLIGATORIO
        if (s.fechaNacimiento.isBlank()) errores["fechaNacimiento"] = AppConstants.CAMPO_OBLIGATORIO
        if (s.generoBiologico.isBlank()) errores["generoBiologico"] = AppConstants.CAMPO_OBLIGATORIO
        if (s.generoConElQueSeIdentifica.isBlank()) {
            errores["generoConElQueSeIdentifica"] = AppConstants.CAMPO_OBLIGATORIO
        }
        if (s.ciudad.isBlank()) errores["ciudad"] = AppConstants.CAMPO_OBLIGATORIO
        if (s.provincia.isBlank()) errores["provincia"] = AppConstants.CAMPO_OBLIGATORIO
    }

    private fun validarEmail(errores: MutableMap<String, String>, s: EditProfileState) {
        if (s.email.isBlank()) {
            errores["email"] = AppConstants.CAMPO_OBLIGATORIO
        } else if (!isValidEmail(s.email.trim())) {
            errores["email"] = "Ingresá un correo válido"
        }
    }

    private fun validarTelefono(errores: MutableMap<String, String>, s: EditProfileState) {
        if (s.telefono.isBlank()) {
            errores["telefono"] = AppConstants.CAMPO_OBLIGATORIO
        } else if (s.telefono.trim().any { !it.isDigit() && it !in "+-() " }) {
            errores["telefono"] = "Ingresá un teléfono válido"
        }
    }

    private fun validarPeso(errores: MutableMap<String, String>, s: EditProfileState) {
        if (s.peso.isBlank()) {
            errores["peso"] = AppConstants.CAMPO_OBLIGATORIO
        } else {
            val numero = s.peso.trim().replace(',', '.').toDoubleOrNull()
            if (numero == null || numero <= 0) {
                errores["peso"] = "Ingresá un peso válido"
            }
        }
    }

    private fun validarAlturaPersona(errores: MutableMap<String, String>, s: EditProfileState) {
        if (s.alturaPersona.isBlank()) {
            errores["alturaPersona"] = AppConstants.CAMPO_OBLIGATORIO
        } else {
            val numero = s.alturaPersona.trim().toIntOrNull()
            if (numero == null || numero <= 0) {
                errores["alturaPersona"] = "Ingresá una altura válida"
            }
        }
    }

    private fun validarAlturaDireccion(errores: MutableMap<String, String>, s: EditProfileState) {
        if (s.alturaDireccion.isBlank()) {
            errores["alturaDireccion"] = AppConstants.CAMPO_OBLIGATORIO
        } else if (!s.alturaDireccion.trim().all { it.isDigit() }) {
            errores["alturaDireccion"] = "Ingresá un valor válido"
        }
    }

    private fun validarPiso(errores: MutableMap<String, String>, s: EditProfileState) {
        if (s.piso.isNotBlank() && !s.piso.trim().all { it.isDigit() }) {
            errores["piso"] = "Ingresá un valor válido"
        }
    }

    private fun validarCodigoPostal(errores: MutableMap<String, String>, s: EditProfileState) {
        if (s.codigoPostal.isBlank()) {
            errores["codigoPostal"] = AppConstants.CAMPO_OBLIGATORIO
        } else if (!s.codigoPostal.trim().all { it.isDigit() }) {
            errores["codigoPostal"] = "Ingresá un código postal válido"
        }
    }

    private fun validarCiudadProvincia(errores: MutableMap<String, String>, s: EditProfileState) {
        if (s.ciudad.isBlank()) errores["ciudad"] = AppConstants.CAMPO_OBLIGATORIO
        if (s.provincia.isBlank()) errores["provincia"] = AppConstants.CAMPO_OBLIGATORIO
    }

    private fun buildRequest(): PerfilUsuarioRequest {
        val s = _state.value
        return PerfilUsuarioRequest(
            nombre = s.nombre.trim(),
            apellido = s.apellido.trim(),
            tipoDocumento = tipoDocumentoParaBackend(s.tipoDocumento),
            numeroDocumento = s.numeroDocumento.trim(),
            fechaNacimiento = fechaParaBackend(s.fechaNacimiento),
            generoBiologico = s.generoBiologico.ifBlank { "" },
            generoConElQueSeIdentifica = s.generoConElQueSeIdentifica.ifBlank { "" },
            email = s.email.trim().ifBlank { "" },
            telefono = s.telefono.trim().ifBlank { "" },
            calle = s.calle.trim().ifBlank { "" },
            alturaDireccion = s.alturaDireccion.trim().ifBlank { "" },
            piso = s.piso.trim().ifBlank { "" },
            codigoPostal = s.codigoPostal.trim().ifBlank { "" },
            ciudad = s.ciudad.trim().ifBlank { "" },
            provincia = s.provincia.trim().ifBlank { "" },
            peso = s.peso.trim().replace(',', '.').toDoubleOrNull() ?: 0.0,
            alturaPersona = s.alturaPersona.trim().toIntOrNull() ?: 0
        )
    }

    private fun tipoDocumentoParaBackend(tipo: String): String = when (tipo) {
        "Lib. Cívica" -> "LIBRETA_CIVICA"
        "Lib. Enrolamiento" -> "LIBRETA_ENROLAMIENTO"
        else -> tipo.uppercase()
    }

    private fun tipoDocumentoParaUi(tipo: String): String = when (tipo) {
        "LIBRETA_CIVICA" -> "Lib. Cívica"
        "LIBRETA_ENROLAMIENTO" -> "Lib. Enrolamiento"
        else -> tipo
    }

    private fun formatearFechaParaPicker(fecha: String): String {
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

    private fun fechaParaBackend(fecha: String): String {
        val partes = fecha.split("/")
        if (partes.size != 3) return ""

        return try {
            val dia = partes[0].toInt()
            val mes = partes[1].toInt()
            val anio = partes[2].toInt()
            if (dia in 1..31 && mes in 1..12 && anio in 1900..2100) {
                "${anio}-${mes.toString().padStart(2, '0')}-${dia.toString().padStart(2, '0')}"
            } else {
                ""
            }
        } catch (e: NumberFormatException) {
            ""
        }
    }

    private fun extraerNumero(texto: String?, esAltura: Boolean): String {
        if (texto.isNullOrBlank()) return ""

        val valor = texto.trim().split(" ").firstOrNull()
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?: return ""

        return if (esAltura) {
            val esMetros = texto.contains("m") && !texto.contains("cm")
            if (esMetros) (valor * 100).toInt().toString() else valor.toInt().toString()
        } else {
            if (valor % 1.0 == 0.0) valor.toInt().toString() else valor.toString()
        }
    }
}

data class EditProfileState(
    val nombre: String = "",
    val apellido: String = "",
    val tipoDocumento: String = "",
    val numeroDocumento: String = "",
    val fechaNacimiento: String = "",
    val generoBiologico: String = "",
    val generoConElQueSeIdentifica: String = "",
    val email: String = "",
    val telefono: String = "",
    val calle: String = "",
    val alturaDireccion: String = "",
    val piso: String = "",
    val codigoPostal: String = "",
    val ciudad: String = "",
    val provincia: String = "",
    val peso: String = "",
    val alturaPersona: String = "",
    val errors: Map<String, String> = emptyMap(),
    val isSaving: Boolean = false,
    val generalError: String? = null,
    val saved: Boolean = false
)
