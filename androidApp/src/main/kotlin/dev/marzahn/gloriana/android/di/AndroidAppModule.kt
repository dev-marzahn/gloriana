package dev.marzahn.gloriana.android.di

import android.content.pm.ApplicationInfo
import dev.marzahn.gloriana.android.AndroidAvatarTranscoder
import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.characters.AvatarTranscoder
import dev.marzahn.gloriana.logic.characters.DebuggableBuild
import dev.marzahn.gloriana.logic.di.logicModule
import dev.marzahn.gloriana.logic.storage.androidGlorianaDatabase
import dev.marzahn.gloriana.ui.di.uiModule
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

private val androidPlatformModule = module {
  single { androidGlorianaDatabase(androidContext()) }
  single<LibraryDirectory> { LibraryDirectory { androidContext().filesDir.toPath() } }
  single<DebuggableBuild> {
    DebuggableBuild { androidContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
  }
  single { AndroidAvatarTranscoder() } bind AvatarTranscoder::class
}

val androidAppModule = module {
  includes(logicModule, uiModule, androidPlatformModule)
}
