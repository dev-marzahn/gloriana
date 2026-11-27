package dev.marzahn.gloriana.logic.presets.file

import dev.marzahn.gloriana.logic.connection.Sampler
import dev.marzahn.gloriana.logic.presets.PresetSampler
import dev.marzahn.gloriana.logic.presets.WrapperPrompt

internal const val PROMPTS_KEY = "prompts"
internal const val PROMPT_ORDER_KEY = "prompt_order"
internal const val ORDER_KEY = "order"
internal const val CHARACTER_ID_KEY = "character_id"
internal const val GLOBAL_ORDER_CHARACTER_ID = 100_001
internal const val IDENTIFIER_KEY = "identifier"
internal const val ENABLED_KEY = "enabled"
internal const val GLORIANA_KEY = "gloriana"
internal const val GROUPS_KEY = "groups"
internal const val GROUP_NAME_KEY = "name"
internal const val GROUP_AT_KEY = "at"

internal enum class WrapperHome {
  TopLevel,
  GlorianaKey,
}

internal val WrapperPrompt.home: WrapperHome
  get() = when (this) {
    WrapperPrompt.Impersonation,
    WrapperPrompt.ContinueNudge,
    WrapperPrompt.NewChat,
    WrapperPrompt.NewGroupChat,
    WrapperPrompt.NewExampleChat,
    WrapperPrompt.GroupNudge,
    WrapperPrompt.WorldInfoFormat,
    WrapperPrompt.ScenarioFormat,
    WrapperPrompt.PersonalityFormat,
    WrapperPrompt.ReplaceEmptyMessage,
    -> WrapperHome.TopLevel
    WrapperPrompt.PersonaDescription -> WrapperHome.GlorianaKey
  }

internal val PresetSampler.fileKey: String
  get() = when (this) {
    is PresetSampler.OnConnection -> when (sampler) {
      Sampler.Temperature -> "temperature"
      Sampler.TopP -> "top_p"
      Sampler.TopK -> "top_k"
      Sampler.FrequencyPenalty -> "frequency_penalty"
      Sampler.PresencePenalty -> "presence_penalty"
      Sampler.Seed -> "seed"
    }
    PresetSampler.WithoutConnectionField.TopA -> "top_a"
    PresetSampler.WithoutConnectionField.MinP -> "min_p"
    PresetSampler.WithoutConnectionField.RepetitionPenalty -> "repetition_penalty"
  }

internal val allPresetSamplers: List<PresetSampler> =
  Sampler.entries.map(PresetSampler::OnConnection) + PresetSampler.WithoutConnectionField.entries

internal val credentialKeys: Set<String> = setOf(
  "reverse_proxy",
  "proxy_password",
  "custom_url",
  "custom_include_body",
  "custom_exclude_body",
  "custom_include_headers",
  "vertexai_region",
  "vertexai_express_project_id",
  "azure_base_url",
  "azure_deployment_name",
  "workers_ai_account_id",
)

internal val keysNeverStored: Set<String> =
  allPresetSamplers.map { it.fileKey }.toSet() + setOf("openai_max_context", "openai_max_tokens") + credentialKeys
