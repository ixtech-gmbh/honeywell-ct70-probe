plugins {
    id("com.android.application")
}

android {
    namespace = "de.ixtech.ct70warmup"
    compileSdk = 35
    defaultConfig {
        applicationId = "de.ixtech.ct70warmup"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
