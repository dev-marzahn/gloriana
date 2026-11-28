package dev.marzahn.gloriana.logic.connection.api

import dev.marzahn.gloriana.logic.connection.ApiFormat
import okhttp3.Dns
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Isolated
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Isolated
class PlainHttpTest {
  @TempDir
  lateinit var directory: Path

  private val stub by lazy { StubHostReplies(directory) }

  @AfterEach
  fun stopHost() = stub.close()

  private fun address(literal: String) = InetAddress.getByName(literal)

  private fun resolving(vararg literals: String) = Dns { literals.map(::address) }

  @Test
  fun privateAddressesAreTheOwnNetworkRanges() {
    listOf(
      "127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.20", "169.254.1.1", "100.64.0.1", "100.127.255.254",
      "::1", "fe80::1", "fd12::1", "fd7a:115c:a1e0::1",
    ).forEach { assertTrue(address(it).isPrivate(), it) }
    listOf("8.8.8.8", "172.32.0.1", "100.128.0.1", "100.63.255.255", "2001:4860::8888", "0.0.0.0", "fec0::1")
      .forEach { assertFalse(address(it).isPrivate(), it) }
  }

  @Test
  fun aHostNameGoesThroughOnlyWhenEveryAddressItResolvesToIsPrivate() {
    assertEquals(listOf(address("192.168.1.20")), PrivateAddressesOnly(resolving("192.168.1.20")).lookup("nas"))
    assertFailsWith<PlainHttpRefused> { PrivateAddressesOnly(resolving("192.168.1.20", "8.8.8.8")).lookup("mixed") }
    assertFailsWith<PlainHttpRefused> { PrivateAddressesOnly(resolving("8.8.8.8")).lookup("public") }
    assertFailsWith<PlainHttpRefused> { PrivateAddressesOnly(resolving()).lookup("nothing") }
  }

  @Test
  fun plainHttpToAPublicAddressIsRefused() {
    val public = openedConnection(ApiFormat.OpenAiChatCompletions, baseUrl = "http://8.8.8.8/v1")

    assertEquals(listOf<ModelEvent>(ModelEvent.Failed(ModelApiError.PlainHttpToPublicHost)), stub.events(public))
  }

  @Test
  fun plainHttpToAPrivateHostNameGoesThroughTheResolverCheck() {
    val localhost = stub.host.baseUrl("normal").replace("127.0.0.1", "localhost")
    val events = stub.events(openedConnection(ApiFormat.OpenAiChatCompletions, baseUrl = localhost))

    assertEquals(ModelEvent.Finish(FinishReason.Completed), events.last())
  }

  @Test
  fun plainHttpToAPrivateAddressGoesThroughWithNoProxy() {
    val system = ProxySelector.getDefault()
    val asked = mutableListOf<URI>()
    ProxySelector.setDefault(DeadProxy(asked))
    try {
      val events = stub.events(stub.connection(ApiFormat.OpenAiChatCompletions, "normal"))

      assertEquals(ModelEvent.Finish(FinishReason.Completed), events.last())
      assertEquals(emptyList(), asked)
    } finally {
      ProxySelector.setDefault(system)
    }
  }

  private class DeadProxy(private val asked: MutableList<URI>) : ProxySelector() {
    override fun select(uri: URI): List<Proxy> {
      asked += uri
      return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 1)))
    }

    override fun connectFailed(uri: URI, address: SocketAddress, failure: IOException) = Unit
  }
}
