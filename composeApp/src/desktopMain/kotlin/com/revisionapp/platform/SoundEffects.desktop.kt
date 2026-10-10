package com.revisionapp.platform

import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import javax.sound.sampled.AudioSystem

actual object PlatformSoundEffects {
    private val playbackQueue = Executors.newSingleThreadExecutor { task ->
        Thread(task, "revisionapp-sound-effects").apply { isDaemon = true }
    }

    actual fun play(waveData: ByteArray) {
        playbackQueue.execute {
            runCatching { playWave(waveData) }
        }
    }

    private fun playWave(waveData: ByteArray) {
        val stream = AudioSystem.getAudioInputStream(ByteArrayInputStream(waveData))
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
}
