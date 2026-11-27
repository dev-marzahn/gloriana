package dev.marzahn.gloriana.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

@Immutable
data class MotionToken(val durationMillis: Int, val easing: Easing, val delayMillis: Int = 0) {
  fun <T> tween(): TweenSpec<T> = tween(durationMillis, delayMillis, easing)

  fun after(previous: MotionToken): MotionToken = copy(delayMillis = previous.delayMillis + previous.durationMillis)
}

@Immutable
data class LookMotion(
  val standard: MotionToken,
  val enter: MotionToken,
  val exit: MotionToken,
  val fadeThroughOut: MotionToken,
  val fadeThroughIn: MotionToken,
  val paneOut: MotionToken,
  val paneIn: MotionToken,
)
