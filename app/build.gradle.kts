plugins {
    id("com.android.application")
}

android {
    namespace = "de.ixtech.ct70probe"
    compileSdk = 35
    defaultConfig {
        applicationId = "de.ixtech.ct70probe"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
