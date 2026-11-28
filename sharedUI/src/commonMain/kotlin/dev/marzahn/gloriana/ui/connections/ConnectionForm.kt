package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionDraft
import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.ConnectionProblem
import dev.marzahn.gloriana.logic.connection.ConnectionTemplate
import dev.marzahn.gloriana.logic.connection.CustomBodyText
import dev.marzahn.gloriana.logic.connection.HeaderDraft
import dev.marzahn.gloriana.logic.connection.HeaderValueInput
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.connection.customBodyText
import dev.marzahn.gloriana.logic.connection.numberProblems
import dev.marzahn.gloriana.logic.connection.problems
import dev.marzahn.gloriana.logic.connection.samplerProblems
import dev.marzahn.gloriana.logic.connection.textProblems
import dev.marzahn.gloriana.logic.connection.withCustomBody
@ConsistentCopyVisibility
data class TypedSecret private constructor(val text: SecretText) : KeyEdit, ValueEdit {
  override val isEntering: Boolean get() = true

  override val typedText: SecretText get() = text

  companion object {
    fun of(text: SecretText): TypedSecret? = if (text.isEmpty) null else TypedSecret(text)
  }
}

sealed interface KeyEdit {
  data object Kept : KeyEdit

  data object Removed : KeyEdit

  data object Entering : KeyEdit

  val isEntering: Boolean get() = this == Entering

  val typedText: SecretText get() = SecretText.Empty

  companion object {
    fun typing(text: SecretText): KeyEdit = TypedSecret.of(text) ?: Entering
  }
}

sealed interface ValueEdit {
  data object Kept : ValueEdit

  data object Entering : ValueEdit

  val isEntering: Boolean get() = this == Entering

  val typedText: SecretText get() = SecretText.Empty

  companion object {
    fun typing(text: SecretText): ValueEdit = TypedSecret.of(text) ?: Entering
  }
}

data class HeaderRow(val id: Int, val name: String, val storedName: String?, val value: ValueEdit) {
  val isBlank: Boolean get() = storedName == null && name.isEmpty() && value == ValueEdit.Entering

  fun needsKeysToBecome(after: HeaderRow): Boolean = renamesStoredValue(after) || startsEnteringValue(after)

  fun valueInput(): HeaderValueInput = when (value) {
    ValueEdit.Kept -> HeaderValueInput.Kept(storedName.orEmpty())
    ValueEdit.Entering -> storedName?.let(HeaderValueInput::Kept) ?: HeaderValueInput.Entered("")
    is TypedSecret -> HeaderValueInput.Entered(value.text.text)
  }

  private fun renamesStoredValue(after: HeaderRow) = storedName != null && after.name != name

  private fun startsEnteringValue(after: HeaderRow) = after.value.isEntering && after.value != value &&
    (value == ValueEdit.Kept || value == ValueEdit.Entering)
}

