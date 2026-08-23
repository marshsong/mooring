// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.data

import io.github.marshsong.mooring.engine.model.EventLog
import io.github.marshsong.mooring.engine.model.Rule
import io.github.marshsong.mooring.engine.model.Target
import io.github.marshsong.mooring.engine.model.TargetGroup

/**
 * 数据访问门面：规则配置（目标/组/规则）与运行数据（用量/事件）。
 * 服务与后续控制台共用；实现替换不影响上层。
 */
interface MooringRepository {

    suspend fun enabledTargets(): List<Target>
    suspend fun allTargets(): List<Target>
    suspend fun allGroups(): List<TargetGroup>
    suspend fun allRules(): List<Rule>

    /** 全量替换配置（启用目标、组、规则），用于种子初始化与控制台同步。 */
    suspend fun replaceConfig(targets: List<Target>, groups: List<TargetGroup>, rules: List<Rule>)

    suspend fun usedSeconds(targetId: String, dateStr: String): Long

    suspend fun addUsage(targetId: String, dateStr: String, seconds: Long)

    /** 当日各目标用量（dateStr = yyyy-MM-dd）。 */
    suspend fun usageMap(dateStr: String): Map<String, Long>

    suspend fun insertEvent(event: EventLog)

    /** 删除早于 beforeTs 的事件（90 天清理）。 */
    suspend fun cleanupOldEvents(beforeTs: Long)
}
