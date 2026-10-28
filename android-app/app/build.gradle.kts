plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.turni.calendario"
    compileSdk = 32

    defaultConfig {
        applicationId = "com.turni.calendario"
        minSdk = 26
        targetSdk = 32
        versionCode = 44
        versionName = "1.7.2"

        testInstrumentationRunner = "com.turni.calendario.WidgetRenderingInstrumentation"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.2.0"
        useLiveLiterals = false
    }
    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.1")
    implementation("androidx.activity:activity-compose:1.5.1")
    implementation("androidx.compose.ui:ui:1.2.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.2.0")
    implementation("androidx.compose.material3:material3:1.0.0-alpha14")
    implementation("androidx.compose.material:material-icons-extended:1.2.0")
    implementation("androidx.navigation:navigation-compose:2.5.1")

    debugImplementation("androidx.compose.ui:ui-tooling:1.2.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.2.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
