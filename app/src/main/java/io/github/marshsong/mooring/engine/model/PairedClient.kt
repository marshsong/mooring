// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 已配对浏览器。按 UA 判定；不在白名单的客户端（含手机自带浏览器）只读。
 * 最多 3 个已配对浏览器。
 */
@Entity(tableName = "paired_clients")
@Serializable
data class PairedClient(
    @PrimaryKey val id: String,
    val userAgent: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
)
