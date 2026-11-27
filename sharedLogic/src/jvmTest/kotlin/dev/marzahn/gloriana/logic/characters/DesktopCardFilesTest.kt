package dev.marzahn.gloriana.logic.characters

import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DesktopCardFilesTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun aMissingPickedFileIsUnavailable() {
    assertEquals(OpenedFile.Unavailable, DesktopPickedFile(directory.resolve("gone.png")).open())
  }

  @Test
  fun aPickedFileReadsItsBytes() {
    val file = directory.resolve("card.json")
    Files.write(file, byteArrayOf(1, 2, 3))

    val opened = assertIs<OpenedFile.Opened>(DesktopPickedFile(file).open())

    val bytes = ByteArray(3)
    opened.source.use { it.read(bytes, 0, 3) }
    assertEquals(listOf<Byte>(1, 2, 3), bytes.toList())
  }

  @Test
  fun anExportIsWrittenWhole() {
    val target = directory.resolve("out.png")

    val write = DesktopExportDestination(target).write { sink -> sink.write(byteArrayOf(9, 8), 0, 2) }

    assertEquals(ExportWrite.Written, write)
    assertEquals(listOf<Byte>(9, 8), Files.readAllBytes(target).toList())
    assertEquals(listOf("out.png"), directory.toFile().list().orEmpty().toList())
  }

  @Test
  fun anExportThatFailsLeavesNoFile() {
    val target = directory.resolve("out.png")

    val write = DesktopExportDestination(target).write { sink ->
      sink.write(byteArrayOf(1), 0, 1)
      throw IOException("No space left on device")
    }

    assertEquals(ExportWrite.Failed(ExportProblem(ExportFailure.StorageFull, incompleteFileLeft = false)), write)
    assertEquals(emptyList(), directory.toFile().list().orEmpty().toList())
  }

  @Test
  fun anExportToAFolderThatCantBeWrittenSaysSo() {
    val target = directory.resolve("missing").resolve("out.png")

    val write = DesktopExportDestination(target).write { }

    assertEquals(ExportWrite.Failed(ExportProblem(ExportFailure.CantWrite, incompleteFileLeft = false)), write)
  }
}
