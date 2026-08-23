// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.data

import androidx.room.TypeConverter
import io.github.marshsong.mooring.engine.model.BlockAction
import io.github.marshsong.mooring.engine.model.CooldownStatus
import io.github.marshsong.mooring.engine.model.EventType
import io.github.marshsong.mooring.engine.model.RuleType
import io.github.marshsong.mooring.engine.model.TargetKind
import io.github.marshsong.mooring.engine.model.TargetSource

/** Room 枚举类型转换器。 */
class Converters {

    @TypeConverter
    fun fromTargetKind(value: TargetKind): String = value.name

    @TypeConverter
    fun toTargetKind(value: String): TargetKind = TargetKind.valueOf(value)

    @TypeConverter
    fun fromTargetSource(value: TargetSource): String = value.name

    @TypeConverter
    fun toTargetSource(value: String): TargetSource = TargetSource.valueOf(value)

    @TypeConverter
    fun fromRuleType(value: RuleType): String = value.name

    @TypeConverter
    fun toRuleType(value: String): RuleType = RuleType.valueOf(value)

    @TypeConverter
    fun fromBlockAction(value: BlockAction): String = value.name

    @TypeConverter
    fun toBlockAction(value: String): BlockAction = BlockAction.valueOf(value)

    @TypeConverter
    fun fromEventType(value: EventType): String = value.name

    @TypeConverter
    fun toEventType(value: String): EventType = EventType.valueOf(value)

    @TypeConverter
    fun fromCooldownStatus(value: CooldownStatus): String = value.name

    @TypeConverter
    fun toCooldownStatus(value: String): CooldownStatus = CooldownStatus.valueOf(value)
}
