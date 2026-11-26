import com.android.build.api.dsl.Lint
import org.gradle.api.Project

const val ANDROID_TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"

internal class AndroidSdk(val compile: Int, val min: Int, val target: Int)

internal val Project.androidSdk: AndroidSdk
  get() = AndroidSdk(
    compile = catalog.sdkLevel("android-compileSdk"),
    min = catalog.sdkLevel("android-minSdk"),
    target = catalog.sdkLevel("android-targetSdk"),
  )

internal fun Lint.failOnWarnings() {
  warningsAsErrors = true
  abortOnError = true
}
