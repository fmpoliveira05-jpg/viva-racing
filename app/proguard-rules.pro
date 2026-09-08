# Regras de ofuscacao especificas da aplicacao Viva Racing.

# Modelos convertidos por Gson/Firestore atraves de reflexao
-keep class pt.ipp.estg.cmu.vivaracing.data.remote.dto.** { *; }
-keep class pt.ipp.estg.cmu.vivaracing.data.model.** { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*
