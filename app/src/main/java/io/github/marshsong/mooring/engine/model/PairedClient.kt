// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine.model

/**
 * 已配对浏览器。按 UA 判定；不在白名单的客户端（含手机自带浏览器）只读。
 * 最多 3 个已配对浏览器。
 */
data class PairedClient(
    val id: String,
    val userAgent: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
)
