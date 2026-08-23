// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.marshsong.mooring.data.dao.EventDao
import io.github.marshsong.mooring.data.dao.GroupDao
import io.github.marshsong.mooring.data.dao.RuleDao
import io.github.marshsong.mooring.data.dao.SubscriptionDao
import io.github.marshsong.mooring.data.dao.TargetDao
import io.github.marshsong.mooring.data.dao.UsageDao
import io.github.marshsong.mooring.engine.model.CooldownRecord
import io.github.marshsong.mooring.engine.model.EventLog
import io.github.marshsong.mooring.engine.model.PairedClient
import io.github.marshsong.mooring.engine.model.Rule
import io.github.marshsong.mooring.engine.model.Subscription
import io.github.marshsong.mooring.engine.model.Target
import io.github.marshsong.mooring.engine.model.TargetGroup
import io.github.marshsong.mooring.engine.model.UsageDaily

@Database(
    entities = [
        Target::class,
        TargetGroup::class,
        Rule::class,
        UsageDaily::class,
        EventLog::class,
        CooldownRecord::class,
        PairedClient::class,
        Subscription::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MooringDatabase : RoomDatabase() {

    abstract fun targetDao(): TargetDao
    abstract fun groupDao(): GroupDao
    abstract fun ruleDao(): RuleDao
    abstract fun usageDao(): UsageDao
    abstract fun eventDao(): EventDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        @Volatile
        private var instance: MooringDatabase? = null

        fun get(context: Context): MooringDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MooringDatabase::class.java,
                    "mooring.db",
                ).build().also { instance = it }
            }
    }
}
