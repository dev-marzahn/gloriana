package dev.marzahn.gloriana.android

import android.app.Application
import dev.marzahn.gloriana.android.di.androidAppModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class GlorianaApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    startKoin {
      androidContext(this@GlorianaApplication)
      modules(androidAppModule)
    }
  }
}
