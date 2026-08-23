// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.marshsong.mooring.engine.model.TargetGroup

@Dao
interface GroupDao {

    @Query("SELECT * FROM target_groups")
    suspend fun getAll(): List<TargetGroup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(groups: List<TargetGroup>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: TargetGroup)

    @Query("DELETE FROM target_groups WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM target_groups")
    suspend fun clear()
}
