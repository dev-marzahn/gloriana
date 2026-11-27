package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.ConnectionDraft
import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.ConnectionLibrary
import dev.marzahn.gloriana.logic.connection.GlobalModelSettings
import dev.marzahn.gloriana.logic.connection.HeaderDraft
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.connection.draft
import dev.marzahn.gloriana.logic.keys.KeyVault
import java.nio.file.Path

internal class ProbeHarness(directory: Path) : AutoCloseable {
  val stub = StubHostReplies(directory)
  val library = ConnectionLibrary(directory.resolve("library"))
  val settings = GlobalModelSettings(library.settings)

  fun probe(vault: KeyVault = library.vault): ConnectionProbe =
    VaultConnectionProbe(vault, settings, stub.replies, stub.calls)

  fun draftFor(
    format: ApiFormat,
    mode: String,
    key: KeyInput = KeyInput.Entered(TEST_KEY),
    headers: List<HeaderDraft> = emptyList(),
    fields: (ConnectionFields) -> ConnectionFields = { it },
  ): ConnectionDraft = draft(baseUrl = stub.host.baseUrl(mode), key = key, headers = headers, format = format)
    .let { it.copy(fields = fields(it.fields)) }

  override fun close() {
    stub.close()
    library.close()
  }
}
