package com.arabicflashcards.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.runtime.staticCompositionLocalOf
import com.arabicflashcards.app.R

/**
 * Short UI sound cues (flip, grade, lesson complete). Synthesized tones
 * bundled as raw WAV resources — no external assets or licensing.
 * Silenced whenever the phone's ringer isn't in normal mode, on top of
 * the in-app mute toggle, so the app never talks over a silenced phone.
 */
class SoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val flipSoundId = soundPool.load(appContext, R.raw.flip, 1)
    private val correctSoundId = soundPool.load(appContext, R.raw.correct, 1)
    private val incorrectSoundId = soundPool.load(appContext, R.raw.incorrect, 1)
    private val lessonCompleteSoundId = soundPool.load(appContext, R.raw.lesson_complete, 1)

    var enabled: Boolean = true

    private fun play(soundId: Int, volume: Float) {
        if (!enabled) return
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        soundPool.play(soundId, volume, volume, 1, 0, 1f)
    }

    fun playFlip() = play(flipSoundId, volume = 0.5f)
    fun playCorrect() = play(correctSoundId, volume = 0.9f)
    fun playIncorrect() = play(incorrectSoundId, volume = 0.7f)
    fun playLessonComplete() = play(lessonCompleteSoundId, volume = 0.9f)

    fun release() {
        soundPool.release()
    }
}

val LocalSoundPlayer = staticCompositionLocalOf<SoundPlayer> {
    error("No SoundPlayer provided — wrap content in CompositionLocalProvider(LocalSoundPlayer provides ...)")
}
