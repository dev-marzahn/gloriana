package dev.marzahn.gloriana.logic.connection.storage

import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.settings.LibrarySetting
import kotlinx.serialization.builtins.serializer
import kotlin.uuid.Uuid

internal val DefaultConnectionSetting = LibrarySetting("defaultConnection", Uuid.serializer())

internal val GlobalSamplersSetting = LibrarySetting("globalSamplers", Samplers.serializer())
