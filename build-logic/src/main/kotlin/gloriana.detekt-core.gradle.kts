plugins {
  id("dev.detekt")
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom(isolated.rootProject.projectDirectory.file("config/detekt/detekt.yml"))
  source.setFrom(layout.projectDirectory.dir("src"))
}

dependencies {
  detektPlugins(catalog.library("compose-rules-detekt"))
}
