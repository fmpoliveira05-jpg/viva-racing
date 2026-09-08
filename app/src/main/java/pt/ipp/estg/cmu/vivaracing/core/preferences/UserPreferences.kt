package pt.ipp.estg.cmu.vivaracing.core.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Modo de tema escolhido pelo utilizador. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AUTO_LIGHT_SENSOR }

/** Fotografia imutável do estado das preferências da aplicação. */
data class AppPreferences(
    val language: String = "system",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val anonymousResults: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val notifyOnlySubscribed: Boolean = true,
    val notifyOnlyFriends: Boolean = false,
    val notifyAthletePositions: Boolean = true,
    val notifyFriendActivity: Boolean = true,
    val powerSavingEnabled: Boolean = false,
    val highAccuracyTracking: Boolean = true
) {
    companion object {
        const val LANGUAGE_SYSTEM = "system"
    }
}

/**
 * Gestão das preferências da aplicação com recurso a SharedPreferences, tal
 * como abordado nas aulas.
 *
 * As alterações são publicadas através de um [Flow] construído sobre o
 * `OnSharedPreferenceChangeListener`, o que permite que a interface em Jetpack
 * Compose reaja imediatamente a qualquer mudança de definição.
 */
class UserPreferences(context: Context) {

    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun observe(): Flow<AppPreferences> = callbackFlow {
        trySend(snapshot())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(snapshot())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun snapshot(): AppPreferences = AppPreferences(
        language = preferences.getString(KEY_LANGUAGE, AppPreferences.LANGUAGE_SYSTEM)
            ?: AppPreferences.LANGUAGE_SYSTEM,
        themeMode = runCatching {
            ThemeMode.valueOf(preferences.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM),
        anonymousResults = preferences.getBoolean(KEY_ANONYMOUS, false),
        notificationsEnabled = preferences.getBoolean(KEY_NOTIFICATIONS, true),
        notifyOnlySubscribed = preferences.getBoolean(KEY_ONLY_SUBSCRIBED, true),
        notifyOnlyFriends = preferences.getBoolean(KEY_ONLY_FRIENDS, false),
        notifyAthletePositions = preferences.getBoolean(KEY_ATHLETE_POSITIONS, true),
        notifyFriendActivity = preferences.getBoolean(KEY_FRIEND_ACTIVITY, true),
        powerSavingEnabled = preferences.getBoolean(KEY_POWER_SAVING, false),
        highAccuracyTracking = preferences.getBoolean(KEY_HIGH_ACCURACY, true)
    )

    fun setLanguage(language: String) = edit { putString(KEY_LANGUAGE, language) }

    fun setThemeMode(mode: ThemeMode) = edit { putString(KEY_THEME, mode.name) }

    fun setAnonymousResults(enabled: Boolean) = edit { putBoolean(KEY_ANONYMOUS, enabled) }

    fun setNotificationsEnabled(enabled: Boolean) = edit { putBoolean(KEY_NOTIFICATIONS, enabled) }

    fun setNotifyOnlySubscribed(enabled: Boolean) = edit { putBoolean(KEY_ONLY_SUBSCRIBED, enabled) }

    /** Restringe todas as notificações da comunidade a atividade de amigos. */
    fun setNotifyOnlyFriends(enabled: Boolean) = edit { putBoolean(KEY_ONLY_FRIENDS, enabled) }

    /** Avisos de passagem dos atletas subscritos. */
    fun setNotifyAthletePositions(enabled: Boolean) =
        edit { putBoolean(KEY_ATHLETE_POSITIONS, enabled) }

    /** Avisos de novas provas e participações publicadas por amigos. */
    fun setNotifyFriendActivity(enabled: Boolean) =
        edit { putBoolean(KEY_FRIEND_ACTIVITY, enabled) }

    fun setHighAccuracyTracking(enabled: Boolean) = edit { putBoolean(KEY_HIGH_ACCURACY, enabled) }

    /**
     * Ativada automaticamente pelo recetor de eventos do sistema quando a
     * bateria fica fraca ou o modo de poupança de energia é ligado.
     */
    fun setPowerSavingEnabled(enabled: Boolean) = edit { putBoolean(KEY_POWER_SAVING, enabled) }

    var lastAlertCheck: Long
        get() = preferences.getLong(KEY_LAST_ALERT_CHECK, 0L)
        set(value) = edit { putLong(KEY_LAST_ALERT_CHECK, value) }

    var activeTrackingSession: String?
        get() = preferences.getString(KEY_ACTIVE_SESSION, null)
        set(value) = edit { putString(KEY_ACTIVE_SESSION, value) }

    private inline fun edit(block: SharedPreferences.Editor.() -> Unit) {
        val editor = preferences.edit()
        editor.block()
        editor.apply()
    }

    companion object {
        const val KEY_LANGUAGE = "pref_language"
        const val KEY_THEME = "pref_theme_mode"
        const val KEY_ANONYMOUS = "pref_anonymous_results"
        const val KEY_NOTIFICATIONS = "pref_notifications_enabled"
        const val KEY_ONLY_SUBSCRIBED = "pref_notify_only_subscribed"
        const val KEY_ONLY_FRIENDS = "pref_notify_only_friends"
        const val KEY_ATHLETE_POSITIONS = "pref_notify_athlete_positions"
        const val KEY_FRIEND_ACTIVITY = "pref_notify_friend_activity"
        const val KEY_POWER_SAVING = "pref_power_saving"
        const val KEY_HIGH_ACCURACY = "pref_high_accuracy_tracking"
        const val KEY_LAST_ALERT_CHECK = "pref_last_alert_check"
        const val KEY_ACTIVE_SESSION = "pref_active_tracking_session"
    }
}
