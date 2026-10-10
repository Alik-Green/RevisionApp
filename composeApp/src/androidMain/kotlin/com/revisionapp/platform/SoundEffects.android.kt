package com.revisionapp.platform

import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque

actual object PlatformSoundEffects {
    private val handler = Handler(Looper.getMainLooper())
    private val pending = ArrayDeque<ByteArray>()
    private var currentPlayer: MediaPlayer? = null

    actual fun play(waveData: ByteArray) {
        handler.post {
            pending.addLast(waveData)
            playNext()
        }
    }

    private fun playNext() {
        if (currentPlayer != null || pending.isEmpty()) return
        val player = MediaPlayer()
        currentPlayer = player
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            player.setVolume(0.58f, 0.58f)
            player.setDataSource(ByteArrayMediaDataSource(pending.removeFirst()))
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { finish(it) }
            player.setOnErrorListener { mediaPlayer, _, _ ->
                finish(mediaPlayer)
                true
            }
            player.prepareAsync()
        } catch (_: RuntimeException) {
            finish(player)
        }
    }

    private fun finish(player: MediaPlayer) {
        if (currentPlayer !== player) return
        currentPlayer = null
        player.release()
        playNext()
    }

    private class ByteArrayMediaDataSource(private val bytes: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position < 0 || position >= bytes.size) return -1
            val start = position.toInt()
            val count = minOf(size, bytes.size - start)
            bytes.copyInto(buffer, destinationOffset = offset, startIndex = start, endIndex = start + count)
            return count
        }

        override fun getSize(): Long = bytes.size.toLong()

        override fun close() = Unit
    }
}
