plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
  namespace = "com.hb.dahuaviewer"; compileSdk = 34
  defaultConfig { applicationId = "com.hb.dahuaviewer"; minSdk = 24; targetSdk = 34; versionCode = 4; versionName = "1.3.0"
    ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") } }
  signingConfigs { create("rel") { storeFile = file("../release.keystore"); storePassword = System.getenv("KS_PASS"); keyAlias = "dahua"; keyPassword = System.getenv("KS_PASS") } }
  buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("rel") } }
  buildFeatures { buildConfig = true }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
}
dependencies {
  implementation("org.videolan.android:libvlc-all:3.6.0")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
  implementation("androidx.work:work-runtime-ktx:2.9.1")
  implementation("androidx.biometric:biometric:1.1.0")
  testImplementation("junit:junit:4.13.2")
  implementation("androidx.recyclerview:recyclerview:1.3.2")
  implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
