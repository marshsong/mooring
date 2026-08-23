// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.data

import androidx.room.withTransaction
import io.github.marshsong.mooring.engine.model.EventLog
import io.github.marshsong.mooring.engine.model.PairedClient
import io.github.marshsong.mooring.engine.model.Rule
import io.github.marshsong.mooring.engine.model.Subscription
import io.github.marshsong.mooring.engine.model.Target
import io.github.marshsong.mooring.engine.model.TargetGroup
import io.github.marshsong.mooring.engine.model.UsageDaily

/** Room 实现的仓库。 */
class RoomMooringRepository(private val db: MooringDatabase) : MooringRepository {

    override suspend fun upsertTargets(targets: List<Target>) {
        if (targets.isNotEmpty()) db.targetDao().upsertAll(targets)
    }

    override suspend fun upsertRules(rules: List<Rule>) {
        if (rules.isNotEmpty()) db.ruleDao().upsertAll(rules)
    }

    override suspend fun upsertGroups(groups: List<TargetGroup>) {
        if (groups.isNotEmpty()) db.groupDao().upsertAll(groups)
    }

    override suspend fun deleteGroup(id: String) {
        db.groupDao().delete(id)
    }

    override suspend fun deleteRule(id: String) {
        db.ruleDao().delete(id)
    }

    override suspend fun recentEvents(limit: Int): List<EventLog> = db.eventDao().getRecent(limit)

    override suspend fun subscriptions(): List<Subscription> = db.subscriptionDao().getAll()

    override suspend fun enabledSubscriptions(): List<Subscription> = db.subscriptionDao().getEnabled()

    override suspend fun upsertSubscription(subscription: Subscription) {
        db.subscriptionDao().upsert(subscription)
    }

    override suspend fun pairedClients(): List<PairedClient> = db.pairedClientDao().getAll()

    override suspend fun pairedClientByUserAgent(userAgent: String): PairedClient? =
        db.pairedClientDao().getByUserAgent(userAgent)

    override suspend fun upsertPairedClient(client: PairedClient) {
        db.pairedClientDao().upsert(client)
    }

    override suspend fun pairedClientCount(): Int = db.pairedClientDao().count()

    override suspend fun deletePairedClient(id: String) {
        db.pairedClientDao().delete(id)
    }

    override suspend fun enabledTargets(): List<Target> = db.targetDao().getEnabled()

    override suspend fun allTargets(): List<Target> = db.targetDao().getAll()

    override suspend fun allGroups(): List<TargetGroup> = db.groupDao().getAll()

    override suspend fun allRules(): List<Rule> = db.ruleDao().getAll()

    override suspend fun replaceConfig(targets: List<Target>, groups: List<TargetGroup>, rules: List<Rule>) {
        db.withTransaction {
            db.targetDao().clear()
            db.groupDao().clear()
            db.ruleDao().clear()
            if (targets.isNotEmpty()) db.targetDao().upsertAll(targets)
            if (groups.isNotEmpty()) db.groupDao().upsertAll(groups)
            if (rules.isNotEmpty()) db.ruleDao().upsertAll(rules)
        }
    }

    override suspend fun usedSeconds(targetId: String, dateStr: String): Long =
        db.usageDao().getSeconds(dateStr, targetId) ?: 0L

    override suspend fun addUsage(targetId: String, dateStr: String, seconds: Long) {
        if (seconds <= 0) return
        val rows = db.usageDao().addSeconds(dateStr, targetId, seconds)
        if (rows == 0) {
            db.usageDao().insert(UsageDaily(dateStr = dateStr, targetId = targetId, usedSeconds = seconds))
        }
    }

    override suspend fun usageMap(dateStr: String): Map<String, Long> =
        db.usageDao().getForDate(dateStr).associate { it.targetId to it.usedSeconds }

    override suspend fun insertEvent(event: EventLog) {
        db.eventDao().insert(event)
    }

    override suspend fun cleanupOldEvents(beforeTs: Long) {
        db.eventDao().deleteOlderThan(beforeTs)
    }
}
