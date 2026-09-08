package pt.ipp.estg.cmu.vivaracing.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

/** Contacto selecionado pelo utilizador na agenda do dispositivo. */
data class ContactInfo(val displayName: String, val phoneNumber: String)

/**
 * Integração com aplicações nativas do Android.
 *
 * Todas as ações são concretizadas com `Intent` implícitos, o que permite ao
 * utilizador escolher a aplicação preferida (marcador, mensagens, partilha) e
 * evita pedir permissões desnecessárias: apenas a leitura da agenda exige
 * autorização explícita.
 */
object AndroidInteractions {

    /** Abre o marcador telefónico com o número preenchido (não efetua a chamada). */
    fun dial(context: Context, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phoneNumber.trim()}"))
        context.startActivity(intent)
    }

    /** Abre a aplicação de mensagens com o destinatário e o texto preenchidos. */
    fun sendSms(context: Context, phoneNumber: String, message: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phoneNumber.trim()}")).apply {
            putExtra("sms_body", message)
        }
        context.startActivity(intent)
    }

    /** Partilha texto através de qualquer aplicação instalada. */
    fun share(context: Context, subject: String, message: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    }

    /** Abre a localização numa aplicação de mapas instalada no dispositivo. */
    fun openInMaps(context: Context, latitude: Double, longitude: Double, label: String) {
        val encodedLabel = Uri.encode(label)
        val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
    }

    /**
     * Lê o nome e o número de telefone do contacto devolvido pelo seletor da
     * agenda. Requer a permissão `READ_CONTACTS`.
     */
    fun readContact(context: Context, contactUri: Uri): ContactInfo? {
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )

        context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null

            val contactId = cursor.getString(0)
            val displayName = cursor.getString(1).orEmpty()
            val hasPhone = cursor.getInt(2) > 0
            if (!hasPhone) return ContactInfo(displayName, "")

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { phoneCursor ->
                if (phoneCursor.moveToFirst()) {
                    return ContactInfo(displayName, phoneCursor.getString(0).orEmpty())
                }
            }
            return ContactInfo(displayName, "")
        }
        return null
    }
}
