package pt.ipp.estg.cmu.vivaracing.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp

/**
 * Reenvia para o Firestore os registos criados sem ligação à rede.
 *
 * Este trabalho complementa a fila interna do Firestore: assegura que, mesmo
 * após o processo da aplicação ter sido terminado pelo sistema, os registos
 * marcados como pendentes na base de dados local acabam por ser publicados.
 */
class PendingUploadWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as VivaRacingApp).container
        if (container.authService.currentUid == null) return Result.success()

        return runCatching {
            container.raceRepository.pushPendingRaces()
            Result.success()
        }.getOrElse {
            Log.w(TAG, "Reenvio de registos pendentes falhou", it)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "PendingUploadWorker"
    }
}
