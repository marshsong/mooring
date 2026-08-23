// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 规则类型：每日限额 / 时段禁用 / 永久禁用。 */
enum class RuleType { DAILY_QUOTA, SCHEDULE_BLOCK, ALWAYS_BLOCK }

/** 拦截动作：仅覆盖 / 覆盖并自动返回。 */
enum class BlockAction { OVERLAY_ONLY, OVERLAY_AND_BACK }

/**
 * 作用于目标或组的限制策略。
 *
 * targetId 指向 `APP:` / `FUNC:` 目标，或 `GROUP:<groupId>`（组配额）。
 * startHHmm / endHHmm 为整型时分（如 900 表示 09:00），支持跨零点。
 */
@Entity(tableName = "rules")
@Serializable
data class Rule(
    @PrimaryKey val id: String,
    val targetId: String,
    val type: RuleType,
    val quotaMinutes: Int? = null,
    val startHHmm: Int? = null,
    val endHHmm: Int? = null,
    val action: BlockAction = BlockAction.OVERLAY_AND_BACK,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
