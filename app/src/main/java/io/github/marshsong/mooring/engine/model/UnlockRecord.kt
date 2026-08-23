// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine.model

import androidx.room.Entity
import kotlinx.serialization.Serializable

/**
 * 今日临时解锁记录：为某目标当日配额增加 bonusSeconds。
 * 主键 (dateStr, targetId)；次日 dateStr 变化即自然过期，无需清理任务。
 */
@Entity(tableName = "unlock_records", primaryKeys = ["dateStr", "targetId"])
@Serializable
data class UnlockRecord(
    val dateStr: String,
    val targetId: String,
    val bonusSeconds: Long,
    val grantedAt: Long,
)
