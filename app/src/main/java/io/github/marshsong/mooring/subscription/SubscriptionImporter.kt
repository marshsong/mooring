// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.subscription

import io.github.marshsong.mooring.data.MooringRepository
import io.github.marshsong.mooring.engine.TargetId
import io.github.marshsong.mooring.engine.model.Rule
import io.github.marshsong.mooring.engine.model.RuleType
import io.github.marshsong.mooring.engine.model.Subscription
import io.github.marshsong.mooring.engine.model.Target
import io.github.marshsong.mooring.engine.model.TargetKind
import io.github.marshsong.mooring.engine.model.TargetSource
import io.github.marshsong.mooring.engine.subscription.ParsedSubscription
import io.github.marshsong.mooring.engine.subscription.SubscriptionParser

/**
 * 订阅导入：校验通过后持久化订阅，并为其 feature 创建 FUNC 目标；
 * `alwaysBlock: true` 的 feature 自动附加 ALWAYS_BLOCK 规则（PRD F9）。
 */
class SubscriptionImporter(private val repository: MooringRepository) {

    /** 返回解析后的订阅；校验失败抛 SubscriptionValidationException。 */
    suspend fun import(text: String): ParsedSubscription {
        val parsed = SubscriptionParser.parse(text)
        val now = System.currentTimeMillis()
        val subId = "sub-$now"

        repository.upsertSubscription(
            Subscription(
                id = subId,
                name = parsed.name,
                configJson = text,
                version = parsed.subscriptionVersion,
                enabled = true,
                importedAt = now,
                updatedAt = now,
            )
        )

        val existingTargets = repository.allTargets()
        val existingRules = repository.allRules()
        val newTargets = mutableListOf<Target>()
        val newRules = mutableListOf<Rule>()

        parsed.apps.forEach { app ->
            app.features.forEach { feature ->
                val targetId = TargetId.func(app.packageName, feature.featureId)
                if (existingTargets.none { it.targetId == targetId }) {
                    newTargets += Target(
                        targetId = targetId,
                        label = feature.label,
                        kind = TargetKind.FUNC,
                        packageName = app.packageName,
                        source = TargetSource.SUBSCRIPTION,
                        enabled = true,
                        createdAt = now,
                    )
                }
                if (feature.alwaysBlock &&
                    existingRules.none { it.targetId == targetId && it.type == RuleType.ALWAYS_BLOCK } &&
                    newRules.none { it.targetId == targetId && it.type == RuleType.ALWAYS_BLOCK }
                ) {
                    newRules += Rule(
                        id = "sub-abs-$targetId",
                        targetId = targetId,
                        type = RuleType.ALWAYS_BLOCK,
                        enabled = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
            }
        }

        if (newTargets.isNotEmpty()) repository.upsertTargets(newTargets)
        if (newRules.isNotEmpty()) repository.upsertRules(newRules)
        return parsed
    }
}
