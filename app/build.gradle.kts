plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.audio_arcade.audio_arcade"
    compileSdk = 36

    defaultConfig {
        resourceConfigurations += setOf("fr","en","es","bg","bn","hi","sr")
        applicationId = "com.audio_arcade.audio_arcade"
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../audio_arcade_release.jks")
            storePassword = System.getenv("AA_STORE_PASSWORD") ?: "Audio_ArcadeKey"
            keyAlias = "audio_arcade_key"
            keyPassword = System.getenv("AA_KEY_PASSWORD") ?: "Audio_ArcadeKey"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")

    // Media3 ExoPlayer pour la lecture audio
    implementation(libs.androidx.media3.exoplayer)
    implementation("androidx.media3:media3-session:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")

    implementation(libs.androidx.games.activity)
    implementation("com.mpatric:mp3agic:0.9.1")
    implementation("com.mrljdx:ffmpeg-kit-full:6.1.4")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation(files("libs/arcadeshare.aar"))
//    implementation("com.github.Audio-Arcade:ArcadeShare-Android:3.0.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("androidx.compose.ui:ui-graphics")
}