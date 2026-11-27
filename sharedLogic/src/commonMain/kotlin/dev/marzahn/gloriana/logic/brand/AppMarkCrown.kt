package dev.marzahn.gloriana.logic.brand

data class OutlinePoint(val x: Float, val y: Float)

internal data class OutlineBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
  val width: Float get() = right - left
  val height: Float get() = bottom - top
}

internal fun List<OutlinePoint>.bounds(): OutlineBounds =
  OutlineBounds(minOf { it.x }, minOf { it.y }, maxOf { it.x }, maxOf { it.y })

object AppMarkCrown {
  const val VIEWPORT = 24f

  val shapes: List<List<OutlinePoint>> = listOf(
    listOf(
      OutlinePoint(4f, 17.5f),
      OutlinePoint(3f, 7.5f),
      OutlinePoint(8.25f, 11.5f),
      OutlinePoint(12f, 5f),
      OutlinePoint(15.75f, 11.5f),
      OutlinePoint(21f, 7.5f),
      OutlinePoint(20f, 17.5f),
    ),
    listOf(OutlinePoint(4f, 19f), OutlinePoint(20f, 19f), OutlinePoint(20f, 20.5f), OutlinePoint(4f, 20.5f)),
  )
}
