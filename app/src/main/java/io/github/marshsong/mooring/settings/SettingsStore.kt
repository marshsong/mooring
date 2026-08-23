// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * 运行设置（冷静期时长 / 每日解锁上限 / 专注默认时长）。
 *
 * // DECISION: MVP 用 SharedPreferences 承载这三个低频设置；数据量极小且非关键路径，
 * 与 PRD 的 DataStore 意图一致（可配置、可持久化）。后续如需要可平滑迁移。
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mooring_settings", Context.MODE_PRIVATE)

    var cooldownMinutes: Int
        get() = prefs.getInt(KEY_COOLDOWN, DEFAULT_COOLDOWN_MINUTES).coerceIn(5, 30)
        set(value) {
            prefs.edit().putInt(KEY_COOLDOWN, value.coerceIn(5, 30)).apply()
        }

    var extraUnlockMaxPerDay: Int
        get() = prefs.getInt(KEY_UNLOCK_MAX, DEFAULT_UNLOCK_MAX)
        set(value) {
            prefs.edit().putInt(KEY_UNLOCK_MAX, value).apply()
        }

    var focusDefaultMinutes: Int
        get() = prefs.getInt(KEY_FOCUS, DEFAULT_FOCUS_MINUTES)
        set(value) {
            prefs.edit().putInt(KEY_FOCUS, value.coerceIn(5, 120)).apply()
        }

    companion object {
        private const val KEY_COOLDOWN = "cooldown_minutes"
        private const val KEY_UNLOCK_MAX = "unlock_max_per_day"
        private const val KEY_FOCUS = "focus_default_minutes"
        const val DEFAULT_COOLDOWN_MINUTES = 10
        const val DEFAULT_UNLOCK_MAX = 2
        const val DEFAULT_FOCUS_MINUTES = 45
    }
}
