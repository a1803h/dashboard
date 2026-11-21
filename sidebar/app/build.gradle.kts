plugins { id("com.android.application") }
android {
 namespace = "tj.umed.edgesidebar"
 compileSdk = 35
 defaultConfig { applicationId = "tj.umed.edgesidebar.lsposed"; minSdk = 26; targetSdk = 32; versionCode = 2; versionName = "2.0-lsposed-beta1" }
 buildTypes { release { isMinifyEnabled = false } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

repositories { maven { url = uri("https://repository-cdn.liferay.com/nexus/content/repositories/gradle-plugins/") } }
dependencies { compileOnly("de.robv.android.xposed:api:82") }
