package dev.marzahn.gloriana.ui.presets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.presets.NotCopied
import dev.marzahn.gloriana.logic.presets.NotCopiedBecause
import dev.marzahn.gloriana.logic.presets.PresetSampler
import dev.marzahn.gloriana.logic.presets.PresetSamplers
import dev.marzahn.gloriana.logic.presets.SamplerChange
import dev.marzahn.gloriana.logic.presets.SamplerDifference
import dev.marzahn.gloriana.logic.presets.SentValue
import dev.marzahn.gloriana.ui.components.ButtonKind
import dev.marzahn.gloriana.ui.components.GlorianaButton
import dev.marzahn.gloriana.ui.components.GlorianaDropdownField
import dev.marzahn.gloriana.ui.components.GlorianaSheet
import dev.marzahn.gloriana.ui.components.SheetButtons
import dev.marzahn.gloriana.ui.connections.label
import dev.marzahn.gloriana.ui.connections.rangeText
import dev.marzahn.gloriana.ui.connections.shown
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.done
import dev.marzahn.gloriana.ui.resources.ic_arrow_forward_20
import dev.marzahn.gloriana.ui.resources.ic_check_20
import dev.marzahn.gloriana.ui.resources.ic_check_circle_20
import dev.marzahn.gloriana.ui.resources.joined_by_dot
import dev.marzahn.gloriana.ui.resources.offer_already_matching
import dev.marzahn.gloriana.ui.resources.offer_changes_on
import dev.marzahn.gloriana.ui.resources.offer_choice_default
import dev.marzahn.gloriana.ui.resources.offer_copy
import dev.marzahn.gloriana.ui.resources.offer_copying
import dev.marzahn.gloriana.ui.resources.offer_dont_copy
import dev.marzahn.gloriana.ui.resources.offer_format_takes_none
import dev.marzahn.gloriana.ui.resources.offer_from_model_settings
import dev.marzahn.gloriana.ui.resources.offer_not_copied
import dev.marzahn.gloriana.ui.resources.offer_not_set
import dev.marzahn.gloriana.ui.resources.offer_out_of_range
import dev.marzahn.gloriana.ui.resources.offer_sampler_value
import dev.marzahn.gloriana.ui.resources.offer_sampler_value_range
import dev.marzahn.gloriana.ui.resources.offer_text
import dev.marzahn.gloriana.ui.resources.offer_title
import dev.marzahn.gloriana.ui.resources.sampler_min_p
import dev.marzahn.gloriana.ui.resources.sampler_repetition_penalty
import dev.marzahn.gloriana.ui.resources.sampler_top_a
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import dev.marzahn.gloriana.ui.theme.SheetStyle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val ArrowSize = 16.dp
private val MatchIconSize = 20.dp
private const val TABULAR_FIGURES = "tnum"

private val PresetSampler.label: StringResource
  get() = when (this) {
    is PresetSampler.OnConnection -> sampler.label
    PresetSampler.WithoutConnectionField.TopA -> Res.string.sampler_top_a
    PresetSampler.WithoutConnectionField.MinP -> Res.string.sampler_min_p
    PresetSampler.WithoutConnectionField.RepetitionPenalty -> Res.string.sampler_repetition_penalty
  }

private val PresetSampler.fileOrder: Int
  get() = when (this) {
    is PresetSampler.OnConnection -> sampler.ordinal
    is PresetSampler.WithoutConnectionField -> Sampler.entries.size + ordinal
  }

@Composable
fun SamplerOfferSheet(
  offer: SamplerOfferState,
  onPick: (OfferChoice) -> Unit,
  onCopy: () -> Unit,
  onDecline: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val spacing = GlorianaTheme.spacing
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  val motion = GlorianaTheme.motion
  GlorianaSheet(onDismiss = { if (!offer.copying) onDecline() }, modifier, dismissible = !offer.copying) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s12)) {
      Text(stringResource(Res.string.offer_title), style = type.headlineSmall, color = colors.text)
      Text(stringResource(Res.string.offer_text, offer.presetName), style = type.bodyMedium, color = colors.textMuted)
      GlorianaDropdownField(
        selected = offer.picked,
        options = offer.choices,
        label = { choice ->
          if (choice.isDefault) stringResource(Res.string.offer_choice_default, choice.name) else choice.name
        },
        onSelect = onPick,
        placeholder = offer.picked.name,
        enabled = !offer.copying,
        detail = { stringResource(it.format.label) },
      )
      AnimatedContent(
        offer.difference,
        transitionSpec = { fadeIn(motion.standard.tween()) togetherWith fadeOut(motion.standard.tween()) },
        contentKey = { it?.connectionId },
      ) { difference ->
        if (difference != null) DifferenceList(difference, offer.samplers, offer.picked)
      }
      OfferButtons(offer, onCopy, onDecline)
    }
  }
}

@Composable
private fun DifferenceList(difference: SamplerDifference, values: PresetSamplers, picked: OfferChoice) {
  val spacing = GlorianaTheme.spacing
  Column(verticalArrangement = Arrangement.spacedBy(spacing.s8)) {
    if (difference.connectionMatches) {
      AlreadyMatching(picked.name)
    } else {
      GroupHeader(stringResource(Res.string.offer_changes_on, picked.name))
      difference.changes.forEach { ChangeRow(it) }
    }
    NotCopiedLines(difference.notCopied, values, picked.format)
  }
}

@Composable
private fun GroupHeader(text: String) {
  Text(
    text,
    Modifier.padding(top = GlorianaTheme.spacing.s8),
    style = GlorianaTheme.type.material.labelLarge,
    color = GlorianaTheme.colors.textSubtle,
  )
}

