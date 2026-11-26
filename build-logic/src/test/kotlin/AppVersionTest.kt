import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppVersionTest {
  @Test
  fun parsesMajorMinorPatch() {
    assertEquals(AppVersion(1, 2, 3), AppVersion.parse("1.2.3"))
  }

  @Test
  fun rejectsAnythingButThreeNumbers() {
    listOf("1.2", "1.2.3-beta", "a.b.c", "").forEach { text ->
      assertFailsWith<IllegalArgumentException>(text) { AppVersion.parse(text) }
    }
  }

  @Test
  fun rejectsPartsThatOverflowTheirSlots() {
    assertFailsWith<IllegalArgumentException> { AppVersion(1, 100, 0) }
    assertFailsWith<IllegalArgumentException> { AppVersion(1, 0, 100) }
  }

  @Test
  fun rejectsAMajorWhoseCodesPassPlaysLimit() {
    val highest = AppVersion(20999, 99, 99)
    assertTrue(Abi.entries.all { highest.codeFor(it) <= AppVersion.MAX_VERSION_CODE })
    assertFailsWith<IllegalArgumentException> { AppVersion(21000, 0, 0) }
    assertFailsWith<IllegalArgumentException> { AppVersion(Int.MAX_VALUE, 0, 0) }
  }

  @Test
  fun codeKeepsEveryPartInItsOwnDigits() {
    assertEquals(10203, AppVersion(1, 2, 3).code)
    assertEquals(9999, AppVersion(0, 99, 99).code)
  }

  @Test
  fun abiCodeAppendsTheAbiSlot() {
    val version = AppVersion(0, 1, 0)
    assertEquals(1001, version.codeFor(Abi.ArmeabiV7a))
    assertEquals(1002, version.codeFor(Abi.Arm64V8a))
    assertEquals(1003, version.codeFor(Abi.X8664))
  }

  @Test
  fun everyAbiOfANewerVersionOutranksEveryAbiOfAnOlderOne() {
    val older = Abi.entries.maxOf { AppVersion(0, 1, 0).codeFor(it) }
    val newer = Abi.entries.minOf { AppVersion(0, 1, 1).codeFor(it) }
    assertTrue(newer > older)
  }

  @Test
  fun findsAnAbiById() {
    assertEquals(Abi.X8664, Abi.of("x86_64"))
  }

  @Test
  fun namesTheKnownAbisForAnUnknownOne() {
    val error = assertFailsWith<IllegalArgumentException> { Abi.of("mips") }
    assertTrue("arm64-v8a" in error.message.orEmpty())
  }
}
