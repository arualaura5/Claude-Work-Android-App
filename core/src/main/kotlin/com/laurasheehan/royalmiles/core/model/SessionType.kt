package com.laurasheehan.royalmiles.core.model

enum class SessionType {
    REST,
    EASY_RUN,
    LONG_RUN,
    STRENGTH,
    YOGA,
    CYCLE,
    SWIM,
    RACE,
}

enum class TrainingPhase(val label: String) {
    BASE("Base Building"),
    BUILD("Build"),
    PEAK("Peak Week"),
    TAPER("Taper & Race"),
}
