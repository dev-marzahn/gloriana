import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("com.android.lint")
}

private val sdk = androidSdk

lint {
  failOnWarnings()
}

kotlin {
  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  applyDefaultHierarchyTemplate {
    common {
      group("jvmShared") {
        withJvm()
        withCompilations { it.target.platformType == KotlinPlatformType.androidJvm }
      }
    }
  }

  jvm {
    compilerOptions {
      compileAgainstJvmLevel()
    }
  }

  android {
    compileSdk = sdk.compile
    minSdk = sdk.min
    compilerOptions {
      targetJvmLevel()
    }
  }

  compilerOptions {
    failOnWarnings()
  }
}

private val jvmTest = kotlin.sourceSets.named("jvmTest").get()
runTestsOnJUnitPlatform(
  configurations.named(jvmTest.implementationConfigurationName),
  configurations.named(jvmTest.runtimeOnlyConfigurationName),
)
