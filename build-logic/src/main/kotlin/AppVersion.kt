import org.gradle.api.Project

val Project.appVersion: AppVersion
  get() = AppVersion.parse(providers.gradleProperty("gloriana.version").get())

enum class Abi(val id: String, val slot: Int) {
  ArmeabiV7a("armeabi-v7a", 1),
  Arm64V8a("arm64-v8a", 2),
  X8664("x86_64", 3),
  ;

  init {
    require(slot in 1 until AppVersion.ABI_SLOTS) { "$id's slot $slot must be within 1 until ${AppVersion.ABI_SLOTS}" }
  }

  companion object {
    fun of(id: String): Abi = requireNotNull(entries.find { it.id == id }) {
      "Unknown ABI $id; known: ${entries.joinToString { it.id }}"
    }
  }
}

data class AppVersion(val major: Int, val minor: Int, val patch: Int) {
  init {
    require(major >= 0) { "major must not be negative: $this" }
    require(minor in 0 until PART_SLOTS && patch in 0 until PART_SLOTS) {
      "minor and patch must be below $PART_SLOTS to fit the version code: $this"
    }
    require(longCode * ABI_SLOTS + ABI_SLOTS - 1 <= MAX_VERSION_CODE) {
      "$this gives a version code above Google Play's limit of $MAX_VERSION_CODE"
    }
  }

  private val longCode: Long get() = (major.toLong() * PART_SLOTS + minor) * PART_SLOTS + patch

  val code: Int get() = longCode.toInt()

  fun codeFor(abi: Abi): Int = code * ABI_SLOTS + abi.slot

  override fun toString() = "$major.$minor.$patch"

  companion object {
    const val PART_SLOTS = 100
    const val ABI_SLOTS = 10
    const val MAX_VERSION_CODE = 2_100_000_000

    private val pattern = Regex("""(\d+)\.(\d+)\.(\d+)""")

    fun parse(text: String): AppVersion {
      val parts = requireNotNull(pattern.matchEntire(text)) { "The app version must be X.Y.Z; it's $text" }
        .groupValues.drop(1).map(String::toInt)
      return AppVersion(parts[0], parts[1], parts[2])
    }
  }
}
