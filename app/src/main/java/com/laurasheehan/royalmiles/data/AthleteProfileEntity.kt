package com.laurasheehan.royalmiles.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A single optional field. This is not a weight-tracking feature — no history, no trend, just
 * what's needed to scale the g/kg nutrition targets to the person.
 */
@Entity(tableName = "athlete_profile")
data class AthleteProfileEntity(
    @PrimaryKey val id: Int = 0,
    val bodyWeightKg: Double? = null,
)

@Dao
interface AthleteProfileDao {
    @Query("SELECT * FROM athlete_profile WHERE id = 0")
    fun observe(): Flow<AthleteProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: AthleteProfileEntity)
}
