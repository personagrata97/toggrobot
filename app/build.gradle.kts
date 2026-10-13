plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.toggrobot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.toggrobot"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }

    // Sabit imza anahtari: yeni surumler eskisinin uzerine guncelleme olarak kurulur, ayarlar silinmez
    signingConfigs {
        create("toggrobot") {
            storeFile = file("toggrobot.jks")
            storePassword = "toggrobot"
            keyAlias = "toggrobot"
            keyPassword = "toggrobot"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("toggrobot")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
