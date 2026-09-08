package pt.ipp.estg.cmu.vivaracing.data.repository

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import pt.ipp.estg.cmu.vivaracing.BuildConfig
import pt.ipp.estg.cmu.vivaracing.data.remote.api.SupabaseStorageApi
import java.util.UUID

/**
 * Envio de imagens para o Supabase Storage através da respetiva API REST.
 *
 * O ficheiro é lido do `ContentResolver` (fotografia captada com a CameraX ou
 * imagem escolhida no seletor do sistema) e enviado como corpo binário. O URL
 * público devolvido é guardado no documento Firestore correspondente.
 */
class StorageRepository(
    private val context: Context,
    private val storageApi: SupabaseStorageApi
) {

    suspend fun uploadImage(uri: Uri, folder: String): Result<String> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Nao foi possivel ler a imagem selecionada")

        val mimeType = context.contentResolver.getType(uri) ?: DEFAULT_MIME_TYPE
        val extension = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            else -> "jpg"
        }
        val objectPath = "$folder/${UUID.randomUUID()}.$extension"

        val response = storageApi.uploadObject(
            bucket = BuildConfig.SUPABASE_BUCKET,
            path = objectPath,
            authorization = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        )

        if (!response.isSuccessful) {
            error("Supabase devolveu HTTP ${response.code()}: ${response.errorBody()?.string()}")
        }

        publicUrl(objectPath)
    }

    private fun publicUrl(objectPath: String): String {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        return "$base/storage/v1/object/public/${BuildConfig.SUPABASE_BUCKET}/$objectPath"
    }

    private companion object {
        const val DEFAULT_MIME_TYPE = "image/jpeg"
    }
}
