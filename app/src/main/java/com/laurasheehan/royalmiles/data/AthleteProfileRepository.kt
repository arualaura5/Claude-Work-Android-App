package com.laurasheehan.royalmiles.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AthleteProfileRepository(private val dao: AthleteProfileDao) {
    fun observeBodyWeightKg(): Flow<Double?> = dao.observe().map { it?.bodyWeightKg }

    suspend fun setBodyWeightKg(kg: Double?) = dao.upsert(AthleteProfileEntity(id = 0, bodyWeightKg = kg))
}
