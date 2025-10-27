import com.android.build.gradle.internal.api.ApkVariantOutputImpl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.napcat.monitor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.napcat.monitor"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    signingConfigs {
        create("release") {
            val storeFilePath = (findProperty("SIGNING_STORE_FILE") as String?)
                ?: (findProperty("RELEASE_STORE_FILE") as String?)
                ?: (findProperty("key.jks") as String?)
                ?: "key.jks"
            storeFile = file(storeFilePath)

            storePassword = (findProperty("SIGNING_STORE_PASSWORD") as String?)
                ?: System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = (findProperty("SIGNING_KEY_ALIAS") as String?)
                ?: System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = (findProperty("SIGNING_KEY_PASSWORD") as String?)
                ?: System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = false
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*.kotlin_module"
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    @Suppress("DEPRECATION")
    applicationVariants.all {
        outputs.forEach { output ->
            val apkOutput = output as? ApkVariantOutputImpl
            if (apkOutput != null) {
                val abiFilter = apkOutput.filters.find { it.filterType == "ABI" }?.identifier
                if (abiFilter != null) {
                    val baseVersionCode = defaultConfig.versionCode ?: 0
                    apkOutput.versionCodeOverride = when (abiFilter) {
                        "armeabi-v7a" -> 1000 + baseVersionCode // 32位版本: 1001
                        "arm64-v8a"   -> 2000 + baseVersionCode // 64位版本: 2001
                        else -> apkOutput.versionCodeOverride
                    }
                }
            }
        }
    }
}


kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    //implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    //implementation(libs.retrofit)
    //implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp)
    debugImplementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}