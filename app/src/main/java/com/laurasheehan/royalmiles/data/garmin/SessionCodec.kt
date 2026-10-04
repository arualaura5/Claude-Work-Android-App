package com.laurasheehan.royalmiles.data.garmin

import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.data.SessionEntity
import org.json.JSONObject
import java.time.LocalDate

/**
 * A whole session row as JSON, kept with a Garmin decision so undoing it restores exactly what
 * was there. Every field of [SessionEntity] must appear here; SessionCodecTest fails if one is
 * added without being carried.
 */
object SessionCodec {
    fun encode(s: SessionEntity): String = JSONObject()
        .put("id", s.id)
        .put("eventId", s.eventId)
        .put("date", s.date.toString())
        .put("type", s.type.name)
        .put("title", s.title)
        .put("phase", s.phase.name)
        .put("weekNumber", s.weekNumber)
        .put("targetDistanceKm", s.targetDistanceKm ?: JSONObject.NULL)
        .put("targetDurationMin", s.targetDurationMin ?: JSONObject.NULL)
        .put("optional", s.optional)
        .put("notes", s.notes)
        .put("isCompleted", s.isCompleted)
        .put("actualDistanceKm", s.actualDistanceKm ?: JSONObject.NULL)
        .put("actualDurationMin", s.actualDurationMin ?: JSONObject.NULL)
        .put("completedAt", s.completedAt?.toString() ?: JSONObject.NULL)
        .put("isCustom", s.isCustom)
        .put("effortRating", s.effortRating ?: JSONObject.NULL)
        .put("isSkipped", s.isSkipped)
        .put("supersededByCoach", s.supersededByCoach)
        .put("actualAvgHeartRate", s.actualAvgHeartRate ?: JSONObject.NULL)
        .put("actualMaxHeartRate", s.actualMaxHeartRate ?: JSONObject.NULL)
        .put("actualCalories", s.actualCalories ?: JSONObject.NULL)
        .put("actualElevationGainM", s.actualElevationGainM ?: JSONObject.NULL)
        .put("sourceApp", s.sourceApp ?: JSONObject.NULL)
        .put("sourceActivityId", s.sourceActivityId ?: JSONObject.NULL)
        .put("bodyNote", s.bodyNote ?: JSONObject.NULL)
        .toString()

    fun decode(json: String): SessionEntity {
        val o = JSONObject(json)
        fun str(name: String): String? = if (o.isNull(name)) null else o.getString(name)
        fun int(name: String): Int? = if (o.isNull(name)) null else o.getInt(name)
        fun dbl(name: String): Double? = if (o.isNull(name)) null else o.getDouble(name)
        return SessionEntity(
            id = o.getLong("id"),
            eventId = o.getString("eventId"),
            date = LocalDate.parse(o.getString("date")),
            type = SessionType.valueOf(o.getString("type")),
            title = o.getString("title"),
            phase = TrainingPhase.valueOf(o.getString("phase")),
            weekNumber = o.getInt("weekNumber"),
            targetDistanceKm = dbl("targetDistanceKm"),
            targetDurationMin = int("targetDurationMin"),
            optional = o.getBoolean("optional"),
            notes = o.getString("notes"),
            isCompleted = o.getBoolean("isCompleted"),
            actualDistanceKm = dbl("actualDistanceKm"),
            actualDurationMin = int("actualDurationMin"),
            completedAt = str("completedAt")?.let(LocalDate::parse),
            isCustom = o.getBoolean("isCustom"),
            effortRating = int("effortRating"),
            isSkipped = o.getBoolean("isSkipped"),
            supersededByCoach = o.getBoolean("supersededByCoach"),
            actualAvgHeartRate = int("actualAvgHeartRate"),
            actualMaxHeartRate = int("actualMaxHeartRate"),
            actualCalories = int("actualCalories"),
            actualElevationGainM = int("actualElevationGainM"),
            sourceApp = str("sourceApp"),
            sourceActivityId = str("sourceActivityId"),
            bodyNote = str("bodyNote"),
        )
    }
}
