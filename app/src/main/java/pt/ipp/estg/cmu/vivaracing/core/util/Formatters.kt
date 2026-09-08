package pt.ipp.estg.cmu.vivaracing.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Formatação de datas, durações e distâncias sensível ao idioma ativo. */
object Formatters {

    fun formatDateTime(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        if (timestamp <= 0L) return "--"
        return SimpleDateFormat("dd/MM/yyyy HH:mm", locale).format(Date(timestamp))
    }

    fun formatDate(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        if (timestamp <= 0L) return "--"
        return SimpleDateFormat("dd/MM/yyyy", locale).format(Date(timestamp))
    }

    fun formatTime(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        if (timestamp <= 0L) return "--"
        return SimpleDateFormat("HH:mm", locale).format(Date(timestamp))
    }

    /** Converte segundos em hh:mm:ss. */
    fun formatDuration(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0L)
        val hours = TimeUnit.SECONDS.toHours(safeSeconds)
        val minutes = TimeUnit.SECONDS.toMinutes(safeSeconds) % 60
        val seconds = safeSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    /** Apresenta metros em km com uma casa decimal, ou em metros se for curto. */
    fun formatDistance(meters: Double): String = if (meters < 1_000) {
        String.format(Locale.getDefault(), "%.0f m", meters)
    } else {
        String.format(Locale.getDefault(), "%.2f km", meters / 1_000)
    }

    fun formatSpeed(kilometersPerHour: Double): String =
        String.format(Locale.getDefault(), "%.1f km/h", kilometersPerHour)

    /** Ritmo em minutos por quilómetro, indicador habitual em corrida. */
    fun formatPace(distanceMeters: Double, durationSeconds: Long): String {
        if (distanceMeters < 1.0 || durationSeconds <= 0L) return "--"
        val minutesPerKm = (durationSeconds / 60.0) / (distanceMeters / 1_000.0)
        val minutes = minutesPerKm.toInt()
        val seconds = ((minutesPerKm - minutes) * 60).toInt()
        return String.format(Locale.US, "%d:%02d /km", minutes, seconds)
    }

    fun formatCoordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
}
