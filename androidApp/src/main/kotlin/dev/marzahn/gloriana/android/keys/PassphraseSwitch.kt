package dev.marzahn.gloriana.android.keys

import dev.marzahn.gloriana.logic.debug.DebugSwitch
import dev.marzahn.gloriana.logic.debug.DebugSwitches
import dev.marzahn.gloriana.logic.keys.DeviceKey
import dev.marzahn.gloriana.logic.keys.DeviceKeyStore
import dev.marzahn.gloriana.logic.keys.NewDeviceKey

enum class KeySwitch(override val key: String) : DebugSwitch {
  ForcePassphrase("keys.passphrase"),
}

class PassphraseSwitch(private val keystore: DeviceKeyStore, private val switches: DebugSwitches) : DeviceKeyStore {
  override fun find(name: String): DeviceKey = if (forced()) DeviceKey.NoStore else keystore.find(name)

  override fun create(): NewDeviceKey = if (forced()) NewDeviceKey.NoStore else keystore.create()

  override fun delete(name: String) = keystore.delete(name)

  private fun forced() = switches.isOn(KeySwitch.ForcePassphrase)
}
