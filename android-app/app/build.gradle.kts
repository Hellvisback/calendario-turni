plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.turni.calendario"
    compileSdk = 36

    defaultConfig {
        applicationId = if(project.hasProperty("setupProbe")) "com.turni.calendario.setupqa" else "com.turni.calendario"
        minSdk = 26
        targetSdk = 36
        versionCode = 70
        versionName = "1.8.0-beta.25"

        testInstrumentationRunner = if(project.hasProperty("integrityProbe"))
            "com.turni.calendario.DatabaseIntegrityInstrumentation" else
            "com.turni.calendario.WidgetRenderingInstrumentation"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // Machine-private signing material can be supplied by a remote environment.
    val externalKeyStore = providers.environmentVariable("TURNISYNC_KEYSTORE_PATH").orNull
    if (!externalKeyStore.isNullOrBlank()) {
        fun signingSecret(name: String): String = providers.environmentVariable(name).orNull
            ?.takeIf { it.isNotBlank() } ?: error("Missing signing environment variable: $name")
        signingConfigs.create("externalBeta") {
            storeFile = file(externalKeyStore).also { require(it.isFile) { "Signing keystore file is unavailable" } }
            storePassword = signingSecret("TURNISYNC_KEYSTORE_PASSWORD")
            keyAlias = signingSecret("TURNISYNC_KEYSTORE_ALIAS")
            keyPassword = signingSecret("TURNISYNC_KEYSTORE_KEY_PASSWORD")
        }
    }
    // Installable beta: same certificate as existing device builds, without debug overhead.
    buildTypes.create("beta") {
        initWith(buildTypes.getByName("release"))
        signingConfig = signingConfigs.findByName("externalBeta") ?: signingConfigs.getByName("debug")
        isDebuggable = false
        isMinifyEnabled = true
        isShrinkResources = true
        matchingFallbacks += listOf("release")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.navigation:navigation-compose:2.5.1")

    debugImplementation("androidx.compose.ui:ui-tooling:1.7.8")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.8")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
