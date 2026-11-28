package dev.marzahn.gloriana.logic.debug

interface FaultPoint {
  val key: String
}

fun interface Faults {
  fun reached(point: FaultPoint)

  companion object {
    val None = Faults { }
  }
}

class FaultInjected(point: FaultPoint) : RuntimeException("fault point ${point.key}")

interface DebugSwitch {
  val key: String
}

interface DebugSignal {
  val fileName: String
}

interface DebugSwitches {
  fun isOn(switch: DebugSwitch): Boolean

  fun take(signal: DebugSignal): Boolean

  companion object {
    val None = object : DebugSwitches {
      override fun isOn(switch: DebugSwitch) = false

      override fun take(signal: DebugSignal) = false
    }
  }
}
