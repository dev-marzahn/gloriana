plugins {
  id("gloriana.kotlin-jvm")
  id("gloriana.detekt-core")
}

dependencies {
  compileOnly(libs.detekt.api)
  testImplementation(libs.detekt.api)
  testImplementation(libs.detekt.test)
}
