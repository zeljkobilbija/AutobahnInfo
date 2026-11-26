plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.zeljkobilbija001.autobahninfo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.zeljkobilbija001.autobahninfo"
        minSdk = 24
        targetSdk = 37
        versionCode = 6
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Potpis za Google Play: ključ i lozinka iz C:Usersporod.gradlegradle.properties (van projekta, ne ide na GitHub).
    // Bez upisane lozinke release ostaje nepotpisan, kao ranije.
    val potpisKljuc = providers.gradleProperty("POTPIS_AUTOBAHNINFO").orNull
    val potpisLozinka = providers.gradleProperty("POTPIS_LOZINKA").orNull
    val potpis = potpisKljuc != null && !potpisLozinka.isNullOrEmpty() && file(potpisKljuc).exists()
    signingConfigs {
        if (potpis) {
            create("release") {
                storeFile = file(potpisKljuc!!)
                storePassword = potpisLozinka
                keyAlias = providers.gradleProperty("POTPIS_AUTOBAHNINFO_ALIAS").orNull ?: "key0"
                keyPassword = potpisLozinka
            }
        }
    }

    buildTypes {
        release {
            if (potpis) signingConfig = signingConfigs.getByName("release")
            // R8 (skraćivanje i maskiranje) – Google traži maskiranje (rok feb. 2027); mapping ide u .aab.
            // Pravila u proguard-rules.pro čuvaju WorkDatabase_Impl (WorkManager iz AdMob-a; Room ga traži po imenu,
            // bez pravila je release padao pri pokretanju – InterstateInfo 16.11.2026).
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.core.splashscreen)
    implementation(libs.recyclerview)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.billing)
    implementation(libs.work.runtime)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
