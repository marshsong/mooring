// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import io.github.marshsong.mooring.R
import io.github.marshsong.mooring.engine.RuleEngine

/**
 * 勒马页：全屏拦截覆盖页。
 *
 * 不透明主题、锁定触摸返回、无任何宽限/继续按钮；文案按触发原因展示，
 * 底部一行小字指向电脑控制台。re-launch 时通过 onNewIntent 更新文案。
 */
class ReinActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.rein_page)
        applyReason(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyReason(intent)
    }

    override fun onBackPressed() {
        // 锁触摸返回：不响应。
    }

    /** 系统返回手势/按键同样吞掉。 */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.dispatchKeyEvent(event)
    }

    private fun applyReason(intent: Intent) {
        val reasonName = intent.getStringExtra(EXTRA_REASON)
        val msgRes = when (reasonName) {
            RuleEngine.BlockReason.QUOTA_EXHAUSTED.name -> R.string.rein_quota_exhausted
            RuleEngine.BlockReason.SCHEDULE_BLOCK.name -> R.string.rein_schedule_block
            RuleEngine.BlockReason.ALWAYS_BLOCK.name -> R.string.rein_always_block
            else -> R.string.rein_default
        }
        findViewById<TextView>(R.id.reinMessage).setText(msgRes)
    }

    companion object {
        const val EXTRA_REASON = "reason"
        const val EXTRA_TARGET_LABEL = "targetLabel"
    }
}
