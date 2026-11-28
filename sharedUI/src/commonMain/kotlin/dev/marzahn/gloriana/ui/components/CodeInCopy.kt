package dev.marzahn.gloriana.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val PRIVATE_USE_AREA_START = 0xE000

internal fun slot(index: Int): Char = Char(PRIVATE_USE_AREA_START + index)

fun code(text: String): AnnotatedString =
  buildAnnotatedString { withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(text) } }

@Composable
fun textWithCode(resource: StringResource, code: String): AnnotatedString = textWith(resource, code(code))

@Composable
fun textWith(resource: StringResource, vararg parts: AnnotatedString): AnnotatedString =
  spliced(stringResource(resource, *Array(parts.size) { slot(it).toString() }), parts.toList())

internal fun spliced(template: String, parts: List<AnnotatedString>): AnnotatedString = buildAnnotatedString {
  template.forEach { char ->
    val part = parts.getOrNull(char - slot(0))
    if (part != null) append(part) else append(char)
  }
}
