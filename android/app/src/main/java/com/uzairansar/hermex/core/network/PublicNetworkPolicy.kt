package com.uzairansar.hermex.core.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

internal object PublicNetworkDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        requirePublicNetworkHost(hostname)
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (addresses.isEmpty() || addresses.any { !isPublicNetworkAddress(it) }) {
            throw UnknownHostException("Remote media cannot access local or private network addresses.")
        }
        return addresses
    }
}

internal fun requirePublicNetworkHost(host: String) {
    val normalized = host.trim().trimEnd('.').lowercase()
    val blockedSuffixes = listOf(
        "localhost",
        ".localhost",
        ".local",
        ".lan",
        ".internal",
        ".home.arpa",
        ".test",
        ".invalid",
        ".example",
    )
    if (normalized.isEmpty() || blockedSuffixes.any(normalized::endsWith)) {
        throw UnknownHostException("Remote media cannot access local or private network hosts.")
    }
    if ('.' !in normalized && ':' !in normalized) {
        throw UnknownHostException("Remote media requires a public host name.")
    }
}

internal fun isPublicNetworkAddress(address: InetAddress): Boolean {
    if (
        address.isAnyLocalAddress ||
        address.isLoopbackAddress ||
        address.isLinkLocalAddress ||
        address.isSiteLocalAddress ||
        address.isMulticastAddress
    ) {
        return false
    }
    val bytes = address.address.map { it.toInt() and 0xff }
    if (bytes.size == 4) {
        val first = bytes[0]
        val second = bytes[1]
        val third = bytes[2]
        return when {
            first == 0 || first == 10 || first == 127 -> false
            first == 100 && second in 64..127 -> false
            first == 169 && second == 254 -> false
            first == 172 && second in 16..31 -> false
            first == 192 && second == 0 && third in setOf(0, 2) -> false
            first == 192 && second == 88 && third == 99 -> false
            first == 192 && second == 168 -> false
            first == 198 && second in 18..19 -> false
            first == 198 && second == 51 && third == 100 -> false
            first == 203 && second == 0 && third == 113 -> false
            first >= 224 -> false
            else -> true
        }
    }
    if (bytes.size == 16) {
        val uniqueLocal = bytes[0] and 0xfe == 0xfc
        val documentation = bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == 0xb8
        return !uniqueLocal && !documentation
    }
    return false
}
