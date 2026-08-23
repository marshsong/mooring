// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.detect

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 前台用量计时器。
 *
 * 语义：命中目标即开始累计（精度 1 秒）；每 10 秒落盘一次 + 离开时落盘，
 * 避免进程被杀丢数据。clock 与 nowLocal 可注入以便测试。
 */
class UsageTracker(
    private val clock: () -> Long,
    private val nowLocal: () -> LocalDateTime,
    private val onAccumulate: (targetId: String, seconds: Long, dateStr: String) -> Unit,
) {

    private var currentTargetId: String? = null
    private var currentSinceMs: Long = 0L

    /** 前台变化时调用；targetId 为 null 表示离开已管控目标。 */
    fun onForeground(targetId: String?) {
        flush()
        currentTargetId = targetId
        currentSinceMs = clock()
    }

    /** 落盘当前累计并重置起点（不改变当前目标），供 10 秒心跳调用。 */
    fun flush() {
        val targetId = currentTargetId ?: return
        val elapsedSeconds = (clock() - currentSinceMs) / 1000
        if (elapsedSeconds <= 0) return
        onAccumulate(targetId, elapsedSeconds, nowLocal().format(DATE_FMT))
        currentSinceMs = clock()
    }

    val isTracking: Boolean get() = currentTargetId != null

    companion object {
        private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
