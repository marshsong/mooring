// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已导入的 T2 订阅。configJson 为原始订阅 JSON 文本（用户自备/社区，仓库不入库真实特征）。
 * 导入后每个 feature 成为独立的 FUNC 目标。
 */
@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey val id: String,
    val name: String,
    val configJson: String,
    val version: Int,
    val enabled: Boolean,
    val importedAt: Long,
    val updatedAt: Long,
)
