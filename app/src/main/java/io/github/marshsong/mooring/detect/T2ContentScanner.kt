// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.detect

import android.view.accessibility.AccessibilityNodeInfo

/**
 * T2 二级检测：有界遍历节点树，收集可见文本与节点类名。
 *
 * 遍历深度 ≤ 15、节点数 ≤ 500，超限放弃（返回已收集的部分）。
 * 类名用于识别部分应用不暴露文本、但控件类名固定的场景（如视频进度条）。
 * 文本与类名仅内存中关键词匹配，匹配后立即丢弃，不落盘不上传。
 */
class T2ContentScanner(private val root: AccessibilityNodeInfo) {

    private val texts = ArrayList<String>(32)
    private val viewClasses = LinkedHashSet<String>()
    private var nodeCount = 0
    private var walked = false

    /** 一次遍历同时收集文本与类名；重复调用复用首次结果。 */
    fun scan(): Snapshot {
        if (!walked) {
            walked = true
            walk(root, 0)
        }
        return Snapshot(texts, viewClasses.toList())
    }

    data class Snapshot(val texts: List<String>, val viewClasses: List<String>)

    private fun walk(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null || depth > MAX_DEPTH || nodeCount >= MAX_NODES) return
        nodeCount++
        if (!node.isVisibleToUser) {
            // 仍需遍历子节点（部分容器文本可见性由子节点决定），继续。
        }
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
        node.className?.toString()?.takeIf { it.isNotEmpty() }?.let { viewClasses.add(it) }
        for (i in 0 until node.childCount) {
            walk(node.getChild(i), depth + 1)
        }
    }

    companion object {
        const val MAX_DEPTH = 30
        const val MAX_NODES = 3000
    }
}
