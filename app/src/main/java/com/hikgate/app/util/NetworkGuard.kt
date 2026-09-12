package com.hikgate.app.util

/**
 * Decides whether a host looks like a LAN/private address (192.168.x.x,
 * 10.x.x.x, 172.16-31.x.x, localhost/127.0.0.1) vs. something reached over
 * the open internet. Used only to decide how loudly to warn before an
 * insecure HTTP connection — cameras on your own LAN over plain HTTP are a
 * normal (if imperfect) reality of this hardware generation; the same
 * connection to a public IP deserves a stronger warning.
 */
object NetworkGuard {

    fun isPrivateOrLocal(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true) || host == "127.0.0.1") return true

        val parts = host.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false // hostnames / IPv6 fall through to "not confirmed private"

        val (a, b, _, _) = parts
        return when {
            a == 10 -> true
            a == 192 && b == 168 -> true
            a == 172 && b in 16..31 -> true
            a == 127 -> true
            else -> false
        }
    }
}
