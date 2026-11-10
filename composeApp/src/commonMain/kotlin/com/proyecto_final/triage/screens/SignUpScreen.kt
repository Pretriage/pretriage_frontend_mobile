package com.proyecto_final.triage.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.proyecto_final.triage.components.InfoBanner
import com.proyecto_final.triage.components.InputDropdownField
import com.proyecto_final.triage.components.InputTextField
import com.proyecto_final.triage.components.ProgressBar
import com.proyecto_final.triage.network.auth.RegisterRequest
import com.proyecto_final.triage.theme.AppTheme
import com.proyecto_final.triage.theme.Spacing
import com.proyecto_final.triage.viewmodels.SignUpState
import com.proyecto_final.triage.viewmodels.SignUpViewModel
import org.jetbrains.compose.resources.painterResource
import triage.composeapp.generated.resources.Res
import triage.composeapp.generated.resources.logo
import com.proyecto_final.triage.AppConstants


class SignUpScreen : Screen {

    @Composable
    override fun Content() {

        val navigator = LocalNavigator.current
        val viewModel = remember {
            SignUpViewModel()
        }

        val state by viewModel.state.collectAsState()

        var currentStep by remember {
            mutableStateOf(1)
        }

        LaunchedEffect(state) {

            if (state is SignUpState.Success) {

                /*
                 * Guardamos el mensaje antes de volver
                 * al SignIn que ya está debajo en Voyager.
                 */
                RegistrationSuccessMessage.message =
                    "¡Cuenta creada correctamente!"

                /*
                 * Volvemos al SignIn existente.
                 */
                navigator?.pop()
            }
        }

        SignUpContent(
            currentStep = currentStep,

            onStepChange = {
                currentStep = it
            },

            onBack = {
                navigator?.pop()
            },

            viewModel = viewModel,

            state = state
        )
    }
}


@Preview
@Composable
fun SignUpPreview() {

    AppTheme {

        SignUpContent(
            currentStep = 1,

            onStepChange = {},

            onBack = {},

            viewModel = SignUpViewModel(),

            state = SignUpState.Idle
        )
    }
}


