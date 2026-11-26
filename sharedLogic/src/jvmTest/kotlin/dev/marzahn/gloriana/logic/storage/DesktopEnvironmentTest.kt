package dev.marzahn.gloriana.logic.storage

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopEnvironmentTest {
  @Test
  fun macOsKeepsItInApplicationSupport() {
    assertEquals(
      Path("/Users/ada/Library/Application Support/Gloriana"),
      environment("Mac OS X", "/Users/ada").dataDirectory,
    )
  }

  @Test
  fun windowsKeepsItInAppData() {
    assertEquals(
      Path("C:\\Users\\ada\\AppData\\Roaming", "Gloriana"),
      environment("Windows 11", "C:\\Users\\ada", "APPDATA" to "C:\\Users\\ada\\AppData\\Roaming").dataDirectory,
    )
  }

  @Test
  fun windowsWithoutAppDataFallsBackToRoamingUnderHome() {
    assertEquals(
      Path("C:\\Users\\ada", "AppData", "Roaming", "Gloriana"),
      environment("Windows 11", "C:\\Users\\ada").dataDirectory,
    )
  }

  @Test
  fun linuxFollowsXdgDataHome() {
    assertEquals(
      Path("/data/ada/Gloriana"),
      environment("Linux", "/home/ada", "XDG_DATA_HOME" to "/data/ada").dataDirectory,
    )
  }

  @Test
  fun linuxFallsBackToLocalShareWhenXdgDataHomeIsEmpty() {
    assertEquals(
      Path("/home/ada/.local/share/Gloriana"),
      environment("Linux", "/home/ada", "XDG_DATA_HOME" to "").dataDirectory,
    )
  }

  @Test
  fun linuxIgnoresARelativeXdgDataHome() {
    assertEquals(
      Path("/home/ada/.local/share/Gloriana"),
      environment("Linux", "/home/ada", "XDG_DATA_HOME" to "data/ada").dataDirectory,
    )
  }

  @Test
  fun theDebuggableBuildKeepsItsOwnDirectory() {
    assertEquals(
      Path("/Users/ada/Library/Application Support/Gloriana Debug"),
      environment("Mac OS X", "/Users/ada", build = DesktopBuild.Debuggable).dataDirectory,
    )
  }

  @Test
  fun windowsIgnoresARelativeAppData() {
    assertEquals(
      Path("C:\\Users\\ada", "AppData", "Roaming", "Gloriana"),
      environment("Windows 11", "C:\\Users\\ada", "APPDATA" to "AppData").dataDirectory,
    )
  }

  @Test
  fun theGlorianaDebuggablePropertyPicksTheBuild() {
    assertEquals(DesktopBuild.Debuggable, withDebuggableProperty("true") { DesktopBuild.current() })
    assertEquals(DesktopBuild.Release, withDebuggableProperty(null) { DesktopBuild.current() })
    assertEquals(DesktopBuild.Release, withDebuggableProperty("false") { DesktopBuild.current() })
  }

  private fun <T> withDebuggableProperty(value: String?, read: () -> T): T {
    val before = System.getProperty("gloriana.debuggable")
    try {
      setOrClear("gloriana.debuggable", value)
      return read()
    } finally {
      setOrClear("gloriana.debuggable", before)
    }
  }

  private fun setOrClear(name: String, value: String?) {
    if (value == null) System.clearProperty(name) else System.setProperty(name, value)
  }

  private fun environment(
    osName: String,
    userHome: String,
    vararg variables: Pair<String, String>,
    build: DesktopBuild = DesktopBuild.Release,
  ) = DesktopEnvironment(osName, userHome, build, variables.toMap()::get)
}
