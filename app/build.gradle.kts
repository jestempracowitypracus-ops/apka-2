import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appId = "pl.pomocdrogowa.zlecenia"

// Konfiguracja Firebase jest czytana z app/google-services.json (jeśli plik istnieje).
// Dzięki temu aplikacja zawsze się buduje - bez pliku pokaże ekran z instrukcją.
data class FirebaseCfg(
    val appId: String,
    val apiKey: String,
    val projectId: String,
    val senderId: String,
    val storageBucket: String,
)

fun readFirebaseConfig(): FirebaseCfg? {
    val file = project.file("google-services.json")
    if (!file.exists()) return null
    @Suppress("UNCHECKED_CAST")
    val json = JsonSlurper().parse(file) as Map<String, Any?>
    val info = json["project_info"] as Map<String, Any?>
    val clients = json["client"] as List<Map<String, Any?>>
    val client = clients.firstOrNull {
        val ci = it["client_info"] as Map<String, Any?>
        val aci = ci["android_client_info"] as Map<String, Any?>
        aci["package_name"] == appId
    } ?: error("google-services.json nie zawiera aplikacji o pakiecie $appId")
    val clientInfo = client["client_info"] as Map<String, Any?>
    val apiKeys = client["api_key"] as List<Map<String, Any?>>
    return FirebaseCfg(
        appId = clientInfo["mobilesdk_app_id"] as String,
        apiKey = apiKeys.first()["current_key"] as String,
        projectId = info["project_id"] as String,
        senderId = info["project_number"] as String,
        storageBucket = (info["storage_bucket"] as String?) ?: "",
    )
}

val firebaseCfg = readFirebaseConfig()

android {
    namespace = appId
    compileSdk = 35

    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")

        buildConfigField("boolean", "FIREBASE_CONFIGURED", (firebaseCfg != null).toString())
        if (firebaseCfg != null) {
            // Te same zasoby, które generuje wtyczka google-services.
            resValue("string", "google_app_id", firebaseCfg.appId)
            resValue("string", "google_api_key", firebaseCfg.apiKey)
            resValue("string", "project_id", firebaseCfg.projectId)
            resValue("string", "gcm_defaultSenderId", firebaseCfg.senderId)
            resValue("string", "google_storage_bucket", firebaseCfg.storageBucket)
        }
    }

    signingConfigs {
        // Wspólny klucz, żeby każdą nową wersję dało się zainstalować "na" poprzednią.
        create("shared") {
            storeFile = file("signing.keystore")
            storePassword = "pomocdrogowa"
            keyAlias = "zlecenia"
            keyPassword = "pomocdrogowa"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
}
