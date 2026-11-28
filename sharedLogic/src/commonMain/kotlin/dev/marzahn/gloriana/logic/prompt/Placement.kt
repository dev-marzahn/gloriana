package dev.marzahn.gloriana.logic.prompt

internal fun place(gathering: Gathering): List<AssembledSection> = gathering.inOrder.flatMap { gathered ->
  when (gathered) {
    is Gathered.Fixed -> listOf(gathered.section)
    is Gathered.History -> listOfNotNull(gathered.opening) + withInChat(gathered.messages, gathering.inChat)
  }
}

private fun withInChat(messages: List<AssembledSection>, inChat: List<InChatSection>): List<AssembledSection> {
  val atPoint = inChat.groupBy { (messages.size - it.depth).coerceIn(0, messages.size) }
  return (0..messages.size).flatMap { point ->
    atPoint[point].orEmpty().map { it.section } + listOfNotNull(messages.getOrNull(point))
  }
}
