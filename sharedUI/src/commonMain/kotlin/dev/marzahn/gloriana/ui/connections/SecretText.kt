package dev.marzahn.gloriana.ui.connections

import kotlin.jvm.JvmInline

@JvmInline
value class SecretText(val text: String) {
  val isEmpty: Boolean get() = text.isEmpty()

  override fun toString() = "SecretText(•••)"

  companion object {
    val Empty = SecretText("")
  }
}
