// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine.model

/** 冷静期状态。 */
enum class CooldownStatus { PENDING, CONFIRMED, CANCELLED, EXPIRED }

/**
 * 放宽操作需经冷静期：发起后等到期，在确认窗口内确认才生效。
 * payloadJson 记录发起时的变更描述（如规则 ID 与新旧值）。
 */
data class CooldownRecord(
    val id: String,
    val payloadJson: String,
    val status: CooldownStatus,
    val requestedAt: Long,
    val expiresAt: Long,
)
