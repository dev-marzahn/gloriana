package dev.marzahn.gloriana.ui.connections

import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.field_step_bad_address
import dev.marzahn.gloriana.ui.resources.field_step_headers
import dev.marzahn.gloriana.ui.resources.field_step_host_error
import dev.marzahn.gloriana.ui.resources.field_step_no_key
import dev.marzahn.gloriana.ui.resources.field_step_plain_http
import dev.marzahn.gloriana.ui.resources.field_step_rate_limited
import dev.marzahn.gloriana.ui.resources.field_step_replace_key
import dev.marzahn.gloriana.ui.resources.field_step_stream
import dev.marzahn.gloriana.ui.resources.field_step_timeout
import dev.marzahn.gloriana.ui.resources.field_step_typed_key
import dev.marzahn.gloriana.ui.resources.field_step_unexpected
import dev.marzahn.gloriana.ui.resources.field_step_unreachable
import dev.marzahn.gloriana.ui.resources.list_step_bad_address
import dev.marzahn.gloriana.ui.resources.list_step_headers
import dev.marzahn.gloriana.ui.resources.list_step_host_error
import dev.marzahn.gloriana.ui.resources.list_step_no_key
import dev.marzahn.gloriana.ui.resources.list_step_plain_http
import dev.marzahn.gloriana.ui.resources.list_step_rate_limited
import dev.marzahn.gloriana.ui.resources.list_step_replace_key
import dev.marzahn.gloriana.ui.resources.list_step_stream
import dev.marzahn.gloriana.ui.resources.list_step_timeout
import dev.marzahn.gloriana.ui.resources.list_step_typed_key
import dev.marzahn.gloriana.ui.resources.list_step_unexpected
import dev.marzahn.gloriana.ui.resources.list_step_unreachable
import dev.marzahn.gloriana.ui.resources.step_bad_address
import dev.marzahn.gloriana.ui.resources.step_headers
import dev.marzahn.gloriana.ui.resources.step_host_error
import dev.marzahn.gloriana.ui.resources.step_no_key
import dev.marzahn.gloriana.ui.resources.step_plain_http
import dev.marzahn.gloriana.ui.resources.step_rate_limited
import dev.marzahn.gloriana.ui.resources.step_replace_key
import dev.marzahn.gloriana.ui.resources.step_stream
import dev.marzahn.gloriana.ui.resources.step_timeout
import dev.marzahn.gloriana.ui.resources.step_typed_key
import dev.marzahn.gloriana.ui.resources.step_unexpected
import dev.marzahn.gloriana.ui.resources.step_unreachable
import org.jetbrains.compose.resources.StringResource

enum class StepFor { Test, ModelList, ModelField }

private class Steps(val test: StringResource, val list: StringResource, val field: StringResource) {
  fun of(use: StepFor) = when (use) {
    StepFor.Test -> test
    StepFor.ModelList -> list
    StepFor.ModelField -> field
  }
}

private val unreachableSteps =
  Steps(Res.string.step_unreachable, Res.string.list_step_unreachable, Res.string.field_step_unreachable)
private val timeoutSteps = Steps(Res.string.step_timeout, Res.string.list_step_timeout, Res.string.field_step_timeout)
private val replaceKeySteps =
  Steps(Res.string.step_replace_key, Res.string.list_step_replace_key, Res.string.field_step_replace_key)
private val typedKeySteps =
  Steps(Res.string.step_typed_key, Res.string.list_step_typed_key, Res.string.field_step_typed_key)
private val headersSteps = Steps(Res.string.step_headers, Res.string.list_step_headers, Res.string.field_step_headers)
private val noKeySteps = Steps(Res.string.step_no_key, Res.string.list_step_no_key, Res.string.field_step_no_key)
private val rateLimitedSteps =
  Steps(Res.string.step_rate_limited, Res.string.list_step_rate_limited, Res.string.field_step_rate_limited)
private val hostErrorSteps =
  Steps(Res.string.step_host_error, Res.string.list_step_host_error, Res.string.field_step_host_error)
private val plainHttpSteps =
  Steps(Res.string.step_plain_http, Res.string.list_step_plain_http, Res.string.field_step_plain_http)
private val badAddressSteps =
  Steps(Res.string.step_bad_address, Res.string.list_step_bad_address, Res.string.field_step_bad_address)
private val streamSteps = Steps(Res.string.step_stream, Res.string.list_step_stream, Res.string.field_step_stream)
private val unexpectedSteps =
  Steps(Res.string.step_unexpected, Res.string.list_step_unexpected, Res.string.field_step_unexpected)

internal fun HostFailure.step(use: StepFor): StringResource = steps().of(use)

private fun KeyContext.rejectedSteps(): Steps = when (this) {
  KeyContext.SavedKey -> replaceKeySteps
  KeyContext.TypedKey -> typedKeySteps
  KeyContext.HeadersOnly -> headersSteps
  KeyContext.NoKey -> noKeySteps
}

private fun HostFailure.steps(): Steps = when (problem) {
  HostProblem.Unreachable -> unreachableSteps
  is HostProblem.Timeout -> timeoutSteps
  is HostProblem.KeyRejected -> keys.rejectedSteps()
  is HostProblem.RateLimited -> rateLimitedSteps
  is HostProblem.HostError -> hostErrorSteps
  HostProblem.PlainHttpToPublicHost -> plainHttpSteps
  HostProblem.BadAddress -> badAddressSteps
  HostProblem.BadHeader -> headersSteps
  HostProblem.StreamNotBoolean -> streamSteps
  HostProblem.Unexpected -> unexpectedSteps
}
