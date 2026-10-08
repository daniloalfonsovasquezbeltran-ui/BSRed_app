package com.example.bsred

import java.net.URI

internal object TrackingPolicy {
    fun trustedAddress(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "bsred.onrender.com" && uri.userInfo == null && (uri.port == -1 || uri.port == 443)
    } catch (_: Exception) { false }
    fun validJourney(id: Long, token: String) = id > 0 && Regex("[A-Za-z0-9_-]{43}").matches(token)
    fun freshPosition(at: Long, now: Long) = now - at in -30000L..120000L
}
