import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
}

// Assinatura do release: variáveis de ambiente (CI) ou ~/.persondot/keystore.properties (local).
// A chave NUNCA fica no repositório. Mantenha a mesma chave sempre, senão o Android não deixa atualizar o app.
val localKeys = Properties().apply {
  val f = File(System.getProperty("user.home"), ".persondot/keystore.properties")
  if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(env: String, prop: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: localKeys.getProperty(prop)

android {
  namespace = "com.persondot"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.persondot.app"
    minSdk = 26
    targetSdk = 36
    // O CI define estes valores a partir da tag (v1.2.3 -> 10203).
    versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
    versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
    buildConfigField("String", "GITHUB_REPO", "\"GabrielCostaLuiz/personDot\"")
  }

  signingConfigs {
    create("release") {
      val path = secret("KEYSTORE_PATH", "storeFile") ?: "${System.getProperty("user.home")}/.persondot/release.jks"
      storeFile = file(path)
      storePassword = secret("STORE_PASSWORD", "storePassword")
      keyAlias = secret("KEY_ALIAS", "keyAlias") ?: "persondot"
      keyPassword = secret("KEY_PASSWORD", "keyPassword")
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isReturnDefaultValues = true } }
  lint { checkReleaseBuilds = false }  // o CI roda os testes; o lint de release só atrasa o APK
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  ksp(libs.androidx.room.compiler)
  debugImplementation(libs.androidx.compose.ui.tooling)
  testImplementation(libs.junit)
  testImplementation("org.json:json:20240303")
  testImplementation(libs.kotlinx.coroutines.test)
}
