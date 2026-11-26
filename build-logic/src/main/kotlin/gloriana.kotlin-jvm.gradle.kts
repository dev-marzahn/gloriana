plugins {
  id("org.jetbrains.kotlin.jvm")
}

compileJavaAgainstJvmLevel()

kotlin {
  compilerOptions {
    compileAgainstJvmLevel()
    failOnWarnings()
  }
}

runTestsOnJUnitPlatform(configurations.testImplementation, configurations.testRuntimeOnly)