@Composable
fun SignUpContent(
    currentStep: Int,
    onStepChange: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: SignUpViewModel,
    state: SignUpState
) {

    var showStep1Errors by remember {
        mutableStateOf(false)
    }

    var showStep2Errors by remember {
        mutableStateOf(false)
    }

    var showStep3Errors by remember {
        mutableStateOf(false)
    }


    // =========================================================
    // PASO 1
    // =========================================================

    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var repeatPassword by remember {
        mutableStateOf("")
    }


    // =========================================================
    // PASO 2
    // =========================================================

    var nombre by remember {
        mutableStateOf("")
    }

    var apellido by remember {
        mutableStateOf("")
    }

    var tipoDocumento by remember {
        mutableStateOf("")
    }

    var numeroDocumento by remember {
        mutableStateOf("")
    }

    var fechaNacimiento by remember {
        mutableStateOf("")
    }

    var genero by remember {
        mutableStateOf("")
    }

    var sexo by remember {
        mutableStateOf("")
    }


    // =========================================================
    // PASO 3
    // =========================================================

    var peso by remember {
        mutableStateOf("")
    }

    var altura by remember {
        mutableStateOf("")
    }


    // =========================================================
    // CREAR CUENTA
    // =========================================================

    val onCreateAccount = {

        val request = RegisterRequest(

            nombre = nombre,

            apellido = apellido,

            numeroDocumento = numeroDocumento,

            tipoDocumento = tipoDocumento,

            email = email,

            password = password,

            tipoUsuario = "Paciente",

            rol = "USER"
        )

        viewModel.createAccount(request)
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
            )
            .padding(horizontal = 24.dp)
            .verticalScroll(
                rememberScrollState()
            ),

        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(
            modifier = Modifier.height(36.dp)
        )


        // =====================================================
        // HEADER
        // =====================================================

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {

            Icon(
                imageVector =
                    Icons.AutoMirrored.Filled.ArrowBack,

                contentDescription = "Volver",

                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable {

                        if (currentStep > 1) {

                            onStepChange(
                                currentStep - 1
                            )

                        } else {

                            onBack()
                        }
                    }
            )


            Image(
                painter = painterResource(
                    Res.drawable.logo
                ),

                contentDescription = "Logo",

                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )
        }


        Spacer(
            modifier = Modifier.height(4.dp)
        )


        // =====================================================
        // TÍTULO
        // =====================================================

        Text(
            text = when (currentStep) {

                1 -> "Creá tu cuenta"

                2 -> "Información personal"

                else -> "Información de salud"
            },

            style =
                MaterialTheme.typography.headlineMedium,

            color =
                MaterialTheme.colorScheme.onBackground,

            modifier = Modifier.fillMaxWidth(),

            textAlign = TextAlign.Center
        )


        Text(
            text = when (currentStep) {

                1 -> "Datos de acceso"

                else -> "Contanos un poco sobre vos"
            },

            style =
                MaterialTheme.typography.bodyMedium,

            color =
                MaterialTheme.colorScheme.secondary,

            modifier = Modifier.fillMaxWidth(),

            textAlign = TextAlign.Center
        )


        Spacer(
            modifier = Modifier.height(16.dp)
        )


        // =====================================================
        // PROGRESO
        // =====================================================

        ProgressBar(
            currentStep = currentStep,
            totalSteps = 3
        )


        Spacer(
            modifier = Modifier.height(
                Spacing.md
            )
        )


        // =====================================================
        // CONTENIDO
        // =====================================================

        when (currentStep) {

            // =================================================
            // PASO 1
            // =================================================

            1 -> Step1Content(

                email = email,

                onEmailChange = {
                    email = it
                },

                password = password,

                onPasswordChange = {
                    password = it
                },

                repeatPassword = repeatPassword,

                onRepeatPasswordChange = {
                    repeatPassword = it
                },

                showErrors = showStep1Errors
            )


            // =================================================
            // PASO 2
            // =================================================

            2 -> Step2Content(

                nombre = nombre,

                onNombreChange = {
                    nombre = it
                },

                apellido = apellido,

                onApellidoChange = {
                    apellido = it
                },

                tipoDocumento = tipoDocumento,

                onTipoDocumentoChange = {

                    tipoDocumento = it

                    numeroDocumento = ""
                },

                numeroDocumento = numeroDocumento,

                onNumeroDocumentoChange = {
                    numeroDocumento = it
                },

                fechaNacimiento = fechaNacimiento,

                onFechaNacimientoChange = {
                    fechaNacimiento = it
                },

                genero = genero,

                onGeneroChange = {
                    genero = it
                },

                sexo = sexo,

                onSexoChange = {
                    sexo = it
                },

                showErrors = showStep2Errors
            )


            // =================================================
            // PASO 3
            // =================================================

            3 -> Step3Content(

                peso = peso,

                onPesoChange = {
                    peso = it
                },

                altura = altura,

                onAlturaChange = {
                    altura = it
                },

                pesoError =
                    showStep3Errors &&
                            peso.isBlank(),

                alturaError =
                    showStep3Errors &&
                            altura.isBlank(),

                state = state
            )
        }


        Spacer(
            modifier = Modifier.weight(1f)
        )


        // =====================================================
        // BOTÓN
        // =====================================================

        Button(

            onClick = {

                when (currentStep) {

                    // =========================================
                    // PASO 1
                    // =========================================

                    1 -> {

                        showStep1Errors = true

                        val valid =
                            email.isNotBlank() &&
                                    isValidEmail(email) &&
                                    password.isNotBlank() &&
                                    isValidPassword(password) &&
                                    repeatPassword.isNotBlank() &&
                                    password == repeatPassword

                        if (valid) {

                            onStepChange(2)
                        }
                    }


                    // =========================================
                    // PASO 2
                    // =========================================

                    2 -> {

                        showStep2Errors = true

                        val maxDocumento =
                            when (tipoDocumento) {

                                "DNI" -> 8

                                "LE", "LC" -> 7

                                else -> 0
                            }


                        val documentoValido =
                            maxDocumento > 0 &&
                                    numeroDocumento.length ==
                                    maxDocumento


                        val fechaValida =
                            isValidDate(
                                fechaNacimiento
                            )


                        val datosValidos =
                            nombre.isNotBlank() &&
                                    apellido.isNotBlank() &&
                                    tipoDocumento.isNotBlank() &&
                                    documentoValido &&
                                    fechaValida &&
                                    genero.isNotBlank() &&
                                    sexo.isNotBlank()


                        if (datosValidos) {

                            onStepChange(3)
                        }
                    }


                    // =========================================
                    // PASO 3
                    // =========================================

                    3 -> {

                        showStep3Errors = true

                        if (
                            peso.isNotBlank() &&
                            altura.isNotBlank()
                        ) {

                            onCreateAccount()
                        }
                    }
                }
            },

            shape = RoundedCornerShape(12.dp),

            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {

            Text(
                text =
                    if (currentStep == 3) {
                        "Crear cuenta"
                    } else {
                        "Continuar"
                    },

                style =
                    MaterialTheme.typography.labelLarge
            )
        }


        Spacer(
            modifier = Modifier.height(24.dp)
        )
    }
}


