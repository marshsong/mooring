// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine.subscription

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 订阅校验失败异常；message 用于向控制台返回 400 SUBSCRIPTION_INVALID。 */
class SubscriptionValidationException(message: String) : IllegalArgumentException(message)

/**
 * 订阅 JSON 解析与校验（纯逻辑）。
 *
 * 校验规则：JSON 可解析、包名格式合法、activityPatterns 可编译为正则、
 * 至少一个 feature。任一失败整体拒绝。
 */
object SubscriptionParser {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val PACKAGE_REGEX = Regex("^[A-Za-z0-9_.]+$")

    fun parse(text: String): ParsedSubscription {
        val raw = try {
            json.decodeFromString(SubscriptionJson.serializer(), text)
        } catch (e: Exception) {
            throw SubscriptionValidationException("订阅 JSON 解析失败：${e.message}")
        }

        if (raw.apps.isEmpty() || raw.apps.sumOf { it.features.size } == 0) {
            throw SubscriptionValidationException("订阅至少需要一个 feature")
        }

        val apps = raw.apps.map { app ->
            if (!app.packageName.contains('.') || !PACKAGE_REGEX.matches(app.packageName)) {
                throw SubscriptionValidationException("包名格式非法：${app.packageName}")
            }
            val features = app.features.map { feature ->
                if (feature.featureId.isBlank()) {
                    throw SubscriptionValidationException("featureId 不能为空")
                }
                feature.activityPatterns.forEach { pattern ->
                    try {
                        Regex(pattern)
                    } catch (e: Exception) {
                        throw SubscriptionValidationException("activityPatterns 无法编译为正则：$pattern")
                    }
                }
                ParsedSubscription.ParsedFeature(
                    featureId = feature.featureId,
                    label = feature.label.ifBlank { feature.featureId },
                    alwaysBlock = feature.alwaysBlock,
                    activityPatterns = feature.activityPatterns,
                    contentRules = feature.contentRules?.let { cr ->
                        ParsedSubscription.ContentRules(
                            titleKeywords = cr.titleKeywords,
                            tabKeywords = cr.tabKeywords,
                            requiredTabs = cr.requiredTabs,
                            extraKeywords = cr.extraKeywords,
                        )
                    },
                )
            }
            ParsedSubscription.ParsedApp(app.packageName, features)
        }

        return ParsedSubscription(
            subscriptionVersion = raw.subscriptionVersion,
            name = raw.name.ifBlank { "未命名订阅" },
            apps = apps,
        )
    }

    @Serializable
    private data class SubscriptionJson(
        @SerialName("subscriptionVersion") val subscriptionVersion: Int = 1,
        val name: String = "",
        val apps: List<AppJson> = emptyList(),
    ) {
        @Serializable
        data class AppJson(
            @SerialName("package") val packageName: String,
            val features: List<FeatureJson> = emptyList(),
        )

        @Serializable
        data class FeatureJson(
            @SerialName("featureId") val featureId: String,
            val label: String = "",
            @SerialName("alwaysBlock") val alwaysBlock: Boolean = false,
            @SerialName("activityPatterns") val activityPatterns: List<String> = emptyList(),
            @SerialName("contentRules") val contentRules: ContentRulesJson? = null,
        )

        @Serializable
        data class ContentRulesJson(
            @SerialName("titleKeywords") val titleKeywords: List<String> = emptyList(),
            @SerialName("tabKeywords") val tabKeywords: List<String> = emptyList(),
            @SerialName("requiredTabs") val requiredTabs: Int = 1,
            @SerialName("extraKeywords") val extraKeywords: List<String> = emptyList(),
        )
    }
}
