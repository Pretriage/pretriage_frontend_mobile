package com.proyecto_final.triage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cafe.adriel.voyager.core.annotation.ExperimentalVoyagerApi
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.jetpack.ProvideNavigatorLifecycleKMPSupport
import com.proyecto_final.triage.screens.SignInScreen
import com.proyecto_final.triage.screens.SplashScreen
import com.proyecto_final.triage.storage.TokenStorageProvider
import com.proyecto_final.triage.theme.AppTheme
import com.proyecto_final.triage.viewmodels.AuthState
import com.proyecto_final.triage.viewmodels.LocalStudiesViewModel
import com.proyecto_final.triage.viewmodels.StudiesViewModel

@OptIn(ExperimentalVoyagerApi::class)
@Composable
@Preview
fun App() {
    val studiesViewModel = remember { StudiesViewModel() }
    val sessionExpired by AuthState.sessionExpired.collectAsState()

    AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                CompositionLocalProvider(LocalStudiesViewModel provides studiesViewModel) {
                    ProvideNavigatorLifecycleKMPSupport {
                        Navigator(SplashScreen())
                    }
                }

                if (sessionExpired) {
                    LaunchedEffect(Unit) {
                        AuthState.reset()
                        TokenStorageProvider.instance.clearTokens()
                        com.proyecto_final.triage.notificaciones.SeguimientoConsulta.detener()
                    }
                    ProvideNavigatorLifecycleKMPSupport {
                        Navigator(SignInScreen())
                    }
                }
            }
        }
    }
}
