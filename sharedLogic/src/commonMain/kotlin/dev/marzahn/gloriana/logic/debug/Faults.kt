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
