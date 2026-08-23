// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.web

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import java.util.concurrent.CopyOnWriteArrayList

/**
 * WebSocket 事件广播：向所有已连接控制台客户端推送事件 JSON。
 * 事件仅含目标/规则/状态，不含任何页面内容。
 */
class EventHub {

    private val sessions = CopyOnWriteArrayList<WebSocketSession>()

    fun attach(session: WebSocketSession) {
        sessions.add(session)
    }

    fun detach(session: WebSocketSession) {
        sessions.remove(session)
    }

    /** 向所有连接广播事件。 */
    suspend fun broadcast(eventJson: String) {
        for (session in sessions) {
            runCatching { session.send(io.ktor.websocket.Frame.Text(eventJson)) }
        }
    }

    val sessionCount: Int get() = sessions.size
}
