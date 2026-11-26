package dev.marzahn.gloriana.android

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class LauncherLabelTest {
  @Test
  fun launcherLabelMatchesTheAppNameShownInTheUi() {
    assertEquals(appName("gloriana.uiStrings"), appName("gloriana.launcherStrings"))
  }

  private fun appName(stringsProperty: String): String {
    val strings = File(requireNotNull(System.getProperty(stringsProperty)) { "$stringsProperty is not set" })
    val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(strings).getElementsByTagName("string")
    return (0 until nodes.length).map(nodes::item).single { it.attributes.getNamedItem("name").nodeValue == "app_name" }
      .textContent
  }
}
