plugins {
  id("gloriana.detekt-core")
}

dependencies {
  detektPlugins(project(":detektRules"))
}
