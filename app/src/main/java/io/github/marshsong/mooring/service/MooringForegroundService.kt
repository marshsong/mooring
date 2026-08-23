// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import io.github.marshsong.mooring.AppRuntime
import io.github.marshsong.mooring.MainActivity
import io.github.marshsong.mooring.R
import io.github.marshsong.mooring.engine.TargetId
import io.github.marshsong.mooring.web.RuntimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 前台保活服务（华为专项）：常驻通知 + 承载 Web 服务。
 * foregroundServiceType="specialUse"；BOOT_COMPLETED 后自动恢复。
 */
class MooringForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val today get() = LocalDate.now().format(ISO_DATE)

    override fun onCreate() {
        super.onCreate()
        AppRuntime.init(this)
        AppRuntime.ensureServerStarted(this)
        RuntimeStatus.moored = true
        startForeground(NOTIFICATION_ID, buildNotification("Mooring 守护中"))
        scope.launch {
            while (isActive) {
                delay(60_000)
                updateNotification()
            }
        }
        Log.i(TAG, "FOREGROUND started, server running=${AppRuntime.webServer?.isRunning}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun updateNotification() {
        runCatching {
            val text = computeRemainingText()
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    private suspend fun computeRemainingText(): String {
        val dateStr = today
        val usage = AppRuntime.repository.usageMap(dateStr)
        val groups = AppRuntime.repository.allGroups()
        val group = groups.firstOrNull { it.name == "视频组" } ?: return "已拴牢"
        val targets = AppRuntime.repository.allTargets()
        val rules = AppRuntime.repository.allRules()
        val quotaRule = rules.firstOrNull {
            it.targetId == TargetId.group(group.id) && it.type == io.github.marshsong.mooring.engine.model.RuleType.DAILY_QUOTA
        }
        val quotaMinutes = quotaRule?.quotaMinutes ?: return "已拴牢"
        val usedMinutes = targets
            .filter { it.enabled && it.groupId == group.id }
            .sumOf { (usage[it.targetId] ?: 0L) / 60 }
        val remaining = (quotaMinutes - usedMinutes).coerceAtLeast(0)
        return "今日视频组剩余 $remaining 分钟"
    }

    private fun buildNotification(text: String): Notification {
        val channelId = CHANNEL_ID
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Mooring 守护", NotificationManager.IMPORTANCE_LOW)
        )

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Mooring 守护中")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "Mooring"
        private const val CHANNEL_ID = "mooring_keepalive"
        private const val NOTIFICATION_ID = 1001
        private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
