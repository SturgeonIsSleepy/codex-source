plugins { id("com.android.application") }
android {
    namespace = "com.wanderingwitch.player"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.wanderingwitch.player"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.8"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
