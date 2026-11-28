package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.HeldSecrets
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.connection.SecretsState
import dev.marzahn.gloriana.logic.connection.api.ModelApiError
import dev.marzahn.gloriana.logic.keys.KeysBlocked
import kotlin.time.Duration

enum class KeyContext { SavedKey, TypedKey, HeadersOnly, NoKey }

sealed interface HostProblem {
  data object Unreachable : HostProblem

  data class Timeout(val seconds: Int) : HostProblem

  sealed interface WithBody : HostProblem {
    val body: String
  }

  data class KeyRejected(val status: Int, override val body: String) : WithBody

  data class RateLimited(override val body: String) : WithBody

  data class HostError(val status: Int?, override val body: String) : WithBody

  data object PlainHttpToPublicHost : HostProblem

  data object BadAddress : HostProblem

  data object BadHeader : HostProblem

  data object StreamNotBoolean : HostProblem

  data object Unexpected : HostProblem
}

data class HostFailure(val problem: HostProblem, val keys: KeyContext)

enum class KeysProblem { KeychainUnavailable, Unreadable }

sealed interface PageNote {
  sealed interface BlockedSend : PageNote

  data class KeysBlockedSend(val problem: KeysProblem, val held: HeldSecrets) : BlockedSend

  data class KeysMissing(val held: HeldSecrets) : BlockedSend

  data class KeysBlockedSave(val problem: KeysProblem) : PageNote

  data object NotSavedStorage : PageNote

  data object NotSaved : PageNote

  data object NotDeleted : PageNote

  data object NotMadeDefault : PageNote
}

sealed interface Refusal {
  sealed interface Shown : Refusal

  data class Host(val failure: HostFailure) : Shown

  data class Keys(val note: PageNote.BlockedSend) : Shown

  data object Locked : Refusal
}

class Probed(val keys: KeyContext, val timeoutSeconds: Int)

fun ModelApiError.refusal(probed: Probed): Refusal {
  val host = { problem: HostProblem -> Refusal.Host(HostFailure(problem, probed.keys)) }
  return when (this) {
    ModelApiError.Unreachable -> host(HostProblem.Unreachable)
    ModelApiError.Timeout -> host(HostProblem.Timeout(probed.timeoutSeconds))
    is ModelApiError.KeyRejected -> host(HostProblem.KeyRejected(status, body))
    is ModelApiError.RateLimited -> host(HostProblem.RateLimited(body))
    is ModelApiError.HostError -> host(HostProblem.HostError(status, body))
    ModelApiError.PlainHttpToPublicHost -> host(HostProblem.PlainHttpToPublicHost)
    ModelApiError.BadAddress -> host(HostProblem.BadAddress)
    ModelApiError.BadHeader -> host(HostProblem.BadHeader)
    ModelApiError.StreamNotBoolean -> host(HostProblem.StreamNotBoolean)
    ModelApiError.Unexpected -> host(HostProblem.Unexpected)
    is ModelApiError.KeysMissing -> Refusal.Keys(PageNote.KeysMissing(held))
    is ModelApiError.KeysNotOpened -> refusal()
  }
}

private fun ModelApiError.KeysNotOpened.refusal(): Refusal = when (why) {
  KeysBlocked.Locked -> Refusal.Locked
  KeysBlocked.Unavailable -> Refusal.Keys(PageNote.KeysBlockedSend(KeysProblem.KeychainUnavailable, held))
  KeysBlocked.Unreadable -> Refusal.Keys(PageNote.KeysBlockedSend(KeysProblem.Unreadable, held))
}

sealed interface TestOutcome {
  data class Passed(val took: Duration, val reply: String) : TestOutcome

  data class Failed(val failure: HostFailure) : TestOutcome
}

sealed interface ModelList {
  data object Asking : ModelList

  data class Listed(val models: List<String>) : ModelList

  data class Failed(val refusal: Refusal.Shown) : ModelList
}

sealed interface ModelListNote {
  data object NoneListed : ModelListNote

  data class Failed(val failure: HostFailure) : ModelListNote
}

sealed interface PageActivity {
  val locksForm: Boolean get() = this == OpeningSaved || this == Deleting

  val isSaving: Boolean get() = this == Saving || this == OpeningSaved

  data object Idle : PageActivity

  data object Saving : PageActivity

  data object OpeningSaved : PageActivity

  data object Testing : PageActivity

  data object MakingDefault : PageActivity

  data object ConfirmingDelete : PageActivity

  data object Deleting : PageActivity

  data class PickingModel(val list: ModelList) : PageActivity

  sealed interface Prompt : PageActivity

  data class SettingPassphrase(val entry: NewPassphraseEntry) : Prompt

  data class Unlocking(val prompt: UnlockPrompt, val then: AfterUnlock) : Prompt
}

sealed interface AfterUnlock {
  data object Save : AfterUnlock

  data object Test : AfterUnlock

  data object ModelList : AfterUnlock

  data object OnlyUnlock : AfterUnlock

  class Edit(val field: FormField, val change: FormChange) : AfterUnlock
}

sealed interface ConnectionPageState {
  data object Loading : ConnectionPageState

  data object CouldNotLoad : ConnectionPageState

  data object Gone : ConnectionPageState

  data class Shown(
    val saved: Connection?,
    val isDefault: Boolean,
    val copy: EditedCopy<ConnectionForm>,
    val errors: FormErrors,
    val secrets: SecretsState,
    val globalSamplers: Samplers,
    val activity: PageActivity,
    val test: TestOutcome?,
    val note: PageNote?,
    val modelListNote: ModelListNote?,
  ) : ConnectionPageState {
    val form: ConnectionForm get() = copy.draft

    val canSave: Boolean get() = activity == PageActivity.Idle && (saved == null || copy.hasChanges)

    val canEdit: Boolean get() = !activity.locksForm
  }
}
