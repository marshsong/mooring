// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.web

/**
 * 运行时状态（服务与 API 共享）。
 * Moored=服务正常 / Adrift=监测掉线；focusActive 供 M4 专注模式使用。
 */
object RuntimeStatus {
    @Volatile var moored: Boolean = false
    @Volatile var accessibilityEnabled: Boolean = false
    @Volatile var focusActive: Boolean = false
    @Volatile var focusRemainingSeconds: Long = 0
}
