import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

internal object JvmLevel {
  val java = JavaVersion.VERSION_11
  val kotlin = JvmTarget.fromTarget(java.toString())
  val release = java.majorVersion.toInt()
}
