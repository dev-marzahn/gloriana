package dev.marzahn.gloriana.detekt

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

internal fun translatableLiterals(value: KtExpression?): List<KtStringTemplateExpression> =
  literalsReaching(value).filter { it.hasWords() }

private fun KtStringTemplateExpression.hasWords() =
  entries.any { it is KtLiteralStringTemplateEntry && it.text.replace(formatSpecifier, "").any(Char::isLetter) }

private val formatSpecifier = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z%]""")

private val textKeepingCalls = setOf(
  "format", "uppercase", "lowercase", "plus", "trim", "trimStart", "trimEnd", "repeat", "padStart", "padEnd",
)

private fun literalsReaching(value: KtExpression?): List<KtStringTemplateExpression> = when (value) {
  is KtStringTemplateExpression -> listOf(value)
  is KtParenthesizedExpression -> literalsReaching(value.expression)
  is KtBlockExpression -> literalsReaching(value.statements.lastOrNull())
  is KtIfExpression -> literalsReaching(value.then) + literalsReaching(value.`else`)
  is KtWhenExpression -> value.entries.flatMap { literalsReaching(it.expression) }
  is KtBinaryExpression -> if (value.operationToken in joiningOperators) {
    literalsReaching(value.left) + literalsReaching(value.right)
  } else {
    emptyList()
  }
  is KtQualifiedExpression -> literalsThroughCall(value)
  is KtCallExpression -> value.textItCarries().flatMap(::literalsReaching)
  else -> emptyList()
}

private fun literalsThroughCall(value: KtQualifiedExpression): List<KtStringTemplateExpression> {
  val call = value.selectorExpression as? KtCallExpression ?: return emptyList()
  val keepsReceiversText = call.calleeExpression?.text in textKeepingCalls
  val fromReceiver = if (keepsReceiversText) literalsReaching(value.receiverExpression) else emptyList()
  return fromReceiver + literalsReaching(call)
}

private val joiningOperators = setOf(KtTokens.PLUS, KtTokens.ELVIS)

private fun KtCallExpression.textItCarries(): List<KtExpression?> = when (calleeExpression?.text) {
  "AnnotatedString", "plus" -> listOf(firstArgument())
  "buildAnnotatedString" -> lambdaArguments
    .flatMap { it.collectDescendantsOfType<KtCallExpression> { call -> call.calleeExpression?.text == "append" } }
    .map { it.firstArgument() }
  else -> emptyList()
}

private fun KtCallExpression.firstArgument() = valueArguments.firstOrNull()?.getArgumentExpression()
