// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.engine

/** 目标 ID 的构造与解析，遵循术语表形态：APP: / FUNC: / GROUP:。 */
object TargetId {

    const val PREFIX_APP = "APP:"
    const val PREFIX_FUNC = "FUNC:"
    const val PREFIX_GROUP = "GROUP:"

    fun app(packageName: String): String = PREFIX_APP + packageName

    fun func(packageName: String, featureId: String): String =
        PREFIX_FUNC + packageName + ":" + featureId

    fun group(groupId: String): String = PREFIX_GROUP + groupId

    fun isGroupRuleTarget(targetId: String): Boolean = targetId.startsWith(PREFIX_GROUP)

    fun parsePackage(targetId: String): String? = when {
        targetId.startsWith(PREFIX_APP) -> targetId.removePrefix(PREFIX_APP)
        targetId.startsWith(PREFIX_FUNC) -> {
            val rest = targetId.removePrefix(PREFIX_FUNC)
            rest.substringBefore(':')
        }
        else -> null
    }
}