data class ConnectionForm(
  val name: String,
  val format: ApiFormat?,
  val baseUrl: String,
  val model: String,
  val contextSize: String,
  val maxReplyTokens: String,
  val streaming: Boolean,
  val includeUsage: Boolean,
  val samplers: SamplerTexts,
  val timeout: String,
  val customBody: String,
  val keyStored: Boolean,
  val key: KeyEdit,
  val headers: List<HeaderRow>,
) {
  val keptHeaders: List<HeaderRow> get() = headers.filterNot { it.isBlank }

  val promptBudget: Int?
    get() {
      val context = contextSize.wholeNumber() ?: return null
      val reply = maxReplyTokens.wholeNumber() ?: return null
      return (context - reply).takeIf { it > 0 }
    }

  val keyContext: KeyContext
    get() = when (keyInput()) {
      is KeyInput.Entered -> KeyContext.TypedKey
      KeyInput.Kept -> if (keyStored) KeyContext.SavedKey else headersOrNoKey()
      KeyInput.Removed -> headersOrNoKey()
    }

  fun needsKeysToBecome(after: ConnectionForm): Boolean = startsEnteringKey(after) || after.headers.any { row ->
    headers.firstOrNull { it.id == row.id }?.needsKeysToBecome(row) == true
  }

  private fun startsEnteringKey(after: ConnectionForm) = after.key.isEntering && after.key != key &&
    (key == KeyEdit.Kept || key == KeyEdit.Entering)

  fun withHeader() = copy(headers = headers + HeaderRow(nextHeaderId(), "", null, ValueEdit.Entering))

  fun withoutHeader(id: Int) = copy(headers = headers.filterNot { it.id == id })

  fun withHeaderChanged(id: Int, change: (HeaderRow) -> HeaderRow) =
    copy(headers = headers.map { if (it.id == id) change(it) else it })

  fun sameAs(other: ConnectionForm): Boolean =
    comparable() == other.comparable() && samplers.sameAs(other.samplers)

  private fun headersOrNoKey() = if (keptHeaders.isNotEmpty()) KeyContext.HeadersOnly else KeyContext.NoKey

  private fun nextHeaderId(): Int = (headers.maxOfOrNull { it.id } ?: -1) + 1

  private fun comparable() = ComparedForm(
    fields = copy(
      contextSize = contextSize.byValue(),
      maxReplyTokens = maxReplyTokens.byValue(),
      timeout = timeout.byValue(),
      samplers = SamplerTexts(emptyMap()),
      key = KeyEdit.Kept,
      headers = emptyList(),
    ),
    key = keyInput(),
    headers = keptHeaders.map { it.draft() },
  )

  fun withHeaderIdsOf(vararg earlier: ConnectionForm?): ConnectionForm {
    val earlierRows = earlier.flatMap { it?.headers.orEmpty() }
    val earlierIds = earlierRows.map { it.id }.toSet()
    var nextId = (earlierRows + headers).maxOfOrNull { it.id + 1 } ?: 0
    val claimed = mutableSetOf<Int>()
    return copy(
      headers = headers.map { row ->
        val match = earlierRows.firstOrNull { it.id !in claimed && it.name.trim().equals(row.name, ignoreCase = true) }
        val id = match?.id ?: row.id.takeUnless { it in earlierIds || it in claimed } ?: nextId++
        claimed += id
        row.copy(id = id)
      },
    )
  }

  companion object {
    fun of(connection: Connection) = fromFields(
      fields = connection.fields,
      keyStored = connection.key != null,
      headers = connection.headers.mapIndexed { index, header ->
        HeaderRow(index, header.name, header.name, ValueEdit.Kept)
      },
    )

    fun of(template: ConnectionTemplate) = fromFields(template.fields(), keyStored = false, headers = emptyList())

    val fromNothing = ConnectionForm(
      name = "",
      format = null,
      baseUrl = "",
      model = "",
      contextSize = ConnectionFields.DEFAULT_CONTEXT_SIZE.toString(),
      maxReplyTokens = ConnectionFields.DEFAULT_MAX_REPLY_TOKENS.toString(),
      streaming = true,
      includeUsage = true,
      samplers = SamplerTexts(emptyMap()),
      timeout = ConnectionFields.DEFAULT_TIMEOUT_SECONDS.toString(),
      customBody = "",
      keyStored = false,
      key = KeyEdit.Entering,
      headers = emptyList(),
    )

    private fun fromFields(fields: ConnectionFields, keyStored: Boolean, headers: List<HeaderRow>) = ConnectionForm(
      name = fields.name,
      format = fields.format,
      baseUrl = fields.baseUrl,
      model = fields.model,
      contextSize = fields.contextSize.toString(),
      maxReplyTokens = fields.maxReplyTokens.toString(),
      streaming = fields.streaming,
      includeUsage = fields.includeUsage,
      samplers = SamplerTexts.of(fields.samplers),
      timeout = fields.timeoutSeconds.toString(),
      customBody = fields.customBodyText,
      keyStored = keyStored,
      key = if (keyStored) KeyEdit.Kept else KeyEdit.Entering,
      headers = headers,
    )
  }
}

