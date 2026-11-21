plugins { id("com.android.application") }
android {
 namespace = "tj.umed.edgesidebar"
 compileSdk = 35
 defaultConfig { applicationId = "tj.umed.edgesidebar"; minSdk = 26; targetSdk = 32; versionCode = 1; versionName = "1.0-beta1" }
 buildTypes { release { isMinifyEnabled = false } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
