// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionParserTest {

    private val valid = """
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

    @Test
    fun `parses valid PRD sample`() {
        val parsed = SubscriptionParser.parse(valid)
        assertEquals(1, parsed.subscriptionVersion)
        assertEquals("Mock Super App Feed Blocker", parsed.name)
        assertEquals(1, parsed.apps.size)
        assertEquals(2, parsed.featureCount)
        val feed = parsed.apps[0].features.first { it.featureId == "MOCK_FEED" }
        assertTrue(feed.contentRules!!.tabKeywords.contains("FOLLOWING"))
        assertEquals(1, feed.contentRules!!.requiredTabs)
    }

    @Test(expected = SubscriptionValidationException::class)
    fun `rejects invalid json`() {
        SubscriptionParser.parse("{ not json")
    }

    @Test(expected = SubscriptionValidationException::class)
    fun `rejects empty apps`() {
        SubscriptionParser.parse("""{"apps":[]}""")
    }

    @Test(expected = SubscriptionValidationException::class)
    fun `rejects feature-less app`() {
        SubscriptionParser.parse("""{"apps":[{"package":"com.example.a","features":[]}]}""")
    }

    @Test(expected = SubscriptionValidationException::class)
    fun `rejects invalid package name`() {
        SubscriptionParser.parse("""{"apps":[{"package":"noDots","features":[{"featureId":"F1"}]}]}""")
    }

    @Test(expected = SubscriptionValidationException::class)
    fun `rejects uncompilable regex`() {
        SubscriptionParser.parse(
            """{"apps":[{"package":"com.example.a","features":[{"featureId":"F1","activityPatterns":["["]}]}]}"""
        )
    }

    @Test(expected = SubscriptionValidationException::class)
    fun `rejects blank featureId`() {
        SubscriptionParser.parse("""{"apps":[{"package":"com.example.a","features":[{"featureId":"  "}]}]}""")
    }

    @Test
    fun `blank label falls back to featureId`() {
        val parsed = SubscriptionParser.parse("""{"apps":[{"package":"com.example.a","features":[{"featureId":"F1"}]}]}""")
        assertEquals("F1", parsed.apps[0].features[0].label)
        assertNull(parsed.apps[0].features[0].contentRules)
    }
}
