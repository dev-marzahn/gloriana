package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.assets.AssetHash
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CardFixtures {
  private val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

  fun image(red: Int, green: Int = 0, blue: Int = 0, width: Int = 2, height: Int = 2): ByteArray {
    val header = ByteArrayOutputStream().also {
      DataOutputStream(it).apply {
        writeInt(width)
        writeInt(height)
        write(byteArrayOf(8, 2, 0, 0, 0))
      }
    }.toByteArray()
    val pixels = ByteArrayOutputStream().also { out ->
      DeflaterOutputStream(out).use { deflated ->
        repeat(height) {
          deflated.write(0)
          repeat(width) { deflated.write(byteArrayOf(red.toByte(), green.toByte(), blue.toByte())) }
        }
      }
    }.toByteArray()
    return ByteArrayOutputStream().also { png ->
      png.write(signature)
      png.chunk("IHDR", header)
      png.chunk("IDAT", pixels)
      png.chunk("IEND", ByteArray(0))
    }.toByteArray()
  }

  fun png(image: ByteArray, vararg texts: Pair<String, ByteArray>): ByteArray {
    val iendStart = image.size - 12
    return ByteArrayOutputStream().also { png ->
      png.write(image, 0, iendStart)
      texts.forEach { (keyword, text) ->
        png.chunk("tEXt", keyword.toByteArray(Charsets.ISO_8859_1) + 0.toByte() + text)
      }
      png.write(image, iendStart, 12)
    }.toByteArray()
  }

  fun base64(json: JsonElement): ByteArray = Base64.getEncoder().encode(Json.encodeToString(json).toByteArray())

  fun base64(bytes: ByteArray): ByteArray = Base64.getEncoder().encode(bytes)

  fun zip(vararg entries: Pair<String, ByteArray>, method: Int = ZipEntry.DEFLATED): ByteArray =
    ByteArrayOutputStream().also { out ->
      ZipOutputStream(out).use { zip ->
        entries.forEach { (name, bytes) ->
          val entry = ZipEntry(name).apply {
            this.method = method
            if (method == ZipEntry.STORED) {
              size = bytes.size.toLong()
              crc = CRC32().apply { update(bytes) }.value
            }
          }
          zip.putNextEntry(entry)
          zip.write(bytes)
          zip.closeEntry()
        }
      }
    }.toByteArray()

  fun json(card: JsonObject): ByteArray = Json.encodeToString(card).toByteArray()

  fun sha256(bytes: ByteArray) = AssetHash(MessageDigest.getInstance("SHA-256").digest(bytes).toHexString())

  private fun ByteArrayOutputStream.chunk(type: String, body: ByteArray) {
    val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
    val crc = CRC32().apply {
      update(typeBytes)
      update(body)
    }
    DataOutputStream(this).apply {
      writeInt(body.size)
      write(typeBytes)
      write(body)
      writeInt(crc.value.toInt())
    }
  }
}
