package dev.marzahn.gloriana.logic.connection.api

import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Response
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

private const val BYTE_MASK = 0xff
private const val UNIQUE_LOCAL_MASK = 0xfe
private const val UNIQUE_LOCAL_PREFIX = 0xfc
private const val SHARED_SPACE_FIRST = 100
private const val SHARED_SPACE_MASK = 0xc0
private const val SHARED_SPACE_PREFIX = 0x40

internal fun InetAddress.isPrivate(): Boolean =
  isLoopbackAddress || isLinkLocalAddress || isPrivateV4() || isUniqueLocal() || isSharedAddressSpace()

private fun InetAddress.isPrivateV4(): Boolean = this is Inet4Address && isSiteLocalAddress

private fun InetAddress.isUniqueLocal(): Boolean =
  this is Inet6Address && (byteAt(0) and UNIQUE_LOCAL_MASK) == UNIQUE_LOCAL_PREFIX

private fun InetAddress.isSharedAddressSpace(): Boolean =
  this is Inet4Address && byteAt(0) == SHARED_SPACE_FIRST && (byteAt(1) and SHARED_SPACE_MASK) == SHARED_SPACE_PREFIX

private fun InetAddress.byteAt(index: Int): Int = address[index].toInt() and BYTE_MASK

internal class PrivateAddressesOnly(private val resolver: Dns) : Dns {
  override fun lookup(hostname: String): List<InetAddress> {
    val addresses = resolver.lookup(hostname)
    if (addresses.isEmpty() || !addresses.all(InetAddress::isPrivate)) throw PlainHttpRefused()
    return addresses
  }
}

internal object PrivateAddressLiteralsOnly : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val host = chain.request().url.host
    if (host.isAddressLiteral() && !InetAddress.getByName(host).isPrivate()) throw PlainHttpRefused()
    return chain.proceed(chain.request())
  }
}

private fun String.isAddressLiteral(): Boolean = contains(':') || all { it.isDigit() || it == '.' }