// =============================================================
// PASO 1
// =============================================================

@Composable
fun Step1Content(
    email: String,
    onEmailChange: (String) -> Unit,

    password: String,
    onPasswordChange: (String) -> Unit,

    repeatPassword: String,
    onRepeatPasswordChange: (String) -> Unit,

    showErrors: Boolean
) {

    val hasMinLength =
        password.length in 8..32

    val hasUppercase =
        password.any {
            it.isUpperCase()
        }

    val hasLowercase =
        password.any {
            it.isLowerCase()
        }

    val hasSpecial =
        password.any {
            !it.isLetterOrDigit()
        }

    val isEmailValid =
        isValidEmail(email)


    InputTextField(
        label = "Correo electrónico",

        value = email,

        onValueChange = onEmailChange,

        leadingIcon = Icons.Filled.Email,

        isError =
            showErrors &&
                    (
                            email.isBlank() ||
                                    !isEmailValid
                            ),

        errorMessage =
            if (email.isBlank()) {
                AppConstants.CAMPO_OBLIGATORIO
            } else {
                "Ingresá un correo válido"
            }
    )


    Spacer(
        modifier = Modifier.height(
            Spacing.sm
        )
    )


    val isPasswordValid =
        hasMinLength &&
                hasUppercase &&
                hasLowercase &&
                hasSpecial


    InputTextField(
        label = "Contraseña",

        value = password,

        onValueChange = onPasswordChange,

        leadingIcon = Icons.Filled.Lock,

        isPassword = true,

        isError =
            showErrors &&
                    (
                            password.isBlank() ||
                                    !isPasswordValid
                            ),

        errorMessage =
            if (password.isBlank()) {
                AppConstants.CAMPO_OBLIGATORIO
            } else {
                "La contraseña no cumple los requisitos"
            }
    )


    Spacer(
        modifier = Modifier.height(
            Spacing.md
        )
    )


    Text(
        text = "La contraseña debe tener:",

        style =
            MaterialTheme.typography.bodyMedium,

        modifier = Modifier.fillMaxWidth(),

        color = Color(0xFFB0BEC5)
    )


    Spacer(
        modifier = Modifier.height(6.dp)
    )


    PasswordRequirement(
        text = "Entre 8 y 32 caracteres",
        met = hasMinLength
    )


    PasswordRequirement(
        text = "Al menos una letra mayúscula",
        met = hasUppercase
    )


    PasswordRequirement(
        text = "Al menos una letra minúscula",
        met = hasLowercase
    )


    PasswordRequirement(
        text = "Al menos un carácter especial",
        met = hasSpecial
    )


    Spacer(
        modifier = Modifier.height(16.dp)
    )


    val passwordsMatch =
        password == repeatPassword


    InputTextField(
        label = "Repetir contraseña",

        value = repeatPassword,

        onValueChange =
            onRepeatPasswordChange,

        leadingIcon =
            Icons.Filled.Lock,

        isPassword = true,

        isError =
            showErrors &&
                    (
                            repeatPassword.isBlank() ||
                                    !passwordsMatch
                            ),

        errorMessage =
            if (repeatPassword.isBlank()) {
                AppConstants.CAMPO_OBLIGATORIO
            } else {
                "Las contraseñas no coinciden"
            }
    )
}


