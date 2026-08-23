// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.marshsong.mooring.engine.model.Subscription

@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscriptions")
    suspend fun getAll(): List<Subscription>

    @Query("SELECT * FROM subscriptions WHERE enabled = 1")
    suspend fun getEnabled(): List<Subscription>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subscription: Subscription)

    @Query("DELETE FROM subscriptions")
    suspend fun clear()
}
