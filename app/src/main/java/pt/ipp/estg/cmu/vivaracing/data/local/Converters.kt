package pt.ipp.estg.cmu.vivaracing.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint

/**
 * Conversores de tipos do Room. A lista de pontos de um percurso é guardada
 * como texto JSON numa única coluna, evitando uma tabela de relação adicional
 * para dados que são sempre lidos em bloco.
 */
class Converters {

    @TypeConverter
    fun fromGeoPointList(value: List<GeoPoint>?): String = gson.toJson(value ?: emptyList<GeoPoint>())

    /**
     * As listas de marcadores de visibilidade são curtas e sem estrutura
     * interna, pelo que são guardadas como texto simples separado pelo
     * caráter de unidade (U+001F), mais compacto do que JSON.
     */
    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        (value ?: emptyList()).joinToString(SEPARATOR)

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList() else value.split(SEPARATOR)

    @TypeConverter
    fun toGeoPointList(value: String?): List<GeoPoint> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { gson.fromJson<List<GeoPoint>>(value, listType) }.getOrNull() ?: emptyList()
    }

    private companion object {
        const val SEPARATOR = "\u001F"
        val gson = Gson()
        val listType = object : TypeToken<List<GeoPoint>>() {}.type
    }
}
