package pt.ipp.estg.cmu.vivaracing.ui.screens.info

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import pt.ipp.estg.cmu.vivaracing.R
import java.util.Locale

/**
 * Ecrã de informação e regulamento.
 *
 * O conteúdo é apresentado num `WebView` que carrega um documento HTML
 * incluído nos recursos da aplicação (pasta `assets`). Esta abordagem permite
 * apresentar conteúdo formatado e traduzido sem exigir ligação à Internet, e
 * corresponde ao componente WebView abordado nas aulas.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AboutScreen() {
    val language = remember { Locale.getDefault().language }
    val assetFile = when (language) {
        "pt" -> "help_pt.html"
        "es" -> "help_es.html"
        else -> "help_en.html"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.about_header),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = false
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    loadUrl("file:///android_asset/$assetFile")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
