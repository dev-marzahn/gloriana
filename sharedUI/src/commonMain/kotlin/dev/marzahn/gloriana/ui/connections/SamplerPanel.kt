package dev.marzahn.gloriana.ui.connections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.ui.components.InfoLine
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.sampler_hint_global
import dev.marzahn.gloriana.ui.resources.sampler_hint_not_sent
import dev.marzahn.gloriana.ui.resources.sampler_kept
import dev.marzahn.gloriana.ui.resources.samplers_intro
import dev.marzahn.gloriana.ui.resources.samplers_pick_format
import dev.marzahn.gloriana.ui.theme.GlorianaTheme
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SamplerRows(page: ConnectionPageState.Shown, editing: FormEditing) {
  val standard = GlorianaTheme.motion.standard
  AnimatedContent(
    targetState = editing.form.format,
    transitionSpec = { fadeIn(standard.tween()) togetherWith fadeOut(standard.tween()) },
  ) { format ->
    if (format == null) {
      Text(
        stringResource(Res.string.samplers_pick_format),
        style = GlorianaTheme.type.material.bodyMedium,
        color = GlorianaTheme.colors.textMuted,
      )
    } else {
      FormatSamplers(page, editing, format)
    }
  }
}

@Composable
private fun FormatSamplers(page: ConnectionPageState.Shown, editing: FormEditing, format: ApiFormat) {
  val samplers = editing.form.samplers
  Column(verticalArrangement = Arrangement.spacedBy(GlorianaTheme.spacing.s14)) {
    Text(
      stringResource(Res.string.samplers_intro),
      style = GlorianaTheme.type.material.bodyMedium,
      color = GlorianaTheme.colors.textMuted,
    )
    format.samplerRanges.forEach { (sampler, range) ->
      val field = FormField.SamplerField(sampler)
      SamplerRow(
        sampler = sampler,
        text = samplers[sampler],
        hint = samplerHint(page, sampler, rangeText(range), samplers.isSet(sampler), format),
        error = (editing.error(field) as? FieldError.Sampler)?.error,
        onChange = { text -> editing.edit(field) { it.copy(samplers = it.samplers.with(sampler, text)) } },
        fieldModifier = Modifier.focusRequester(editing.focus(field)),
        enabled = editing.enabled,
      )
    }
    Sampler.entries.filter { it !in format.samplerRanges && samplers.holdsValue(it) }.forEach { sampler ->
      InfoLine(stringResource(Res.string.sampler_kept, stringResource(sampler.label), stringResource(format.label)))
    }
  }
}

@Composable
private fun samplerHint(
  page: ConnectionPageState.Shown,
  sampler: Sampler,
  range: String,
  isSet: Boolean,
  format: ApiFormat,
): String {
  val global = sampler.valueIn(page.globalSamplers)?.takeIf { format.samplerRanges[sampler]?.holds(it) == true }
  return when {
    isSet -> range
    global != null -> stringResource(Res.string.sampler_hint_global, range, global.shown())
    else -> stringResource(Res.string.sampler_hint_not_sent, range)
  }
}
