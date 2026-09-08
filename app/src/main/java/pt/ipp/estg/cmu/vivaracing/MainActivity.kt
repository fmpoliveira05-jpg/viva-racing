package pt.ipp.estg.cmu.vivaracing

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import pt.ipp.estg.cmu.vivaracing.core.Constants
import pt.ipp.estg.cmu.vivaracing.ui.components.rememberSensorReadings
import pt.ipp.estg.cmu.vivaracing.ui.navigation.VivaRacingRoot
import pt.ipp.estg.cmu.vivaracing.ui.theme.VivaRacingTheme

/**
 * Única atividade da aplicação.
 *
 * Estende `AppCompatActivity` porque a troca de idioma recorre a API de
 * idiomas por aplicação do AppCompat. Toda a interface é construída em Jetpack
 * Compose a partir do método `setContent`, e a navegação entre ecrãs é gerida
 * pelo componente Navigation dentro de [VivaRacingRoot].
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val deepLinkRoute = intent?.getStringExtra(Constants.EXTRA_DEEP_LINK_ROUTE)

        setContent {
            VivaRacingContent(deepLinkRoute = deepLinkRoute)
        }
    }
}

@Composable
private fun VivaRacingContent(deepLinkRoute: String?) {
    val context = LocalContext.current
    val application = context.applicationContext as VivaRacingApp
    val preferences = application.container.preferences

    val settings by preferences.observe()
        .collectAsState(initial = remember { preferences.snapshot() })

    val sensorReadings by rememberSensorReadings()

    RequestNotificationPermission()

    VivaRacingTheme(
        themeMode = settings.themeMode,
        lowLightDetected = sensorReadings.isLowLight
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            VivaRacingRoot(
                container = application.container,
                deepLinkRoute = deepLinkRoute
            )
        }
    }
}

/**
 * A partir do Android 13 a apresentação de notificações exige autorização
 * explícita do utilizador. O pedido é feito uma única vez, no arranque.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val alreadyRequested = remember { mutableStateOf(false) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (!alreadyRequested.value) {
            alreadyRequested.value = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
