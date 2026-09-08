package pt.ipp.estg.cmu.vivaracing.ui.screens.camera

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import pt.ipp.estg.cmu.vivaracing.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

/**
 * Captura de fotografias com a API CameraX (elemento de bonificação).
 *
 * A integração é feita com [LifecycleCameraController], a API de alto nível da
 * CameraX. Comparada com a utilização direta do `ProcessCameraProvider`, tem
 * duas vantagens neste projeto:
 *
 *  - dispensa manipular o `ListenableFuture` do Guava, que não está exposto no
 *    classpath de compilação quando outras bibliotecas (Firebase) trazem o
 *    Guava apenas em tempo de execução;
 *  - o controlador liga-se e desliga-se do ciclo de vida com uma única
 *    chamada, o que reduz o risco de manter a câmara aberta indevidamente.
 *
 * O `PreviewView` da CameraX é embebido em Jetpack Compose através de
 * `AndroidView`, e o `DisposableEffect` garante a desassociação explícita
 * quando o componente sai da composição.
 */
@SuppressLint("MissingPermission")
@Composable
fun CameraCaptureOverlay(
    onImageCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor: Executor = remember(context) { ContextCompat.getMainExecutor(context) }

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }

    // Liga a câmara ao ciclo de vida do ecrã e liberta-a ao sair.
    DisposableEffect(lifecycleOwner, cameraController) {
        runCatching { cameraController.bindToLifecycle(lifecycleOwner) }
        onDispose { cameraController.unbind() }
    }

    // A troca de câmara é feita apenas pela alteração do seletor: o
    // controlador reconfigura-se sozinho, sem nova ligação ao ciclo de vida.
    LaunchedEffect(lensFacing) {
        runCatching {
            cameraController.cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    controller = cameraController
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FloatingActionButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_cancel)
                )
            }

            FloatingActionButton(onClick = {
                capturePhoto(context, cameraController, executor, onImageCaptured)
            }) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = stringResource(R.string.action_take_photo)
                )
            }

            FloatingActionButton(onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            }) {
                Icon(
                    imageVector = Icons.Filled.Cameraswitch,
                    contentDescription = stringResource(R.string.action_switch_camera)
                )
            }
        }
    }
}

/**
 * Grava a fotografia na cache da aplicação e devolve um URI partilhável,
 * obtido através do FileProvider declarado no manifesto.
 */
private fun capturePhoto(
    context: Context,
    cameraController: LifecycleCameraController,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit
) {
    val photosDirectory = File(context.cacheDir, "photos").apply { mkdirs() }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val photoFile = File(photosDirectory, "VIVA_$timestamp.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    cameraController.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                onImageCaptured(uri)
            }

            override fun onError(exception: ImageCaptureException) {
                // A captura falhou: o utilizador pode repetir ou optar por
                // escolher uma imagem existente na galeria.
            }
        }
    )
}
