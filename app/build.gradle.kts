import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// ---------------------------------------------------------------------------
// Leitura das chaves privadas a partir de local.properties.
// O ficheiro local.properties nunca e versionado, pelo que as chaves nao sao
// expostas no repositorio GitLab. Caso a chave nao exista e usado um valor de
// substituicao para que o projeto continue a compilar.
// ---------------------------------------------------------------------------
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun secret(key: String, fallback: String): String =
    localProperties.getProperty(key) ?: System.getenv(key) ?: fallback

android {
    namespace = "pt.ipp.estg.cmu.vivaracing"
    compileSdk = 35

    defaultConfig {
        applicationId = "pt.ipp.estg.cmu.vivaracing"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // A chave do Google Maps e injetada no AndroidManifest.xml
        manifestPlaceholders["MAPS_API_KEY"] = secret("MAPS_API_KEY", "MISSING_MAPS_API_KEY")

        // As restantes chaves ficam disponiveis atraves da classe BuildConfig
        buildConfigField("String", "WEATHER_API_KEY", "\"${secret("WEATHER_API_KEY", "MISSING_WEATHER_API_KEY")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL", "https://missing.supabase.co")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY", "MISSING_SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "SUPABASE_BUCKET", "\"${secret("SUPABASE_BUCKET", "viva-racing")}\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Exporta o esquema da base de dados Room para permitir migracoes verificaveis
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Base AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose (Material 3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Persistencia local (cache offline)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Trabalho periodico em segundo plano
    implementation(libs.androidx.work.runtime.ktx)

    // Rede / APIs REST
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)

    // Firebase (base de dados online e autenticacao)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)

    // Localizacao e mapas
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    // Imagens e camara
    implementation(libs.coil.compose)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Pedido de permissoes em Compose
    implementation(libs.accompanist.permissions)

    // Necessaria em tempo de compilacao para as APIs assincronas baseadas em
    // ListenableFuture (CameraX e WorkManager)
    implementation(libs.guava)

    // Testes
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
