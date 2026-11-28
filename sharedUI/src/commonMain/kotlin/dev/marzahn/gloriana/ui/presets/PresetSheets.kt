package dev.marzahn.gloriana.ui.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.logic.presets.PresetContent
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.WrapperPrompt
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.Confirmation
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaConfirmation
import dev.marzahn.gloriana.ui.components.GlorianaDropdownField
import dev.marzahn.gloriana.ui.components.GlorianaSheet
import dev.marzahn.gloriana.ui.components.GlorianaTextField
import dev.marzahn.gloriana.ui.components.LabelledField
import dev.marzahn.gloriana.ui.components.Panel
import dev.marzahn.gloriana.ui.components.SheetButtons
import dev.marzahn.gloriana.ui.components.tappable
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.add_group
import dev.marzahn.gloriana.ui.resources.add_group_confirm
import dev.marzahn.gloriana.ui.resources.cancel
import dev.marzahn.gloriana.ui.resources.delete
import dev.marzahn.gloriana.ui.resources.delete_preset_chats
import dev.marzahn.gloriana.ui.resources.delete_text
import dev.marzahn.gloriana.ui.resources.delete_title
import dev.marzahn.gloriana.ui.resources.deleting
import dev.marzahn.gloriana.ui.resources.group_name
import dev.marzahn.gloriana.ui.resources.group_name_empty
import dev.marzahn.gloriana.ui.resources.group_prompt_text
import dev.marzahn.gloriana.ui.resources.group_starts_above
import dev.marzahn.gloriana.ui.resources.ic_check_20
import dev.marzahn.gloriana.ui.resources.ic_chevron_right
import dev.marzahn.gloriana.ui.resources.ic_wrap_text
import dev.marzahn.gloriana.ui.resources.list_end
import dev.marzahn.gloriana.ui.resources.rename
import dev.marzahn.gloriana.ui.resources.rename_group_title
import dev.marzahn.gloriana.ui.resources.wrapper_empty
import dev.marzahn.gloriana.ui.resources.wrappers_panel
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.SheetStyle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

private val ChevronSize = 20.dp

sealed interface GroupPrompt {
  data object Add : GroupPrompt

  data class Rename(val groupId: Uuid, val name: String) : GroupPrompt

  companion object {
    private const val ADD = "add"
    private const val RENAME = "rename"

    val Saver: Saver<GroupPrompt?, List<String>> = Saver(
      save = { prompt ->
        when (prompt) {
          null -> null
          Add -> listOf(ADD)
          is Rename -> listOf(RENAME, prompt.groupId.toString(), prompt.name)
        }
      },
      restore = { saved ->
        when (saved.firstOrNull()) {
          ADD -> Add
          RENAME -> saved.takeIf { it.size == 3 }?.let { Rename(Uuid.parse(it[1]), it[2]) }
          else -> null
        }
      },
    )
  }
}

@Composable
internal fun rememberGroupPrompt(): MutableState<GroupPrompt?> =
  rememberSaveable(stateSaver = GroupPrompt.Saver) { mutableStateOf(null) }

@Composable
fun GroupPromptSheet(
  prompt: GroupPrompt,
  content: PresetContent,
  onDone: (PresetIntent?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val spacing = GlorianaTheme.spacing
  val type = GlorianaTheme.type.material
  val colors = GlorianaTheme.colors
  val starting = (prompt as? GroupPrompt.Rename)?.name.orEmpty()
  var name by rememberSaveable { mutableStateOf(starting) }
  var showEmpty by rememberSaveable { mutableStateOf(false) }
  var above by rememberSaveable { mutableStateOf<String?>(null) }
  val submit = {
    val trimmed = name.trim()
    showEmpty = trimmed.isEmpty()
    if (!showEmpty) {
      onDone(
        when (prompt) {
          GroupPrompt.Add -> PresetIntent.AddGroup(trimmed, above?.let(Uuid::parse))
          is GroupPrompt.Rename -> PresetIntent.RenameGroup(prompt.groupId, trimmed)
        },
      )
    }
  }
  GlorianaSheet(onDismiss = { onDone(null) }, modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s12)) {
      val title = when (prompt) {
        GroupPrompt.Add -> stringResource(Res.string.add_group)
        is GroupPrompt.Rename -> stringResource(Res.string.rename_group_title, prompt.name)
      }
      Text(title, style = type.headlineSmall, color = colors.text)
      Text(stringResource(Res.string.group_prompt_text), style = type.bodyMedium, color = colors.textMuted)
      val emptyError = if (showEmpty) stringResource(Res.string.group_name_empty) else null
      LabelledField(stringResource(Res.string.group_name), error = emptyError) {
        GlorianaTextField(
          name,
          {
            name = it
            showEmpty = false
          },
          Modifier.fillMaxWidth(),
          error = emptyError,
        )
      }
      if (prompt == GroupPrompt.Add) {
        StartsAboveField(content.entries, above?.let(Uuid::parse)) { above = it?.toString() }
      }
      PromptButtons(prompt, submit, onCancel = { onDone(null) })
    }
  }
}

