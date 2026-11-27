package dev.marzahn.gloriana.logic.connection.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import okhttp3.Dns
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.SocketFactory

internal actual val modelHttpModule: Module = module {
  single { OkHttpModelClients() } bind ModelHttpClients::class
}

internal class OkHttpModelClients : ModelHttpClients {
  private val secure = modelHttpClient(OkHttp.create())
  private val plain = modelHttpClient(
    OkHttp.create {
      config {
        proxy(Proxy.NO_PROXY)
        socketFactory(NoProxySockets)
        dns(PrivateAddressesOnly(Dns.SYSTEM))
        addInterceptor(PrivateAddressLiteralsOnly)
      }
    },
  )

  override fun forUrl(url: Url): HttpClient = if (url.protocol == URLProtocol.HTTP) plain else secure
}

internal object NoProxySockets : SocketFactory() {
  override fun createSocket(): Socket = Socket(Proxy.NO_PROXY)

  override fun createSocket(host: String, port: Int): Socket = connected(InetSocketAddress(host, port))

  override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
    connected(InetSocketAddress(host, port), InetSocketAddress(localHost, localPort))

  override fun createSocket(host: InetAddress, port: Int): Socket = connected(InetSocketAddress(host, port))

  override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
    connected(InetSocketAddress(address, port), InetSocketAddress(localAddress, localPort))

  private fun connected(remote: InetSocketAddress, local: InetSocketAddress? = null): Socket =
    createSocket().apply {
      local?.let(::bind)
      connect(remote)
    }
}
