package dev.marzahn.gloriana.ui.connections

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.character_line_break
import dev.marzahn.gloriana.ui.resources.character_special
import dev.marzahn.gloriana.ui.resources.error_header_name_character
import dev.marzahn.gloriana.ui.resources.error_header_value_character
import dev.marzahn.gloriana.ui.resources.error_key_character
import org.jetbrains.compose.resources.getString
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SecretErrorTextTest {
  @Test
  fun aKeyOrHeaderValueErrorNamesTheKindOfCharacterNeverTheCharacter() = runComposeUiTest {
    setContent {
      Text(FieldError.KeyBadCharacter('é').text())
      Text(FieldError.HeaderValueBadCharacter('ß').text())
      Text(FieldError.KeyBadCharacter('\n').text())
      Text(FieldError.HeaderNameBadCharacter('é').text())
      Text(FieldError.KeyBadCharacter('"').text())
    }

    val special = getString(Res.string.character_special)
    onNode(hasText(getString(Res.string.error_header_value_character, special))).assertExists()
    onNode(hasText(getString(Res.string.error_key_character, getString(Res.string.character_line_break))))
      .assertExists()
    onNode(hasText(getString(Res.string.error_header_name_character, "é"))).assertExists()
    onAllNodes(hasText(getString(Res.string.error_key_character, special))).assertCountEquals(2)
    onAllNodes(hasText("\"", substring = true)).assertCountEquals(0)
  }
}
