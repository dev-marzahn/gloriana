package dev.marzahn.gloriana.android.di

import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

class AndroidGraphTest {
  @OptIn(KoinExperimentalAPI::class)
  @Test
  fun everyDefinitionResolves() {
    androidAppModule.verify()
  }
}
