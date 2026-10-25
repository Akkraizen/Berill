import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeCompiler)
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_11
  }
}

dependencies {
  implementation(project(":app:shared"))

  implementation(libs.androidx.activity.compose)

  implementation(libs.compose.uiToolingPreview)
  debugImplementation(libs.compose.uiTooling)
}

val signingProperties = Properties().apply {
  rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun signingValue(environment: String, property: String): String? =
  providers.environmentVariable(environment).orNull?.takeIf { it.isNotBlank() }
    ?: signingProperties.getProperty(property)?.takeIf { it.isNotBlank() }

val releaseKeyStore = signingValue("ANDROID_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")
val releaseSigningAvailable = listOf(
  releaseKeyStore,
  releaseStorePassword,
  releaseKeyAlias,
  releaseKeyPassword
).all { it != null }

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
  val signingAvailable = releaseSigningAvailable
  val keyStoreFile = releaseKeyStore?.let { rootProject.file(it) }
  doLast {
    check(signingAvailable) {
      "Release signing is required. Configure ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD, or keystore.properties."
    }
    check(keyStoreFile!!.isFile) { "Android release keystore does not exist" }
  }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validateReleaseSigning) }

android {
  namespace = "dev.akkraizen.berill"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "dev.akkraizen.berill"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = rootProject.extra["appVersionCode"] as Int
    versionName = rootProject.extra["appVersionName"] as String
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1,INDEX.LIST,io.netty.versions.properties}"
    }
  }
  signingConfigs {
    if (releaseSigningAvailable) {
      create("release") {
        storeFile = rootProject.file(releaseKeyStore!!)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }
  buildTypes {
    release {
      if (releaseSigningAvailable)
        signingConfig = signingConfigs.getByName("release")
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
  }
}
