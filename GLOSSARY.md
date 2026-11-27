# Gloriana

A local-first app for roleplay chats with AI models, using the user's own provider keys.

## Language

**Character**:
An AI-played identity with a description, personality and greeting that the user chats with.
_Avoid_: Bot, card

**Character Card**:
The file format a Character is imported from or exported to.
_Avoid_: Card (alone), bot file

**Persona**:
The identity the user plays as in a chat.
_Avoid_: User profile, user character

**Persona Label**:
A name for a Persona that only the user sees, never sent to the model.
_Avoid_: Display name, nickname

**Persona Note**:
A description of a Persona that only the user sees, to tell Personas apart.
_Avoid_: Helper description

**Lorebook**:
A collection of entries injected into the prompt when their keywords appear in the chat.
_Avoid_: World Info, world book

## Memory

**Verbatim Window**:
The most recent messages of a chat, sent to the model word for word.
_Avoid_: History limit, context window

**Snippet**:
A summary of one batch of messages, or of older Snippets, that have left the Verbatim Window.
_Avoid_: Summary (for one piece), memory entry

**Layer**:
The depth of a Snippet: Layer 0 summarises messages, each deeper Layer summarises Snippets of the Layer above.
_Avoid_: Level, tier

**Summary**:
The assembled block of all Layers that stands in for every message outside the Verbatim Window.
_Avoid_: Memory, long-term memory

**Stale Snippet**:
A Snippet whose messages or source Snippets changed after it was written.

**Swipe**:
An alternative message at the same point in a chat, sibling to the one shown.
_Avoid_: Alternate, reroll

**Fork**:
A new, independent chat that starts as a copy of an existing chat up to a chosen message.
_Avoid_: Branch, copy, clone

**Hidden Message**:
A message kept in the chat but left out of every prompt and Snippet.
_Avoid_: Ghosted message, excluded message

**Connection**:
A saved way to reach a model: its API format, base URL, key, model and sampler settings.
_Avoid_: API, provider, endpoint

**Preset**:
An ordered set of prompt entries that decides how a prompt is assembled.
_Avoid_: Prompt template, jailbreak

**Group**:
A named header among a Preset's entries that organises the list only: it sends nothing, and an entry belongs to
the nearest group above it. Not a group chat (a chat with several Characters), which is a different thing.
_Avoid_: Folder, section, group header

**Tag**:
A label that any Character, Persona, Lorebook or preset can carry many of, used for filtering.

**Folder**:
A nestable container that holds each item in exactly one place, used for browsing.
_Avoid_: Category, Group (a Group is a header among a Preset's entries)

**Side Chat**:
An out-of-character conversation beside a chat whose messages never enter that chat's prompt or Summary.
_Avoid_: OOC chat
