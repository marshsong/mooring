// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring

import android.content.Context
import io.github.marshsong.mooring.data.MooringDatabase
import io.github.marshsong.mooring.data.MooringRepository
import io.github.marshsong.mooring.data.RoomMooringRepository
import io.github.marshsong.mooring.engine.DetectorConfig
import io.github.marshsong.mooring.settings.SettingsStore
import io.github.marshsong.mooring.web.WebServer

/**
 * 进程级共享运行时：仓库 / 设置 / Web 服务在同一进程内只初始化一次。
 * 无障碍服务、前台保活服务、主界面共用，避免端口双重绑定与数据不一致。
 */
object AppRuntime {

    lateinit var context: Context
        private set
    lateinit var repository: MooringRepository
        private set
    lateinit var settings: SettingsStore
        private set
    var detectorConfig: DetectorConfig? = null

    @Volatile
    var onConfigChanged: (suspend () -> Unit)? = null

    private var _webServer: WebServer? = null

    val webServer: WebServer? get() = _webServer

    fun init(context: Context) {
        if (::repository.isInitialized) return
        this.context = context.applicationContext
        repository = RoomMooringRepository(MooringDatabase.get(this.context))
        settings = SettingsStore(this.context)
    }

    fun ensureServerStarted(context: Context) {
        init(context)
        if (_webServer != null) return
        val ws = WebServer(
            context = context,
            repository = repository,
            configProvider = { detectorConfig ?: DetectorConfig() },
            settings = settings,
            onConfigChanged = { onConfigChanged?.invoke() },
        )
        ws.start()
        _webServer = ws
    }

    fun stopServer() {
        _webServer?.stop()
        _webServer = null
    }
}
