package com.revisionapp.platform

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

actual object PlatformSoundEffects {
    private data class Tone(
        val frequency: Double,
        val durationMs: Int,
        val pauseAfterMs: Int = 45,
    )

    private const val SAMPLE_RATE = 44_100
    private val playbackQueue = Executors.newSingleThreadExecutor { task ->
        Thread(task, "revisionapp-sound-effects").apply { isDaemon = true }
    }

    actual fun play(effect: SoundEffect) {
        playbackQueue.execute {
            runCatching { playSequence(tonesFor(effect)) }
        }
    }

    private fun playSequence(tones: List<Tone>) {
        val pcm = ByteArrayOutputStream()
        tones.forEach { tone ->
            appendTone(pcm, tone.frequency, tone.durationMs)
            repeat(SAMPLE_RATE * tone.pauseAfterMs / 1_000) {
                pcm.write(0)
                pcm.write(0)
            }
        }
        val bytes = pcm.toByteArray()
        val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
        val stream = AudioInputStream(ByteArrayInputStream(bytes), format, bytes.size.toLong() / 2)
        val clip = AudioSystem.getClip()
        try {
            clip.open(stream)
            clip.start()
            while (clip.isRunning) Thread.sleep(15)
        } finally {
            clip.close()
            stream.close()
        }
    }

    private fun appendTone(output: ByteArrayOutputStream, frequency: Double, durationMs: Int) {
        val samples = SAMPLE_RATE * durationMs / 1_000
        val fadeSamples = (SAMPLE_RATE * 0.012).toInt()
        for (index in 0 until samples) {
            val fadeIn = (index.toDouble() / fadeSamples).coerceIn(0.0, 1.0)
            val fadeOut = ((samples - index).toDouble() / fadeSamples).coerceIn(0.0, 1.0)
            val envelope = minOf(fadeIn, fadeOut)
            val wave = kotlin.math.sin(2.0 * Math.PI * frequency * index / SAMPLE_RATE) * envelope * 0.24
            val value = (wave * Short.MAX_VALUE).toInt()
            output.write(value and 0xFF)
            output.write((value shr 8) and 0xFF)
        }
    }

    private fun tonesFor(effect: SoundEffect): List<Tone> = when (effect) {
        SoundEffect.CORRECT -> listOf(Tone(659.25, 95), Tone(880.0, 125, pauseAfterMs = 0))
        SoundEffect.INCORRECT -> listOf(Tone(392.0, 105), Tone(293.66, 165, pauseAfterMs = 0))
        SoundEffect.LESSON_COMPLETE -> listOf(
            Tone(523.25, 100),
            Tone(659.25, 100),
            Tone(783.99, 100),
            Tone(1_046.5, 210, pauseAfterMs = 0),
        )
        SoundEffect.QUEST_COMPLETE -> listOf(
            Tone(659.25, 85),
            Tone(783.99, 85),
            Tone(987.77, 85),
            Tone(1_318.5, 220, pauseAfterMs = 0),
        )
        SoundEffect.STREAK_EXTENDED -> listOf(
            Tone(783.99, 95),
            Tone(987.77, 95),
            Tone(1_174.66, 200, pauseAfterMs = 0),
        )
    }
}
