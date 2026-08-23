// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine.model

/** 事件类型（存储与 WebSocket 共用语义；事件只记目标与规则，不记页面内容）。 */
enum class EventType {
    SERVICE_STATUS,
    T1_FOREGROUND,
    USAGE,
    BLOCKED,
    COOLDOWN_UPDATE,
    RULES_CHANGED,
    TARGETS_CHANGED,
    FOCUS_UPDATE,
}

/** 事件日志。保留 90 天，启动时清理。 */
data class EventLog(
    val id: Long,
    val ts: Long,
    val type: EventType,
    val targetId: String? = null,
    val ruleId: String? = null,
    val detailJson: String? = null,
)
