// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.cooldown

import io.github.marshsong.mooring.data.MooringRepository
import io.github.marshsong.mooring.engine.model.CooldownRecord
import io.github.marshsong.mooring.engine.model.CooldownStatus
import io.github.marshsong.mooring.settings.SettingsStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** 已有进行中冷静期。 */
class CooldownConflictException : Exception()

/**
 * 冷静期引擎：
 * 发起放宽 → 等 expiresAt 到期 → 到期后 120 秒确认窗口内"确认生效"才应用，超时自动作废；
 * 同时刻仅允许一个进行中冷静期；取消随时可。
 */
class CooldownManager(
    private val repository: MooringRepository,
    private val settings: SettingsStore,
    private val applyAction: suspend (JsonObject) -> Boolean,
) {

    @Serializable
    data class RequestResult(val cooldownId: String, val expiresAt: Long, val changePreview: JsonObject?)

    sealed class ConfirmResult {
        data object Confirmed : ConfirmResult()
        data object Expired : ConfirmResult()
        data object Waiting : ConfirmResult()
        data object NotFound : ConfirmResult()
        data object ApplyFailed : ConfirmResult()
    }

    /** 发起冷静期；存在进行中则抛 CooldownConflictException。 */
    suspend fun request(payload: JsonObject, preview: JsonObject?): RequestResult {
        if (repository.activeCooldown() != null) throw CooldownConflictException()
        val now = System.currentTimeMillis()
        val expiresAt = now + settings.cooldownMinutes * 60_000L
        val id = "cd-$now"
        repository.upsertCooldown(
            CooldownRecord(
                id = id,
                payloadJson = payload.toString(),
                status = CooldownStatus.PENDING,
                requestedAt = now,
                expiresAt = expiresAt,
            )
        )
        return RequestResult(id, expiresAt, preview)
    }

    suspend fun confirm(id: String): ConfirmResult {
        val record = repository.cooldownById(id) ?: return ConfirmResult.NotFound
        if (record.status != CooldownStatus.PENDING) return ConfirmResult.NotFound
        val now = System.currentTimeMillis()
        if (now < record.expiresAt) return ConfirmResult.Waiting
        if (now > record.expiresAt + CONFIRM_WINDOW_MS) {
            repository.setCooldownStatus(id, CooldownStatus.EXPIRED)
            return ConfirmResult.Expired
        }
        val payload = runCatching {
            Json { ignoreUnknownKeys = true }.parseToJsonElement(record.payloadJson).jsonObject
        }.getOrNull()
        val applied = payload != null && applyAction(payload)
        repository.setCooldownStatus(id, if (applied) CooldownStatus.CONFIRMED else CooldownStatus.CANCELLED)
        return if (applied) ConfirmResult.Confirmed else ConfirmResult.ApplyFailed
    }

    suspend fun cancel(id: String): Boolean {
        val record = repository.cooldownById(id) ?: return false
        if (record.status != CooldownStatus.PENDING) return false
        repository.setCooldownStatus(id, CooldownStatus.CANCELLED)
        return true
    }

    companion object {
        const val CONFIRM_WINDOW_MS = 120_000L
    }
}
