// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package com.example.mocksuperapp.feed

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Mock Feed 页面。窗口类名命中订阅的 feed 正则；内容含标题与 Tab 关键词，
 * 可被 T2 二级内容规则命中。
 */
class FeedActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        root.addView(TextView(this).apply { text = "MOCKFEED"; textSize = 28f })
        root.addView(TextView(this).apply { text = "FOR YOU" })
        root.addView(TextView(this).apply { text = "FOLLOWING" })
        root.addView(TextView(this).apply { text = "Scrollable mock feed content" })
        setContentView(root)
    }
}
