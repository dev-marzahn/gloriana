package dev.marzahn.gloriana.detekt

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression

private const val CONTENT_DESCRIPTION = "contentDescription"

private enum class CallShape { Unqualified, OnAnyReceiver }

private class UiTextParameter(val name: String, val position: Int)

private class UiTextFunction(val shape: CallShape, val parameters: List<UiTextParameter>)

private fun unqualified(vararg parameters: UiTextParameter) = UiTextFunction(CallShape.Unqualified, parameters.toList())

private fun onAnyReceiver(vararg parameters: UiTextParameter) =
  UiTextFunction(CallShape.OnAnyReceiver, parameters.toList())

private val uiTextFunctions = mapOf(
  "Text" to unqualified(UiTextParameter("text", 0)),
  "BasicText" to unqualified(UiTextParameter("text", 0)),
  "Icon" to unqualified(UiTextParameter(CONTENT_DESCRIPTION, 1)),
  "Image" to unqualified(UiTextParameter(CONTENT_DESCRIPTION, 1)),
  "Notification" to unqualified(UiTextParameter("title", 0), UiTextParameter("message", 1)),
  "setContentTitle" to onAnyReceiver(UiTextParameter("title", 0)),
  "setContentText" to onAnyReceiver(UiTextParameter("text", 0)),
  "setSubText" to onAnyReceiver(UiTextParameter("text", 0)),
  "setTicker" to onAnyReceiver(UiTextParameter("tickerText", 0)),
  "setBigContentTitle" to onAnyReceiver(UiTextParameter("title", 0)),
  "setSummaryText" to onAnyReceiver(UiTextParameter("cs", 0)),
  "bigText" to onAnyReceiver(UiTextParameter("cs", 0)),
  "addAction" to onAnyReceiver(UiTextParameter("title", 1)),
)

internal fun KtCallExpression.uiTextArguments(): List<KtExpression> {
  val parameters = uiTextFunction()?.parameters.orEmpty()
  return valueArguments.withIndex().mapNotNull { (position, argument) ->
    val name = argument.getArgumentName()?.asName?.asString()
    val carriesUiText = name == CONTENT_DESCRIPTION ||
      parameters.any { parameter -> if (name == null) parameter.position == position else parameter.name == name }
    argument.getArgumentExpression()?.takeIf { carriesUiText }
  }
}

internal fun KtBinaryExpression.assignsContentDescription(): Boolean {
  val target = (left as? KtQualifiedExpression)?.selectorExpression ?: left
  val targetName = (target as? KtNameReferenceExpression)?.getReferencedName()
  return operationToken == KtTokens.EQ && targetName == CONTENT_DESCRIPTION
}

private fun KtCallExpression.uiTextFunction(): UiTextFunction? {
  val function = uiTextFunctions[calleeExpression?.text] ?: return null
  val receiver = (parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression == this }?.receiverExpression
  val callsTheFunctionItself = receiver == null || receiver.isPackagePath()
  return function.takeIf { it.shape == CallShape.OnAnyReceiver || callsTheFunctionItself }
}

private fun KtExpression.isPackagePath(): Boolean = when (this) {
  is KtNameReferenceExpression -> getReferencedName().first().isLowerCase()
  is KtDotQualifiedExpression -> receiverExpression.isPackagePath() && selectorExpression?.isPackagePath() == true
  else -> false
}
