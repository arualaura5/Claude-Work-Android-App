package com.laurasheehan.royalmiles.data.garmin

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

enum class GarminDecision {
    /** One clear fit, linked without asking; shown on the dashboard until she's seen it. */
    AUTO_LINKED,

    /** She said which planned session it was. */
    LINKED,

    /** She said it was a different day's session, done early or late. */
    OTHER_DAY,

    /** It replaced that day's planned session. */
    SWAPPED,

    /** Done, but not part of the plan. */
    EXTRA,

    /** Not something to log. */
    IGNORED,
}

/**
 * What happened to one Garmin activity, and exactly what it changed, so it can be undone.
 *
 * Nothing is deleted when she undoes a decision: the row is kept, marked undone, as the record
 * that she said "not this one". The activity's figures are kept as Garmin recorded them when she
 * accepted them, so a later edit on Garmin's side can't silently rewrite her log.
 */
@Entity(tableName = "garmin_decisions", indices = [Index("activityId"), Index("sessionId")])
data class GarminDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: String,
    val decision: GarminDecision,
    /** The session now holding the activity; null when ignored. */
    val sessionId: Long?,
    val activityDate: LocalDate,
    val activityName: String?,
    val activityKind: String?,
    val distanceKm: Double?,
    val durationMin: Int?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    /** That session's row before the decision changed it (SessionCodec), for an exact undo. */
    val previousSessionJson: String?,
    /** For a swap: the planned session before it was marked replaced. */
    val replacedSessionJson: String?,
    /** For a swap or an extra: the session created for the activity, removed on undo. */
    val createdSessionId: Long?,
    val decidedAtMillis: Long,
    /** An automatic link she has seen and left in place. */
    val acknowledged: Boolean = false,
    val undoneAtMillis: Long? = null,
) {
    val isActive: Boolean get() = undoneAtMillis == null
}

@Dao
interface GarminDecisionDao {
    @Insert
    suspend fun insert(decision: GarminDecisionEntity): Long

    @Update
    suspend fun update(decision: GarminDecisionEntity)

    @Query("SELECT * FROM garmin_decisions WHERE id = :id")
    suspend fun getById(id: Long): GarminDecisionEntity?

    @Query("SELECT * FROM garmin_decisions ORDER BY decidedAtMillis ASC, id ASC")
    suspend fun getAll(): List<GarminDecisionEntity>

    @Query("SELECT * FROM garmin_decisions WHERE activityId = :activityId AND undoneAtMillis IS NULL LIMIT 1")
    suspend fun activeFor(activityId: String): GarminDecisionEntity?

    @Query("SELECT * FROM garmin_decisions WHERE sessionId = :sessionId AND undoneAtMillis IS NULL LIMIT 1")
    suspend fun activeForSession(sessionId: Long): GarminDecisionEntity?

    @Query("SELECT * FROM garmin_decisions ORDER BY decidedAtMillis DESC, id DESC")
    fun observeAll(): Flow<List<GarminDecisionEntity>>
}
