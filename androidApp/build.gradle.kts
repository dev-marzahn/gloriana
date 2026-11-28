import com.android.build.api.variant.FilterConfiguration

plugins {
  id("gloriana.android-app")
  id("gloriana.detekt")
  id("gloriana.no-http-logging")
}

android {
  namespace = "dev.marzahn.gloriana.android"

  defaultConfig {
    applicationId = "dev.marzahn.gloriana"
    versionCode = appVersion.code
    versionName = appVersion.toString()
  }
  splits {
    abi {
      isEnable = true
      reset()
      include(*Abi.entries.map { it.id }.toTypedArray())
      isUniversalApk = false
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
  lint {
    disable += setOf("ChromeOsAbiSupport", "MissingApplicationIcon")
  }
  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
    }
  }
}

androidComponents {
  onVariants { variant ->
    variant.outputs.forEach { output ->
      val abiId = output.filters.single { it.filterType == FilterConfiguration.FilterType.ABI }.identifier
      output.versionCode.set(appVersion.codeFor(Abi.of(abiId)))
    }
  }
}

val stringsFiles = mapOf(
  "gloriana.launcherStrings" to layout.projectDirectory.file("src/main/res/values/strings.xml"),
  "gloriana.uiStrings" to
    isolated.rootProject.projectDirectory.file("sharedUI/src/commonMain/composeResources/values/strings.xml"),
)

tasks.withType<Test>().configureEach {
  stringsFiles.forEach { (name, file) -> jvmArgumentProviders += SystemPropertyFile(name, file, workingDir) }
}

dependencies {
  implementation(projects.sharedUI)
  implementation(libs.androidx.activity.compose)
  implementation(libs.koin.android)

  testImplementation(libs.koin.test)
  testImplementation(libs.kotlinx.coroutines.test)

  androidTestImplementation(libs.kotlin.test)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.compose.ui.test.junit4)
  androidTestImplementation(libs.compose.components.resources)
}
