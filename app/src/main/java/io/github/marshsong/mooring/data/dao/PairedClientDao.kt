// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.marshsong.mooring.engine.model.PairedClient

@Dao
interface PairedClientDao {

    @Query("SELECT * FROM paired_clients")
    suspend fun getAll(): List<PairedClient>

    @Query("SELECT * FROM paired_clients WHERE userAgent = :userAgent")
    suspend fun getByUserAgent(userAgent: String): PairedClient?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(client: PairedClient)

    @Query("DELETE FROM paired_clients WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM paired_clients")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM paired_clients")
    suspend fun count(): Int
}
