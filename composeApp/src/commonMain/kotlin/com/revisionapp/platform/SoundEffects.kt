package com.revisionapp.platform

/** Short feedback sounds used for answers and learning milestones. */
enum class SoundEffect {
    CORRECT,
    INCORRECT,
    LESSON_COMPLETE,
    QUEST_COMPLETE,
    STREAK_EXTENDED,
}

/** Platform-native playback keeps sound optional and avoids putting device APIs in common code. */
expect object PlatformSoundEffects {
    fun play(effect: SoundEffect)
}
