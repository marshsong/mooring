// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine

import io.github.marshsong.mooring.engine.model.Target
import io.github.marshsong.mooring.engine.model.TargetKind
import io.github.marshsong.mooring.engine.model.TargetSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 通用检测配置（assets 内置，可经控制台热更新）。
 * 只含通用参数与 T1 内置目录，不含任何特定应用的红线特征。
 */
@Serializable
data class DetectorConfig(
    @SerialName("configVersion") val configVersion: Int = 2,
    @SerialName("detectionDebounceMs") val detectionDebounceMs: Long = 300,
    @SerialName("excludedPackages") val excludedPackages: List<String> = emptyList(),
    @SerialName("appCatalog") val appCatalog: List<CatalogApp> = emptyList(),
) {

    @Serializable
    data class CatalogApp(
        @SerialName("package") val packageName: String,
        val label: String,
        val category: String,
    )

    /** 把内置目录转成默认停用的 CATALOG 目标。 */
    fun catalogTargets(): List<Target> = appCatalog.map { app ->
        Target(
            targetId = TargetId.app(app.packageName),
            label = app.label,
            kind = TargetKind.APP,
            packageName = app.packageName,
            source = TargetSource.CATALOG,
            enabled = false,
            createdAt = System.currentTimeMillis(),
        )
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
        }

        fun fromJson(text: String): DetectorConfig = json.decodeFromString(serializer(), text)

        /** 以系统默认启动器包名补齐排除清单（运行时补充，不入配置）。 */
        fun withLauncherExcluded(config: DetectorConfig, launcherPackage: String): DetectorConfig {
            if (launcherPackage.isBlank()) return config
            if (config.excludedPackages.any { it == launcherPackage }) return config
            return config.copy(excludedPackages = config.excludedPackages + launcherPackage)
        }
    }
}
