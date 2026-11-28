package dev.marzahn.gloriana.logic.keys

import java.io.IOException

interface WrappingKey {
  fun seal(plain: ByteArray, associatedData: ByteArray): ByteArray

  fun open(sealed: ByteArray, associatedData: ByteArray): Unwrapped
}

sealed interface Unwrapped {
  class Opened(val bytes: ByteArray) : Unwrapped

  data object WrongKey : Unwrapped

  data object Lost : Unwrapped
}

sealed interface DeviceKey {
  class Found(val key: WrappingKey) : DeviceKey

  data object Lost : DeviceKey

  data object Unconfirmed : DeviceKey

  data object Unusable : DeviceKey

  data object Unreachable : DeviceKey

  data object NoStore : DeviceKey
}

sealed interface NewDeviceKey {
  class Made(val name: String, val key: WrappingKey) : NewDeviceKey

  data object Unreachable : NewDeviceKey

  data object NoStore : NewDeviceKey
}

interface DeviceKeyStore {
  fun find(name: String): DeviceKey

  fun create(): NewDeviceKey

  fun delete(name: String)
}

class DeviceKeyUnreachable(cause: Throwable) : IOException(cause)
