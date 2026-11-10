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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
        _state.update { anterior ->
            // Al corregir un campo se limpia su error para no mostrar avisos desactualizados.
            val estado = anterior.copy(errors = anterior.errors - campo)
            when (campo) {
                "nombre" -> estado.copy(nombre = valor)
                "apellido" -> estado.copy(apellido = valor)
                "tipoDocumento" -> estado.copy(tipoDocumento = valor, errors = estado.errors - "numeroDocumento")
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
        validarNombre(errores, "nombre", s.nombre)
        validarNombre(errores, "apellido", s.apellido)
        validarDocumento(errores, s)
        validarFechaNacimiento(errores, s)
        validarEmail(errores, s)
        validarTelefono(errores, s)
        validarCalle(errores, s)
        validarAlturaDireccion(errores, s)
        validarPiso(errores, s)
        validarCodigoPostal(errores, s)
        validarCiudadProvincia(errores, s)
        validarPeso(errores, s)
        validarAlturaPersona(errores, s)

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
        if (s.calle.isBlank()) errores["calle"] = AppConstants.CAMPO_OBLIGATORIO
        if (s.ciudad.isBlank()) errores["ciudad"] = AppConstants.CAMPO_OBLIGATORIO
        if (s.provincia.isBlank()) errores["provincia"] = AppConstants.CAMPO_OBLIGATORIO
    }

    /** Nombres y apellidos: solo letras (con tildes), espacios, apóstrofos y guiones. */
    private fun validarNombre(errores: MutableMap<String, String>, campo: String, valor: String) {
        val texto = valor.trim()
        if (texto.isEmpty()) return
        when {
            texto.length !in PerfilLimites.NOMBRE_MIN..PerfilLimites.NOMBRE_MAX ->
                errores[campo] = "Debe tener entre ${PerfilLimites.NOMBRE_MIN} y ${PerfilLimites.NOMBRE_MAX} caracteres"
            !texto.all { it.isLetter() || it in " '’-" } || !texto.first().isLetter() || !texto.last().isLetter() ->
                errores[campo] = "Usá solo letras, espacios, apóstrofos o guiones"
        }
    }

    private fun validarDocumento(errores: MutableMap<String, String>, s: EditProfileState) {
        val numero = s.numeroDocumento.trim()
        if (numero.isEmpty()) return
        val rango = PerfilLimites.largoDocumento(s.tipoDocumento)
        when {
            !numero.all { it.isDigit() } -> errores["numeroDocumento"] = "Ingresá solo números"
            numero.length !in rango ->
                errores["numeroDocumento"] = if (rango.first == rango.last) {
                    "Debe tener ${rango.first} números"
                } else {
                    "Debe tener entre ${rango.first} y ${rango.last} números"
                }
            numero.trimStart('0').isEmpty() -> errores["numeroDocumento"] = "Ingresá un número de documento válido"
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun validarFechaNacimiento(errores: MutableMap<String, String>, s: EditProfileState) {
        if (s.fechaNacimiento.isBlank()) return
        val fecha = parsearFecha(s.fechaNacimiento)
        if (fecha == null) {
            errores["fechaNacimiento"] = "Ingresá una fecha válida (DD/MM/AAAA)"
            return
        }
        val hoy = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val edad = hoy.year - fecha.year -
            if (hoy.month.ordinal < fecha.month.ordinal ||
                (hoy.month == fecha.month && hoy.day < fecha.day)) 1 else 0
        when {
            fecha > hoy -> errores["fechaNacimiento"] = "La fecha no puede ser futura"
            edad > PerfilLimites.EDAD_MAXIMA -> errores["fechaNacimiento"] = "Revisá el año de nacimiento"
        }
    }

    private fun validarEmail(errores: MutableMap<String, String>, s: EditProfileState) {
        val email = s.email.trim()
        when {
            email.isEmpty() -> errores["email"] = AppConstants.CAMPO_OBLIGATORIO
            email.length > PerfilLimites.EMAIL_MAX -> errores["email"] = "El correo es demasiado largo"
            !isValidEmail(email) -> errores["email"] = "Ingresá un correo válido"
        }
    }

    /** Teléfono: dígitos con separadores habituales y un "+" opcional solo al inicio. */
    private fun validarTelefono(errores: MutableMap<String, String>, s: EditProfileState) {
        val telefono = s.telefono.trim()
        val digitos = telefono.count { it.isDigit() }
        when {
            telefono.isEmpty() -> errores["telefono"] = AppConstants.CAMPO_OBLIGATORIO
            telefono.any { !it.isDigit() && it !in "+-() " } || telefono.lastIndexOf('+') > 0 ->
                errores["telefono"] = "Usá solo números, espacios, guiones, paréntesis y + al inicio"
            digitos !in PerfilLimites.TELEFONO_DIGITOS_MIN..PerfilLimites.TELEFONO_DIGITOS_MAX ->
                errores["telefono"] = "Debe tener entre ${PerfilLimites.TELEFONO_DIGITOS_MIN} y ${PerfilLimites.TELEFONO_DIGITOS_MAX} números"
        }
    }

    private fun validarCalle(errores: MutableMap<String, String>, s: EditProfileState) {
        val calle = s.calle.trim()
        if (calle.isEmpty()) return
        when {
            calle.length !in PerfilLimites.CALLE_MIN..PerfilLimites.CALLE_MAX ->
                errores["calle"] = "Debe tener entre ${PerfilLimites.CALLE_MIN} y ${PerfilLimites.CALLE_MAX} caracteres"
            !calle.any { it.isLetter() } || calle.any { !it.isLetterOrDigit() && it !in " .,'’-°º/" } ->
                errores["calle"] = "Ingresá un nombre de calle válido"
        }
    }

    private fun validarAlturaDireccion(errores: MutableMap<String, String>, s: EditProfileState) {
        val altura = s.alturaDireccion.trim()
        val numero = altura.toIntOrNull()
        when {
            altura.isEmpty() -> errores["alturaDireccion"] = AppConstants.CAMPO_OBLIGATORIO
            !altura.all { it.isDigit() } || numero == null -> errores["alturaDireccion"] = "Ingresá solo números"
            numero !in PerfilLimites.ALTURA_DIRECCION_MIN..PerfilLimites.ALTURA_DIRECCION_MAX ->
                errores["alturaDireccion"] = "Ingresá una altura entre ${PerfilLimites.ALTURA_DIRECCION_MIN} y ${PerfilLimites.ALTURA_DIRECCION_MAX}"
        }
    }

    private fun validarPiso(errores: MutableMap<String, String>, s: EditProfileState) {
        val piso = s.piso.trim()
        if (piso.isEmpty()) return
        val numero = piso.toIntOrNull()
        if (!piso.all { it.isDigit() } || numero == null || numero > PerfilLimites.PISO_MAX) {
            errores["piso"] = "Ingresá un piso entre 0 y ${PerfilLimites.PISO_MAX}"
        }
    }

    /** Código postal argentino: 4 números (1425) o formato CPA (C1425ABC). */
    private fun validarCodigoPostal(errores: MutableMap<String, String>, s: EditProfileState) {
        val cp = s.codigoPostal.trim().uppercase()
        val esNumerico = cp.length == 4 && cp.all { it.isDigit() } && cp.first() != '0'
        val esCpa = cp.length == 8 && cp[0].isLetter() &&
            cp.substring(1, 5).all { it.isDigit() } && cp.substring(5).all { it.isLetter() }
        when {
            cp.isEmpty() -> errores["codigoPostal"] = AppConstants.CAMPO_OBLIGATORIO
            !esNumerico && !esCpa -> errores["codigoPostal"] = "Ingresá 4 números (ej.: 1425) o el formato CPA (ej.: C1425ABC)"
        }
    }

    private fun validarCiudadProvincia(errores: MutableMap<String, String>, s: EditProfileState) {
        if (s.ciudad.isBlank()) errores["ciudad"] = AppConstants.CAMPO_OBLIGATORIO
        if (s.provincia.isBlank()) errores["provincia"] = AppConstants.CAMPO_OBLIGATORIO
    }

    private fun validarPeso(errores: MutableMap<String, String>, s: EditProfileState) {
        val texto = s.peso.trim().replace(',', '.')
        val numero = texto.toDoubleOrNull()
        val decimales = texto.substringAfter('.', "").length
        when {
            texto.isEmpty() -> errores["peso"] = AppConstants.CAMPO_OBLIGATORIO
            numero == null || decimales > 1 -> errores["peso"] = "Ingresá un peso válido (ej.: 70 o 70,5)"
            numero < PerfilLimites.PESO_MIN || numero > PerfilLimites.PESO_MAX ->
                errores["peso"] = "Ingresá un peso entre ${PerfilLimites.PESO_MIN.toInt()} y ${PerfilLimites.PESO_MAX.toInt()} kg"
        }
    }

    private fun validarAlturaPersona(errores: MutableMap<String, String>, s: EditProfileState) {
        val texto = s.alturaPersona.trim()
        val numero = texto.toIntOrNull()
        when {
            texto.isEmpty() -> errores["alturaPersona"] = AppConstants.CAMPO_OBLIGATORIO
            numero == null -> errores["alturaPersona"] = "Ingresá la altura en centímetros (ej.: 170)"
            numero !in PerfilLimites.ALTURA_CM_MIN..PerfilLimites.ALTURA_CM_MAX ->
                errores["alturaPersona"] = "Ingresá una altura entre ${PerfilLimites.ALTURA_CM_MIN} y ${PerfilLimites.ALTURA_CM_MAX} cm"
        }
    }

    private fun parsearFecha(fecha: String): LocalDate? {
        val partes = fecha.trim().split("/")
        if (partes.size != 3) return null
        val dia = partes[0].toIntOrNull() ?: return null
        val mes = partes[1].toIntOrNull() ?: return null
        val anio = partes[2].toIntOrNull() ?: return null
        return try {
            LocalDate(anio, mes, dia)
        } catch (e: IllegalArgumentException) {
            null
        }
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
            codigoPostal = s.codigoPostal.trim().uppercase().ifBlank { "" },
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

/** Límites de los datos del perfil para que lo cargado sea razonable. */
object PerfilLimites {
    const val NOMBRE_MIN = 2
    const val NOMBRE_MAX = 50
    const val EMAIL_MAX = 100
    const val TELEFONO_MAX = 20
    const val TELEFONO_DIGITOS_MIN = 8
    const val TELEFONO_DIGITOS_MAX = 15
    const val CALLE_MIN = 2
    const val CALLE_MAX = 60
    const val ALTURA_DIRECCION_MIN = 1
    const val ALTURA_DIRECCION_MAX = 99999
    const val PISO_MAX = 200
    const val CODIGO_POSTAL_MAX = 8
    const val PESO_MIN = 1.0
    const val PESO_MAX = 350.0
    const val ALTURA_CM_MIN = 40
    const val ALTURA_CM_MAX = 250
    const val EDAD_MAXIMA = 120

    /** DNI: 7 u 8 números. Libretas cívica y de enrolamiento: 6 a 8 números. */
    fun largoDocumento(tipo: String): IntRange = when (tipo) {
        "DNI" -> 7..8
        else -> 6..8
    }

    fun maxDocumento(tipo: String): Int = largoDocumento(tipo).last
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
