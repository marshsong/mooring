// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.web

import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.marshsong.mooring.data.MooringRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.github.marshsong.mooring.engine.DetectorConfig
import io.github.marshsong.mooring.engine.TargetId
import io.github.marshsong.mooring.engine.model.BlockAction
import io.github.marshsong.mooring.engine.model.PairedClient
import io.github.marshsong.mooring.engine.model.Rule
import io.github.marshsong.mooring.engine.model.RuleType
import io.github.marshsong.mooring.engine.model.Target
import io.github.marshsong.mooring.engine.model.TargetGroup
import io.github.marshsong.mooring.engine.model.TargetKind
import io.github.marshsong.mooring.engine.model.TargetSource
import io.github.marshsong.mooring.subscription.SubscriptionImporter
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 内嵌 Web 服务：托管控制台前端 + REST API + WebSocket。
 * 端口固定 8765，绑定 0.0.0.0，仅局域网可达；由服务守护协程管理生命周期。
 */
class WebServer(
    private val context: Context,
    private val repository: MooringRepository,
    private val configProvider: () -> DetectorConfig,
    private val onConfigChanged: suspend () -> Unit,
) {
    val hub = EventHub()

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ApplicationEngine? = null

    fun start() {
        if (server != null) return
        val srv = embeddedServer(CIO, port = PORT, host = "0.0.0.0", module = { configure() })
        server = srv
        serverScope.launch {
            while (isActive) {
                try {
                    srv.start(wait = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("Mooring", "SERVER crashed: ${e.message}; restarting in 5s")
                    delay(5000)
                }
            }
        }
    }

    fun stop() {
        serverScope.cancel()
        server?.stop(100, 500)
        server = null
    }

    val isRunning: Boolean get() = server != null

    private fun Application.configure() {
        install(ContentNegotiation) { json(apiJson) }
        install(WebSockets)
        routing {
            staticAssets()
            apiRoutes()
            webSocket("/ws") { wsHandler() }
        }
    }

    // ---------------- 静态前端 ----------------

    private fun Route.staticAssets() {
        get("/") { context.respondAsset("web/index.html") }
        get("/app.js") { context.respondAsset("web/app.js") }
        get("/style.css") { context.respondAsset("web/style.css") }
        get("/vendor/jsqr.js") { context.respondAsset("web/vendor/jsqr.js") }
    }

    private suspend fun ApplicationCall.respondAsset(assetPath: String) {
        val bytes = runCatching { context.assets.open(assetPath).use { it.readBytes() } }.getOrNull()
        if (bytes == null) {
            respond(HttpStatusCode.NotFound, "not found")
            return
        }
        val type = when (assetPath.substringAfterLast('.', "").lowercase()) {
            "html" -> ContentType.Text.Html
            "css" -> ContentType.Text.CSS
            "js" -> ContentType("application", "javascript")
            else -> ContentType.Text.Plain
        }
        respondBytes(bytes, type)
    }

    // ---------------- API ----------------

    private fun Route.apiRoutes() {
        route("/api") {
            post("/pair") { context.handlePair() }
            get("/status") { context.handleStatus() }
            get("/catalog") { context.respondOk(apiJson.encodeToJsonElement(configProvider().appCatalog)) }
            get("/apps/installed") { context.respondOk(apiJson.encodeToJsonElement(installedLaunchableApps())) }

            get("/targets") { context.respondOk(apiJson.encodeToJsonElement(repository.allTargets())) }
            post("/targets") { context.handleEnableTarget() }
            delete("/targets/{id}") { context.handleDisableTarget() }

            get("/groups") { context.respondOk(apiJson.encodeToJsonElement(repository.allGroups())) }
            post("/groups") { context.handleCreateGroup() }
            put("/groups/{id}") { context.handleUpdateGroup() }
            delete("/groups/{id}") { context.handleDeleteGroup() }

            get("/rules") { context.respondOk(apiJson.encodeToJsonElement(repository.allRules())) }
            post("/rules") { context.handleCreateRule() }
            put("/rules/{id}") { context.handleUpdateRule() }
            delete("/rules/{id}") { context.handleDeleteRule() }

            get("/subscriptions") { context.respondOk(apiJson.encodeToJsonElement(repository.subscriptions())) }
            post("/subscriptions") { context.handleImportSubscription() }
            put("/subscriptions/{id}") { context.handleToggleSubscription() }

            get("/usage") { context.handleUsage() }
            get("/events") { context.handleEvents() }
        }
    }

    // ---------------- 鉴权 ----------------

    /** 写接口：需有效 Token + 已配对 UA。 */
    private suspend fun ApplicationCall.checkWrite(): Boolean {
        if (!TokenManager.isValid(context, request.headers["X-Anchor-Token"])) {
            respondFail(HttpStatusCode.Unauthorized, "TOKEN_INVALID")
            return false
        }
        val ua = request.headers[HttpHeaders.UserAgent] ?: ""
        if (repository.pairedClientByUserAgent(ua) == null) {
            respondFail(HttpStatusCode.Forbidden, "MOBILE_READONLY")
            return false
        }
        return true
    }

    // ---------------- 处理器 ----------------

    private suspend fun ApplicationCall.handlePair() {
        val req = runCatching { receive<PairRequest>() }.getOrNull()
            ?: return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        if (!TokenManager.isValid(context, req.token)) {
            return respondFail(HttpStatusCode.Unauthorized, "TOKEN_INVALID")
        }
        val ua = req.userAgent.ifBlank { request.headers[HttpHeaders.UserAgent] ?: "" }
        if (ua.isBlank()) return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        val now = System.currentTimeMillis()
        val existing = repository.pairedClientByUserAgent(ua)
        if (existing == null && repository.pairedClientCount() >= MAX_PAIRED) {
            return respondFail(HttpStatusCode.Forbidden, "PAIR_LIMIT")
        }
        repository.upsertPairedClient(
            PairedClient(
                id = existing?.id ?: "pc-$now",
                userAgent = ua,
                firstSeenAt = existing?.firstSeenAt ?: now,
                lastSeenAt = now,
            )
        )
        respondOk(apiJson.encodeToJsonElement(PairResult(deviceName = DEVICE_NAME, paired = true)))
    }

    private suspend fun ApplicationCall.handleStatus() {
        val dateStr = LocalDate.now().format(ISO_DATE)
        val targets = repository.allTargets()
        val groups = repository.allGroups()
        val usage = repository.usageMap(dateStr)
        val groupUsage = groups.associate { g ->
            g.id to targets.filter { it.enabled && it.groupId == g.id }.sumOf { usage[it.targetId] ?: 0L }
        }
        val ua = request.headers[HttpHeaders.UserAgent]
        val currentPaired = ua != null && repository.pairedClientByUserAgent(ua) != null
        respondOk(
            apiJson.encodeToJsonElement(
                StatusDto(
                    moored = RuntimeStatus.moored,
                    accessibilityEnabled = RuntimeStatus.accessibilityEnabled,
                    focusActive = RuntimeStatus.focusActive,
                    focusRemainingSeconds = RuntimeStatus.focusRemainingSeconds,
                    today = usage,
                    groupsToday = groupUsage,
                    rules = repository.allRules(),
                    targets = targets,
                    groups = groups,
                    pairedClients = repository.pairedClientCount(),
                    currentClientPaired = currentPaired,
                    serverPort = PORT,
                )
            )
        )
    }

    private suspend fun ApplicationCall.handleEnableTarget() {
        if (!checkWrite()) return
        val req = runCatching { receive<TargetEnableRequest>() }.getOrNull()
            ?: return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        val now = System.currentTimeMillis()
        val kind = if (req.kind == "FUNC" && !req.featureId.isNullOrBlank()) TargetKind.FUNC else TargetKind.APP
        val targetId = if (kind == TargetKind.FUNC) {
            TargetId.func(req.packageName, req.featureId!!)
        } else {
            TargetId.app(req.packageName)
        }
        val existing = repository.allTargets().firstOrNull { it.targetId == targetId }
        repository.upsertTargets(
            listOf(
                Target(
                    targetId = targetId,
                    label = req.label.ifBlank { existing?.label ?: req.packageName },
                    kind = kind,
                    packageName = req.packageName,
                    groupId = req.groupId ?: existing?.groupId,
                    source = existing?.source ?: TargetSource.CUSTOM,
                    enabled = true,
                    createdAt = existing?.createdAt ?: now,
                )
            )
        )
        val newRules = mutableListOf<Rule>()
        req.quotaMinutes?.let { q ->
            newRules += Rule(
                id = "api-quota-$targetId",
                targetId = targetId,
                type = RuleType.DAILY_QUOTA,
                quotaMinutes = q,
                enabled = true,
                createdAt = now,
                updatedAt = now,
            )
        }
        if (req.alwaysBlock) {
            newRules += Rule(
                id = "api-abs-$targetId",
                targetId = targetId,
                type = RuleType.ALWAYS_BLOCK,
                enabled = true,
                createdAt = now,
                updatedAt = now,
            )
        }
        if (newRules.isNotEmpty()) repository.upsertRules(newRules)
        onConfigChanged()
        respondOk(apiJson.encodeToJsonElement(mapOf("targetId" to targetId, "enabled" to true)))
    }

    private suspend fun ApplicationCall.handleDisableTarget() {
        if (!checkWrite()) return
        val id = parameters["id"] ?: return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        val existing = repository.allTargets().firstOrNull { it.targetId == id }
            ?: return respondFail(HttpStatusCode.NotFound, "NOT_FOUND")
        // 停用 = 放宽，M4 接入冷静期；M3 直接应用。
        repository.upsertTargets(listOf(existing.copy(enabled = false)))
        onConfigChanged()
        respondOk(apiJson.encodeToJsonElement(mapOf("targetId" to id, "enabled" to false)))
    }

    private suspend fun ApplicationCall.handleCreateGroup() {
        if (!checkWrite()) return
        val req = runCatching { receive<GroupRequest>() }.getOrNull()
            ?: return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        if (req.name.isBlank()) return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        val now = System.currentTimeMillis()
        val groupId = "g-$now"
        repository.upsertGroups(listOf(TargetGroup(id = groupId, name = req.name, createdAt = now)))
        req.memberTargetIds?.forEach { targetId ->
            repository.allTargets().firstOrNull { it.targetId == targetId }?.let { t ->
                repository.upsertTargets(listOf(t.copy(groupId = groupId)))
            }
        }
        req.quotaMinutes?.let { q ->
            repository.upsertRules(
                listOf(
                    Rule(
                        id = "api-gq-$groupId",
                        targetId = TargetId.group(groupId),
                        type = RuleType.DAILY_QUOTA,
                        quotaMinutes = q,
                        enabled = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            )
        }
        onConfigChanged()
        respondOk(apiJson.encodeToJsonElement(mapOf("id" to groupId)))
    }

    private suspend fun ApplicationCall.handleUpdateGroup() {
        if (!checkWrite()) return
        val id = parameters["id"] ?: return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        val existing = repository.allGroups().firstOrNull { it.id == id }
            ?: return respondFail(HttpStatusCode.NotFound, "NOT_FOUND")
        val req = runCatching { receive<GroupRequest>() }.getOrNull()
            ?: return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        repository.upsertGroups(listOf(existing.copy(name = req.name.ifBlank { existing.name })))
        req.memberTargetIds?.let { members ->
            repository.allTargets().filter { it.groupId == id }.forEach { t ->
                repository.upsertTargets(listOf(t.copy(groupId = null)))
            }
            members.forEach { targetId ->
                repository.allTargets().firstOrNull { it.targetId == targetId }?.let { t ->
                    repository.upsertTargets(listOf(t.copy(groupId = id)))
                }
            }
        }
        req.quotaMinutes?.let { q ->
            val ruleId = "api-gq-$id"
            repository.upsertRules(
                listOf(
                    Rule(
                        id = ruleId,
                        targetId = TargetId.group(id),
                        type = RuleType.DAILY_QUOTA,
                        quotaMinutes = q,
                        enabled = true,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            )
        }
        onConfigChanged()
        respondOk(apiJson.encodeToJsonElement(mapOf("id" to id)))
    }

    private suspend fun ApplicationCall.handleDeleteGroup() {
        if (!checkWrite()) return
        val id = parameters["id"] ?: return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        repository.deleteGroup(id)
        repository.allTargets().filter { it.groupId == id }.forEach { t ->
            repository.upsertTargets(listOf(t.copy(groupId = null)))
        }
        repository.allRules().filter { it.targetId == TargetId.group(id) }.forEach { r ->
            repository.deleteRule(r.id)
        }
        onConfigChanged()
        respondOk(apiJson.encodeToJsonElement(mapOf("id" to id, "deleted" to true)))
    }

    private suspend fun ApplicationCall.handleCreateRule() {
        if (!checkWrite()) return
        val req = runCatching { receive<RuleRequest>() }.getOrNull()
            ?: return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        val now = System.currentTimeMillis()
        repository.upsertRules(listOf(req.toRule(id = "api-r-$now", now = now)))
        onConfigChanged()
        respondOk(apiJson.encodeToJsonElement(mapOf("id" to "api-r-$now")))
    }

    private suspend fun ApplicationCall.handleUpdateRule() {
        if (!checkWrite()) return
        val id = parameters["id"] ?: return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        val existing = repository.allRules().firstOrNull { it.id == id }
            ?: return respondFail(HttpStatusCode.NotFound, "NOT_FOUND")
        val req = runCatching { receive<RuleRequest>() }.getOrNull()
            ?: return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        repository.upsertRules(
            listOf(
                existing.copy(
                    type = req.type(ifBlank = existing.type),
                    quotaMinutes = req.quotaMinutes ?: existing.quotaMinutes,
                    startHHmm = req.startHHmm ?: existing.startHHmm,
                    endHHmm = req.endHHmm ?: existing.endHHmm,
                    action = req.action(ifBlank = existing.action),
                    enabled = req.enabled,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        )
        onConfigChanged()
        respondOk(apiJson.encodeToJsonElement(mapOf("id" to id)))
    }

    private suspend fun ApplicationCall.handleDeleteRule() {
        if (!checkWrite()) return
        val id = parameters["id"] ?: return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        repository.deleteRule(id)
        onConfigChanged()
        respondOk(apiJson.encodeToJsonElement(mapOf("id" to id, "deleted" to true)))
    }

    private suspend fun ApplicationCall.handleImportSubscription() {
        if (!checkWrite()) return
        val text = runCatching { receiveText() }.getOrNull()
            ?: return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        val parsed = try {
            SubscriptionImporter(repository).import(text)
        } catch (e: IllegalArgumentException) {
            return respondFail(HttpStatusCode.BadRequest, "SUBSCRIPTION_INVALID")
        }
        onConfigChanged()
        respondOk(
            apiJson.encodeToJsonElement(
                mapOf("name" to parsed.name, "features" to parsed.featureCount, "version" to parsed.subscriptionVersion)
            )
        )
    }

    private suspend fun ApplicationCall.handleToggleSubscription() {
        if (!checkWrite()) return
        val id = parameters["id"] ?: return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        val existing = repository.subscriptions().firstOrNull { it.id == id }
            ?: return respondFail(HttpStatusCode.NotFound, "NOT_FOUND")
        val req = runCatching { receive<SubscriptionToggleRequest>() }.getOrNull()
            ?: return respondFail(HttpStatusCode.BadRequest, "BAD_REQUEST")
        repository.upsertSubscription(existing.copy(enabled = req.enabled, updatedAt = System.currentTimeMillis()))
        onConfigChanged()
        respondOk(apiJson.encodeToJsonElement(mapOf("id" to id, "enabled" to req.enabled)))
    }

    private suspend fun ApplicationCall.handleUsage() {
        val dateStr = LocalDate.now().format(ISO_DATE)
        val targets = repository.allTargets()
        val groups = repository.allGroups()
        val usage = repository.usageMap(dateStr)
        val groupUsage = groups.associate { g ->
            g.id to targets.filter { it.enabled && it.groupId == g.id }.sumOf { usage[it.targetId] ?: 0L }
        }
        respondOk(apiJson.encodeToJsonElement(UsageDto(date = dateStr, targets = usage, groups = groupUsage)))
    }

    private suspend fun ApplicationCall.handleEvents() {
        val limit = (request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 200)
        respondOk(apiJson.encodeToJsonElement(repository.recentEvents(limit)))
    }

    // ---------------- WebSocket ----------------

    private suspend fun DefaultWebSocketServerSession.wsHandler() {
        hub.attach(this)
        try {
            send(Frame.Text(wsEvent("SERVICE_STATUS", "connected")))
            for (frame in incoming) {
                // 目前不处理入站消息；连接保持以接收广播。
            }
        } finally {
            hub.detach(this)
        }
    }

    // ---------------- 工具 ----------------

    private fun installedLaunchableApps(): List<InstalledAppDto> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = runCatching { ri.loadLabel(pm).toString() }.getOrNull() ?: pkg
                InstalledAppDto(packageName = pkg, label = label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    // ---------------- 响应 ----------------

    @Serializable
    data class ApiResponse(val code: Int, val msg: String, val data: JsonElement? = null)

    private suspend fun ApplicationCall.respondOk(data: JsonElement) {
        respond(ApiResponse(code = 0, msg = "ok", data = data))
    }

    private suspend fun ApplicationCall.respondFail(status: HttpStatusCode, name: String) {
        respond(status, ApiResponse(code = status.value, msg = name, data = null))
    }

    private fun wsEvent(type: String, detail: String): String =
        buildJsonObject {
            put("type", type)
            put("ts", System.currentTimeMillis())
            put("detail", detail)
        }.toString()

    // ---------------- DTO ----------------

    @Serializable
    data class PairRequest(val token: String, val userAgent: String = "")

    @Serializable
    data class PairResult(val deviceName: String, val paired: Boolean)

    @Serializable
    data class TargetEnableRequest(
        val packageName: String,
        val label: String = "",
        val kind: String = "APP",
        val featureId: String? = null,
        val groupId: String? = null,
        val quotaMinutes: Int? = null,
        val alwaysBlock: Boolean = false,
    )

    @Serializable
    data class GroupRequest(
        val name: String = "",
        val memberTargetIds: List<String>? = null,
        val quotaMinutes: Int? = null,
    )

    @Serializable
    data class RuleRequest(
        val targetId: String = "",
        val type: String = "DAILY_QUOTA",
        val quotaMinutes: Int? = null,
        val startHHmm: Int? = null,
        val endHHmm: Int? = null,
        val action: String = "OVERLAY_AND_BACK",
        val enabled: Boolean = true,
    )

    @Serializable
    data class SubscriptionToggleRequest(val enabled: Boolean)

    @Serializable
    data class StatusDto(
        val moored: Boolean,
        val accessibilityEnabled: Boolean,
        val focusActive: Boolean,
        val focusRemainingSeconds: Long,
        val today: Map<String, Long>,
        val groupsToday: Map<String, Long>,
        val rules: List<Rule>,
        val targets: List<Target>,
        val groups: List<TargetGroup>,
        val pairedClients: Int,
        val currentClientPaired: Boolean,
        val serverPort: Int,
    )

    @Serializable
    data class UsageDto(
        val date: String,
        val targets: Map<String, Long>,
        val groups: Map<String, Long>,
    )

    @Serializable
    data class InstalledAppDto(val packageName: String, val label: String)

    private fun RuleRequest.toRule(id: String, now: Long): Rule = Rule(
        id = id,
        targetId = targetId,
        type = runCatching { RuleType.valueOf(type) }.getOrDefault(RuleType.DAILY_QUOTA),
        quotaMinutes = quotaMinutes,
        startHHmm = startHHmm,
        endHHmm = endHHmm,
        action = runCatching { BlockAction.valueOf(action) }.getOrDefault(BlockAction.OVERLAY_AND_BACK),
        enabled = enabled,
        createdAt = now,
        updatedAt = now,
    )

    private fun RuleRequest.type(ifBlank: RuleType): RuleType =
        runCatching { RuleType.valueOf(type) }.getOrDefault(ifBlank)

    private fun RuleRequest.action(ifBlank: BlockAction): BlockAction =
        runCatching { BlockAction.valueOf(action) }.getOrDefault(ifBlank)

    companion object {
        const val PORT = 8765
        private const val DEVICE_NAME = "Mooring"
        private const val MAX_PAIRED = 3
        private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val apiJson = Json { ignoreUnknownKeys = true }
    }
}
