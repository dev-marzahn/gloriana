package dev.marzahn.gloriana.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.delete_failed
import dev.marzahn.gloriana.ui.resources.delete_failed_detail
import dev.marzahn.gloriana.ui.resources.ic_check_20
import dev.marzahn.gloriana.ui.resources.ic_refresh_20
import dev.marzahn.gloriana.ui.resources.not_saved
import dev.marzahn.gloriana.ui.resources.not_saved_storage
import dev.marzahn.gloriana.ui.resources.retry
import dev.marzahn.gloriana.ui.resources.save
import dev.marzahn.gloriana.ui.resources.saving
import dev.marzahn.gloriana.ui.state.SaveFailure
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.Navigation
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val HeaderTile = 56.dp
private val HeaderIcon = 28.dp
private val SkeletonName = 180.dp
private val SkeletonMeta = 120.dp
private val SkeletonButtonHeight = 40.dp
private val SkeletonButtonWidth = 96.dp
private val SkeletonPanel = 220.dp

enum class BackButtonShown { BelowTheWorkbench, OnEveryWidth }

class PageBack(
  val label: String,
  val onBack: () -> Unit,
  val shown: BackButtonShown = BackButtonShown.BelowTheWorkbench,
)

sealed interface PageName {
  data class Named(val name: String) : PageName

  data class Unnamed(val placeholder: String) : PageName

  companion object {
    fun of(name: String, placeholder: String): PageName = if (name.isBlank()) Unnamed(placeholder) else Named(name)
  }
}

@Composable
fun DetailPage(back: PageBack, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  val spacing = GlorianaTheme.spacing
  val onWorkbench = GlorianaTheme.widthClass.navigation == Navigation.Workbench
  Column(
    modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (onWorkbench) spacing.s20 else spacing.s16),
    verticalArrangement = Arrangement.spacedBy(spacing.s16),
  ) {
    if (!onWorkbench || back.shown == BackButtonShown.OnEveryWidth) BackButton(back.label, back.onBack)
    content()
  }
}

@Composable
fun DetailHeader(
  icon: DrawableResource,
  name: PageName,
  meta: @Composable FlowRowScope.() -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit = {},
) {
  DetailHeader(
    tile = {
      Box(
        Modifier.size(HeaderTile).background(GlorianaTheme.colors.surface2, GlorianaTheme.shapes.panel),
        contentAlignment = Alignment.Center,
      ) {
        Icon(painterResource(icon), null, Modifier.size(HeaderIcon), tint = GlorianaTheme.colors.textMuted)
      }
    },
    name = name,
    meta = meta,
    modifier = modifier,
    actions = actions,
  )
}

@Composable
fun DetailHeader(
  tile: @Composable () -> Unit,
  name: PageName,
  meta: @Composable FlowRowScope.() -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit = {},
) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  val spacing = GlorianaTheme.spacing
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing.s14)) {
    tile()
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.s6)) {
      when (name) {
        is PageName.Named -> Text(name.name, style = type.headlineLarge, color = colors.text)
        is PageName.Unnamed -> Text(name.placeholder, style = type.headlineLarge, color = colors.textSubtle)
      }
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.s6),
        verticalArrangement = Arrangement.spacedBy(spacing.s6),
        content = meta,
      )
    }
    actions()
  }
}

@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier, intro: String? = null) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  Column(modifier, verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s6)) {
    Text(title, style = type.headlineLarge, color = colors.text)
    if (intro != null) Text(intro, style = type.bodyMedium, color = colors.textMuted)
  }
}

@Composable
fun SaveButton(saving: Boolean, enabled: Boolean, onSave: () -> Unit, modifier: Modifier = Modifier) {
  GlorianaButton(
    stringResource(if (saving) Res.string.saving else Res.string.save),
    onSave,
    modifier,
    kind = ButtonKind.Primary,
    icon = Res.drawable.ic_check_20,
    enabled = enabled,
  )
}

@Composable
fun NotDeletedNote(name: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
  NoteCard(modifier) {
    NoteLine(
      boldThenPlain(stringResource(Res.string.delete_failed, name), stringResource(Res.string.delete_failed_detail)),
    )
    GlorianaButton(stringResource(Res.string.retry), onRetry, icon = Res.drawable.ic_refresh_20)
  }
}

class NotSavedLine(val headline: StringResource, val detail: StringResource)

class NotSavedCopy(val storageFull: NotSavedLine, val other: NotSavedLine) {
  companion object {
    fun forANamedItem(storageFullDetail: StringResource, otherDetail: StringResource) = NotSavedCopy(
      NotSavedLine(Res.string.not_saved_storage, storageFullDetail),
      NotSavedLine(Res.string.not_saved, otherDetail),
    )
  }
}

@Composable
fun notSavedText(failure: SaveFailure, copy: NotSavedCopy, vararg headlineArgs: Any): AnnotatedString {
  val line = when (failure) {
    SaveFailure.Storage -> copy.storageFull
    SaveFailure.Other -> copy.other
  }
  return boldThenPlain(stringResource(line.headline, *headlineArgs), stringResource(line.detail))
}

@Composable
fun NotSavedNote(failure: SaveFailure, copy: NotSavedCopy, name: String, modifier: Modifier = Modifier) {
  NoteCard(modifier) { NoteLine(notSavedText(failure, copy, name)) }
}

enum class SkeletonHeader { Tile, None }

@Composable
fun DetailSkeleton(header: SkeletonHeader, buttons: Int, modifier: Modifier = Modifier) {
  val spacing = GlorianaTheme.spacing
  Skeleton(modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s16)) {
      if (header == SkeletonHeader.Tile) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s14)) {
          SkeletonBlock(Modifier.size(HeaderTile))
          Column(verticalArrangement = Arrangement.spacedBy(spacing.s8)) {
            SkeletonBlock(Modifier.size(width = SkeletonName, height = spacing.s20))
            SkeletonBlock(Modifier.size(width = SkeletonMeta, height = spacing.s12))
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(spacing.s8)) {
        repeat(buttons) { SkeletonBlock(Modifier.size(width = SkeletonButtonWidth, height = SkeletonButtonHeight)) }
      }
      SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonPanel), on = SkeletonSurface.Raised)
    }
  }
}
