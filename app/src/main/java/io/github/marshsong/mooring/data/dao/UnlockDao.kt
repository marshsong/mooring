// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.marshsong.mooring.engine.model.UnlockRecord

@Dao
interface UnlockDao {

    @Query("SELECT * FROM unlock_records WHERE dateStr = :dateStr")
    suspend fun getForDate(dateStr: String): List<UnlockRecord>

    @Query("SELECT COUNT(*) FROM unlock_records WHERE dateStr = :dateStr AND targetId = :targetId")
    suspend fun countForTarget(dateStr: String, targetId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: UnlockRecord)
}
