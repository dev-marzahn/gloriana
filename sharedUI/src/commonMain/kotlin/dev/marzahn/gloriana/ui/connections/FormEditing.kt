package dev.marzahn.gloriana.ui.connections

import androidx.compose.ui.focus.FocusRequester

class FormEditing(
  val page: ConnectionPageState.Shown,
  private val focus: FocusRequesters,
  private val onIntent: (PageIntent) -> Unit,
) {
  val form: ConnectionForm get() = page.form

  val enabled: Boolean get() = page.canEdit

  fun error(field: FormField): FieldError? = page.errors[field]

  fun focus(field: FormField): FocusRequester = focus.of(field)

  fun edit(field: FormField, change: (ConnectionForm) -> ConnectionForm) = onIntent(PageIntent.Edit(field, change))

  fun toggle(change: (ConnectionForm) -> ConnectionForm) = onIntent(PageIntent.Toggle(change))

  fun openModelList() = onIntent(PageIntent.OpenModelList)
}
