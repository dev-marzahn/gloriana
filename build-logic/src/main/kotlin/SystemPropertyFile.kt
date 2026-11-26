import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

class SystemPropertyFile(
  @get:Input val name: String,
  file: RegularFile,
  @get:Internal val workingDir: File,
) : CommandLineArgumentProvider {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  val file: File = file.asFile

  override fun asArguments() = listOf("-D$name=${file.relativeTo(workingDir).path}")
}
