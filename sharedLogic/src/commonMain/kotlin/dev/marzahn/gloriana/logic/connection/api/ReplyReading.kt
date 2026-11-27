package dev.marzahn.gloriana.logic.connection.api

internal data class ReplyState(
  val promptTokens: Long? = null,
  val replyTokens: Long? = null,
  val reason: FinishReason? = null,
  val refused: Boolean = false,
) {
  fun closing(fallback: FinishReason): List<ModelEvent> {
    val usage = if (promptTokens == null && replyTokens == null) null else ModelEvent.Usage(promptTokens, replyTokens)
    val reason = reason ?: fallback
    val shown = if (refused && reason == FinishReason.Completed) FinishReason.Refused else reason
    return listOfNotNull(usage, ModelEvent.Finish(shown))
  }
}

internal abstract class StreamReader(private val scrubber: Scrubber) {
  private var state = ReplyState()
  var over = false
    private set

  fun read(data: String): List<ModelEvent> = if (over) emptyList() else readEvent(data)

  fun failed(error: ModelApiError): List<ModelEvent> =
    if (over) emptyList() else ending(listOf(ModelEvent.Failed(error)))

  fun ended(): List<ModelEvent> = if (over) emptyList() else ending(state.closing(FinishReason.Unfinished))

  protected abstract fun readEvent(data: String): List<ModelEvent>

  protected fun update(change: (ReplyState) -> ReplyState) {
    state = change(state)
  }

  protected fun finished(): List<ModelEvent> = ending(state.closing(FinishReason.Completed))

  protected fun hostFailed(data: String): List<ModelEvent> =
    failed(ModelApiError.HostError(status = null, body = scrubber.scrub(data)))

  private fun ending(events: List<ModelEvent>): List<ModelEvent> {
    over = true
    return events
  }
}
