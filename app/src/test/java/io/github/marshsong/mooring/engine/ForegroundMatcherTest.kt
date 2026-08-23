// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine

import io.github.marshsong.mooring.engine.model.Target
import io.github.marshsong.mooring.engine.model.TargetKind
import io.github.marshsong.mooring.engine.model.TargetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundMatcherTest {

    private val config = DetectorConfig(
        configVersion = 2,
        detectionDebounceMs = 300,
        excludedPackages = listOf("com.example.systemui"),
        appCatalog = emptyList(),
    )

    private fun target(pkg: String, enabled: Boolean = true) = Target(
        targetId = TargetId.app(pkg),
        label = pkg,
        kind = TargetKind.APP,
        packageName = pkg,
        source = TargetSource.CATALOG,
        enabled = enabled,
        createdAt = 0L,
    )

    @Test
    fun `matches enabled app target on foreground`() {
        val t = target("com.example.a")
        val hit = ForegroundMatcher.match("com.example.a", "io.github.marshsong.mooring", config, listOf(t))
        assertEquals(listOf(t), hit)
    }

    @Test
    fun `ignores self package`() {
        val t = target("io.github.marshsong.mooring")
        val hit = ForegroundMatcher.match("io.github.marshsong.mooring", "io.github.marshsong.mooring", config, listOf(t))
        assertTrue(hit.isEmpty())
    }

    @Test
    fun `ignores excluded packages`() {
        val t = target("com.example.systemui")
        val hit = ForegroundMatcher.match("com.example.systemui", "self", config, listOf(t))
        assertTrue(hit.isEmpty())
    }

    @Test
    fun `ignores disabled targets`() {
        val t = target("com.example.a", enabled = false)
        val hit = ForegroundMatcher.match("com.example.a", "self", config, listOf(t))
        assertTrue(hit.isEmpty())
    }

    @Test
    fun `ignores non-matching foreground`() {
        val t = target("com.example.a")
        val hit = ForegroundMatcher.match("com.example.other", "self", config, listOf(t))
        assertTrue(hit.isEmpty())
    }

    @Test
    fun `null or blank foreground returns empty`() {
        val t = target("com.example.a")
        assertTrue(ForegroundMatcher.match(null, "self", config, listOf(t)).isEmpty())
        assertTrue(ForegroundMatcher.match("", "self", config, listOf(t)).isEmpty())
    }
}
