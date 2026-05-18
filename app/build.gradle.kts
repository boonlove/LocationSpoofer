import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

val appVersionName: String by rootProject.extra
val appVersionCode: Int by rootProject.extra

android {
    namespace = "com.suseoaa.locationspoofer"
    compileSdk = 36

    fun getLocalConfig(key: String): String? {
        val localYml = file("../local.yml")
        if (localYml.exists()) {
            val line = localYml.readLines().find { it.startsWith("$key:") }
            if (line != null) {
                return line.substringAfter(":").trim().removeSurrounding("\"").removeSurrounding("'")
            }
        }
        return null
    }

    val googleMapsApiKey = System.getenv("GOOGLE_MAPS_API_KEY") ?: getLocalConfig("GOOGLE_MAPS_API_KEY") ?: ""

    fun getSigningConfig(key: String): String? {
        val properties = Properties()
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        if (keystorePropertiesFile.exists()) {
            try {
                properties.load(keystorePropertiesFile.inputStream())
                return properties.getProperty(key)
            } catch (e: Exception) {
                println("Warning: 无法加载 keystore.properties 文件: ${e.message}")
            }
        }
        return null
    }

    splits {
        abi {
            isEnable = true           // 启用 ABI 拆分
            reset()                   // 清除默认配置
            include("arm64-v8a")      // 只生成 arm64-v8a 架构
            isUniversalApk = false    // 不生成通用 APK
        }
    }

    defaultConfig {
        applicationId = "com.suseoaa.locationspoofer"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        vectorDrawables {
            useSupportLibrary = true
        }

        manifestPlaceholders["googleMapsApiKey"] = googleMapsApiKey
    }

    signingConfigs {
        val storeFilePath = getSigningConfig("storeFile").toString()
        val storePassword = getSigningConfig("storePassword")
        val keyAlias = getSigningConfig("keyAlias")
        val keyPassword = getSigningConfig("keyPassword")
        val hasSigning = rootProject.file(storeFilePath).exists() && storePassword != null && keyAlias != null && keyPassword != null
        if (hasSigning) {
            create("release") {
                this.storeFile = rootProject.file(storeFilePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
				enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"$googleMapsApiKey\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"$googleMapsApiKey\"")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

base {
    archivesName.set(
        "LocationSpoofer_v${appVersionName}_${appVersionCode}"
    )
}

dependencies {
    compileOnly(libs.xposed.api)
    implementation(libs.koin.androidx.compose)
    implementation(libs.amap.map)
    implementation(libs.amap.search)
    implementation(libs.google.maps)
    implementation(libs.google.places)
    implementation(libs.play.services.location)
    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)
}