// =============================================================
// PASO 2
// =============================================================

@Composable
fun Step2Content(
    nombre: String,
    onNombreChange: (String) -> Unit,

    apellido: String,
    onApellidoChange: (String) -> Unit,

    tipoDocumento: String,
    onTipoDocumentoChange: (String) -> Unit,

    numeroDocumento: String,
    onNumeroDocumentoChange: (String) -> Unit,

    fechaNacimiento: String,
    onFechaNacimientoChange: (String) -> Unit,

    genero: String,
    onGeneroChange: (String) -> Unit,

    sexo: String,
    onSexoChange: (String) -> Unit,

    showErrors: Boolean
) {

    // =========================================================
    // NOMBRE
    // =========================================================

    InputTextField(
        label = "Nombre",

        value = nombre,

        onValueChange = onNombreChange,

        leadingIcon = Icons.Filled.Person,

        isError =
            showErrors &&
                    nombre.isBlank(),

        errorMessage =
            AppConstants.CAMPO_OBLIGATORIO
    )


    Spacer(
        modifier = Modifier.height(
            Spacing.sm
        )
    )


    // =========================================================
    // APELLIDO
    // =========================================================

    InputTextField(
        label = "Apellido",

        value = apellido,

        onValueChange = onApellidoChange,

        leadingIcon = Icons.Filled.Person,

        isError =
            showErrors &&
                    apellido.isBlank(),

        errorMessage =
            AppConstants.CAMPO_OBLIGATORIO
    )


    Spacer(
        modifier = Modifier.height(
            Spacing.sm
        )
    )


    // =========================================================
    // DOCUMENTO
    // =========================================================

    Row(
        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        Column(
            modifier = Modifier.weight(1f)
        ) {

            var expanded by remember {
                mutableStateOf(false)
            }


            InputDropdownField(

                label = "Tipo de documento",

                value = tipoDocumento,

                expanded = expanded,

                onExpandedChange = {
                    expanded = it
                },

                options = listOf(
                    "DNI",
                    "LE",
                    "LC"
                ),

                onOptionSelected = {
                    onTipoDocumentoChange(it)
                },

                isError =
                    showErrors &&
                            tipoDocumento.isBlank(),

                errorMessage =
                    "Seleccioná un tipo"
            )
        }


        Column(
            modifier = Modifier.weight(1f)
        ) {

            val maxDocumento =
                when (tipoDocumento) {

                    "DNI" -> 8

                    "LE", "LC" -> 7

                    else -> 8
                }


            InputTextField(

                label = "N.º de documento",

                value = numeroDocumento,

                onValueChange =
                    onNumeroDocumentoChange,

                keyboardType =
                    KeyboardType.Number,

                maxLength =
                    maxDocumento,

                isError =
                    showErrors &&
                            (
                                    numeroDocumento.isBlank() ||
                                            numeroDocumento.length !=
                                            maxDocumento
                                    ),

                errorMessage =
                    if (numeroDocumento.isBlank()) {
                        AppConstants.CAMPO_OBLIGATORIO
                    } else {
                        "Debe tener $maxDocumento números"
                    }
            )
        }
    }

    // =========================================================
    // FECHA
    // =========================================================

    Text(
        text = "Fecha de nacimiento",

        style =
            MaterialTheme.typography.bodyLarge,

        modifier = Modifier.fillMaxWidth()
    )


    Spacer(
        modifier = Modifier.height(8.dp)
    )


    OutlinedTextField(

        value = fechaNacimiento,

        onValueChange = { newValue ->

            val digits =
                newValue
                    .filter {
                        it.isDigit()
                    }
                    .take(8)

            onFechaNacimientoChange(
                digits
            )
        },

        leadingIcon = {

            Icon(
                imageVector =
                    Icons.Filled.CalendarMonth,

                contentDescription = null
            )
        },

        placeholder = {
            Text("DD/MM/AAAA")
        },

        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.Number
            ),

        visualTransformation =
            DateVisualTransformation(),

        singleLine = true,

        shape =
            RoundedCornerShape(12.dp),

        modifier =
            Modifier.fillMaxWidth(),

        isError =
            showErrors &&
                    !isValidDate(
                        fechaNacimiento
                    ),

        supportingText = {

            if (
                showErrors &&
                !isValidDate(
                    fechaNacimiento
                )
            ) {

                Text(
                    text =
                        "Ingresá una fecha válida",

                    color =
                        MaterialTheme.colorScheme.error
                )
            }
        }
    )


    Spacer(
        modifier = Modifier.height(
            Spacing.sm
        )
    )


    // =========================================================
    // GÉNERO / SEXO
    // =========================================================

    Row(
        modifier = Modifier.fillMaxWidth(),

        horizontalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        Column(
            modifier = Modifier.weight(1f)
        ) {

            var expanded by remember {
                mutableStateOf(false)
            }


            InputDropdownField(

                label = "Género",

                value = genero,

                expanded = expanded,

                onExpandedChange = {
                    expanded = it
                },

                options = listOf(
                    "Masculino",
                    "Femenino",
                    "Otro"
                ),

                onOptionSelected = {
                    onGeneroChange(it)
                },

                isError =
                    showErrors &&
                            genero.isBlank(),

                errorMessage =
                    "Seleccioná un género"
            )
        }


        Column(
            modifier = Modifier.weight(1f)
        ) {

            var expanded by remember {
                mutableStateOf(false)
            }


            InputDropdownField(

                label = "Sexo",

                value = sexo,

                expanded = expanded,

                onExpandedChange = {
                    expanded = it
                },

                options = listOf(
                    "Masculino",
                    "Femenino"
                ),

                onOptionSelected = {
                    onSexoChange(it)
                },

                isError =
                    showErrors &&
                            sexo.isBlank(),

                errorMessage =
                    "Seleccioná un sexo"
            )
        }
    }
}


