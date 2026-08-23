// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine.subscription

/** 解析并校验通过后的订阅结构（纯数据，供 T2 引擎消费）。 */
data class ParsedSubscription(
    val subscriptionVersion: Int,
    val name: String,
    val apps: List<ParsedApp>,
) {
    val featureCount: Int get() = apps.sumOf { it.features.size }

    data class ParsedApp(
        val packageName: String,
        val features: List<ParsedFeature>,
    )

    data class ParsedFeature(
        val featureId: String,
        val label: String,
        val alwaysBlock: Boolean,
        val activityPatterns: List<String>,
        val contentRules: ContentRules?,
    )

    data class ContentRules(
        val titleKeywords: List<String>,
        val tabKeywords: List<String>,
        val requiredTabs: Int,
        val extraKeywords: List<String>,
    )
}
