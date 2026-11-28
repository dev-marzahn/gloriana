package dev.marzahn.gloriana.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeInCopyTest {
  @Test
  fun theCodeAloneIsSetInMonospace() {
    val template = "Sends ${slot(0)}. Turn it off if the host refuses it."
    val text = spliced(template, listOf(code("stream_options.include_usage")))

    assertEquals("Sends stream_options.include_usage. Turn it off if the host refuses it.", text.text)
    val span = text.spanStyles.single()
    assertEquals(FontFamily.Monospace, span.item.fontFamily)
    assertEquals("stream_options.include_usage", text.text.substring(span.start, span.end))
  }

  @Test
  fun aTranslationThatMovesTheCodeKeepsItWhole() {
    val text = spliced("${slot(0)} entfernt einen Schlüssel.", listOf(code("null")))

    assertEquals("null entfernt einen Schlüssel.", text.text)
    assertEquals(0 until 4, text.spanStyles.single().let { it.start until it.end })
  }

  @Test
  fun aSentenceCarriesTheCodeOfThePartsItWraps() {
    val reason = spliced("${slot(0)} in the custom body isn't true or false", listOf(code("stream")))
    val sentence = spliced("${slot(0)} ${slot(1)}", listOf(reason, AnnotatedString("Fix it, then try again.")))

    assertEquals("stream in the custom body isn't true or false Fix it, then try again.", sentence.text)
    assertEquals(0 until 6, sentence.spanStyles.single().let { it.start until it.end })
  }
}
