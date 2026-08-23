// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package com.example.mocksuperapp.live

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Mock Live 页面：命中订阅的 live 正则与 extraKeyword。 */
class LiveActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        root.addView(TextView(this).apply { text = "LIVE NOW"; textSize = 28f })
        setContentView(root)
    }
}
