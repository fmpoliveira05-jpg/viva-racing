package pt.ipp.estg.cmu.vivaracing.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import pt.ipp.estg.cmu.vivaracing.core.Constants

/**
 * Camada fina sobre o cliente de localização fundida (fused) dos Google Play
 * Services.
 *
 * Expõe as atualizações de localização como um [Flow], de modo a que a
 * subscrição seja automaticamente cancelada quando o consumidor (ViewModel ou
 * serviço) termina o seu ciclo de vida.
 */
class LocationProvider(private val context: Context) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun lastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        return runCatching { client.lastLocation.await() }.getOrNull()
    }

    /**
     * @param intervalMillis periodicidade pedida ao sistema. O serviço reduz
     * este valor quando o dispositivo entra em poupança de energia.
     * @param highAccuracy quando falso é usada a prioridade equilibrada, que
     * privilegia a rede em detrimento do GPS e consome menos bateria.
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(
        intervalMillis: Long = Constants.LOCATION_INTERVAL_NORMAL,
        highAccuracy: Boolean = true
    ): Flow<Location> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }

        val priority = if (highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val request = LocationRequest.Builder(priority, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .setMinUpdateDistanceMeters(Constants.LOCATION_MIN_DISTANCE_METERS)
            .setWaitForAccurateLocation(highAccuracy)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
