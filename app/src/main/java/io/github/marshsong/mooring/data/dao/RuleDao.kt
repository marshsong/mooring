// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.marshsong.mooring.engine.model.Rule

@Dao
interface RuleDao {

    @Query("SELECT * FROM rules")
    suspend fun getAll(): List<Rule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<Rule>)

    @Query("DELETE FROM rules")
    suspend fun clear()
}
