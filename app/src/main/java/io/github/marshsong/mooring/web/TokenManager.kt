// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.web

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

/** 配对令牌：首次启动生成 32 位随机 hex，仅经二维码/引导页展示。 */
object TokenManager {

    private const val PREFS = "mooring_token"
    private const val KEY_TOKEN = "token"

    @Volatile
    private var cached: String? = null

    fun getOrCreate(context: Context): String {
        cached?.let { return it }
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_TOKEN, null)
        if (!existing.isNullOrEmpty()) {
            cached = existing
            return existing
        }
        val token = generate()
        prefs.edit().putString(KEY_TOKEN, token).apply()
        cached = token
        return token
    }

    fun isValid(context: Context, token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        val expected = getOrCreate(context)
        return constantTimeEquals(expected, token)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    private fun generate(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