// =============================================================
// PASO 3
// =============================================================

@Composable
fun Step3Content(
    peso: String,
    onPesoChange: (String) -> Unit,

    altura: String,
    onAlturaChange: (String) -> Unit,

    pesoError: Boolean,
    alturaError: Boolean,

    state: SignUpState
) {

    InputTextField(

        label = "Peso (kg)",

        value = peso,

        onValueChange = onPesoChange,

        leadingIcon =
            Icons.Filled.FitnessCenter,

        keyboardType =
            KeyboardType.Number,

        isError = pesoError,

        errorMessage =
            AppConstants.CAMPO_OBLIGATORIO
    )


    Spacer(
        modifier = Modifier.height(
            Spacing.sm
        )
    )


    InputTextField(

        label = "Altura (cm)",

        value = altura,

        onValueChange = onAlturaChange,

        leadingIcon =
            Icons.Filled.Height,

        keyboardType =
            KeyboardType.Number,

        isError = alturaError,

        errorMessage =
            AppConstants.CAMPO_OBLIGATORIO
    )


    Spacer(
        modifier = Modifier.height(
            Spacing.sm
        )
    )


    InfoBanner(
        text =
            "Estos datos estarán protegidos y nos permitirán brindar una mejor experiencia"
    )


    if (
        state is SignUpState.Error
    ) {

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = state.message,

            color =
                MaterialTheme.colorScheme.error,

            style =
                MaterialTheme.typography.bodyMedium,

            modifier = Modifier.fillMaxWidth()
        )
    }
}


