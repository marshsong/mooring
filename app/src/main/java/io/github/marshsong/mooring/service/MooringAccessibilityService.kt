// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import io.github.marshsong.mooring.AppRuntime
import io.github.marshsong.mooring.block.BlockManager
import io.github.marshsong.mooring.data.MooringRepository
import io.github.marshsong.mooring.detect.T2ContentScanner
import io.github.marshsong.mooring.detect.UsageTracker
import io.github.marshsong.mooring.engine.DetectorConfig
import io.github.marshsong.mooring.engine.ForegroundMatcher
import io.github.marshsong.mooring.engine.RuleEngine
import io.github.marshsong.mooring.engine.TargetId
import io.github.marshsong.mooring.engine.model.EventLog
import io.github.marshsong.mooring.engine.model.EventType
import io.github.marshsong.mooring.engine.model.Rule
import io.github.marshsong.mooring.engine.model.RuleType
import io.github.marshsong.mooring.engine.model.Target
import io.github.marshsong.mooring.engine.model.TargetGroup
import io.github.marshsong.mooring.engine.model.TargetKind
import io.github.marshsong.mooring.engine.model.TargetSource
import io.github.marshsong.mooring.engine.subscription.ParsedSubscription
import io.github.marshsong.mooring.engine.t2.T2Detector
import io.github.marshsong.mooring.subscription.SubscriptionImporter
import io.github.marshsong.mooring.ui.ReinActivity
import io.github.marshsong.mooring.web.RuntimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 无障碍监测服务。
 *
 * M1：T1 应用级检测 + 用量统计（Room 持久化）+ 拦截执行（勒马页）。
 * 事件路径只做内存操作（快照 + 用量缓存），DB 读写全部异步，满足单次事件 ≤50ms。
 */
class MooringAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val contentHandler = Handler(Looper.getMainLooper())
    private val contentScanExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tickRunnable: Runnable = Runnable { tick() }
    private val engine = RuleEngine()
    private val detector = T2Detector()
    private val pendingContentScans = HashSet<String>()

    private lateinit var repository: MooringRepository
    private lateinit var blockManager: BlockManager

    private var config: DetectorConfig? = null
    private var snapTargets: List<Target> = emptyList()
    private var snapRules: List<Rule> = emptyList()
    private var usageCache: MutableMap<String, Long> = mutableMapOf()
    private var bonusCache: MutableMap<String, Long> = mutableMapOf()
    private var currentForegroundTargetId: String? = null
    private var currentPackage: String? = null
    private var lastWindowClassName: String? = null
    private var lastPositiveSignalAt: Long = 0L
    private var tracker: UsageTracker? = null
    private var today: String = LocalDate.now().format(ISO_DATE)

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppRuntime.init(this)
        config = loadConfig()
        AppRuntime.detectorConfig = config
        repository = AppRuntime.repository
        AppRuntime.onConfigChanged = { refreshRuntimeConfig() }
        AppRuntime.ensureServerStarted(this)
        RuntimeStatus.moored = true
        RuntimeStatus.accessibilityEnabled = true
        blockManager = BlockManager(
            startRein = { target, reason -> startRein(target, reason) },
            performBack = { performGlobalAction(GLOBAL_ACTION_BACK) },
            isForegroundBlocked = { targetId ->
                targetId == currentForegroundTargetId &&
                    System.currentTimeMillis() - lastPositiveSignalAt < ACTIVE_WINDOW_MS
            },
        )
        tracker = UsageTracker(
            clock = { System.currentTimeMillis() },
            nowLocal = { LocalDateTime.now() },
            onAccumulate = { targetId, seconds, dateStr -> onUsageAccumulated(targetId, seconds, dateStr) },
        )

        scope.launch {
            bootstrapDevIfFirstRun()
            ensureDefaultRules()
            cleanupStaleFocusRules()
            snapTargets = repository.allTargets()
            snapRules = repository.allRules()
            reloadT2()
            usageCache = repository.usageMap(today).toMutableMap()
            bonusCache = repository.todayBonuses(today).toMutableMap()
            repository.cleanupOldEvents(System.currentTimeMillis() - RETENTION_MS)
            logEvent(EventType.SERVICE_STATUS, null, null, "Moored")
            Log.i(
                TAG,
                "SERVICE_STATUS Moored configVersion=${config?.configVersion} " +
                    "targets=${snapTargets.size} rules=${snapRules.size} t2Features=${detector.featureCount}",
            )
        }
        handler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowChanged(event)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> onContentChanged(event)
        }
    }

    private fun onWindowChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        val className = event.className?.toString()
        val cfg = config ?: return
        if (pkg == null) return

        lastWindowClassName = className
        val leftApp = pkg != currentPackage
        currentPackage = pkg

        // 一级：窗口类名匹配 T2 功能（最高优先级）
        val t2Id = resolveT2TargetId(pkg, className)
        if (t2Id != null) {
            lastPositiveSignalAt = System.currentTimeMillis()
            setCurrentTarget(t2Id)
        } else if (leftApp || !detector.hasActiveFeatures(pkg)) {
            // 换了应用，或无订阅：直接解析 T1（或清空）
            val t1Id = ForegroundMatcher.match(pkg, packageName, cfg, snapTargets).firstOrNull()?.targetId
            setCurrentTarget(t1Id)
        }
        // 同包内一级未命中：保留当前目标，交给内容扫描精化（避免微信切窗漏记）
        if (detector.hasActiveFeatures(pkg)) {
            scheduleContentScan(pkg)
        }
        Log.i(TAG, "T1_FOREGROUND pkg=$pkg className=$className hit=${currentForegroundTargetId ?: "none"}")
    }

    private fun resolveT2TargetId(pkg: String, className: String?): String? {
        if (!detector.hasActiveFeatures(pkg)) return null
        return detector.matchByClassName(pkg, className)?.let { feature ->
            val targetId = detector.targetIdOf(pkg, feature)
            if (snapTargets.any { it.targetId == targetId && it.enabled }) targetId else null
        }
    }

    /** 统一设置当前目标：更新状态、通知拦截器、切换计时器起点。 */
    private fun setCurrentTarget(targetId: String?) {
        if (currentForegroundTargetId == targetId) return
        currentForegroundTargetId = targetId
        blockManager.onForegroundChanged(targetId)
        tracker?.onForeground(targetId)
        if (targetId != null) {
            snapTargets.firstOrNull { it.targetId == targetId }?.let { evaluateBlock(it) }
        }
    }

    private fun onContentChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        if (pkg != null && detector.hasActiveFeatures(pkg)) {
            scheduleContentScan(pkg)
        }
    }

    /** 二级内容扫描（按检测配置去抖，每包同时仅一个挂起任务）。扫描在后台线程，结果回主线程应用。 */
    private fun scheduleContentScan(pkg: String) {
        if (!pendingContentScans.add(pkg)) return
        val debounce = config?.detectionDebounceMs ?: 300L
        contentHandler.postDelayed({
            pendingContentScans.remove(pkg)
            contentScanExecutor.execute {
                runCatching {
                    val root = rootInActiveWindow ?: return@runCatching
                    val texts = T2ContentScanner(root).collectTexts()
                    val feature = detector.matchByContent(pkg, texts)
                    if (detector.hasActiveFeatures(pkg)) {
                        Log.i(
                            TAG,
                            "T2_SCAN texts=${texts.size} first=${texts.take(5).joinToString("|") { it.take(12) }} matched=${feature != null}",
                        )
                    }
                    contentHandler.post { applyContentScanResult(pkg, feature) }
                }
            }
        }, debounce)
    }

    /** 在主线程应用内容扫描结果。内容仅用于"加锁"，永不清除（部分应用的可访问树为空）。 */
    private fun applyContentScanResult(pkg: String, feature: ParsedSubscription.ParsedFeature?) {
        if (feature != null) {
            lastPositiveSignalAt = System.currentTimeMillis()
            val targetId = detector.targetIdOf(pkg, feature)
            val target = snapTargets.firstOrNull { it.targetId == targetId && it.enabled } ?: return
            setCurrentTarget(targetId)
            Log.i(TAG, "T2_DETECTED target=$targetId level=CONTENT")
            evaluateBlock(target)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "SERVICE_STATUS Adrift service interrupted")
    }

    override fun onDestroy() {
        tracker?.flush()
        handler.removeCallbacksAndMessages(null)
        contentHandler.removeCallbacksAndMessages(null)
        contentScanExecutor.shutdownNow()
        // server owned by AppRuntime, kept alive by foreground service
        RuntimeStatus.moored = false
        scope.cancel()
        super.onDestroy()
    }

    /** BlockManager 回调：弹出勒马页。 */
    private fun startRein(target: Target, reason: RuleEngine.BlockReason) {
        val intent = Intent(this, ReinActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ReinActivity.EXTRA_REASON, reason.name)
            .putExtra(ReinActivity.EXTRA_TARGET_LABEL, target.label)
        startActivity(intent)
    }

    /** 对目标做拦截评估；命中则触发勒马页并记录事件。 */
    private fun evaluateBlock(target: Target): Boolean {
        // 活跃门控：FUNC 目标只有在最近确实被检测到（避免粘性锁在聊天中误拦）
        if (target.kind == TargetKind.FUNC &&
            System.currentTimeMillis() - lastPositiveSignalAt > ACTIVE_WINDOW_MS
        ) {
            return false
        }
        val now = LocalDateTime.now()
        val result = engine.evaluate(
            RuleEngine.Input(
                target = target,
                allTargets = snapTargets,
                rules = snapRules,
                now = now,
                usageOfToday = { id -> usageCache[id] ?: 0L },
                bonusSecondsOfToday = { id -> bonusCache[id] ?: 0L },
            )
        )
        if (result.blocked) {
            blockManager.trigger(target, result)
            logEvent(
                EventType.BLOCKED, target.targetId, result.ruleId,
                "reason=${result.reason} source=${result.source}",
            )
            Log.i(TAG, "BLOCKED target=${target.targetId} reason=${result.reason} rule=${result.ruleId}")
            AppRuntime.webServer?.hub?.let { hub ->
                scope.launch { hub.broadcast(buildWsEvent("BLOCKED")) }
            }
        }
        return result.blocked
    }

    /** 用量累计回调：更新缓存 + 异步落库 + 重新评估当前前台（可能跨过配额）。 */
    private fun onUsageAccumulated(targetId: String, seconds: Long, dateStr: String) {
        if (dateStr != today) {
            today = dateStr
            usageCache.clear()
        }
        usageCache[targetId] = (usageCache[targetId] ?: 0L) + seconds
        Log.i(TAG, "USAGE date=$dateStr target=$targetId seconds=$seconds total=${usageCache[targetId]}")

        scope.launch {
            repository.addUsage(targetId, dateStr, seconds)
            logEvent(EventType.USAGE, targetId, null, "seconds=$seconds")
        }

        currentForegroundTargetId?.let { id ->
            snapTargets.firstOrNull { it.targetId == id }?.let { evaluateBlock(it) }
        }
    }

    private fun tick() {
        tracker?.flush()
        checkStickyStale()
        handler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
    }

    /**
     * 粘性锁周期检查：FUNC 目标长时间无正向信号且当前窗口类名已不在功能页时清除。
     * 在功能页内滚动时 lastWindowClassName 保持功能类名，锁持续，计时连续。
     */
    private fun checkStickyStale() {
        val cur = currentForegroundTargetId ?: return
        if (!cur.startsWith("FUNC:")) return
        if (System.currentTimeMillis() - lastPositiveSignalAt <= STICKY_GRACE_MS) return
        val pkg = TargetId.parsePackage(cur) ?: return
        val stillOnFeature = resolveT2TargetId(pkg, lastWindowClassName) != null
        if (!stillOnFeature) {
            setCurrentTarget(null)
        }
    }

    private fun logEvent(type: EventType, targetId: String?, ruleId: String?, detail: String?) {
        val event = EventLog(
            ts = System.currentTimeMillis(),
            type = type,
            targetId = targetId,
            ruleId = ruleId,
            detailJson = detail,
        )
        scope.launch { repository.insertEvent(event) }
    }

    // --- 配置与种子 ---

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
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
    }.getOrNull()

    /** 首次运行把开发期种子（T1 + Mock 订阅）写入数据库；后续以库内配置为准。 */
    private suspend fun bootstrapDevIfFirstRun() {
        if (repository.allTargets().isNotEmpty()) return
        seedDevTargets()
        bootstrapMockSubscription()
    }

    private suspend fun seedDevTargets() {
        val seed = loadDevSeed() ?: return
        val now = System.currentTimeMillis()

        val targets = seed.targets.map { dt ->
            val group = seed.groups.firstOrNull { it.members.contains(dt.packageName) }
            Target(
                targetId = TargetId.app(dt.packageName),
                label = dt.label,
                kind = TargetKind.APP,
                packageName = dt.packageName,
                groupId = group?.id,
                source = TargetSource.CUSTOM,
                enabled = true,
                createdAt = now,
            )
        }
        val groups = seed.groups.map { TargetGroup(it.id, it.name, now) }
        val rules = buildList {
            seed.targets.forEach { dt ->
                val targetId = TargetId.app(dt.packageName)
                dt.quotaMinutes?.let {
                    add(
                        Rule(
                            id = "dev-quota-$targetId",
                            targetId = targetId,
                            type = RuleType.DAILY_QUOTA,
                            quotaMinutes = it,
                            enabled = true,
                            createdAt = now,
                            updatedAt = now,
                        )
                    )
                }
                if (dt.alwaysBlock) {
                    add(
                        Rule(
                            id = "dev-abs-$targetId",
                            targetId = targetId,
                            type = RuleType.ALWAYS_BLOCK,
                            enabled = true,
                            createdAt = now,
                            updatedAt = now,
                        )
                    )
                }
            }
            seed.groups.forEach { g ->
                add(
                    Rule(
                        id = "dev-gq-${g.id}",
                        targetId = TargetId.group(g.id),
                        type = RuleType.DAILY_QUOTA,
                        quotaMinutes = g.quotaMinutes,
                        enabled = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }
        repository.replaceConfig(targets, groups, rules)
        Log.i(TAG, "DEV_SEED applied targets=${targets.size} groups=${groups.size} rules=${rules.size}")
    }

    /** 首次运行导入仓库内置 Mock 订阅，并给非 alwaysBlock 功能加 0 分钟配额（进入即拦，M2 演示）。 */
    private suspend fun bootstrapMockSubscription() {
        val text = try {
            assets.open(MOCK_SUB_ASSET).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "mock subscription asset missing")
            return
        }
        val parsed = try {
            SubscriptionImporter(repository).import(text)
        } catch (e: Exception) {
            Log.w(TAG, "mock subscription import failed: ${e.message}")
            return
        }
        val now = System.currentTimeMillis()
        val demoRules = parsed.apps.flatMap { app ->
            app.features.filter { !it.alwaysBlock }.map { feature ->
                val targetId = TargetId.func(app.packageName, feature.featureId)
                Rule(
                    id = "mock-quota0-$targetId",
                    targetId = targetId,
                    type = RuleType.DAILY_QUOTA,
                    quotaMinutes = 0,
                    enabled = true,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        }
        if (demoRules.isNotEmpty()) repository.upsertRules(demoRules)
        Log.i(TAG, "MOCK_SUB imported features=${parsed.featureCount}")
    }

    /** 从启用的订阅重建 T2 索引（订阅导入/热更新后调用）。 */
    private suspend fun reloadT2() {
        detector.load(repository.enabledSubscriptions())
        Log.i(TAG, "T2_LOAD features=${detector.featureCount} packages=${detector.packages().size}")
    }

    /** 启动清理：移除残留的专注临时规则（进程被杀导致 end() 未跑时）。 */
    private suspend fun cleanupStaleFocusRules() {
        val stale = repository.allRules().filter { it.id.startsWith("focus-") }
        stale.forEach { repository.deleteRule(it.id) }
        if (stale.isNotEmpty()) {
            RuntimeStatus.focusActive = false
            RuntimeStatus.focusRemainingSeconds = 0
            Log.i(TAG, "FOCUS cleaned ${stale.size} stale rules")
        }
    }

    /** 默认规则 v2（F9）：无任何组时自动建"视频组"，纳入已启用视频类目标与 FUNC 目标。 */
    private suspend fun ensureDefaultRules() {
        if (repository.allGroups().isNotEmpty()) return
        val targets = repository.allTargets().filter { it.enabled }
        val catalog = config?.appCatalog ?: return
        val videoTargets = targets.filter { t ->
            t.kind == TargetKind.APP && catalog.any { it.packageName == t.packageName && it.category == "video" }
        }
        val funcTargets = targets.filter { it.kind == TargetKind.FUNC }
        val members = (videoTargets + funcTargets).distinctBy { it.targetId }
        if (members.isEmpty()) return
        val now = System.currentTimeMillis()
        val groupId = "g-default-video"
        repository.upsertGroups(listOf(TargetGroup(id = groupId, name = "视频组", createdAt = now)))
        members.forEach { t -> repository.upsertTargets(listOf(t.copy(groupId = groupId))) }
        repository.upsertRules(
            listOf(
                Rule(
                    id = "gq-$groupId",
                    targetId = TargetId.group(groupId),
                    type = RuleType.DAILY_QUOTA,
                    quotaMinutes = 45,
                    enabled = true,
                    createdAt = now,
                    updatedAt = now,
                ),
                Rule(
                    id = "gs-$groupId",
                    targetId = TargetId.group(groupId),
                    type = RuleType.SCHEDULE_BLOCK,
                    startHHmm = 900,
                    endHHmm = 2200,
                    enabled = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        )
        Log.i(TAG, "DEFAULT_RULES video group created members=${members.size}")
    }

    /** 配置变更后刷新内存快照并向控制台广播（收紧/放宽/订阅热更新）。 */
    private suspend fun refreshRuntimeConfig() {
        snapTargets = repository.allTargets()
        snapRules = repository.allRules()
        reloadT2()
        usageCache = repository.usageMap(today).toMutableMap()
        bonusCache = repository.todayBonuses(today).toMutableMap()
        AppRuntime.webServer?.hub?.broadcast(buildWsEvent("RULES_CHANGED"))
        AppRuntime.webServer?.hub?.broadcast(buildWsEvent("TARGETS_CHANGED"))
    }

    private fun buildWsEvent(type: String): String = kotlinx.serialization.json.buildJsonObject {
        put("type", type)
        put("ts", System.currentTimeMillis())
    }.toString()

    private fun loadDevSeed(): DevSeed? {
        val jsonText = try {
            assets.open(DEV_SEED_ASSET).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return null
        }
        return runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString(DevSeed.serializer(), jsonText)
        }.getOrElse { e ->
            Log.w(TAG, "dev seed parse failed: ${e.message}")
            null
        }
    }

    // --- 开发期种子结构（仅本机测试，不入库） ---

    @Serializable
    private data class DevSeed(
        val targets: List<DevTarget> = emptyList(),
        val groups: List<DevGroup> = emptyList(),
    ) {
        @Serializable
        data class DevTarget(
            @SerialName("package") val packageName: String,
            val label: String,
            val quotaMinutes: Int? = null,
            val alwaysBlock: Boolean = false,
        )

        @Serializable
        data class DevGroup(
            val id: String,
            val name: String,
            val quotaMinutes: Int,
            val members: List<String> = emptyList(),
        )
    }

    companion object {
        private const val TAG = "Mooring"
        private const val CONFIG_ASSET = "detector_config.json"
        private const val DEV_SEED_ASSET = "dev_enabled_targets.json"
        private const val MOCK_SUB_ASSET = "mock_subscription.json"
        private const val TICK_INTERVAL_MS = 10_000L
        private const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
        private const val STICKY_GRACE_MS = 120_000L
        private const val ACTIVE_WINDOW_MS = 30_000L
        private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

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
