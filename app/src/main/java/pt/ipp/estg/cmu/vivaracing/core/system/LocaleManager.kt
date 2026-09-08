package pt.ipp.estg.cmu.vivaracing.core.system

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import pt.ipp.estg.cmu.vivaracing.core.preferences.AppPreferences

/**
 * Gestão do idioma da aplicação.
 *
 * É utilizada a API de idioma por aplicação do AppCompat
 * ([AppCompatDelegate.setApplicationLocales]). Face a alteração manual da
 * `Configuration` seguida de `recreate()`, esta abordagem tem três vantagens:
 * a preferência é persistida pelo sistema, o idioma passa a ser visível nas
 * definições do Android e a recomposição dos ecrãs Compose é feita
 * automaticamente.
 */
object LocaleManager {

    val supportedLanguages = listOf(
        AppPreferences.LANGUAGE_SYSTEM to "language_system",
        "pt" to "language_portuguese",
        "en" to "language_english",
        "es" to "language_spanish"
    )

    fun apply(languageTag: String) {
        val locales = if (languageTag == AppPreferences.LANGUAGE_SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun currentLanguageTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) {
            AppPreferences.LANGUAGE_SYSTEM
        } else {
            locales[0]?.language ?: AppPreferences.LANGUAGE_SYSTEM
        }
    }
}
