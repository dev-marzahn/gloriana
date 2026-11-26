package dev.marzahn.gloriana.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression

class HardcodedUiText(config: Config) : Rule(
  config,
  "Text the UI shows comes from string resources, so the app can be translated without code changes.",
) {
  override fun visitCallExpression(expression: KtCallExpression) {
    super.visitCallExpression(expression)
    expression.uiTextArguments().forEach(::reportLiterals)
  }

  override fun visitBinaryExpression(expression: KtBinaryExpression) {
    super.visitBinaryExpression(expression)
    if (expression.assignsContentDescription()) reportLiterals(expression.right)
  }

  private fun reportLiterals(value: KtExpression?) {
    translatableLiterals(value).forEach { literal -> report(Finding(Entity.from(literal), MESSAGE)) }
  }

  companion object {
    const val NAME = "HardcodedUiText"
    private const val MESSAGE = "UI text is a string literal: use stringResource(Res.string.…) instead."
  }
}
