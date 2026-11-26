plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
}

private val sdk = androidSdk

android {
  compileSdk = sdk.compile

  defaultConfig {
    minSdk = sdk.min
    targetSdk = sdk.target
    testInstrumentationRunner = ANDROID_TEST_RUNNER
  }
  compileOptions {
    sourceCompatibility = JvmLevel.java
    targetCompatibility = JvmLevel.java
  }
  lint {
    failOnWarnings()
  }
}

kotlin {
  compilerOptions {
    targetJvmLevel()
    failOnWarnings()
  }
}

runTestsOnJUnitPlatform(configurations.testImplementation, configurations.testRuntimeOnly)
