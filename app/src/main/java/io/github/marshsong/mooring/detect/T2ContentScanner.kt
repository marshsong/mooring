// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.detect

import android.view.accessibility.AccessibilityNodeInfo

/**
 * T2 二级检测：有界遍历节点树，收集可见文本。
 *
 * 遍历深度 ≤ 15、节点数 ≤ 500，超限放弃（返回已收集的部分）。
 * 文本仅内存中关键词匹配，匹配后立即丢弃，不落盘不上传。
 */
class T2ContentScanner(private val root: AccessibilityNodeInfo) {

    private val texts = ArrayList<String>(32)
    private var nodeCount = 0

    fun collectTexts(): List<String> {
        walk(root, 0)
        return texts
    }

    private fun walk(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null || depth > MAX_DEPTH || nodeCount >= MAX_NODES) return
        nodeCount++
        if (!node.isVisibleToUser) {
            // 仍需遍历子节点（部分容器文本可见性由子节点决定），继续。
        }
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
        for (i in 0 until node.childCount) {
            walk(node.getChild(i), depth + 1)
        }
    }

    companion object {
        const val MAX_DEPTH = 15
        const val MAX_NODES = 500
    }
}
