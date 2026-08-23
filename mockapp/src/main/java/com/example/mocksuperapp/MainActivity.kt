// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package com.example.mocksuperapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.example.mocksuperapp.feed.FeedActivity
import com.example.mocksuperapp.live.LiveActivity

/** Mock 首页：入口按钮进入 Feed / Live 页面（用于 T2 演示）。 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        root.addView(
            Button(this).apply {
                text = "打开 Mock Feed"
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, FeedActivity::class.java))
                }
            }
        )
        root.addView(
            Button(this).apply {
                text = "打开 Mock Live"
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, LiveActivity::class.java))
                }
            }
        )
        setContentView(root)
    }
}
