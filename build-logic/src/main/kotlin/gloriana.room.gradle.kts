plugins {
  id("gloriana.kmp-library")
  id("com.google.devtools.ksp")
  id("androidx.room")
}

room {
  schemaDirectory(layout.projectDirectory.dir("schemas").asFile.path)
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(catalog.library("androidx-room-runtime"))
      api(catalog.library("androidx-sqlite-bundled"))
    }
  }
}

dependencies {
  add("kspAndroid", catalog.library("androidx-room-compiler"))
  add("kspJvm", catalog.library("androidx-room-compiler"))
}
