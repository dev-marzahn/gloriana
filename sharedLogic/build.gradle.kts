plugins {
  id("gloriana.kmp-library")
  id("gloriana.detekt")
  id("gloriana.serialization")
  id("gloriana.room")
}

kotlin {
  android {
    namespace = "dev.marzahn.gloriana.logic"
    withDeviceTest {
      instrumentationRunner = ANDROID_TEST_RUNNER
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(libs.koin.core)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization.json)
    }
    jvmTest.dependencies {
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.konsist)
      implementation(libs.androidx.room.testing)
    }
    getByName("androidDeviceTest").dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.androidx.test.runner)
      implementation(libs.androidx.test.ext.junit)
      implementation(libs.androidx.room.testing)
    }
  }
}
