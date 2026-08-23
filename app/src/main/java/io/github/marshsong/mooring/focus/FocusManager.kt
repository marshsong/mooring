// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.focus

import io.github.marshsong.mooring.data.MooringRepository
import io.github.marshsong.mooring.engine.model.Rule
import io.github.marshsong.mooring.engine.model.RuleType
import io.github.marshsong.mooring.settings.SettingsStore
import io.github.marshsong.mooring.web.RuntimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 专注模式：对全部已启用目标执行临时 ALWAYS_BLOCK（收紧，立即生效）；
 * 期间一切放宽请求返回 403 FOCUS_LOCKED；到期自动还原。
 */
class FocusManager(
    private val repository: MooringRepository,
    private val settings: SettingsStore,
    private val onChanged: suspend () -> Unit,
) {
    private var scope: CoroutineScope? = null
    private val focusRulePrefix = "focus-"

    val isActive: Boolean get() = RuntimeStatus.focusActive

    suspend fun start(minutes: Int) {
        if (RuntimeStatus.focusActive) return
        val clamped = minutes.coerceIn(1, 120)
        val now = System.currentTimeMillis()
        val targets = repository.allTargets().filter { it.enabled }
        if (targets.isEmpty()) return
        repository.upsertRules(
            targets.map { t ->
                Rule(
                    id = "$focusRulePrefix${t.targetId}",
                    targetId = t.targetId,
                    type = RuleType.ALWAYS_BLOCK,
                    enabled = true,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        )
        RuntimeStatus.focusActive = true
        RuntimeStatus.focusRemainingSeconds = clamped * 60L
        onChanged()

        val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = jobScope
        jobScope.launch {
            var remaining = clamped * 60L
            while (isActive && remaining > 0) {
                delay(1000)
                remaining--
                RuntimeStatus.focusRemainingSeconds = remaining
            }
            if (RuntimeStatus.focusActive) end()
        }
    }

    suspend fun end() {
        scope?.cancel()
        scope = null
        repository.allRules().filter { it.id.startsWith(focusRulePrefix) }.forEach { r ->
            repository.deleteRule(r.id)
        }
        RuntimeStatus.focusActive = false
        RuntimeStatus.focusRemainingSeconds = 0
        onChanged()
    }

    suspend fun remainingSeconds(): Long = RuntimeStatus.focusRemainingSeconds
}
