package dev.marzahn.gloriana.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.characters.CharacterPicture
import dev.marzahn.gloriana.ui.characters.fields.FieldContent
import dev.marzahn.gloriana.ui.characters.fields.Folding
import dev.marzahn.gloriana.ui.characters.fields.rememberFieldBlocks
import dev.marzahn.gloriana.ui.characters.metaLine
import dev.marzahn.gloriana.ui.components.tappable
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaSheet
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.avatar_full_screen
import dev.marzahn.gloriana.ui.resources.ic_add_comment_20
import dev.marzahn.gloriana.ui.resources.ic_arrow_forward_20
import dev.marzahn.gloriana.ui.resources.not_available_yet
import dev.marzahn.gloriana.ui.resources.open
import dev.marzahn.gloriana.ui.resources.start_chat
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

private const val DESCRIPTION_LINES = 6
private val PreviewAvatarWidth = 120.dp
private val PreviewAvatarHeight = 160.dp
private val ButtonHeight = 48.dp

data class CharacterPreview(
  val id: Uuid,
  val name: String,
  val creator: String,
  val version: String,
  val description: String,
  val avatarPath: String?,
)

@Composable
fun PreviewSheet(
  preview: CharacterPreview,
  onOpen: () -> Unit,
  onAvatar: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  val shape = GlorianaTheme.shapes.panel
  GlorianaSheet(onDismiss, modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.s16)) {
      val avatarLabel = stringResource(Res.string.avatar_full_screen, preview.name)
      val opens = if (preview.avatarPath != null) {
        Modifier.tappable(shape, Role.Image, avatarLabel, onClick = onAvatar)
      } else {
        Modifier
      }
      CharacterPicture(
        preview.id,
        preview.name,
        preview.avatarPath,
        Modifier.size(PreviewAvatarWidth, PreviewAvatarHeight).clip(shape).then(opens),
      )
      Column(Modifier.weight(1f)) {
        Text(preview.name, style = GlorianaTheme.type.material.headlineSmall, color = colors.text)
        metaLine(preview.creator, preview.version)?.let {
          Text(it, style = GlorianaTheme.type.material.bodyMedium, color = colors.textMuted)
        }
      }
    }
    if (preview.description.isNotBlank()) {
      val blocks = rememberFieldBlocks(preview.description).value.orEmpty()
      Folding(DESCRIPTION_LINES, colors.raised, Modifier.padding(top = spacing.s16), toggle = false) {
        FieldContent(blocks, colors.raised)
      }
    }
    Row(Modifier.fillMaxWidth().padding(top = spacing.s16), horizontalArrangement = Arrangement.spacedBy(spacing.s8)) {
      val tall = Modifier.heightIn(min = ButtonHeight)
      StartChatButton(Modifier.weight(1f).then(tall))
      GlorianaButton(stringResource(Res.string.open), onOpen, tall, icon = Res.drawable.ic_arrow_forward_20)
    }
  }
}

@Composable
fun StartChatButton(modifier: Modifier = Modifier) {
  val notYet = stringResource(Res.string.not_available_yet)
  GlorianaButton(
    stringResource(Res.string.start_chat),
    {},
    modifier,
    kind = ButtonKind.Primary,
    icon = Res.drawable.ic_add_comment_20,
    enabled = false,
    stateDescription = notYet,
  )
}
