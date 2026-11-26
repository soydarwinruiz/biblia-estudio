import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Firma: las contraseñas ya NO están en el código. Se leen de keystore.properties (no se sube a git)
// o de variables de entorno CODICE_STORE_PASSWORD / CODICE_KEY_PASSWORD. Ver keystore.properties.example.
val signProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(prop: String, env: String): String? = signProps.getProperty(prop) ?: System.getenv(env)
val storePass = secret("storePassword", "CODICE_STORE_PASSWORD")
val keyPass = secret("keyPassword", "CODICE_KEY_PASSWORD") ?: storePass
val storePath = secret("storeFile", "CODICE_STORE_FILE") ?: "keystore/biblia-release.jks"
val canSign = storePass != null && rootProject.file(storePath).exists()

android {
    namespace = "com.biblia.estudio"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.biblia.estudio"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.6"
    }
    signingConfigs {
        // Clave propia y estable: permite instalar actualizaciones sin desinstalar.
        if (canSign) create("release") {
            storeFile = rootProject.file(storePath)
            storePassword = storePass
            keyAlias = secret("keyAlias", "CODICE_KEY_ALIAS") ?: "biblia"
            keyPassword = keyPass
        }
    }
    buildTypes {
        // Sin la clave, la versión de depuración usa la clave de depuración estándar (no se puede instalar encima de la publicada).
        if (canSign) debug { signingConfig = signingConfigs.getByName("release") }
        release {
            // Sin R8: el código probado es exactamente el que se instala.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSign) signingConfig = signingConfigs.getByName("release")
            else logger.warn("⚠ Sin keystore.properties: el APK de release saldrá SIN FIRMAR. Copia keystore.properties.example.")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    androidResources { noCompress += listOf("bin") }
    // Esquemas de Room en los assets de depuración (no en release) para probar migraciones con datos reales
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("roborazzi.test.record", "true")
            it.maxHeapSize = "3g"
        }
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.39.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.39.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