@Composable
private fun StartsAboveField(entries: List<PresetEntry>, above: Uuid?, onPick: (Uuid?) -> Unit) {
  val endLabel = stringResource(Res.string.list_end)
  val choices: List<PresetEntry?> = entries + null
  LabelledField(stringResource(Res.string.group_starts_above)) {
    GlorianaDropdownField(
      selected = entries.firstOrNull { it.id == above },
      options = choices,
      label = { choice -> choice?.name ?: endLabel },
      onSelect = { choice -> onPick(choice?.id) },
      placeholder = endLabel,
    )
  }
}

@Composable
private fun PromptButtons(prompt: GroupPrompt, onSubmit: () -> Unit, onCancel: () -> Unit) {
  val sheet = GlorianaTheme.widthClass.sheetStyle == SheetStyle.BottomSheet
  SheetButtons(Modifier.padding(top = GlorianaTheme.spacing.s12)) { buttonModifier ->
    val confirm = @Composable {
      GlorianaButton(
        stringResource(if (prompt == GroupPrompt.Add) Res.string.add_group_confirm else Res.string.rename),
        onSubmit,
        buttonModifier,
        kind = ButtonKind.Primary,
        icon = Res.drawable.ic_check_20,
      )
    }
    val cancel = @Composable {
      GlorianaButton(stringResource(Res.string.cancel), onCancel, buttonModifier, kind = ButtonKind.Ghost)
    }
    if (sheet) {
      confirm()
      cancel()
    } else {
      cancel()
      confirm()
    }
  }
}

@Composable
fun PresetDeleteQuestion(page: PresetPageState.Shown, onIntent: (PresetIntent) -> Unit) {
  val chats = when (val activity = page.session.activity) {
    is PresetActivity.ConfirmingDelete -> activity.chatsUsing
    is PresetActivity.Deleting -> activity.chatsUsing
    PresetActivity.Idle -> return
  }
  val deleting = page.session.activity is PresetActivity.Deleting
  val lines = listOfNotNull(
    stringResource(Res.string.delete_text, page.name),
    chats.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.delete_preset_chats, it, it) },
  )
  GlorianaConfirmation(
    Confirmation(
      title = stringResource(Res.string.delete_title, page.name),
      lines = lines,
      confirmLabel = stringResource(if (deleting) Res.string.deleting else Res.string.delete),
    ),
    onConfirm = { onIntent(PresetIntent.ConfirmDelete) },
    onDismiss = { onIntent(PresetIntent.CancelDelete) },
    busy = deleting,
  )
}

@Composable
fun WrappersPanel(page: PresetPageState.Shown, onOpen: (WrapperPrompt) -> Unit, modifier: Modifier = Modifier) {
  val wrappers = page.content.wrappers
  Panel(
    Res.drawable.ic_wrap_text,
    stringResource(Res.string.wrappers_panel),
    WrapperPromptsInOrder.map { prompt -> { WrapperRow(prompt, wrappers[prompt], { onOpen(prompt) }) } },
    modifier,
  )
}

@Composable
private fun WrapperRow(prompt: WrapperPrompt, text: String, onClick: () -> Unit) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type
  Row(
    Modifier.fillMaxWidth().tappable(GlorianaTheme.shapes.control, onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(stringResource(prompt.title), style = type.material.titleMedium, color = colors.text)
      if (text.isEmpty()) {
        Text(stringResource(Res.string.wrapper_empty), style = type.material.bodyMedium, color = colors.textSubtle)
      } else {
        Text(
          text,
          style = type.reading.copy(fontSize = type.material.bodyMedium.fontSize),
          color = colors.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Icon(painterResource(Res.drawable.ic_chevron_right), null, Modifier.size(ChevronSize), tint = colors.textMuted)
  }
}
