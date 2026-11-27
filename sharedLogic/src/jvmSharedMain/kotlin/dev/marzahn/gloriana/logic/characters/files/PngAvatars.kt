package dev.marzahn.gloriana.logic.characters.files

import dev.marzahn.gloriana.logic.assets.AssetHash
import dev.marzahn.gloriana.logic.assets.AssetStore
import dev.marzahn.gloriana.logic.brand.avatarGradientColors
import dev.marzahn.gloriana.logic.characters.AvatarChange
import dev.marzahn.gloriana.logic.characters.AvatarTranscoder
import dev.marzahn.gloriana.logic.characters.Character
import dev.marzahn.gloriana.logic.characters.TranscodedAvatar
import dev.marzahn.gloriana.logic.characters.card.CardAssetEntry
import java.io.ByteArrayInputStream
import java.io.InputStream

internal val PNG_AVATAR_ENTRY = CardAssetEntry(ICON_ASSET_TYPE, "main", DEFAULT_ASSET_URI, "png")
private const val ANIMATION_PROBE_BYTES = 4 * 1024 * 1024

internal class PngAvatar(val open: () -> InputStream, val change: AvatarChange?)

internal class PngAvatars(private val store: AssetStore, private val transcoder: AvatarTranscoder) {
  fun of(character: Character): PngAvatar {
    val avatar = character.avatar
    val start = avatar?.let { hash -> store.open(hash).use { it.readUpTo(PNG_SIGNATURE.size) } }
    return when {
      avatar == null || start == null -> placeholderAvatar(character, change = null)
      start.startsWith(PNG_SIGNATURE) -> PngAvatar({ store.open(avatar) }, change = null)
      else -> converted(avatar) ?: placeholderAvatar(character, AvatarChange.Unreadable)
    }
  }

  private fun converted(hash: AssetHash): PngAvatar? {
    val converted = transcodedOrNull(hash) ?: return null
    val firstFrameOnly = AnimatedImage.isAnimated(store.open(hash).use { it.readUpTo(ANIMATION_PROBE_BYTES) })
    val lost = firstFrameOnly || converted.downsampled
    val change = AvatarChange.Converted(firstFrameOnly, converted.downsampled).takeIf { lost }
    return PngAvatar({ ByteArrayInputStream(converted.png) }, change)
  }

  @Suppress("TooGenericExceptionCaught", "SwallowedException")
  private fun transcodedOrNull(hash: AssetHash): TranscodedAvatar? = try {
    transcoder.toPng({ store.open(hash) }, CardLimits.AVATAR_PIXELS)
  } catch (_: Exception) {
    null
  }

  private fun placeholderAvatar(character: Character, change: AvatarChange?): PngAvatar {
    val png = PlaceholderAvatar.png(avatarGradientColors(character.id))
    return PngAvatar({ ByteArrayInputStream(png) }, change)
  }
}
