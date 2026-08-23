// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.github.marshsong.mooring.data.MooringDatabase
import io.github.marshsong.mooring.data.MooringRepository
import io.github.marshsong.mooring.data.RoomMooringRepository
import io.github.marshsong.mooring.engine.TargetId
import io.github.marshsong.mooring.engine.model.Rule
import io.github.marshsong.mooring.engine.model.RuleType
import io.github.marshsong.mooring.service.MooringAccessibilityService
import io.github.marshsong.mooring.subscription.SubscriptionImporter
import io.github.marshsong.mooring.web.RuntimeStatus
import io.github.marshsong.mooring.web.TokenManager
import io.github.marshsong.mooring.web.WebServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 手机端 UI（刻意极简，3 个页面）：
 * 引导（无障碍 + 配对令牌/二维码 + USB 兜底）/ 状态（只读）/ 开发（连点版本号 7 次激活）。
 */
class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: MooringRepository

    private lateinit var guideView: LinearLayout
    private lateinit var statusView: LinearLayout
    private lateinit var devView: LinearLayout

    private var devUnlocked = false
    private var versionTaps = 0
    private val today: String get() = LocalDate.now().format(ISO_DATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = RoomMooringRepository(MooringDatabase.get(this))
        setContentView(buildRoot())
        switchTab("guide")
    }

    override fun onResume() {
        super.onResume()
        renderGuide()
        renderStatus()
    }

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tabRow = HorizontalScrollView(this).apply {
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(tabButton("引导") { switchTab("guide") })
                    addView(tabButton("状态") { switchTab("status") })
                    addView(tabButton("开发") { switchTab("dev") })
                }
            )
        }
        root.addView(tabRow, LinearLayout.LayoutParams(match, wrap))

        val content = FrameLayout(this)
        guideView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)) }
        statusView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)) }
        devView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)) }
        content.addView(scroll(guideView))
        content.addView(scroll(statusView))
        content.addView(scroll(devView))
        root.addView(content, LinearLayout.LayoutParams(match, 0, 1f))
        return root
    }

    private fun tabButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
        }

    private fun scroll(view: LinearLayout): ScrollView =
        ScrollView(this).apply { addView(view, ViewGroup.LayoutParams(match, match)) }

    private fun switchTab(name: String) {
        guideView.visibility = if (name == "guide") View.VISIBLE else View.GONE
        statusView.visibility = if (name == "status") View.VISIBLE else View.GONE
        devView.visibility = if (name == "dev") View.VISIBLE else View.GONE
        if (name == "dev") renderDev()
    }

    // ---------------- 引导页 ----------------

    private fun renderGuide() {
        guideView.removeAllViews()
        title(guideView, "Mooring 配对引导")

        val accessibilityOn = MooringAccessibilityService.isAccessibilityEnabled(this)
        section(guideView, "第 1 步：开启无障碍")
        statusLine(guideView, "无障碍服务：${if (accessibilityOn) "已开启" else "未开启"}")
        button(guideView, "打开无障碍设置") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        section(guideView, "第 2 步：电脑配对")
        val token = TokenManager.getOrCreate(this)
        row(guideView, "局域网访问")
        row(guideView, "浏览器打开 http://${lanIp() ?: "手机IP"}:${WebServer.PORT} ，扫描下方二维码，或手动输入令牌。")

        val qr = qrBitmap(qrContent(token), dp(220))
        guideView.addView(ImageView(this).apply {
            setImageBitmap(qr)
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(220)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })

        statusLine(guideView, "令牌（明文）：${token}")
        button(guideView, "复制令牌") { copy(token) }

        section(guideView, "电脑挂了 VPN / 连不上局域网？用 USB 兜底")
        statusLine(guideView, "1. 手机开 USB 调试并连接电脑")
        statusLine(guideView, "2. 电脑执行：adb forward tcp:${WebServer.PORT} tcp:${WebServer.PORT}")
        statusLine(guideView, "3. 浏览器打开 http://127.0.0.1:${WebServer.PORT} 完成配对")
        button(guideView, "复制 adb 命令") {
            copy("adb forward tcp:${WebServer.PORT} tcp:${WebServer.PORT}")
        }
    }

    private fun qrContent(token: String): String {
        val ip = lanIp() ?: ""
        return buildString {
            append("{\"ip\":\"$ip\",\"port\":${WebServer.PORT},\"token\":\"$token\",\"deviceName\":\"Mooring\"}")
        }
    }

    private fun lanIp(): String? = runCatching {
        val wm = getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ip = wm.connectionInfo.ipAddress
        if (ip == 0) null else String.format("%d.%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
    }.getOrNull()

    // ---------------- 状态页 ----------------

    private fun renderStatus() {
        statusView.removeAllViews()
        title(statusView, "Mooring 状态")
        val moored = RuntimeStatus.moored
        statusLine(statusView, "服务：${if (moored) "已拴牢（Moored）" else "脱缰（Adrift）"}")
        statusLine(statusView, "端口：${WebServer.PORT}")
        statusLine(statusView, "局域网 IP：${lanIp() ?: "未知"}")
        statusLine(statusView, "专注模式：${if (RuntimeStatus.focusActive) "进行中" else "未激活"}")

        scope.launch {
            val usage = repository.usageMap(today)
            val targets = repository.allTargets().filter { it.enabled || (usage[it.targetId] ?: 0L) > 0 }
            section(statusView, "今日用量")
            if (targets.isEmpty()) {
                statusLine(statusView, "尚无启用目标。请在电脑控制台完成首次配置。")
            } else {
                targets.forEach { t ->
                    val used = usage[t.targetId] ?: 0L
                    statusLine(statusView, "${t.label}：${fmtSeconds(used)}")
                }
            }
        }
    }

    // ---------------- 开发页 ----------------

    private fun renderDev() {
        if (devView.childCount > 0) return
        title(devView, "开发者页")
        statusLine(devView, "连点下方版本号 7 次解锁")

        val versionLabel = TextView(this).apply {
            text = "版本：0.1.0"
            setTextColor(Color.parseColor("#3B5BDB"))
            textSize = 14f
            setOnClickListener {
                versionTaps++
                if (versionTaps >= 7 && !devUnlocked) {
                    devUnlocked = true
                    Toast.makeText(this@MainActivity, "开发者页已解锁", Toast.LENGTH_SHORT).show()
                    populateDevContent()
                }
            }
        }
        devView.addView(versionLabel, LinearLayout.LayoutParams(match, wrap))
    }

    private fun populateDevContent() {
        section(devView, "连接信息")
        statusLine(devView, "IP：${lanIp() ?: "未知"} / 端口：${WebServer.PORT}")
        statusLine(devView, "adb forward tcp:${WebServer.PORT} tcp:${WebServer.PORT}")

        section(devView, "导入订阅")
        val subInput = EditText(this).apply {
            hint = "粘贴订阅 JSON"
            minLines = 3
        }
        devView.addView(subInput, LinearLayout.LayoutParams(match, wrap))
        button(devView, "导入订阅") {
            val text = subInput.text.toString().trim()
            if (text.isEmpty()) return@button
            scope.launch {
                try {
                    val parsed = SubscriptionImporter(repository).import(text)
                    Toast.makeText(this@MainActivity, "导入成功：${parsed.featureCount} 个功能", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        section(devView, "最近事件")
        scope.launch {
            val events = repository.recentEvents(20)
            events.forEach { e ->
                statusLine(devView, "${e.ts} ${e.type} ${e.targetId ?: ""} ${e.detailJson ?: ""}")
            }
            if (events.isEmpty()) statusLine(devView, "暂无事件")
        }
    }

    // ---------------- 工具 ----------------

    private fun title(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 20f
            setTextColor(Color.parseColor("#101A3A"))
            layoutParams = LinearLayout.LayoutParams(match, wrap).apply { bottomMargin = dp(8) }
        })
    }

    private fun section(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(16); bottomMargin = dp(4) }
        })
    }

    private fun statusLine(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(match, wrap).apply { bottomMargin = dp(4) }
        })
    }

    private fun row(parent: LinearLayout, text: String) {
        statusLine(parent, text)
    }

    private fun button(parent: LinearLayout, text: String, onClick: () -> Unit) {
        parent.addView(Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = dp(4); bottomMargin = dp(4) }
        })
    }

    private fun copy(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("mooring", text))
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }

    private fun qrBitmap(content: String, size: Int): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
        return bmp
    }

    private fun fmtSeconds(seconds: Long): String = if (seconds >= 3600) {
        String.format("%.1f 小时", seconds / 3600.0)
    } else {
        "${seconds / 60} 分钟"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val match = ViewGroup.LayoutParams.MATCH_PARENT
        private const val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
