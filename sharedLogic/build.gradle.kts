plugins {
  id("gloriana.kmp-library")
  id("gloriana.detekt")
  id("gloriana.serialization")
  id("gloriana.room")
}

val projectSources = isolated.rootProject.projectDirectory.asFileTree.matching {
  include("**/src/*/kotlin/**/*.kt")
  exclude("**/build/**")
}

val roomSchemas = layout.projectDirectory.dir("schemas").asFile

tasks.named<Test>("jvmTest") {
  inputs.dir(roomSchemas)
    .withPropertyName("roomSchemas").withPathSensitivity(PathSensitivity.RELATIVE)
  systemProperty("gloriana.roomSchemas", roomSchemas.relativeTo(workingDir).path)
  inputs.files(projectSources)
    .withPropertyName("projectSources").withPathSensitivity(PathSensitivity.RELATIVE)
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
