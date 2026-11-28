package dev.marzahn.gloriana.ui.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.logic.characters.Character
import dev.marzahn.gloriana.logic.characters.CharacterAsset
import dev.marzahn.gloriana.ui.characters.fields.FieldContent
import dev.marzahn.gloriana.ui.characters.fields.Folding
import dev.marzahn.gloriana.ui.characters.fields.rememberFieldBlocks
import dev.marzahn.gloriana.ui.components.Panel
import dev.marzahn.gloriana.ui.components.PanelRow
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.asset_kind
import dev.marzahn.gloriana.ui.resources.field_assets
import dev.marzahn.gloriana.ui.resources.field_pronouns
import dev.marzahn.gloriana.ui.resources.field_tags
import dev.marzahn.gloriana.ui.resources.ic_description
import dev.marzahn.gloriana.ui.resources.ic_draft
import dev.marzahn.gloriana.ui.resources.ic_image
import dev.marzahn.gloriana.ui.resources.ic_lock
import dev.marzahn.gloriana.ui.resources.panel_model
import dev.marzahn.gloriana.ui.resources.panel_private
import dev.marzahn.gloriana.ui.resources.pronouns_not_set
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.lookShadow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val FOLD_LINES = 8
private const val PRONOUN_SEPARATOR = " / "
private val AssetTileSize = 40.dp
private val ImageKinds = setOf("icon", "background", "emotion", "user_icon", "image")

@Composable
fun CharacterPanels(character: Character, modifier: Modifier = Modifier) {
  val model = remember(character) { sentToTheModel(character) }
  val notes = remember(character) { creatorNotes(character) }
  val tags = character.keptFields.tags.filter { it.isNotBlank() }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s16)) {
    val (nickname, rest) = model.partition { it.label == FieldLabel.Nickname }
    val sentRows = nickname.map(::markupRow) + listOf<PanelRow> { PronounsRow(character.pronouns.forms()) } +
      rest.map(::markupRow)
    Panel(Res.drawable.ic_description, stringResource(Res.string.panel_model), sentRows)
    val privateRows = listOfNotNull(
      notes?.let(::markupRow),
      tags.takeIf { it.isNotEmpty() }?.let(::tagsRow),
      character.assets.takeIf { it.isNotEmpty() }?.let { { AssetsRow(it) } },
    )
    if (privateRows.isNotEmpty()) Panel(Res.drawable.ic_lock, stringResource(Res.string.panel_private), privateRows)
  }
}

private fun markupRow(row: FieldRow): PanelRow = { MarkupRow(labelText(row.label), row.value) }

private fun tagsRow(tags: List<String>): PanelRow =
  { PlainRow(stringResource(Res.string.field_tags), tags.joinToString(", ")) }

@Composable
private fun RowLabel(text: String) {
  Text(text, style = GlorianaTheme.type.material.labelMedium, color = GlorianaTheme.colors.textSubtle)
}

@Composable
private fun MarkupRow(label: String, value: String) {
  val panel = GlorianaTheme.colors.raised
  val blocks = rememberFieldBlocks(value).value.orEmpty()
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s6)) {
    RowLabel(label)
    Folding(FOLD_LINES, panel) { FieldContent(blocks, panel) }
  }
}

@Composable
private fun PlainRow(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s6)) {
    RowLabel(label)
    Text(value, style = GlorianaTheme.type.reading, color = GlorianaTheme.colors.text)
  }
}

@Composable
private fun PronounsRow(forms: List<String>) {
  val colors = GlorianaTheme.colors
  val bodyLarge = GlorianaTheme.type.material.bodyLarge
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s6)) {
    RowLabel(stringResource(Res.string.field_pronouns))
    if (forms.isEmpty()) {
      Text(stringResource(Res.string.pronouns_not_set), style = bodyLarge, color = colors.textSubtle)
    } else {
      Text(forms.joinToString(PRONOUN_SEPARATOR), style = bodyLarge, color = colors.text)
    }
  }
}

@Composable
private fun AssetsRow(assets: List<CharacterAsset>) {
  val colors = GlorianaTheme.colors
  val spacing = GlorianaTheme.spacing
  Column(verticalArrangement = Arrangement.spacedBy(spacing.s8)) {
    RowLabel(stringResource(Res.string.field_assets))
    assets.forEach { asset ->
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s12)) {
        val icon = if (asset.type.lowercase() in ImageKinds) Res.drawable.ic_image else Res.drawable.ic_draft
        Box(Modifier.size(AssetTileSize).background(colors.surface2, GlorianaTheme.shapes.control), Alignment.Center) {
          Icon(painterResource(icon), null, tint = colors.textMuted)
        }
        Column {
          Text(asset.name, style = GlorianaTheme.type.material.bodyLarge, color = colors.text)
          Text(
            stringResource(Res.string.asset_kind, assetKind(asset.type), asset.extension.uppercase()),
            style = GlorianaTheme.type.material.bodyMedium,
            color = colors.textMuted,
          )
        }
      }
    }
  }
}

fun assetKind(type: String): String = type.replace('_', ' ').replaceFirstChar { it.uppercase() }
