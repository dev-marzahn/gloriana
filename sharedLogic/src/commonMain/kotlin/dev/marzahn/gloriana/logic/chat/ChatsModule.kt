package dev.marzahn.gloriana.logic.chat

import dev.marzahn.gloriana.logic.chat.file.ChatExport
import dev.marzahn.gloriana.logic.chat.file.ChatFileReads
import dev.marzahn.gloriana.logic.chat.storage.CharacterChats
import dev.marzahn.gloriana.logic.chat.storage.ChatReferences
import dev.marzahn.gloriana.logic.chat.storage.ChatRemovals
import dev.marzahn.gloriana.logic.chat.storage.ChatWrites
import dev.marzahn.gloriana.logic.chat.storage.DraftSettling
import dev.marzahn.gloriana.logic.chat.storage.LeftoverDrafts
import dev.marzahn.gloriana.logic.chat.storage.ReplyRows
import dev.marzahn.gloriana.logic.storage.ChatsUsing
import dev.marzahn.gloriana.logic.storage.ChatsWithCharacter
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import org.koin.dsl.bind
import org.koin.dsl.module

val chatsModule = module {
  single { get<GlorianaDatabase>().chatDao() }
  single { get<GlorianaDatabase>().swipeDraftDao() }
  single { get<GlorianaDatabase>().chatReferenceDao() }
  single { get<GlorianaDatabase>().chatRemovalDao() }
  single { ChatReferences(get(), get()) } bind ChatsUsing::class
  single { ChatRemovals(get(), get(), get(), get(), get()) }
  single { CharacterChats(get(), get(), get()) } bind ChatsWithCharacter::class
  single { ChatWrites(get(), get(), get(), get(), get()) }
  single { ChatOpenings(get(), get(), get(), get()) }
  single { ChatRepository(get(), get(), get(), get(), get(), get(), get(), get()) }
  single { ChatFileReads(get(), get(), get(), get(), get()) }
  single { ChatExport(get()) }
  single { DraftSettling(get(), get(), get(), get(), get()) }
  single { LeftoverDrafts(get(), get(), get(), get()) }
  single { ReplyRows(get(), get(), get(), get(), get(), get()) }
  single(createdAtStart = true) { LeftoverDraftsAtStart(get(), get(), get()) }
}
