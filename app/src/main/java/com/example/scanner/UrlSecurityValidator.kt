package com.example.scanner

import okhttp3.Dns
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

object UrlSecurityValidator {

    fun isSafeHttpUrl(rawUrl: String, allowLocalServers: Boolean): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return false
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase()?.trim() ?: return false
        if (host.isEmpty()) return false

        if (!allowLocalServers && isBlockedHostString(host)) {
            return false
        }
        return true
    }

    fun isBlockedHostString(host: String): Boolean {
        val clean = host.removePrefix("[").removeSuffix("]").lowercase()
        if (clean == "localhost" || clean.endsWith(".localhost") || clean.endsWith(".local")) {
            return true
        }
        if (clean == "0.0.0.0" || clean == "::" || clean == "::1") {
            return true
        }
        // Check literal IPv4 / IPv6
        val ipv4 = parseIpv4OrNull(clean)
        if (ipv4 != null && isPrivateOrReservedIpv4(ipv4)) {
            return true
        }
        if (clean.contains(":")) {
            val addr = runCatching { InetAddress.getByName(clean) }.getOrNull()
            if (addr != null && isBlockedInetAddress(addr)) {
                return true
            }
        }
        return false
    }

    fun isBlockedInetAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        return when (address) {
            is Inet4Address -> isPrivateOrReservedIpv4(address.address)
            is Inet6Address -> {
                val bytes = address.address
                // fc00::/7 Unique local address
                val isUniqueLocal = (bytes[0].toInt() and 0xFE) == 0xFC
                // fe80::/10 Link-local
                val isLinkLocal = (bytes[0].toInt() and 0xFF) == 0xFE && (bytes[1].toInt() and 0xC0) == 0x80
                isUniqueLocal || isLinkLocal
            }
            else -> false
        }
    }

    private fun isPrivateOrReservedIpv4(b: ByteArray): Boolean {
        if (b.size != 4) return true
        val o0 = b[0].toInt() and 0xFF
        val o1 = b[1].toInt() and 0xFF
        return when {
            o0 == 0 -> true // 0.0.0.0/8
            o0 == 10 -> true // 10.0.0.0/8
            o0 == 100 && o1 in 64..127 -> true // 100.64.0.0/10 CGNAT
            o0 == 127 -> true // 127.0.0.0/8 loopback
            o0 == 169 && o1 == 254 -> true // 169.254.0.0/16 link-local / metadata
            o0 == 172 && o1 in 16..31 -> true // 172.16.0.0/12
            o0 == 192 && o1 == 168 -> true // 192.168.0.0/16
            o0 == 192 && o1 == 0 && (b[2].toInt() and 0xFF) == 0 -> true
            o0 >= 224 -> true // Multicast / reserved
            else -> false
        }
    }

    private fun parseIpv4OrNull(host: String): ByteArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        for (i in 0..3) {
            val v = parts[i].toIntOrNull() ?: return null
            if (v !in 0..255) return null
            bytes[i] = v.toByte()
        }
        return bytes
    }

    class SsrfSafeDns(private val allowLocalServers: Boolean) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val resolved = Dns.SYSTEM.lookup(hostname)
            if (!allowLocalServers) {
                for (addr in resolved) {
                    if (isBlockedInetAddress(addr)) {
                        throw UnknownHostException("Blocked private or reserved address for $hostname")
                    }
                }
            }
            return resolved
        }
    }
}
