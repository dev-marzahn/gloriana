package dev.marzahn.gloriana.desktop.di

import dev.marzahn.gloriana.desktop.SkiaAvatarTranscoder
import dev.marzahn.gloriana.desktop.keys.JavaKeyringKeychain
import dev.marzahn.gloriana.desktop.tasks.DesktopKeepAlive
import dev.marzahn.gloriana.desktop.tasks.ProcessActivities
import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.characters.AvatarTranscoder
import dev.marzahn.gloriana.logic.debug.DebuggableBuild
import dev.marzahn.gloriana.logic.di.logicModule
import dev.marzahn.gloriana.logic.keys.DeviceKeyStore
import dev.marzahn.gloriana.logic.keys.KeychainKeyStore
import dev.marzahn.gloriana.logic.storage.DesktopBuild
import dev.marzahn.gloriana.logic.storage.DesktopEnvironment
import dev.marzahn.gloriana.logic.storage.desktopGlorianaDatabase
import dev.marzahn.gloriana.logic.tasks.KeepAlive
import dev.marzahn.gloriana.ui.di.uiModule
import org.koin.dsl.bind
import org.koin.dsl.module

private val desktopPlatformModule = module {
  single<LibraryDirectory> { LibraryDirectory { DesktopEnvironment.current().dataDirectory } }
  single { desktopGlorianaDatabase(get<LibraryDirectory>().path()) }
  single<DebuggableBuild> { DebuggableBuild { DesktopBuild.current() == DesktopBuild.Debuggable } }
  single { SkiaAvatarTranscoder() } bind AvatarTranscoder::class
  single<DeviceKeyStore> { KeychainKeyStore(JavaKeyringKeychain(service = DesktopBuild.current().directoryName)) }
  single<KeepAlive> { DesktopKeepAlive(ProcessActivities.forOs(System.getProperty("os.name"))) }
}

val desktopAppModule = module {
  includes(logicModule, uiModule, desktopPlatformModule)
}
