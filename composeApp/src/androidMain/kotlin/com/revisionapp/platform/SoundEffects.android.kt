package com.revisionapp.platform

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque

actual object PlatformSoundEffects {
    private data class Tone(
        val value: Int,
        val durationMs: Long,
        val pauseAfterMs: Long = 45,
    )

    private val handler = Handler(Looper.getMainLooper())
    private val pending = ArrayDeque<SoundEffect>()
    private var isPlaying = false

    actual fun play(effect: SoundEffect) {
        handler.post {
            pending.addLast(effect)
            playNext()
        }
    }

    private fun playNext() {
        if (isPlaying || pending.isEmpty()) return
        val effect = pending.removeFirst()
        val generator = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 78)
        } catch (_: RuntimeException) {
            playNext()
            return
        }
        val tones = tonesFor(effect)
        isPlaying = true

        fun playTone(index: Int) {
            if (index >= tones.size) {
                handler.postDelayed(
                    {
                        generator.release()
                        isPlaying = false
                        playNext()
                    },
                    30,
                )
                return
            }
            val tone = tones[index]
            generator.startTone(tone.value, tone.durationMs.toInt())
            handler.postDelayed({ playTone(index + 1) }, tone.durationMs + tone.pauseAfterMs)
        }

        playTone(0)
    }

    private fun tonesFor(effect: SoundEffect): List<Tone> = when (effect) {
        SoundEffect.CORRECT -> listOf(Tone(ToneGenerator.TONE_PROP_ACK, 120))
        SoundEffect.INCORRECT -> listOf(Tone(ToneGenerator.TONE_PROP_NACK, 180))
        SoundEffect.LESSON_COMPLETE -> listOf(
            Tone(ToneGenerator.TONE_PROP_PROMPT, 110),
            Tone(ToneGenerator.TONE_PROP_ACK, 150),
            Tone(ToneGenerator.TONE_PROP_BEEP2, 210, pauseAfterMs = 0),
        )
        SoundEffect.QUEST_COMPLETE -> listOf(
            Tone(ToneGenerator.TONE_PROP_ACK, 100),
            Tone(ToneGenerator.TONE_PROP_BEEP2, 130),
            Tone(ToneGenerator.TONE_PROP_ACK, 220, pauseAfterMs = 0),
        )
        SoundEffect.STREAK_EXTENDED -> listOf(
            Tone(ToneGenerator.TONE_PROP_BEEP, 100),
            Tone(ToneGenerator.TONE_PROP_PROMPT, 140),
            Tone(ToneGenerator.TONE_PROP_ACK, 220, pauseAfterMs = 0),
        )
    }
}
