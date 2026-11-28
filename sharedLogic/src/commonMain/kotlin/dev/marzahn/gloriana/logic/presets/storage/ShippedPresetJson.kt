package dev.marzahn.gloriana.logic.presets.storage

internal const val SHIPPED_PRESET_JSON = """{
  "prompts": [
    {
      "identifier": "main",
      "name": "Main Prompt",
      "system_prompt": true,
      "role": "system",
      "content": "This is an ongoing story between {{char}} and {{user}}. Continue it with {{char}}'s next reply, in character."
    },
    { "identifier": "nsfw", "name": "Auxiliary Prompt", "system_prompt": true, "role": "system", "content": "" },
    { "identifier": "dialogueExamples", "name": "Chat Examples", "system_prompt": true, "marker": true },
    {
      "identifier": "jailbreak",
      "name": "Post-History Instructions",
      "system_prompt": true,
      "role": "system",
      "content": ""
    },
    { "identifier": "chatHistory", "name": "Chat History", "system_prompt": true, "marker": true },
    { "identifier": "worldInfoAfter", "name": "World Info (after)", "system_prompt": true, "marker": true },
    { "identifier": "worldInfoBefore", "name": "World Info (before)", "system_prompt": true, "marker": true },
    {
      "identifier": "enhanceDefinitions",
      "name": "Enhance Definitions",
      "system_prompt": true,
      "marker": false,
      "role": "system",
      "content": "Draw on anything you already know about {{char}} to round out their background and personality. Where it disagrees with the Character's own definitions, those definitions win."
    },
    { "identifier": "charDescription", "name": "Char Description", "system_prompt": true, "marker": true },
    { "identifier": "charPersonality", "name": "Char Personality", "system_prompt": true, "marker": true },
    { "identifier": "scenario", "name": "Scenario", "system_prompt": true, "marker": true },
    { "identifier": "personaDescription", "name": "Persona Description", "system_prompt": true, "marker": true }
  ],
  "prompt_order": [
    {
      "character_id": 100001,
      "order": [
        { "identifier": "main", "enabled": true },
        { "identifier": "worldInfoBefore", "enabled": true },
        { "identifier": "personaDescription", "enabled": true },
        { "identifier": "charDescription", "enabled": true },
        { "identifier": "charPersonality", "enabled": true },
        { "identifier": "scenario", "enabled": true },
        { "identifier": "enhanceDefinitions", "enabled": false },
        { "identifier": "nsfw", "enabled": true },
        { "identifier": "worldInfoAfter", "enabled": true },
        { "identifier": "dialogueExamples", "enabled": true },
        { "identifier": "chatHistory", "enabled": true },
        { "identifier": "jailbreak", "enabled": true }
      ]
    }
  ]
}
"""
