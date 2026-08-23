// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine.model

/**
 * 单目标每日用量。主键 (dateStr, targetId)。
 * dateStr = yyyy-MM-dd（本地时区）；组用量 = 成员当日用量求和。
 */
data class UsageDaily(
    val dateStr: String,
    val targetId: String,
    val usedSeconds: Long,
)
