package dev.marzahn.gloriana.logic.brand

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class AvatarGradientColorsTest {
  @Test
  fun theGradientIsPinnedToTheIdsHash() {
    assertEquals(AvatarGradientTable[0], avatarGradientColors(Uuid.fromLongs(0, 0)))
    assertEquals(AvatarGradientTable[1], avatarGradientColors(Uuid.fromLongs(0, 1)))
    assertEquals(AvatarGradientTable[5], avatarGradientColors(Uuid.fromLongs(0, 5)))
    assertEquals(AvatarGradientTable[0], avatarGradientColors(Uuid.fromLongs(0, 6)))
    assertEquals(AvatarGradientTable[1], avatarGradientColors(Uuid.parse("01890a5d-ac96-774b-bcce-b302099a8057")))
  }
}
