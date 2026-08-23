// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.marshsong.mooring.service.MooringForegroundService

/**
 * 开机自检：重启后恢复前台保活服务与 Web 服务。
 * 若无障碍被系统关闭，则推送高优先级"脱缰"通知（由服务启动时检查）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i("Mooring", "BOOT_COMPLETED received, restarting keep-alive service")
        val start = Intent(context, MooringForegroundService::class.java)
        context.startForegroundService(start)
    }
}
