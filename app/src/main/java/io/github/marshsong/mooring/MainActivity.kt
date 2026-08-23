// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import io.github.marshsong.mooring.service.MooringAccessibilityService

/**
 * M0 最小启动页：查看无障碍服务状态并跳转系统设置开启。
 * 正式三页引导 UI 在 M5 接入。
 */
class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        findViewById<Button>(R.id.btnOpenAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        val active = MooringAccessibilityService.isAccessibilityEnabled(this)
        statusText.setText(
            if (active) R.string.main_status_active else R.string.main_status_ready
        )
    }
}
