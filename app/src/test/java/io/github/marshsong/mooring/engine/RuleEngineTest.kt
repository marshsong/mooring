// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine

import io.github.marshsong.mooring.engine.model.BlockAction
import io.github.marshsong.mooring.engine.model.Rule
import io.github.marshsong.mooring.engine.model.RuleType
import io.github.marshsong.mooring.engine.model.Target
import io.github.marshsong.mooring.engine.model.TargetKind
import io.github.marshsong.mooring.engine.model.TargetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RuleEngineTest {

    private val engine = RuleEngine()

    private fun appTarget(
        id: String,
        pkg: String,
        enabled: Boolean = true,
        groupId: String? = null,
    ) = Target(
        targetId = TargetId.app(pkg),
        label = id,
        kind = TargetKind.APP,
        packageName = pkg,
        groupId = groupId,
        source = TargetSource.CATALOG,
        enabled = enabled,
        createdAt = 0L,
    )

    private fun rule(
        id: String,
        targetId: String,
        type: RuleType,
        quotaMinutes: Int? = null,
        startHHmm: Int? = null,
        endHHmm: Int? = null,
        enabled: Boolean = true,
    ) = Rule(
        id = id,
        targetId = targetId,
        type = type,
        quotaMinutes = quotaMinutes,
        startHHmm = startHHmm,
        endHHmm = endHHmm,
        action = BlockAction.OVERLAY_AND_BACK,
        enabled = enabled,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun at(hour: Int, minute: Int): LocalDateTime =
        LocalDateTime.of(2026, 8, 23, hour, minute)

    private fun eval(
        target: Target,
        allTargets: List<Target>,
        rules: List<Rule>,
        now: LocalDateTime,
        usage: Map<String, Long>,
    ) = engine.evaluate(
        RuleEngine.Input(
            target = target,
            allTargets = allTargets,
            rules = rules,
            now = now,
            usageOfToday = { usage[it] ?: 0L },
        )
    )

    // --- DAILY_QUOTA ---

    @Test
    fun `daily quota not hit when usage below limit`() {
        val target = appTarget("a", "com.example.a")
        val rules = listOf(rule("r1", target.targetId, RuleType.DAILY_QUOTA, quotaMinutes = 10))
        val result = eval(target, listOf(target), rules, at(10, 0), usage = mapOf(target.targetId to 5 * 60L))
        assertFalse(result.blocked)
    }

    @Test
    fun `daily quota hit exactly at limit`() {
        val target = appTarget("a", "com.example.a")
        val rules = listOf(rule("r1", target.targetId, RuleType.DAILY_QUOTA, quotaMinutes = 10))
        val result = eval(target, listOf(target), rules, at(10, 0), usage = mapOf(target.targetId to 10 * 60L))
        assertTrue(result.blocked)
        assertEquals(RuleEngine.BlockReason.QUOTA_EXHAUSTED, result.reason)
        assertEquals(RuleEngine.BlockSource.SELF, result.source)
        assertEquals("r1", result.ruleId)
    }

    // --- SCHEDULE_BLOCK ---

    @Test
    fun `schedule block inside window`() {
        val target = appTarget("a", "com.example.a")
        val rules = listOf(rule("r2", target.targetId, RuleType.SCHEDULE_BLOCK, startHHmm = 900, endHHmm = 2200))
        assertTrue(eval(target, listOf(target), rules, at(9, 0), emptyMap()).blocked)
        assertTrue(eval(target, listOf(target), rules, at(12, 30), emptyMap()).blocked)
        assertTrue(eval(target, listOf(target), rules, at(21, 59), emptyMap()).blocked)
    }

    @Test
    fun `schedule block outside window`() {
        val target = appTarget("a", "com.example.a")
        val rules = listOf(rule("r2", target.targetId, RuleType.SCHEDULE_BLOCK, startHHmm = 900, endHHmm = 2200))
        assertFalse(eval(target, listOf(target), rules, at(8, 59), emptyMap()).blocked)
        // end is exclusive
        assertFalse(eval(target, listOf(target), rules, at(22, 0), emptyMap()).blocked)
    }

    @Test
    fun `schedule block crosses midnight`() {
        val target = appTarget("a", "com.example.a")
        val rules = listOf(rule("r3", target.targetId, RuleType.SCHEDULE_BLOCK, startHHmm = 2200, endHHmm = 800))
        assertTrue(eval(target, listOf(target), rules, at(23, 0), emptyMap()).blocked)
        assertTrue(eval(target, listOf(target), rules, at(7, 59), emptyMap()).blocked)
        assertFalse(eval(target, listOf(target), rules, at(9, 0), emptyMap()).blocked)
    }

    @Test
    fun `schedule block equal start end blocks all day`() {
        val target = appTarget("a", "com.example.a")
        val rules = listOf(rule("r4", target.targetId, RuleType.SCHEDULE_BLOCK, startHHmm = 0, endHHmm = 0))
        assertTrue(eval(target, listOf(target), rules, at(3, 0), emptyMap()).blocked)
        assertTrue(eval(target, listOf(target), rules, at(18, 0), emptyMap()).blocked)
    }

    // --- ALWAYS_BLOCK ---

    @Test
    fun `always block hits regardless of time and usage`() {
        val target = appTarget("a", "com.example.a")
        val rules = listOf(rule("r5", target.targetId, RuleType.ALWAYS_BLOCK))
        val result = eval(target, listOf(target), rules, at(0, 1), emptyMap())
        assertTrue(result.blocked)
        assertEquals(RuleEngine.BlockReason.ALWAYS_BLOCK, result.reason)
    }

    // --- Disabled target ---

    @Test
    fun `disabled target never blocks`() {
        val target = appTarget("a", "com.example.a", enabled = false)
        val rules = listOf(rule("r5", target.targetId, RuleType.ALWAYS_BLOCK))
        assertFalse(eval(target, listOf(target), rules, at(10, 0), emptyMap()).blocked)
    }

    // --- Group quota ---

    @Test
    fun `group quota sums member usage and blocks shared budget`() {
        val a = appTarget("a", "com.example.a", groupId = "g1")
        val b = appTarget("b", "com.example.b", groupId = "g1")
        val groupRule = rule("rg", TargetId.group("g1"), RuleType.DAILY_QUOTA, quotaMinutes = 45)

        // A used 30, B used 14 => sum 44 < 45 => B not blocked yet
        val before = eval(b, listOf(a, b), listOf(groupRule), at(18, 0), usage = mapOf(a.targetId to 30 * 60L, b.targetId to 14 * 60L))
        assertFalse(before.blocked)

        // B uses to 15 => sum 45 => blocked
        val atLimit = eval(b, listOf(a, b), listOf(groupRule), at(18, 0), usage = mapOf(a.targetId to 30 * 60L, b.targetId to 15 * 60L))
        assertTrue(atLimit.blocked)
        assertEquals(RuleEngine.BlockSource.GROUP, atLimit.source)
    }

    @Test
    fun `self quota stricter than group blocks self first`() {
        val a = appTarget("a", "com.example.a", groupId = "g1")
        val b = appTarget("b", "com.example.b", groupId = "g1")
        val selfRule = rule("ra", a.targetId, RuleType.DAILY_QUOTA, quotaMinutes = 10)
        val groupRule = rule("rg", TargetId.group("g1"), RuleType.DAILY_QUOTA, quotaMinutes = 45)

        // A alone at 10min: self quota (10) stricter than group (45), blocks via SELF
        val result = eval(a, listOf(a, b), listOf(selfRule, groupRule), at(18, 0), usage = mapOf(a.targetId to 10 * 60L))
        assertTrue(result.blocked)
        assertEquals(RuleEngine.BlockSource.SELF, result.source)
        assertEquals("ra", result.ruleId)
    }

    @Test
    fun `disabled group member does not consume group budget`() {
        val a = appTarget("a", "com.example.a", groupId = "g1")
        val b = appTarget("b", "com.example.b", groupId = "g1", enabled = false)
        val groupRule = rule("rg", TargetId.group("g1"), RuleType.DAILY_QUOTA, quotaMinutes = 45)
        // A at 44min, disabled B at 60min should not count => not blocked
        val result = eval(a, listOf(a, b), listOf(groupRule), at(18, 0), usage = mapOf(a.targetId to 44 * 60L, b.targetId to 60 * 60L))
        assertFalse(result.blocked)
    }

    // --- Rule composition: any rule hits blocks ---

    @Test
    fun `multiple rules any hit blocks`() {
        val target = appTarget("a", "com.example.a")
        val rules = listOf(
            rule("r-quota", target.targetId, RuleType.DAILY_QUOTA, quotaMinutes = 60),
            rule("r-sched", target.targetId, RuleType.SCHEDULE_BLOCK, startHHmm = 900, endHHmm = 2200),
        )
        // Usage zero, but inside schedule window => blocked by schedule
        val result = eval(target, listOf(target), rules, at(12, 0), emptyMap())
        assertTrue(result.blocked)
        assertEquals("r-sched", result.ruleId)
    }
}
