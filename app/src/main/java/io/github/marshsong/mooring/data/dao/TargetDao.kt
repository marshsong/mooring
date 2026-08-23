// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.marshsong.mooring.engine.model.Target

@Dao
interface TargetDao {

    @Query("SELECT * FROM targets")
    suspend fun getAll(): List<Target>

    @Query("SELECT * FROM targets WHERE enabled = 1")
    suspend fun getEnabled(): List<Target>

    @Query("SELECT * FROM targets WHERE targetId = :targetId")
    suspend fun getById(targetId: String): Target?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(targets: List<Target>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(target: Target)

    @Query("DELETE FROM targets")
    suspend fun clear()

    @Query("UPDATE targets SET enabled = :enabled WHERE targetId = :targetId")
    suspend fun setEnabled(targetId: String, enabled: Boolean)
}
