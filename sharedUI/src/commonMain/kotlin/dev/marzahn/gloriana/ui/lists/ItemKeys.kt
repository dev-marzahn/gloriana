package dev.marzahn.gloriana.ui.lists

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable

internal class ItemKeys : LazyListScope {
  val keys = mutableListOf<Any?>()

  override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
    keys += key
  }

  override fun items(
    count: Int,
    key: ((index: Int) -> Any)?,
    contentType: (index: Int) -> Any?,
    itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
  ) {
    repeat(count) { keys += key?.invoke(it) }
  }
}
