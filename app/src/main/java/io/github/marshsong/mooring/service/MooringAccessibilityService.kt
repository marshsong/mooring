// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import io.github.marshsong.mooring.detect.UsageTracker
import io.github.marshsong.mooring.engine.DetectorConfig
import io.github.marshsong.mooring.engine.ForegroundMatcher
import io.github.marshsong.mooring.engine.TargetId
import io.github.marshsong.mooring.engine.model.Target
import io.github.marshsong.mooring.engine.model.TargetKind
import io.github.marshsong.mooring.engine.model.TargetSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

/**
 * 无障碍监测服务。
 *
 * M0 只做 T1 应用级检测与用量统计（写日志，不拦截）。
 * 启用目标来源：开发期由 gitignore 的 assets/dev_enabled_targets.json 注入
 * （仓库不提交）；正式数据源（Room/控制台）在 M1/M3 接入。
 *
 * // DECISION: 前台目标只取首个命中（同包名只应存在一个启用目标）。
 */
class MooringAccessibilityService : AccessibilityService() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val tickRunnable: Runnable = Runnable { tick() }

    private var config: DetectorConfig? = null
    private var enabledTargets: List<Target> = emptyList()
    private var tracker: UsageTracker? = null
    private val sessionUsage = LinkedHashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        val cfg = loadConfig()
        config = cfg
        enabledTargets = loadDevSeedTargets() + cfg.catalogTargets().filter { it.enabled }
        tracker = UsageTracker(
            clock = { System.currentTimeMillis() },
            nowLocal = { LocalDateTime.now() },
            onAccumulate = { targetId, seconds, dateStr ->
                sessionUsage[targetId] = (sessionUsage[targetId] ?: 0L) + seconds
                Log.i(TAG, "USAGE date=$dateStr target=$targetId seconds=$seconds total=${sessionUsage[targetId]}")
            },
        )
        Log.i(
            TAG,
            "SERVICE_STATUS Moored configVersion=${cfg.configVersion} " +
                "excluded=${cfg.excludedPackages.size} enabledTargets=${enabledTargets.size} " +
                "debounceMs=${cfg.detectionDebounceMs}",
        )
        handler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
        val cfg = config ?: return
        val matched = ForegroundMatcher.match(pkg, packageName, cfg, enabledTargets)
        val targetId = matched.firstOrNull()?.targetId
        Log.i(
            TAG,
            "T1_FOREGROUND pkg=$pkg hit=${targetId ?: "none"} " +
                "excluded=${pkg != null && (pkg == packageName || cfg.excludedPackages.any { it == pkg })}",
        )
        tracker?.onForeground(targetId)
    }

    override fun onInterrupt() {
        Log.w(TAG, "SERVICE_STATUS Adrift service interrupted")
    }

    override fun onDestroy() {
        tracker?.flush()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun loadConfig(): DetectorConfig {
        val text = assets.open(CONFIG_ASSET).bufferedReader().use { it.readText() }
        val cfg = DetectorConfig.fromJson(text)
        val launcher = launcherPackage()
        val withLauncher = if (launcher != null) {
            DetectorConfig.withLauncherExcluded(cfg, launcher)
        } else {
            cfg
        }
        Log.i(TAG, "CONFIG loaded version=${cfg.configVersion} catalog=${cfg.appCatalog.size}")
        return withLauncher
    }

    private fun launcherPackage(): String? = runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
        packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
    }.getOrNull()

    /** 开发期种子目标（仅本机，不入库）。 */
    private fun loadDevSeedTargets(): List<Target> {
        val jsonText = try {
            assets.open(DEV_SEED_ASSET).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return emptyList()
        }
        val seed = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(DevSeed.serializer(), jsonText) }
            .getOrElse { e ->
                Log.w(TAG, "dev seed parse failed: ${e.message}")
                return emptyList()
            }
        Log.i(TAG, "DEV_SEED loaded targets=${seed.targets.size}")
        return seed.targets.map { dev ->
            Target(
                targetId = TargetId.app(dev.packageName),
                label = dev.label,
                kind = TargetKind.APP,
                packageName = dev.packageName,
                source = TargetSource.CUSTOM,
                enabled = true,
                createdAt = System.currentTimeMillis(),
            )
        }
    }

    private fun tick() {
        tracker?.flush()
        handler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
    }

    @Serializable
    private data class DevSeed(
        val targets: List<DevTarget> = emptyList(),
    ) {
        @Serializable
        data class DevTarget(
            @SerialName("package") val packageName: String,
            val label: String,
        )
    }

    companion object {
        private const val TAG = "Mooring"
        private const val CONFIG_ASSET = "detector_config.json"
        private const val DEV_SEED_ASSET = "dev_enabled_targets.json"
        private const val TICK_INTERVAL_MS = 10_000L

        /** 无障碍服务是否已开启。 */
        fun isAccessibilityEnabled(context: Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { it.contains(context.packageName, ignoreCase = true) }
        }
    }
}
