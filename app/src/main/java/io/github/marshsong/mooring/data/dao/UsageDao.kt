// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.marshsong.mooring.engine.model.UsageDaily

@Dao
interface UsageDao {

    @Query("SELECT usedSeconds FROM usage_daily WHERE dateStr = :dateStr AND targetId = :targetId")
    suspend fun getSeconds(dateStr: String, targetId: String): Long?

    @Query("SELECT * FROM usage_daily WHERE dateStr = :dateStr")
    suspend fun getForDate(dateStr: String): List<UsageDaily>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(usage: UsageDaily)

    @Query(
        "UPDATE usage_daily SET usedSeconds = usedSeconds + :seconds " +
            "WHERE dateStr = :dateStr AND targetId = :targetId"
    )
    suspend fun addSeconds(dateStr: String, targetId: String, seconds: Long): Int

    @Query("DELETE FROM usage_daily")
    suspend fun clear()
}
