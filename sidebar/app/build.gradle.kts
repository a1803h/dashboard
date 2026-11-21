plugins { id("com.android.application") }
android {
 namespace = "tj.umed.edgesidebar"
 compileSdk = 35
 defaultConfig { applicationId = "tj.umed.edgesidebar.lsposed"; minSdk = 26; targetSdk = 32; versionCode = 3; versionName = "2.1-figma-frame2" }
 buildTypes { release { isMinifyEnabled = false } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies { compileOnly(project(":xposedapi")) }
