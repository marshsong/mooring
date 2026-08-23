// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class UsageTrackerTest {

    private var nowMs = 0L
    private var nowLocal = LocalDateTime.of(2026, 8, 23, 10, 0)
    private val accumulated = mutableListOf<Triple<String, Long, String>>()

    private fun tracker() = UsageTracker(
        clock = { nowMs },
        nowLocal = { nowLocal },
        onAccumulate = { t, s, d -> accumulated.add(Triple(t, s, d)) },
    )

    @Test
    fun `accumulates time while target is foreground`() {
        val tr = tracker()
        nowMs = 0
        tr.onForeground("APP:com.example.a")
        nowMs = 65_000
        tr.flush()
        assertEquals(listOf(Triple("APP:com.example.a", 65L, "2026-08-23")), accumulated)
    }

    @Test
    fun `flush resets base so heartbeats do not double count`() {
        val tr = tracker()
        nowMs = 0
        tr.onForeground("APP:com.example.a")
        nowMs = 10_000
        tr.flush()
        nowMs = 20_000
        tr.flush()
        assertEquals(
            listOf(
                Triple("APP:com.example.a", 10L, "2026-08-23"),
                Triple("APP:com.example.a", 10L, "2026-08-23"),
            ),
            accumulated,
        )
    }

    @Test
    fun `switching foreground flushes previous and starts new`() {
        val tr = tracker()
        nowMs = 0
        tr.onForeground("APP:com.example.a")
        nowMs = 30_000
        tr.onForeground("APP:com.example.b")
        nowMs = 50_000
        tr.flush()
        assertEquals(
            listOf(
                Triple("APP:com.example.a", 30L, "2026-08-23"),
                Triple("APP:com.example.b", 20L, "2026-08-23"),
            ),
            accumulated,
        )
    }

    @Test
    fun `leaving to null stops tracking`() {
        val tr = tracker()
        nowMs = 0
        tr.onForeground("APP:com.example.a")
        nowMs = 5_000
        tr.onForeground(null)
        assertFalse(tr.isTracking)
        nowMs = 5_000
        tr.flush()
        assertEquals(1, accumulated.size)
    }

    @Test
    fun `sub-second elapsed does not accumulate`() {
        val tr = tracker()
        nowMs = 0
        tr.onForeground("APP:com.example.a")
        nowMs = 500
        tr.flush()
        assertTrue(accumulated.isEmpty())
    }
}
