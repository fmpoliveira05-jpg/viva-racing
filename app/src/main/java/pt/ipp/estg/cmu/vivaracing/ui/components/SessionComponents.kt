package pt.ipp.estg.cmu.vivaracing.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.data.repository.SessionState

/**
 * Estado de sessão disponível em toda a árvore de composição.
 *
 * Evita propagar manualmente a informação de que a sessão é anónima por
 * dezenas de assinaturas de funções; cada ecrã consulta o valor apenas onde
 * precisa de bloquear uma ação.
 */
val LocalSession = compositionLocalOf { SessionState() }

/**
 * Devolve a ação que termina a sessão de convidado e devolve o utilizador ao
 * ecrã de autenticação, onde pode criar conta.
 */
@Composable
fun rememberGuestExitAction(): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = (context.applicationContext as VivaRacingApp).container
    return {
        scope.launch { container.authRepository.signOut() }
        Unit
    }
}

/**
 * Aviso apresentado a um convidado quando tenta acionar uma funcionalidade
 * reservada a utilizadores registados.
 *
 * O modo convidado permite consultar as provas públicas da comunidade, mas
 * não criar conteúdo, subscrever provas ou atletas, nem estabelecer amizades.
 */
@Composable
fun GuestNotice(
    modifier: Modifier = Modifier,
    onCreateAccount: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.guest_notice_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.guest_notice_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onCreateAccount) {
                Text(stringResource(R.string.action_create_account))
            }
        }
    }
}

/** Faixa permanente que identifica a sessão de convidado. */
@Composable
fun GuestBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.guest_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
