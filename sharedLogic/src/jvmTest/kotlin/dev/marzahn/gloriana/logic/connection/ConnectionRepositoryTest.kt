package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.connection.storage.ConnectionsEntityType
import dev.marzahn.gloriana.logic.connection.storage.DefaultConnectionSetting
import dev.marzahn.gloriana.logic.keys.ErasingResult
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.KeysBlocked
import dev.marzahn.gloriana.logic.keys.KeysOpening
import dev.marzahn.gloriana.logic.keys.OpenResult
import dev.marzahn.gloriana.logic.keys.PassphraseResult
import dev.marzahn.gloriana.logic.keys.SealResult
import dev.marzahn.gloriana.logic.keys.SecretSlot
import dev.marzahn.gloriana.logic.keys.Vaults
import dev.marzahn.gloriana.logic.keys.apiKey
import dev.marzahn.gloriana.logic.keys.header
import dev.marzahn.gloriana.logic.keys.opened
import dev.marzahn.gloriana.logic.keys.passphrase
import dev.marzahn.gloriana.logic.keys.sealed
import dev.marzahn.gloriana.logic.storage.RowKey
import dev.marzahn.gloriana.logic.storage.TombstoneEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ConnectionRepositoryTest {
  @TempDir
  lateinit var directory: Path

  private val library by lazy { ConnectionLibrary(directory) }

  @AfterEach
  fun closeLibrary() = library.close()

  @Test
  fun aTemplateFillsFormatBaseUrlAndNameWithTheStartingValues() {
    val fields = ConnectionTemplate.OpenRouter.fields()

    assertEquals("OpenRouter", fields.name)
    assertEquals(ApiFormat.OpenAiChatCompletions, fields.format)
    assertEquals("https://openrouter.ai/api/v1", fields.baseUrl)
    assertEquals(16_384 to 1_024, fields.contextSize to fields.maxReplyTokens)
    assertEquals(60, fields.timeoutSeconds)
    assertTrue(fields.streaming && fields.includeUsage)
    assertEquals(Samplers(), fields.samplers)
    assertEquals(
      listOf(
        ApiFormat.AnthropicMessages to "https://api.anthropic.com/v1",
        ApiFormat.OpenAiChatCompletions to "https://api.openai.com/v1",
      ),
      listOf(ConnectionTemplate.Anthropic, ConnectionTemplate.OpenAi).map { it.format to it.baseUrl },
    )
  }

  @Test
  fun everyFieldIsStoredAndTheListIsOrderedByNameLetterCaseAside() = runTest {
    val fields = ConnectionFields(
      name = "beta",
      format = ApiFormat.AnthropicMessages,
      baseUrl = "http://192.168.1.4:8080/v1",
      model = "claude",
      contextSize = 200_000,
      maxReplyTokens = 8_000,
      streaming = false,
      includeUsage = false,
      samplers = Samplers(temperature = 0.7, topK = 40, seed = 9_007_199_254_740_993),
      timeoutSeconds = 120,
      customBody = assertIs<CustomBodyText.Object>(CustomBodyText.parse("""{"thinking": {"type": "on"}}""")).body,
    )
    library.created(draft(name = "Alpha"))
    library.created(ConnectionDraft(fields))
    library.created(draft(name = "Gamma"))

    assertEquals(listOf("Alpha", "beta", "Gamma"), library.list().map { it.fields.name })
    assertEquals(fields, library.list()[1].fields)
  }

  @Test
  fun theNameAndModelAreTrimmedAndATrailingSlashLeavesTheBaseUrl() = runTest {
    val stored = library.created(draft(name = "  Local  ", baseUrl = " http://localhost:5001/v1// ", model = " m "))

    assertEquals(listOf("Local", "http://localhost:5001/v1", "m"), stored.fields.run { listOf(name, baseUrl, model) })
  }

  @Test
  fun theFirstConnectionBecomesTheDefaultAndTheNextDoesNot() = runTest {
    val first = library.created(draft(name = "First"))
    library.created(draft(name = "Second"))

    assertEquals(first.id, library.default())
  }

  @Test
  fun anyConnectionCanBeMadeTheDefault() = runTest {
    library.created(draft(name = "First"))
    val second = library.created(draft(name = "Second"))

    assertEquals(MakeDefaultResult.Made, library.repository.makeDefault(second.id))
    assertEquals(second.id, library.default())
    assertEquals(MakeDefaultResult.Gone, library.repository.makeDefault(Uuid.random()))
    assertEquals(second.id, library.default())
  }

  @Test
  fun aSettingNamingAConnectionNoLongerThereCountsAsUnset() = runTest {
    library.settings.write(DefaultConnectionSetting, Uuid.random())
    assertNull(library.default())

    val made = library.created(draft())

    assertEquals(made.id, library.default())
  }

  @Test
  fun deletingTheDefaultClearsItAndTheNextConnectionMadeBecomesIt() = runTest {
    val first = library.created(draft(name = "First"))
    val second = library.created(draft(name = "Second"))

    assertEquals(ConnectionDeleteResult.Deleted, library.repository.delete(first.id))
    assertNull(library.default())
    assertNull(library.settings.read(DefaultConnectionSetting))

    val third = library.created(draft(name = "Third"))
    assertEquals(third.id, library.default())
    assertEquals(listOf(second.id, third.id), library.list().map { it.id })
  }

  @Test
  fun aDeleteWritesATombstoneErasesTheKeyAndReportsGoneTheSecondTime() = runTest {
    val stored = library.created(
      draft(key = KeyInput.Entered("sk-deleted"), headers = listOf(HeaderDraft("X-Org", entered("org-7")))),
    )
    assertTrue(library.dataKeyFileHolds(stored.id))
    library.clock.epochMillis = 5_000

    assertEquals(ConnectionDeleteResult.Deleted, library.repository.delete(stored.id))

    assertEquals(emptyList(), library.list())
    assertEquals(
      listOf(TombstoneEntity(ConnectionsEntityType.name, RowKey.Row(stored.id), deletedAt = 5_000, updatedAt = 5_000)),
      library.database.tombstoneDao().all(),
    )
    assertFalse(library.dataKeyFileHolds(stored.id))
    assertEquals(OpenResult.Missing, library.vault.open(apiKey(stored.id), checkNotNull(stored.key)))
    assertEquals(ConnectionDeleteResult.Gone, library.repository.delete(stored.id))
    assertEquals(0, library.repository.chatsUsing(stored.id))
  }

  @Test
  fun theKeyAndHeaderValuesAreSealedForTheConnection() = runTest {
    val stored = library.created(
      draft(key = KeyInput.Entered("sk-sealed"), headers = listOf(HeaderDraft("X-Org", entered("org-7")))),
    )

    assertEquals("sk-sealed", library.vault.opened(apiKey(stored.id), checkNotNull(stored.key)))
    assertEquals("org-7", library.vault.opened(header(stored.id, "X-Org"), stored.headers.single().value))
    assertFalse("sk-sealed" in KeyInput.Entered("sk-sealed").toString())
    assertFalse("org-7" in entered("org-7").toString())
  }

  @Test
  fun renamingAHeaderReEncryptsItsValueUnderTheNewName() = runTest {
    val stored = library.created(draft(headers = listOf(HeaderDraft("X-Org", entered("org-7")))))

    val renaming = HeaderDraft("X-Team", HeaderValueInput.Kept(storedName = "X-Org"))
    assertIs<ConnectionSaveResult.Saved>(library.repository.update(stored.id, draft(headers = listOf(renaming))))

    val renamed = library.stored(stored.id).headers.single()
    assertEquals("X-Team", renamed.name)
    assertEquals("org-7", library.vault.opened(header(stored.id, "X-Team"), renamed.value))
    assertEquals(OpenResult.Missing, library.vault.open(header(stored.id, "X-Org"), renamed.value))
  }

  @Test
  fun renamingAHeaderWhoseValueCannotBeReadIsRefusedUntilTheValueIsEnteredAgain() = runTest {
    val stored = library.created(draft(name = "Other", headers = listOf(HeaderDraft("X-Org", entered("org-7")))))
    val moved = library.created(draft(headers = listOf(HeaderDraft("X-Org", entered("org-8")))))
    val rows = library.database.connectionDao()
    rows.update(checkNotNull(rows.find(moved.id)).copy(headers = checkNotNull(rows.find(stored.id)).headers))

    val leftAlone = HeaderDraft("X-Org", HeaderValueInput.Kept(storedName = "X-Org"))
    assertIs<ConnectionSaveResult.Saved>(
      library.repository.update(moved.id, draft(model = "model-2", headers = listOf(leftAlone))),
    )

    val renaming = HeaderDraft("X-Team", HeaderValueInput.Kept(storedName = "X-Org"))
    val result = library.repository.update(moved.id, draft(headers = listOf(renaming)))

    val refused = assertIs<ConnectionSaveResult.Refused>(result)
    assertEquals(setOf(ConnectionProblem.HeaderValueMissing(0)), refused.problems)
    assertEquals("X-Org", library.stored(moved.id).headers.single().name)
    val entering = HeaderDraft("X-Team", entered("org-9"))
    assertIs<ConnectionSaveResult.Saved>(library.repository.update(moved.id, draft(headers = listOf(entering))))
  }

  @Test
  fun anEditKeepsReplacesOrRemovesTheKey() = runTest {
    val stored = library.created(draft(key = KeyInput.Entered("sk-first")))

    assertIs<ConnectionSaveResult.Saved>(library.repository.update(stored.id, draft(name = "Renamed")))
    assertEquals("sk-first", library.vault.opened(apiKey(stored.id), checkNotNull(library.stored(stored.id).key)))

    assertIs<ConnectionSaveResult.Saved>(library.repository.update(stored.id, draft(key = KeyInput.Entered("sk-2"))))
    assertEquals("sk-2", library.vault.opened(apiKey(stored.id), checkNotNull(library.stored(stored.id).key)))

    assertIs<ConnectionSaveResult.Saved>(library.repository.update(stored.id, draft(key = KeyInput.Removed)))
    assertNull(library.stored(stored.id).key)
  }

  @Test
  fun anEditThatRemovesTheKeyAndEveryHeaderValueErasesTheDataKey() = runTest {
    val stored = library.created(
      draft(key = KeyInput.Entered("sk-first"), headers = listOf(HeaderDraft("X-Org", entered("org-1")))),
    )
    val oldKey = checkNotNull(stored.key)

    val keptHeader = HeaderDraft("X-Org", HeaderValueInput.Kept("X-Org"))
    assertIs<ConnectionSaveResult.Saved>(
      library.repository.update(stored.id, draft(key = KeyInput.Removed, headers = listOf(keptHeader))),
    )
    assertTrue(library.dataKeyFileHolds(stored.id))

    assertIs<ConnectionSaveResult.Saved>(library.repository.update(stored.id, draft(key = KeyInput.Removed)))
    assertFalse(library.dataKeyFileHolds(stored.id))
    assertEquals(OpenResult.Missing, library.vault.open(apiKey(stored.id), oldKey))
  }

  @Test
  fun aDataKeyLeftForAConnectionWithNoValuesGoesAtTheNextStart() = runTest {
    val keyless = library.created(draft(name = "No keys"))
    library.vault.sealed(apiKey(keyless.id), "sk-left-behind")
    assertTrue(library.dataKeyFileHolds(keyless.id))
    library.close()

    ConnectionLibrary(directory).use { restarted ->
      restarted.vault.prepare(KeysOpening.IfStored)

      assertFalse(restarted.dataKeyFileHolds(keyless.id))
    }
  }

  @Test
  fun anEditDoesNotTakeTheDefault() = runTest {
    val first = library.created(draft(name = "First"))
    val second = library.created(draft(name = "Second"))

    assertIs<ConnectionSaveResult.Saved>(library.repository.update(second.id, draft(name = "Second, edited")))

    assertEquals(first.id, library.default())
  }

  @Test
  fun anEditWhoseConnectionIsDeletedWhileItSealsIsGoneAndLeavesNoDataKey() = runTest {
    var deleteOnSeal: Uuid? = null
    ConnectionLibrary(directory.resolve("racing"), vaultAround = { vault ->
      object : KeyVault by vault {
        override suspend fun seal(slot: SecretSlot, value: String): SealResult = vault.seal(slot, value).also {
          deleteOnSeal?.let { id -> database.connectionDao().delete(id) }
        }
      }
    }).use { racing ->
      val stored = racing.created(draft(key = KeyInput.Entered("sk-first")))
      deleteOnSeal = stored.id

      val result = racing.repository.update(stored.id, draft(key = KeyInput.Entered("sk-late")))

      assertEquals(ConnectionSaveResult.Gone, result)
      assertEquals(emptyList(), racing.list())
      assertFalse(racing.dataKeyFileHolds(stored.id))
    }
  }

  @Test
  fun anUnreadableRowKeepsItsDataKeyAndAnEditOfItIsRefusedAsUnreadable() = runTest {
    val stored = library.created(draft(key = KeyInput.Entered("sk-later-format")))
    val rows = library.database.connectionDao()
    rows.update(checkNotNull(rows.find(stored.id)).copy(format = "SomeLaterFormat"))

    val result = library.repository.update(stored.id, draft(key = KeyInput.Entered("sk-overwrite")))

    assertEquals(ConnectionSaveResult.Unreadable, result)
    assertTrue(library.dataKeyFileHolds(stored.id))
    assertEquals("sk-later-format", library.vault.opened(apiKey(stored.id), checkNotNull(stored.key)))
  }

  @Test
  fun theErasureRuleCountsAnUnreadableRowAsNeedingItsKey() = runTest {
    val unreadable = library.created(draft(name = "Unreadable", key = KeyInput.Entered("sk-1")))
    val newerFormat = library.created(draft(name = "Newer"))
    val readableWithoutValues = library.created(draft(name = "Plain"))
    listOf(newerFormat, readableWithoutValues).forEach { library.vault.seal(apiKey(it.id), "sk-unused") }
    val rows = library.database.connectionDao()
    rows.update(checkNotNull(rows.find(unreadable.id)).copy(customBody = "not json"))
    rows.update(checkNotNull(rows.find(newerFormat.id)).copy(format = "AFormatFromANewerVersion"))
    val erasure = DataKeyErasure(library.vault, rows)

    listOf(unreadable, newerFormat, readableWithoutValues).forEach { erasure.eraseUnlessStoredValuesNeedIt(it.id) }

    assertTrue(library.dataKeyFileHolds(unreadable.id))
    assertTrue(library.dataKeyFileHolds(newerFormat.id))
    assertFalse(library.dataKeyFileHolds(readableWithoutValues.id))
  }

  @Test
  fun afterAResetNoConnectionCountsAsHoldingKeysAndASecondResetCountsNone() = runTest {
    val vaults = Vaults(directory).apply { keychain.noKeychain = true }
    ConnectionLibrary(directory, vaults = vaults).use { setUp ->
      setUp.vault.setPassphrase("correct horse".passphrase())
      setUp.created(draft(key = KeyInput.Entered("sk-1"), headers = listOf(HeaderDraft("X-Org", entered("org-1")))))
      setUp.created(draft(name = "No keys"))
    }

    val countsBeforeAndAfterAReset = listOf("new horse", "newer horse").map { newPassphrase ->
      ConnectionLibrary(directory, vaults = vaults).use { forgotten ->
        suspend fun holding() = KeysUnlock(forgotten.vault, forgotten.repository).holding()
        forgotten.vault.prepare(KeysOpening.IfPassphraseProtected)
        val before = holding()
        assertEquals(ErasingResult.Done, forgotten.vault.reset(newPassphrase.passphrase()))
        before to holding()
      }
    }

    assertEquals(listOf(1 to 0, 0 to 0), countsBeforeAndAfterAReset)
  }

  @Test
  fun aLockedVaultSavesNothingLeavesNoNewDataKeyAndKeepsTheStoredValues() = runTest {
    val vaults = Vaults(directory).apply { keychain.noKeychain = true }
    val newId = Uuid.random()
    val first = ConnectionLibrary(directory, vaults = vaults).use { setUp ->
      assertEquals(PassphraseResult.Accepted, setUp.vault.setPassphrase("correct horse".passphrase()))
      setUp.created(draft(key = KeyInput.Entered("sk-kept"), headers = listOf(HeaderDraft("X-Org", entered("org-7")))))
    }

    ConnectionLibrary(directory, vaults = vaults, ids = { newId }).use { locked ->
      val renaming = HeaderDraft("X-Team", HeaderValueInput.Kept(storedName = "X-Org"))
      val blocked = ConnectionSaveResult.Blocked(KeysBlocked.Locked)
      assertEquals(blocked, locked.repository.update(first.id, draft(headers = listOf(renaming))))
      assertEquals(blocked, locked.repository.update(first.id, draft(key = KeyInput.Entered("sk-new"))))
      assertEquals(
        blocked,
        locked.repository.create(draft(name = "New", key = KeyInput.Entered("sk-locked"))),
      )

      assertEquals(listOf(first.id), locked.list().map { it.id })
      assertEquals("X-Org", locked.stored(first.id).headers.single().name)
      assertFalse(locked.dataKeyFileHolds(newId))
      assertEquals(PassphraseResult.Accepted, locked.vault.unlock("correct horse".passphrase()))
      assertEquals("sk-kept", locked.vault.opened(apiKey(first.id), checkNotNull(locked.stored(first.id).key)))
    }
  }

  @Test
  fun aRowThisVersionCannotReadIsLeftOutOfTheList() = runTest {
    val kept = library.created(draft(name = "Kept"))
    val unreadable = library.created(draft(name = "Unreadable"))
    val rows = library.database.connectionDao()
    rows.update(checkNotNull(rows.find(unreadable.id)).copy(format = "SomeLaterFormat"))

    assertEquals(listOf(kept.id), library.list().map { it.id })
    assertNull(library.repository.connection(unreadable.id).first())
  }

  @Test
  fun globalSamplersStartUnsetKeepWhatIsSetAndRefuseValuesNoFormatTakes() = runTest {
    val global = GlobalModelSettings(library.settings)
    assertEquals(Samplers(), global.samplers().first())

    assertEquals(GlobalSamplersSave.Saved, global.setSamplers(Samplers(temperature = 1.1, seed = 42)))
    assertEquals(Samplers(temperature = 1.1, seed = 42), global.currentSamplers())
    assertEquals(
      GlobalSamplersSave.Refused(setOf(Sampler.TopP, Sampler.Temperature, Sampler.TopK)),
      global.setSamplers(Samplers(temperature = 2.5, topP = Double.NaN, topK = -1)),
    )

    assertEquals(Samplers(temperature = 1.1, seed = 42), global.samplers().first())
  }
}
