// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.marshsong.mooring.engine.model.CooldownRecord
import io.github.marshsong.mooring.engine.model.CooldownStatus

@Dao
interface CooldownDao {

    @Query("SELECT * FROM cooldown_records WHERE id = :id")
    suspend fun getById(id: String): CooldownRecord?

    @Query("SELECT * FROM cooldown_records WHERE status = :status ORDER BY requestedAt DESC LIMIT 1")
    suspend fun getLatestByStatus(status: CooldownStatus): CooldownRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CooldownRecord)

    @Query("UPDATE cooldown_records SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: CooldownStatus)
}