@Composable
private fun ChangeRow(change: SamplerChange) {
  val colors = GlorianaTheme.colors
  val type = GlorianaTheme.type.material
  val spacing = GlorianaTheme.spacing
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(stringResource(change.sampler.label), Modifier.weight(1f), style = type.bodyLarge, color = colors.text)
    Column(horizontalAlignment = Alignment.End) {
      val now = when (val sent = change.sentNow) {
        is SentValue.Own -> sent.value.shown()
        is SentValue.Global -> sent.value.shown()
        SentValue.NotSet -> stringResource(Res.string.offer_not_set)
      }
      Text(now, style = type.bodyMedium.copy(fontFeatureSettings = TABULAR_FIGURES), color = colors.textSubtle)
      if (change.sentNow is SentValue.Global) {
        Text(stringResource(Res.string.offer_from_model_settings), style = type.bodySmall, color = colors.textSubtle)
      }
    }
    Icon(
      painterResource(Res.drawable.ic_arrow_forward_20),
      null,
      Modifier.padding(horizontal = spacing.s8).size(ArrowSize),
      tint = colors.textSubtle,
    )
    Text(
      change.presetValue.shown(),
      style = type.titleMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
      color = colors.text,
    )
  }
}

@Composable
private fun AlreadyMatching(connectionName: String) {
  val colors = GlorianaTheme.colors
  val shape = GlorianaTheme.shapes.control
  val spacing = GlorianaTheme.spacing
  Row(
    Modifier
      .fillMaxWidth()
      .background(colors.surface2, shape)
      .border(1.dp, colors.borderSubtle, shape)
      .padding(spacing.s12),
    horizontalArrangement = Arrangement.spacedBy(spacing.s10),
  ) {
    Icon(painterResource(Res.drawable.ic_check_circle_20), null, Modifier.size(MatchIconSize), tint = colors.textMuted)
    Text(
      stringResource(Res.string.offer_already_matching, connectionName),
      style = GlorianaTheme.type.material.bodyMedium,
      color = colors.text,
    )
  }
}

@Composable
private fun NotCopiedLines(
  notCopied: List<NotCopied>,
  values: PresetSamplers,
  format: ApiFormat,
  modifier: Modifier = Modifier,
) {
  val sorted = notCopied.sortedBy { it.sampler.fileOrder }
  val formatName = stringResource(format.label)
  val takesNone = sorted.filter { it.because == NotCopiedBecause.FormatSendsNone }
  val outOfRange = sorted.filter { it.because == NotCopiedBecause.OutOfRange }
  if (takesNone.isEmpty() && outOfRange.isEmpty()) return
  val style = GlorianaTheme.type.material.bodyMedium
  val color = GlorianaTheme.colors.textMuted
  Column(modifier, verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s8)) {
    GroupHeader(stringResource(Res.string.offer_not_copied))
    if (takesNone.isNotEmpty()) {
      val line = joined(takesNone.map { valueText(it, values, format = null) })
      Text(stringResource(Res.string.offer_format_takes_none, formatName, line), style = style, color = color)
    }
    if (outOfRange.isNotEmpty()) {
      val line = joined(outOfRange.map { valueText(it, values, format) })
      Text(stringResource(Res.string.offer_out_of_range, formatName, line), style = style, color = color)
    }
  }
}

@Composable
private fun joined(parts: List<String>): String = parts.reduce { joined, part ->
  stringResource(Res.string.joined_by_dot, joined, part)
}

@Composable
private fun valueText(notCopied: NotCopied, values: PresetSamplers, format: ApiFormat?): String {
  val name = stringResource(notCopied.sampler.label)
  val value = values.values[notCopied.sampler]?.shown().orEmpty()
  val range = (notCopied.sampler as? PresetSampler.OnConnection)?.sampler?.let { format?.samplerRanges?.get(it) }
  return if (range == null) {
    stringResource(Res.string.offer_sampler_value, name, value)
  } else {
    stringResource(Res.string.offer_sampler_value_range, name, value, rangeText(range))
  }
}

@Composable
private fun OfferButtons(offer: SamplerOfferState, onCopy: () -> Unit, onDecline: () -> Unit) {
  val declineFocus = remember { FocusRequester() }
  val sheet = GlorianaTheme.widthClass.sheetStyle == SheetStyle.BottomSheet
  val difference = offer.difference
  val matching = difference?.connectionMatches == true
  SheetButtons(Modifier.padding(top = GlorianaTheme.spacing.s12)) { buttonModifier ->
    val copy = @Composable {
      val count = difference?.changes?.size ?: 0
      val label = if (offer.copying) {
        stringResource(Res.string.offer_copying)
      } else {
        pluralStringResource(Res.plurals.offer_copy, count, count)
      }
      GlorianaButton(
        label,
        onCopy,
        buttonModifier,
        kind = ButtonKind.Primary,
        icon = Res.drawable.ic_check_20,
        enabled = difference != null && !offer.copying,
      )
    }
    val decline = @Composable {
      GlorianaButton(
        stringResource(Res.string.offer_dont_copy),
        onDecline,
        buttonModifier.focusRequester(declineFocus),
        kind = ButtonKind.Ghost,
        enabled = !offer.copying,
      )
    }
    when {
      matching -> GlorianaButton(
        stringResource(Res.string.done),
        onDecline,
        buttonModifier.focusRequester(declineFocus),
        kind = ButtonKind.Primary,
        icon = Res.drawable.ic_check_20,
      )
      sheet -> {
        copy()
        decline()
      }
      else -> {
        decline()
        copy()
      }
    }
  }
  LaunchedEffect(matching) { declineFocus.requestFocus() }
}