private fun String.wholeNumber(): Int? = trim().toIntOrNull()

private fun String.byValue(): String = wholeNumber()?.toString() ?: this

sealed interface FormCheck {
  class Ready(val draft: ConnectionDraft) : FormCheck

  class Refused(val errors: FormErrors) : FormCheck
}

fun ConnectionForm.check(waived: Set<ConnectionProblem> = emptySet()): FormCheck {
  val shownSamplers = format?.samplerRanges?.keys.orEmpty()
  val contextSize = contextSize.wholeNumber()
  val maxReplyTokens = maxReplyTokens.wholeNumber()
  val timeout = timeout.wholeNumber()
  val body = CustomBodyText.parse(customBody)
  val key = keyInput()
  val headers = keptHeaders.map { it.draft() }
  val problems = textProblems(name, model, baseUrl) + numberProblems(contextSize, maxReplyTokens, timeout) +
    format?.let { samplerProblems(samplers.toSamplers(shownSamplers), it.samplerRanges) }.orEmpty() +
    key.problems() + headers.problems()
  val readErrors = FormErrors(
    listOfNotNull(
      (FormField.Format to FieldError.FormatEmpty).takeIf { format == null },
      (FormField.ContextSize to FieldError.WholeAboveZero).takeIf { contextSize == null },
      (FormField.MaxReplyTokens to FieldError.WholeAboveZero).takeIf { maxReplyTokens == null },
      (FormField.Timeout to FieldError.WholeAboveZero).takeIf { timeout == null },
      body.error()?.let { FormField.CustomBody to it },
    ).toMap() + samplers.entryErrors(shownSamplers).map { (sampler, error) ->
      FormField.SamplerField(sampler) to FieldError.Sampler(error)
    },
  )
  val errors = readErrors + errorsOf(problems - waived)
  val fields = fields(contextSize, maxReplyTokens, timeout, body)
  return if (errors.isEmpty() && fields != null) {
    FormCheck.Ready(ConnectionDraft(fields, key, headers))
  } else {
    FormCheck.Refused(errors)
  }
}

private fun ConnectionForm.fields(
  contextSize: Int?,
  maxReplyTokens: Int?,
  timeout: Int?,
  body: CustomBodyText,
): ConnectionFields? = when {
  format == null || body !is CustomBodyText.Object -> null
  contextSize == null || maxReplyTokens == null || timeout == null -> null
  else -> ConnectionFields(
    name = name,
    format = format,
    baseUrl = baseUrl,
    model = model,
    contextSize = contextSize,
    maxReplyTokens = maxReplyTokens,
    streaming = streaming,
    includeUsage = includeUsage,
    samplers = samplers.toSamplers(),
    timeoutSeconds = timeout,
  ).withCustomBody(body)
}

private fun CustomBodyText.error(): FieldError? = when (this) {
  is CustomBodyText.Object -> null
  is CustomBodyText.NotJson -> FieldError.NotJson(fault, position)
  CustomBodyText.NotAnObject -> FieldError.NotAnObject
}

private fun ConnectionForm.keyInput(): KeyInput = when (val edit = key) {
  KeyEdit.Kept -> KeyInput.Kept
  KeyEdit.Removed -> KeyInput.Removed
  KeyEdit.Entering -> if (keyStored) KeyInput.Kept else KeyInput.Removed
  is TypedSecret -> KeyInput.Entered(edit.text.text)
}

private fun HeaderRow.draft(): HeaderDraft = HeaderDraft(name, valueInput())

private data class ComparedForm(val fields: ConnectionForm, val key: KeyInput, val headers: List<HeaderDraft>)

fun ConnectionForm.errorsOf(problems: Collection<ConnectionProblem>): FormErrors =
  FormErrors(problems.mapNotNull(::fieldError).associate { it.field to it.error })
