package dev.marzahn.gloriana.logic.keys

const val PASSPHRASE_MIN_LENGTH = 8

enum class NewPassphraseProblem { TooShort, Mismatch }

fun newPassphraseProblem(passphrase: CharSequence, again: CharSequence): NewPassphraseProblem? = when {
  passphrase.length < PASSPHRASE_MIN_LENGTH -> NewPassphraseProblem.TooShort
  !passphrase.contentEquals(again) -> NewPassphraseProblem.Mismatch
  else -> null
}
