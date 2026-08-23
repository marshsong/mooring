// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine.t2

import io.github.marshsong.mooring.engine.model.Subscription
import io.github.marshsong.mooring.engine.subscription.SubscriptionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T2DetectorTest {

    private val configJson = """
        {
          "subscriptionVersion": 1,
          "name": "Mock Super App Feed Blocker",
          "apps": [
            {
              "package": "com.example.mocksuperapp",
              "features": [
                {
                  "featureId": "MOCK_FEED",
                  "label": "Mock Feed",
                  "alwaysBlock": false,
                  "activityPatterns": ["com\\.example\\.mocksuperapp\\.feed\\..*"],
                  "contentRules": {
                    "titleKeywords": ["MOCKFEED"],
                    "tabKeywords": ["FOR YOU", "FOLLOWING"],
                    "requiredTabs": 1
                  }
                },
                {
                  "featureId": "MOCK_LIVE",
                  "label": "Mock Live",
                  "alwaysBlock": true,
                  "activityPatterns": ["com\\.example\\.mocksuperapp\\.live\\..*"],
                  "contentRules": { "extraKeywords": ["LIVE NOW"] }
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun detector(enabled: Boolean = true): T2Detector {
        val d = T2Detector()
        d.load(
            listOf(
                Subscription(
                    id = "sub-1",
                    name = "mock",
                    configJson = configJson,
                    version = 1,
                    enabled = enabled,
                    importedAt = 0,
                    updatedAt = 0,
                )
            )
        )
        return d
    }

    @Test
    fun `has active features for subscribed package only`() {
        val d = detector()
        assertTrue(d.hasActiveFeatures("com.example.mocksuperapp"))
        assertFalse(d.hasActiveFeatures("com.example.other"))
    }

    @Test
    fun `level one matches window class name`() {
        val d = detector()
        val feat = d.matchByClassName("com.example.mocksuperapp", "com.example.mocksuperapp.feed.FeedActivity")
        assertEquals("MOCK_FEED", feat!!.featureId)
        val live = d.matchByClassName("com.example.mocksuperapp", "com.example.mocksuperapp.live.LiveActivity")
        assertEquals("MOCK_LIVE", live!!.featureId)
    }

    @Test
    fun `level one misses unrelated class`() {
        val d = detector()
        assertNull(d.matchByClassName("com.example.mocksuperapp", "com.example.mocksuperapp.chat.ChatActivity"))
    }

    @Test
    fun `level two matches title keyword`() {
        val d = detector()
        val feat = d.matchByContent("com.example.mocksuperapp", listOf("MOCKFEED", "Scrollable feed content"))
        assertEquals("MOCK_FEED", feat!!.featureId)
    }

    @Test
    fun `level two matches tabs when required count reached`() {
        val d = detector()
        val feat = d.matchByContent("com.example.mocksuperapp", listOf("FOR YOU"))
        assertEquals("MOCK_FEED", feat!!.featureId)
    }

    @Test
    fun `level two matches extra keyword`() {
        val d = detector()
        val feat = d.matchByContent("com.example.mocksuperapp", listOf("LIVE NOW"))
        assertEquals("MOCK_LIVE", feat!!.featureId)
    }

    @Test
    fun `level two misses on unrelated content`() {
        val d = detector()
        assertNull(d.matchByContent("com.example.mocksuperapp", listOf("chat message", "联系人")))
    }

    @Test
    fun `empty texts never match`() {
        val d = detector()
        assertNull(d.matchByContent("com.example.mocksuperapp", emptyList()))
    }

    @Test
    fun `disabled subscription not indexed`() {
        val d = detector(enabled = false)
        assertEquals(0, d.featureCount)
        assertFalse(d.hasActiveFeatures("com.example.mocksuperapp"))
    }

    @Test
    fun `target id is built from package and feature`() {
        val d = detector()
        val feat = d.matchByClassName("com.example.mocksuperapp", "com.example.mocksuperapp.feed.FeedActivity")
        assertEquals("FUNC:com.example.mocksuperapp:MOCK_FEED", d.targetIdOf("com.example.mocksuperapp", feat!!))
    }
}
