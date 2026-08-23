package com.laurasheehan.royalmiles.data

import androidx.room.TypeConverter
import com.laurasheehan.royalmiles.core.model.SessionType
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromSessionType(type: SessionType): String = type.name

    @TypeConverter
    fun toSessionType(value: String): SessionType = SessionType.valueOf(value)

    @TypeConverter
    fun fromPhase(phase: TrainingPhase): String = phase.name

    @TypeConverter
    fun toPhase(value: String): TrainingPhase = TrainingPhase.valueOf(value)
}
