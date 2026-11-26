package dev.marzahn.gloriana.desktop.di

import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

class DesktopGraphTest {
  @OptIn(KoinExperimentalAPI::class)
  @Test
  fun everyDefinitionResolves() {
    desktopAppModule.verify()
  }
}
