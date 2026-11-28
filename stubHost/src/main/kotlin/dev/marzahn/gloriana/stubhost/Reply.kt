package dev.marzahn.gloriana.stubhost

const val PLAIN_REPLY = "Hello from the stub host. This reply arrives in parts."

private val pieceBoundary = Regex("(?<=\\s)")

internal sealed class Segment {
  abstract val text: String
  val pieces: List<String> by lazy { text.split(pieceBoundary).filter(String::isNotEmpty) }

  data class Reasoning(override val text: String) : Segment()

  data class Text(override val text: String) : Segment()

  data class Refusal(override val text: String) : Segment()
}

internal data class Tokens(val input: Int, val output: Int, val reasoning: Int) {
  val total: Int get() = input + output
}

internal data class Reply(val segments: List<Segment>, val hitLimit: Boolean = false) {
  constructor(vararg segments: Segment) : this(segments.toList())

  val refused: Boolean = segments.any { it is Segment.Refusal }
  val tokens: Tokens = Tokens(
    input = PROMPT_TOKENS,
    output = segments.sumOf { it.pieces.size },
    reasoning = segments.filterIsInstance<Segment.Reasoning>().sumOf { it.pieces.size },
  )

  companion object {
    private const val PROMPT_TOKENS = 10

    val plain = Reply(Segment.Text(PLAIN_REPLY))
    val withReasoning = Reply(
      Segment.Reasoning("The user greeted me, so I greet them back."),
      Segment.Text("Hello again from the stub host."),
    )
    val withStatus = Reply(
      Segment.Text("The scene goes on.\n\n<details><summary>Status</summary>Health: 10/10\nMood: calm</details>"),
    )
    val refusal = Reply(Segment.Refusal("I can't help with that request."))
    val limited = Reply(listOf(Segment.Text("This reply stops at the token limit")), hitLimit = true)
  }
}
