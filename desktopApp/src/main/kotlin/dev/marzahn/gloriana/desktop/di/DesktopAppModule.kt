package dev.marzahn.gloriana.desktop.di

import dev.marzahn.gloriana.desktop.SkiaAvatarTranscoder
import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.characters.AvatarTranscoder
import dev.marzahn.gloriana.logic.characters.DebuggableBuild
import dev.marzahn.gloriana.logic.di.logicModule
import dev.marzahn.gloriana.logic.storage.DesktopBuild
import dev.marzahn.gloriana.logic.storage.DesktopEnvironment
import dev.marzahn.gloriana.logic.storage.desktopGlorianaDatabase
import dev.marzahn.gloriana.ui.di.uiModule
import org.koin.dsl.bind
import org.koin.dsl.module

private val desktopPlatformModule = module {
  single<LibraryDirectory> { LibraryDirectory { DesktopEnvironment.current().dataDirectory } }
  single { desktopGlorianaDatabase(get<LibraryDirectory>().path()) }
  single<DebuggableBuild> { DebuggableBuild { DesktopBuild.current() == DesktopBuild.Debuggable } }
  single { SkiaAvatarTranscoder() } bind AvatarTranscoder::class
}

val desktopAppModule = module {
  includes(logicModule, uiModule, desktopPlatformModule)
}
