plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val glyphMatrixSdk = providers.gradleProperty("glyphMatrixSdk").orNull

android {
    namespace = "dev.nothingtype"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "dev.nothingtype.keyboard"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "0.8.0"
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
    defaultConfig.buildConfigField("boolean", "GLYPH_SYNC", "false")
    sourceSets.getByName("release").java.srcDir("src/noGlyphSync/java")
    sourceSets.getByName("debug").apply {
        java.srcDir(if (glyphMatrixSdk != null) "src/glyphSync/java" else "src/noGlyphSync/java")
        if (glyphMatrixSdk != null) manifest.srcFile("src/glyphSync/AndroidManifest.xml")
    }
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
        getByName("debug") {
            if (glyphMatrixSdk != null) buildConfigField("boolean", "GLYPH_SYNC", "true")
        }
        getByName("release") {
            if (ownerKeystore != null) signingConfig = signingConfigs.getByName("owner")
        }
    }
}
dependencies {
    if (glyphMatrixSdk != null) {
        val sdk = file(glyphMatrixSdk)
        require(sdk.isFile) { "Glyph Matrix SDK AAR not found: ${sdk.path}" }
        add("debugImplementation", files(sdk))
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
