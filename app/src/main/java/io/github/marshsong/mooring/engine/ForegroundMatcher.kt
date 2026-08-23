// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine

import io.github.marshsong.mooring.engine.model.Target
import io.github.marshsong.mooring.engine.model.TargetKind

/**
 * T1 前台包名匹配（纯函数）。
 *
 * 排除：本应用自身、detector_config 排除清单（含运行时自动补充的桌面启动器）。
 * 只匹配已启用的 APP 目标；不读取任何屏幕内容，仅感知前台包名。
 */
object ForegroundMatcher {

    fun match(
        foregroundPackage: String?,
        selfPackage: String,
        config: DetectorConfig,
        targets: Collection<Target>,
    ): List<Target> {
        val pkg = foregroundPackage ?: return emptyList()
        if (pkg.isEmpty() || pkg == selfPackage) return emptyList()
        if (config.excludedPackages.any { it == pkg }) return emptyList()
        return targets.filter {
            it.enabled && it.kind == TargetKind.APP && it.packageName == pkg
        }
    }
}
