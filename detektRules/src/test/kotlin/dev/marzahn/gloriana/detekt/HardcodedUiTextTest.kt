package dev.marzahn.gloriana.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class HardcodedUiTextTest {
  private val rule = HardcodedUiText(Config.empty)

  private fun literalsFoundIn(body: String): List<String> =
    rule.lint("fun screen() {\n$body\n}", compile = false).map { it.entity.ktElement.text }

  private fun assertFound(literal: String, body: String) = assertEquals(listOf(literal), literalsFoundIn(body))

  private fun assertPasses(body: String) = assertEquals(emptyList(), literalsFoundIn(body))

  @Test
  fun failsALiteralAsTextsFirstArgument() = assertFound("\"Hello\"", "Text(\"Hello\")")

  @Test
  fun failsALiteralNamedAsTextsText() = assertFound("\"Hello\"", "Text(modifier = Modifier, text = \"Hello\")")

  @Test
  fun failsALiteralAsBasicTextsFirstArgument() = assertFound("\"Hello\"", "BasicText(\"Hello\")")

  @Test
  fun failsATemplateWithWords() = assertFound("\"Hi \$name\"", "Text(\"Hi \$name\")")

  @Test
  fun failsALiteralJoinedWithPlus() = assertFound("\"Hi \"", "Text(\"Hi \" + name)")

  @Test
  fun failsALiteralAfterElvis() = assertFound("\"Anonymous\"", "Text(name ?: \"Anonymous\")")

  @Test
  fun failsEachIfBranchThatIsALiteral() =
    assertEquals(listOf("\"On\"", "\"Off\""), literalsFoundIn("Text(if (on) \"On\" else \"Off\")"))

  @Test
  fun failsAWhenBranchThatIsALiteral() =
    assertFound("\"One\"", "Text(when (count) { 1 -> \"One\"\n else -> stringResource(Res.string.many) })")

  @Test
  fun failsALiteralInAnAnnotatedString() = assertFound("\"Hello\"", "Text(AnnotatedString(\"Hello\"))")

  @Test
  fun failsALiteralAppendedInBuildAnnotatedString() =
    assertFound("\"Bold\"", "Text(buildAnnotatedString { withStyle(bold) { append(\"Bold\") } })")

  @Test
  fun failsALiteralFormattedAsTheReceiver() = assertFound("\"Hi %s\"", "Text(\"Hi %s\".format(name))")

  @Test
  fun failsALiteralTransformedByACall() = assertFound("\"Hello\"", "Text(\"Hello\".uppercase())")

  @Test
  fun failsAFullyQualifiedText() = assertFound("\"Hi\"", "androidx.compose.material3.Text(\"Hi\")")

  @Test
  fun failsAFormatWithWordsBesideItsSpecifier() = assertFound("\"%d items\"", "Text(\"%d items\".format(count))")

  @Test
  fun passesAFormatOfOnlySpecifiers() = assertPasses("Text(\"%s\".format(name))")

  @Test
  fun passesALiteralWhoseCallReturnsOtherText() = assertPasses("Text(\"abc\".length.toString())")

  @Test
  fun passesALiteralsHashCode() = assertPasses("Text(\"key\".hashCode().toString())")

  @Test
  fun failsALiteralPassedToPlus() = assertFound("\" says hi\"", "Text(name.plus(\" says hi\"))")

  @Test
  fun failsAPositionalContentDescriptionOfIcon() = assertFound("\"Close\"", "Icon(Icons.Close, \"Close\")")

  @Test
  fun failsAPositionalContentDescriptionOfImage() = assertFound("\"Cover\"", "Image(painter, \"Cover\")")

  @Test
  fun failsANamedContentDescriptionOnAnyCall() =
    assertFound("\"Menu\"", "MenuButton(onClick = {}, contentDescription = \"Menu\")")

  @Test
  fun failsAContentDescriptionAssignedInSemantics() =
    assertFound("\"Avatar\"", "Modifier.semantics { contentDescription = \"Avatar\" }")

  @Test
  fun failsAContentDescriptionAssignedOnAReceiver() = assertFound("\"Avatar\"", "view.contentDescription = \"Avatar\"")

  @Test
  fun failsAContentDescriptionAssignedOnThis() = assertFound("\"Avatar\"", "this.contentDescription = \"Avatar\"")

  @Test
  fun failsANotificationContentTitle() = assertFound("\"New message\"", "builder.setContentTitle(\"New message\")")

  @Test
  fun failsANotificationContentText() = assertFound("\"Open the chat\"", "builder.setContentText(\"Open the chat\")")

  @Test
  fun failsANotificationSubText() = assertFound("\"Chats\"", "builder.setSubText(\"Chats\")")

  @Test
  fun failsANotificationTicker() = assertFound("\"New message\"", "builder.setTicker(\"New message\")")

  @Test
  fun failsABigTextStyleText() = assertFound("\"Long text\"", "BigTextStyle().bigText(\"Long text\")")

  @Test
  fun failsABigContentTitle() = assertFound("\"Title\"", "style.setBigContentTitle(\"Title\")")

  @Test
  fun failsASummaryText() = assertFound("\"Summary\"", "style.setSummaryText(\"Summary\")")

  @Test
  fun failsANotificationActionTitle() = assertFound("\"Reply\"", "builder.addAction(icon, \"Reply\", intent)")

  @Test
  fun failsADesktopNotificationTitle() = assertFound("\"New message\"", "Notification(\"New message\", body)")

  @Test
  fun failsADesktopNotificationMessageByName() =
    assertFound("\"Open the chat\"", "Notification(title, message = \"Open the chat\")")

  @Test
  fun passesAStringResourceInText() = assertPasses("Text(stringResource(Res.string.library_empty))")

  @Test
  fun passesAStringResourceNamedAsText() = assertPasses("Text(text = stringResource(Res.string.library_empty))")

  @Test
  fun passesAStringResourceAsContentDescription() = assertPasses("Icon(Icons.Close, stringResource(Res.string.close))")

  @Test
  fun passesAStringResourceInANotification() = assertPasses("builder.setContentText(getString(Res.string.text))")

  @Test
  fun passesALiteralFormatArgumentOfAStringResource() =
    assertPasses("Text(stringResource(Res.string.greeting, \"arg\"))")

  @Test
  fun passesATestTag() = assertPasses("Icon(Icons.Close, null, Modifier.testTag(\"close\"))")

  @Test
  fun passesANamedTextOfAPlainConstructor() = assertPasses("val message = Message(text = \"Hello\")")

  @Test
  fun passesATextCallOnAReceiver() = assertPasses("send(Frame.Text(\"ping\"))")

  @Test
  fun passesANamedTextOfANodeFinder() = assertPasses("onNodeWithText(text = \"Hello\")")

  @Test
  fun passesAContentDescriptionComparison() = assertPasses("val same = contentDescription == \"Avatar\"")

  @Test
  fun passesAnEmptyString() = assertPasses("Text(\"\")")

  @Test
  fun passesATemplateWithNoWords() = assertPasses("Text(\"\$count\")")

  @Test
  fun passesPunctuationOnly() = assertPasses("Text(\" · \")")
}
