package com.laurasheehan.royalmiles.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY date ASC, id ASC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY date ASC, id ASC")
    suspend fun getAll(): List<SessionEntity>

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Delete
    suspend fun delete(session: SessionEntity)

    @Query(
        """
        DELETE FROM sessions
         WHERE date >= :cutoverDate
           AND isCompleted = 0
           AND isSkipped = 0
           AND isCustom = 0
        """,
    )
    suspend fun deleteRegeneratableSessions(cutoverDate: java.time.LocalDate)
}

@Dao
interface PlanMetaDao {
    @Query("SELECT * FROM plan_meta WHERE id = 0")
    suspend fun get(): PlanMetaEntity?

    @Query("SELECT * FROM plan_meta WHERE id = 0")
    fun observe(): Flow<PlanMetaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: PlanMetaEntity)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: String): EventEntity?

    @Query("SELECT * FROM events ORDER BY raceDate ASC, id ASC")
    suspend fun getAll(): List<EventEntity>

    @Query("SELECT * FROM events ORDER BY raceDate ASC, id ASC")
    fun observeAll(): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: EventEntity)
}
