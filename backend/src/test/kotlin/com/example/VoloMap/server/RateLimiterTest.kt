package com.example.VoloMap.server

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class RateLimiterTest {

    @Test
    fun `allows requests up to the limit then blocks`() {
        val limiter = RateLimiter(enabled = true)
        repeat(3) {
            assertTrue(limiter.isAllowed("key", 3, Duration.ofMinutes(1)))
        }
        assertFalse(limiter.isAllowed("key", 3, Duration.ofMinutes(1)))
    }

    @Test
    fun `different keys are tracked independently`() {
        val limiter = RateLimiter(enabled = true)
        assertTrue(limiter.isAllowed("a", 1, Duration.ofMinutes(1)))
        assertFalse(limiter.isAllowed("a", 1, Duration.ofMinutes(1)))
        assertTrue(limiter.isAllowed("b", 1, Duration.ofMinutes(1)))
    }

    @Test
    fun `disabled limiter always allows`() {
        val limiter = RateLimiter(enabled = false)
        repeat(10) {
            assertTrue(limiter.isAllowed("key", 1, Duration.ofMinutes(1)))
        }
    }
}
