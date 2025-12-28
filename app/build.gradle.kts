plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.devtools.ksp)
    id("kotlin-parcelize")
}

//configurations.all {
//    exclude(group = "com.intellij", module = "annotations")
//}

android {
    namespace = "devandroid.moacir.novoorcamento"
    compileSdk = 35

    defaultConfig {
        applicationId = "devandroid.moacir.novoorcamento"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Configuração do Schema do Room (Opcional, mas útil)
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
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
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    // --- Bibliotecas do Google / Android ---
    // Nota: Leanback é geralmente para TV. Se for só celular, pode remover.
    implementation(libs.androidx.leanback)

    // CORREÇÃO: Usando versão 3.3.0 para evitar erro de compatibilidade com Kotlin
    // Se não usar mapas/lugares, pode remover esta linha inteira.
    implementation("com.google.android.libraries.places:places:3.3.0")

    // Desugaring (para usar APIs novas do Java em Androids antigos)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // --- Core e UI Essenciais ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.vectordrawable.animated)

    // --- Bibliotecas de Terceiros ---
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // --- Room (Banco de Dados) ---
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // --- Gson ---
    implementation(libs.google.code.gson)

    // --- ViewModel e Lifecycle ---
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Navigation Component ---
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)

    // --- Testes ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
