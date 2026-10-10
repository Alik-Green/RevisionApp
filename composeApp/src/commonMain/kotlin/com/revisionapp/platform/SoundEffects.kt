package com.revisionapp.platform

import com.revisionapp.generated.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Short feedback sounds used for answers and learning milestones. */
enum class SoundEffect {
    CORRECT,
    INCORRECT,
    LESSON_COMPLETE,
    QUEST_COMPLETE,
    STREAK_EXTENDED,
}

/** Reads and caches the small CC0 sound assets shared by the Android and desktop players. */
internal object SoundEffectAudio {
    private val loadMutex = Mutex()
    private val cache = mutableMapOf<SoundEffect, ByteArray>()

    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(effect: SoundEffect): ByteArray = loadMutex.withLock {
        cache[effect] ?: Res.readBytes("files/sfx/${effect.assetName}").also { cache[effect] = it }
    }

    private val SoundEffect.assetName: String
        get() = when (this) {
            SoundEffect.CORRECT -> "correct.wav"
            SoundEffect.INCORRECT -> "incorrect.wav"
            SoundEffect.LESSON_COMPLETE -> "lesson-complete.wav"
            SoundEffect.QUEST_COMPLETE -> "quest-complete.wav"
            SoundEffect.STREAK_EXTENDED -> "streak-extended.wav"
        }
}

/** Platform-native playback keeps device APIs and audio decoders out of common code. */
expect object PlatformSoundEffects {
    fun play(waveData: ByteArray)
}
