package com.example.bsred
import org.junit.Assert.*
import org.junit.Test

class TrackingPolicyTest {
    @Test fun nativeBridgeOnlyAcceptsTheProductionHttpsOrigin() {
        assertTrue(TrackingPolicy.trustedAddress("https://bsred.onrender.com/usuario"))
        assertTrue(TrackingPolicy.trustedAddress("https://bsred.onrender.com:443/"))
        for (url in listOf("http://bsred.onrender.com/", "https://bsred.onrender.com.evil.invalid/", "https://bsred.onrender.com@evil.invalid/", "https://user@bsred.onrender.com/", "https://bsred.onrender.com:8443/", "file:///etc/passwd", "javascript:alert(1)"))
            assertFalse(url, TrackingPolicy.trustedAddress(url))
    }
    @Test fun invalidCredentialsCannotStartTracking() {
        assertTrue(TrackingPolicy.validJourney(1,"a".repeat(43)))
        assertFalse(TrackingPolicy.validJourney(0,"a".repeat(43)))
        assertFalse(TrackingPolicy.validJourney(-1,"a".repeat(43)))
        assertFalse(TrackingPolicy.validJourney(1,"short"))
        assertFalse(TrackingPolicy.validJourney(1,"/".repeat(43)))
    }
    @Test fun staleAndFuturePositionsAreNotUploaded() {
        val now=1000000L
        assertTrue(TrackingPolicy.freshPosition(now,now))
        assertTrue(TrackingPolicy.freshPosition(now-120000,now))
        assertFalse(TrackingPolicy.freshPosition(now-120001,now))
        assertFalse(TrackingPolicy.freshPosition(now+30001,now))
    }
}
