// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.block

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.marshsong.mooring.engine.RuleEngine
import io.github.marshsong.mooring.engine.model.Target

/**
 * 勒马页执行器（逻辑与 Android 服务解耦，回调注入）。
 *
 * 触发序列：弹出全屏勒马页 → 3 秒后执行一次返回 → 若 5 秒后仍检测到
 * 目标前台，重复覆盖 + 返回，不设次数上限。服务侧 5 秒轮询兜底重弹。
 */
class BlockManager(
    private val startRein: (target: Target, reason: RuleEngine.BlockReason) -> Unit,
    private val performBack: () -> Unit,
    private val isForegroundBlocked: (targetId: String) -> Boolean,
) {
    private val handler = Handler(Looper.getMainLooper())

    private var blockingTarget: Target? = null
    private var blockingReason: RuleEngine.BlockReason? = null

    /** 目标命中"应拦截状态"时触发。 */
    fun trigger(target: Target, result: RuleEngine.EvaluationResult) {
        val reason = result.reason ?: return
        if (blockingTarget?.targetId == target.targetId && blockingReason == reason) {
            return
        }
        blockingTarget = target
        blockingReason = reason
        Log.d(TAG, "TRIGGER target=${target.targetId} reason=$reason rule=${result.ruleId} source=${result.source}")
        startRein(target, reason)
        scheduleBack()
        schedulePoll()
    }

    /** 前台变化时通知：离开被拦目标则停止轮询。 */
    fun onForegroundChanged(currentTargetId: String?) {
        if (currentTargetId != blockingTarget?.targetId) {
            blockingTarget = null
            blockingReason = null
        }
    }

    private fun scheduleBack() {
        handler.postDelayed({ performBack() }, BACK_DELAY_MS)
    }

    private fun schedulePoll() {
        handler.postDelayed({ poll() }, POLL_INTERVAL_MS)
    }

    private fun poll() {
        val target = blockingTarget ?: return
        val reason = blockingReason ?: return
        if (isForegroundBlocked(target.targetId)) {
            Log.i(TAG, "REBLOCK target=${target.targetId} still foreground")
            startRein(target, reason)
            scheduleBack()
        }
        schedulePoll()
    }

    companion object {
        private const val TAG = "Mooring"
        private const val BACK_DELAY_MS = 3_000L
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
