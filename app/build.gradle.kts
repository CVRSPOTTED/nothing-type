plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dev.nothingtype"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "dev.nothingtype.keyboard"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.7.0"
        testApplicationId = "dev.nothingtype.testing"
        testInstrumentationRunner = "dev.nothingtype.testing.EditorSmokeInstrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions.unitTests {
        isIncludeAndroidResources = true
        all { it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2") }
    }
    buildFeatures { buildConfig = true }
    bundle { language { enableSplit = false } }
    lint { abortOnError = true }
    val ownerKeystore = providers.environmentVariable("NOTHING_TYPE_KEYSTORE").orNull
    signingConfigs {
        if (ownerKeystore != null) create("owner") {
            storeFile = file(ownerKeystore)
            storePassword = providers.environmentVariable("NOTHING_TYPE_STORE_PASSWORD").orNull
                ?: error("Set NOTHING_TYPE_STORE_PASSWORD")
            keyAlias = providers.environmentVariable("NOTHING_TYPE_KEY_ALIAS").orNull
                ?: error("Set NOTHING_TYPE_KEY_ALIAS")
            keyPassword = providers.environmentVariable("NOTHING_TYPE_KEY_PASSWORD").orNull
                ?: error("Set NOTHING_TYPE_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            if (ownerKeystore != null) signingConfig = signingConfigs.getByName("owner")
        }
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
