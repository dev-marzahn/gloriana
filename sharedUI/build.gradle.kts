plugins {
  id("gloriana.kmp-library")
  id("gloriana.compose")
  id("gloriana.koin")
  id("gloriana.detekt")
  id("gloriana.serialization")
}

compose.resources {
  packageOfResClass = "dev.marzahn.gloriana.ui.resources"
  publicResClass = true
}

kotlin {
  android {
    namespace = "dev.marzahn.gloriana.ui"
    androidResources {
      enable = true
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(projects.sharedLogic)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization.core)
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.material3)
      implementation(libs.jetbrains.navigation3.ui)
      implementation(libs.jetbrains.lifecycle.viewmodel.compose)
      implementation(libs.jetbrains.lifecycle.runtime.compose)
      implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.koin.compose.navigation3)
      implementation(libs.jetbrains.markdown)
      implementation(libs.ksoup)
    }
    androidMain.dependencies {
      implementation(libs.androidx.activity.compose)
    }
    jvmTest.dependencies {
      implementation(libs.kotlinx.coroutines.test)
      implementation(compose.desktop.currentOs)
      implementation(libs.compose.ui.test)
      implementation(libs.kotlinx.coroutines.swing)
    }
  }
}
