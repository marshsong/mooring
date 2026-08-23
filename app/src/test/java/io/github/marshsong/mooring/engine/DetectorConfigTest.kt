// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorConfigTest {

    @Test
    fun `parses PRD sample config`() {
        val json = """
            {
              "configVersion": 2,
              "detectionDebounceMs": 300,
              "excludedPackages": [],
              "appCatalog": [
                { "package": "com.ss.android.ugc.aweme", "label": "Douyin", "category": "video" },
                { "package": "com.smile.gifmaker", "label": "Kuaishou", "category": "video" },
                { "package": "tv.danmaku.bili", "label": "Bilibili", "category": "video" },
                { "package": "com.xingin.xhs", "label": "RED", "category": "video" },
                { "package": "com.google.android.youtube", "label": "YouTube", "category": "video" },
                { "package": "com.zhiliaoapp.musically", "label": "TikTok", "category": "video" },
                { "package": "com.instagram.android", "label": "Instagram", "category": "social" }
              ]
            }
        """.trimIndent()

        val config = DetectorConfig.fromJson(json)
        assertEquals(2, config.configVersion)
        assertEquals(300L, config.detectionDebounceMs)
        assertEquals(7, config.appCatalog.size)
        assertEquals("video", config.appCatalog.first().category)
    }

    @Test
    fun `catalog targets are disabled by default`() {
        val json = """{"appCatalog":[{"package":"com.example.app","label":"X","category":"video"}]}"""
        val targets = DetectorConfig.fromJson(json).catalogTargets()
        assertEquals(1, targets.size)
        assertEquals(TargetId.app("com.example.app"), targets[0].targetId)
        assertTrue(!targets[0].enabled)
    }

    @Test
    fun `launcher exclusion is appended without duplicate`() {
        val base = DetectorConfig(excludedPackages = listOf("com.android.launcher"))
        val withLauncher = DetectorConfig.withLauncherExcluded(base, "com.android.launcher")
        assertEquals(1, withLauncher.excludedPackages.size)
        val withNew = DetectorConfig.withLauncherExcluded(base, "com.other.launcher")
        assertEquals(2, withNew.excludedPackages.size)
    }

    @Test
    fun `blank launcher leaves config unchanged`() {
        val base = DetectorConfig(excludedPackages = emptyList())
        val out = DetectorConfig.withLauncherExcluded(base, "")
        assertEquals(base, out)
    }
}
