package pt.ipp.estg.cmu.vivaracing.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import pt.ipp.estg.cmu.vivaracing.service.SensorMonitorService
import pt.ipp.estg.cmu.vivaracing.service.SensorReadings

/**
 * Liga a interface ao serviço *bound* de sensores.
 *
 * Segue o padrão recomendado nas aulas para efeitos colaterais em Jetpack
 * Compose: a ligação é estabelecida dentro de um `DisposableEffect` e o
 * `onDispose` garante que o `unbindService` é executado quando o componente
 * sai da composição, evitando fugas de memória e consumo desnecessário.
 */
@Composable
fun rememberSensorReadings(): State<SensorReadings> {
    val context = LocalContext.current
    val readings = remember { mutableStateOf(SensorReadings()) }
    var boundService by remember { mutableStateOf<SensorMonitorService?>(null) }

    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                boundService = (binder as? SensorMonitorService.LocalBinder)?.service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
            }
        }

        val intent = Intent(context, SensorMonitorService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            boundService = null
            runCatching { context.unbindService(connection) }
        }
    }

    LaunchedEffect(boundService) {
        boundService?.readings?.collect { value -> readings.value = value }
    }

    return readings
}
