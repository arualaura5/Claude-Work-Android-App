package com.laura.royaltasks.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.laura.royaltasks.R

/**
 * Short synthesized chimes bundled as WAVs. Silent whenever the phone's
 * ringer isn't in normal mode, on top of the in-app mute toggle.
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

    private val completeId = soundPool.load(appContext, R.raw.complete, 1)
    private val crownId = soundPool.load(appContext, R.raw.crown, 1)
    private val levelUpId = soundPool.load(appContext, R.raw.level_up, 1)

    var enabled: Boolean = true

    private fun play(soundId: Int, volume: Float) {
        if (!enabled) return
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        soundPool.play(soundId, volume, volume, 1, 0, 1f)
    }

    fun playComplete() = play(completeId, volume = 0.7f)
    fun playCrown() = play(crownId, volume = 0.9f)
    fun playLevelUp() = play(levelUpId, volume = 0.9f)

    fun release() {
        soundPool.release()
    }
}