// =============================================================
// FECHA - VISUAL TRANSFORMATION
// =============================================================

class DateVisualTransformation :
    VisualTransformation {

    override fun filter(
        text: androidx.compose.ui.text.AnnotatedString
    ): TransformedText {

        val digits =
            text.text
                .filter {
                    it.isDigit()
                }
                .take(8)


        val formatted =
            buildString {

                digits.forEachIndexed { index, char ->

                    if (
                        index == 2 ||
                        index == 4
                    ) {
                        append("/")
                    }

                    append(char)
                }
            }


        return TransformedText(

            text =
                androidx.compose.ui.text.AnnotatedString(
                    formatted
                ),

            offsetMapping =
                object : OffsetMapping {

                    override fun originalToTransformed(
                        offset: Int
                    ): Int {

                        return when {

                            offset <= 2 ->
                                offset

                            offset <= 4 ->
                                offset + 1

                            offset <= 8 ->
                                offset + 2

                            else ->
                                formatted.length
                        }
                    }


                    override fun transformedToOriginal(
                        offset: Int
                    ): Int {

                        return when {

                            offset <= 2 ->
                                offset

                            offset <= 5 ->
                                offset - 1

                            offset <= 10 ->
                                offset - 2

                            else ->
                                digits.length
                        }
                    }
                }
        )
    }
}


// =============================================================
// VALIDAR FECHA
// =============================================================

fun isValidDate(
    date: String
): Boolean {

    if (
        date.length != 8 ||
        !date.all {
            it.isDigit()
        }
    ) {
        return false
    }


    val day =
        date.substring(
            0,
            2
        ).toIntOrNull()
            ?: return false


    val month =
        date.substring(
            2,
            4
        ).toIntOrNull()
            ?: return false


    val year =
        date.substring(
            4,
            8
        ).toIntOrNull()
            ?: return false


    if (
        month !in 1..12
    ) {
        return false
    }


    if (
        year !in 1900..2100
    ) {
        return false
    }


    val daysInMonth =
        when (month) {

            2 -> {

                if (
                    year % 4 == 0 &&
                    (
                            year % 100 != 0 ||
                                    year % 400 == 0
                            )
                ) {
                    29
                } else {
                    28
                }
            }

            4, 6, 9, 11 ->
                30

            else ->
                31
        }


    return day in 1..daysInMonth
}


// =============================================================
// VALIDAR PASSWORD
// =============================================================

fun isValidPassword(
    password: String
): Boolean {

    val hasMinLength =
        password.length in 8..32

    val hasUppercase =
        password.any {
            it.isUpperCase()
        }

    val hasLowercase =
        password.any {
            it.isLowerCase()
        }

    val hasSpecial =
        password.any {
            !it.isLetterOrDigit()
        }


    return hasMinLength &&
            hasUppercase &&
            hasLowercase &&
            hasSpecial
}


// =============================================================
// VALIDAR EMAIL
// =============================================================

fun isValidEmail(
    email: String
): Boolean {

    val regex =
        Regex(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        )

    return regex.matches(email)
}


// =============================================================
// PASSWORD REQUIREMENT
// =============================================================

@Composable
fun PasswordRequirement(
    text: String,
    met: Boolean
) {

    val color =
        if (met) {
            Color(0xFF5BB8D4)
        } else {
            Color(0xFFB0BEC5)
        }


    Row(
        verticalAlignment =
            Alignment.CenterVertically,

        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 2.dp
            )
    ) {

        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )


        Spacer(
            modifier = Modifier.width(8.dp)
        )


        Text(
            text = text,

            style =
                MaterialTheme.typography.bodyMedium,

            color = color
        )
    }
}
