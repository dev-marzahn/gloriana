package dev.marzahn.gloriana.ui.messages

import dev.marzahn.gloriana.ui.navigation.ShellNavigator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class MessageAction(val label: String, val perform: (ShellNavigator) -> Unit)

class AppMessage(val text: String, val action: MessageAction? = null)

class AppMessages {
  private val queue = Channel<AppMessage>(Channel.UNLIMITED)

  val messages: Flow<AppMessage> = queue.receiveAsFlow()

  fun show(message: AppMessage) {
    queue.trySend(message)
  }
}
