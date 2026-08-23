// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine.t2

import io.github.marshsong.mooring.engine.TargetId
import io.github.marshsong.mooring.engine.model.Subscription
import io.github.marshsong.mooring.engine.subscription.ParsedSubscription
import io.github.marshsong.mooring.engine.subscription.SubscriptionParser

/**
 * T2 功能级检测器（纯逻辑，无 Android 依赖，可单测）。
 *
 * 两级判定，命中任一级即认为处于该功能页面：
 *  一级：窗口组件类名匹配 activityPatterns（优先）；
 *  二级：节点文本匹配 contentRules（标题关键词 / Tab 关键词 / 额外关键词）。
 */
class T2Detector {

    private class FeatureEntry(
        val packageName: String,
        val feature: ParsedSubscription.ParsedFeature,
        val targetId: String,
        val compiledPatterns: List<Regex>,
    )

    private val byPackage = HashMap<String, List<FeatureEntry>>()
    private val all = ArrayList<FeatureEntry>()

    val featureCount: Int get() = all.size

    /** 从启用的订阅重建索引（热更新入口）。配置损坏时跳过该订阅。 */
    fun load(subscriptions: List<Subscription>) {
        byPackage.clear()
        all.clear()
        subscriptions.filter { it.enabled }.forEach { sub ->
            val parsed = runCatching { SubscriptionParser.parse(sub.configJson) }.getOrNull() ?: return@forEach
            for (app in parsed.apps) {
                val entries = app.features.map { feature ->
                    FeatureEntry(
                        packageName = app.packageName,
                        feature = feature,
                        targetId = TargetId.func(app.packageName, feature.featureId),
                        compiledPatterns = feature.activityPatterns.map { Regex(it) },
                    )
                }
                all.addAll(entries)
                byPackage.merge(app.packageName, entries) { a, b -> a + b }
            }
        }
    }

    /** 该包是否存在已启用订阅的 FUNC 特征（决定是否处理其内容事件）。 */
    fun hasActiveFeatures(packageName: String): Boolean = byPackage.containsKey(packageName)

    fun packages(): Set<String> = byPackage.keys

    /** 一级：窗口类名匹配。 */
    fun matchByClassName(packageName: String, className: String?): ParsedSubscription.ParsedFeature? {
        if (className == null) return null
        return byPackage[packageName]?.firstOrNull { entry ->
            entry.compiledPatterns.any { it.matches(className) }
        }?.feature
    }

    /** 二级：节点文本匹配 contentRules。 */
    fun matchByContent(packageName: String, texts: List<String>): ParsedSubscription.ParsedFeature? {
        if (texts.isEmpty()) return null
        return byPackage[packageName]?.firstOrNull { entry ->
            matchesContentRules(entry.feature.contentRules, texts)
        }?.feature
    }

    /** 由已匹配的 feature 构造 FUNC targetId。 */
    fun targetIdOf(packageName: String, feature: ParsedSubscription.ParsedFeature): String =
        TargetId.func(packageName, feature.featureId)

    private fun matchesContentRules(
        rules: ParsedSubscription.ContentRules?,
        texts: List<String>,
    ): Boolean {
        if (rules == null) return false
        if (rules.titleKeywords.isNotEmpty() && rules.titleKeywords.any { kw -> texts.any { it.contains(kw, ignoreCase = true) } }) {
            return true
        }
        if (rules.tabKeywords.isNotEmpty()) {
            val hitCount = rules.tabKeywords.count { kw -> texts.any { it.contains(kw, ignoreCase = true) } }
            if (hitCount >= rules.requiredTabs) return true
        }
        if (rules.extraKeywords.isNotEmpty() && rules.extraKeywords.any { kw -> texts.any { it.contains(kw, ignoreCase = true) } }) {
            return true
        }
        return false
    }
}
