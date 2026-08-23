// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong

package io.github.marshsong.mooring.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.marshsong.mooring.engine.model.EventLog

@Dao
interface EventDao {

    @Insert
    suspend fun insert(event: EventLog): Long

    @Query("SELECT * FROM event_log ORDER BY ts DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<EventLog>

    @Query("DELETE FROM event_log WHERE ts < :beforeTs")
    suspend fun deleteOlderThan(beforeTs: Long): Int
}
