// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine.model

/**
 * 应用组：多个目标共享一份每日配额。
 * 组配额以 `Rule.targetId = "GROUP:<groupId>"` 承载（见 Rule）。
 */
data class TargetGroup(
    val id: String,
    val name: String,
    val createdAt: Long,
)
