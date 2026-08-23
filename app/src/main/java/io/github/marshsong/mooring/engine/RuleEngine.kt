// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine

import io.github.marshsong.mooring.engine.model.BlockAction
import io.github.marshsong.mooring.engine.model.Rule
import io.github.marshsong.mooring.engine.model.RuleType
import io.github.marshsong.mooring.engine.model.Target
import java.time.LocalDateTime

/**
 * 规则引擎（纯逻辑，无 Android 依赖）：判定"某目标此刻是否应被拦截"。
 *
 * 合成规则：
 *  1. 同一目标多条规则，任一触发即拦截。
 *  2. 目标自身配额与其所属组配额同时生效，任一耗尽即拦截（取更严）。
 *  3. 组配额消耗 = 组内所有启用成员当日用量之和。
 */
class RuleEngine {

    enum class BlockReason { QUOTA_EXHAUSTED, SCHEDULE_BLOCK, ALWAYS_BLOCK }

    enum class BlockSource { SELF, GROUP }

    data class EvaluationResult(
        val blocked: Boolean,
        val reason: BlockReason? = null,
        val ruleId: String? = null,
        val source: BlockSource? = null,
        val action: BlockAction = BlockAction.OVERLAY_AND_BACK,
    )

    data class Input(
        val target: Target,
        val allTargets: List<Target>,
        val rules: List<Rule>,
        val now: LocalDateTime,
        /** 当日某目标已用秒数。 */
        val usageOfToday: (targetId: String) -> Long,
    )

    fun evaluate(input: Input): EvaluationResult {
        val target = input.target
        if (!target.enabled) return EvaluationResult(blocked = false)

        val enabledRules = input.rules.filter { it.enabled }
        val now = input.now

        // 1. 目标自身规则
        for (rule in enabledRules.filter { it.targetId == target.targetId }) {
            if (hits(rule, input.usageOfToday(target.targetId), now)) {
                return EvaluationResult(
                    blocked = true,
                    reason = reasonOf(rule),
                    ruleId = rule.id,
                    source = BlockSource.SELF,
                    action = rule.action,
                )
            }
        }

        // 2. 组规则（组配额 = 组内启用成员用量之和）
        val groupId = target.groupId ?: return EvaluationResult(blocked = false)
        val groupUsage = input.allTargets
            .filter { it.enabled && it.groupId == groupId }
            .sumOf { input.usageOfToday(it.targetId) }
        for (rule in enabledRules.filter { it.targetId == TargetId.group(groupId) }) {
            if (hits(rule, groupUsage, now)) {
                return EvaluationResult(
                    blocked = true,
                    reason = reasonOf(rule),
                    ruleId = rule.id,
                    source = BlockSource.GROUP,
                    action = rule.action,
                )
            }
        }

        return EvaluationResult(blocked = false)
    }

    private fun hits(rule: Rule, usedSeconds: Long, now: LocalDateTime): Boolean = when (rule.type) {
        RuleType.ALWAYS_BLOCK -> true
        RuleType.DAILY_QUOTA -> {
            val quotaSeconds = (rule.quotaMinutes ?: 0) * 60L
            usedSeconds >= quotaSeconds
        }
        RuleType.SCHEDULE_BLOCK -> isWithinSchedule(rule, now)
    }

    private fun reasonOf(rule: Rule): BlockReason = when (rule.type) {
        RuleType.ALWAYS_BLOCK -> BlockReason.ALWAYS_BLOCK
        RuleType.DAILY_QUOTA -> BlockReason.QUOTA_EXHAUSTED
        RuleType.SCHEDULE_BLOCK -> BlockReason.SCHEDULE_BLOCK
    }

    /** start 含、end 不含；支持跨零点；start == end 视为全天。 */
    private fun isWithinSchedule(rule: Rule, now: LocalDateTime): Boolean {
        val start = rule.startHHmm ?: return false
        val end = rule.endHHmm ?: return false
        val startMinute = (start / 100) * 60 + (start % 100)
        val endMinute = (end / 100) * 60 + (end % 100)
        val nowMinute = now.hour * 60 + now.minute
        if (startMinute == endMinute) return true
        return if (startMinute < endMinute) {
            nowMinute in startMinute until endMinute
        } else {
            nowMinute >= startMinute || nowMinute < endMinute
        }
    }
}
