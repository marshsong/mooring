// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 目标形态：APP 整应用（T1）/ FUNC 应用内功能（T2）。 */
enum class TargetKind { APP, FUNC }

/** 目标来源：内置目录 / 用户自定义 / 用户导入订阅。 */
enum class TargetSource { CATALOG, CUSTOM, SUBSCRIPTION }

/**
 * 被管控的实体。
 *
 * targetId 遵循术语表形态：`APP:<package>`、`FUNC:<package>:<featureId>`。
 * 组规则以 `GROUP:<groupId>` 作为规则作用对象（见 Rule）。
 */
@Entity(tableName = "targets")
@Serializable
data class Target(
    @PrimaryKey val targetId: String,
    val label: String,
    val kind: TargetKind,
    val packageName: String,
    val groupId: String? = null,
    val source: TargetSource,
    val enabled: Boolean,
    val createdAt: Long,
)
