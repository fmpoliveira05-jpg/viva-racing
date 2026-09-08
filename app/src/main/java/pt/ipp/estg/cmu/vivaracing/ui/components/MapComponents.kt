package pt.ipp.estg.cmu.vivaracing.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint

/** Descreve um marcador a colocar no mapa. */
data class MapMarker(
    val id: String,
    val position: GeoPoint,
    val title: String,
    val snippet: String = "",
    val hue: Float = BitmapDescriptorFactory.HUE_RED
)

/**
 * Mapa reutilizável construído sobre a biblioteca `maps-compose`.
 *
 * Este componente é usado por todos os ecrãs de mapa da aplicação (provas,
 * alertas, participações e gravação de percurso), garantindo um comportamento
 * uniforme e evitando a duplicação da configuração do Google Maps.
 *
 * @param markers pontos a assinalar no mapa.
 * @param route linha do percurso a desenhar, opcional.
 * @param onMarkerClick devolve o identificador do marcador selecionado.
 */
@Composable
fun VivaRacingMap(
    markers: List<MapMarker>,
    modifier: Modifier = Modifier,
    route: List<GeoPoint> = emptyList(),
    routeColor: Color = Color(0xFFB4380B),
    initialCenter: GeoPoint? = null,
    initialZoom: Float = 12f,
    showMyLocation: Boolean = false,
    onMarkerClick: (String) -> Unit = {},
    onMapClick: (GeoPoint) -> Unit = {}
) {
    val center = remember(initialCenter, markers, route) {
        initialCenter
            ?: markers.firstOrNull()?.position
            ?: route.firstOrNull()
            ?: DEFAULT_CENTER
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(center.latitude, center.longitude),
            initialZoom
        )
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = showMyLocation
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true,
            myLocationButtonEnabled = showMyLocation,
            mapToolbarEnabled = false
        ),
        onMapClick = { latLng -> onMapClick(GeoPoint(latLng.latitude, latLng.longitude)) }
    ) {
        if (route.size >= 2) {
            Polyline(
                points = route.map { LatLng(it.latitude, it.longitude) },
                color = routeColor,
                width = 10f
            )
        }

        markers.forEach { marker ->
            val markerState = rememberMarkerState(
                key = marker.id,
                position = LatLng(marker.position.latitude, marker.position.longitude)
            )
            Marker(
                state = markerState,
                title = marker.title,
                snippet = marker.snippet,
                icon = BitmapDescriptorFactory.defaultMarker(marker.hue),
                onClick = {
                    onMarkerClick(marker.id)
                    false
                }
            )
        }
    }
}

/** Centro por omissão: Felgueiras, onde se localiza a ESTG. */
private val DEFAULT_CENTER = GeoPoint(41.3706, -8.1926)

internal val MapCornerRadius = 16.dp

@Suppress("unused")
private fun Color.toMapArgb(): Int = this.toArgb()
