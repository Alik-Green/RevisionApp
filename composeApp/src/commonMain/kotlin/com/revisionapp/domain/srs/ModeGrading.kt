package com.revisionapp.domain.srs

import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.model.StudyMode

/**
 * Turns what happened in a study mode into an FSRS [Rating].
 *
 * All modes feed the same schedule, and the brief requires a correct answer in a
 * harder mode to count for more than in an easier one. That is driven by
 * [StudyMode.difficultyRank]: a correct *typed* answer earns EASY (the biggest
 * interval increase), a correct tile or multiple-choice answer earns GOOD, and
 * anything wrong earns AGAIN in every mode.
 *
 * Flashcard sessions are self-rated — the user picks Again/Hard/Good/Easy
 * directly and this mapping is not consulted. [StudyMode.MIXED] is always
 * resolved to a concrete mode before grading.
 */
object ModeGrading {

    fun ratingFor(mode: StudyMode, kind: VerdictKind): Rating = when (kind) {
        VerdictKind.INCORRECT -> Rating.AGAIN
        VerdictKind.PARTIAL -> Rating.HARD
        VerdictKind.CORRECT -> if (mode.difficultyRank >= TYPED_RANK) Rating.EASY else Rating.GOOD
    }

    /**
     * The rating to store when the user overrides the automatic verdict with
     * "I was right" / "I was wrong". An override is authoritative, so a claimed
     * correct answer is graded as if it had been typed.
     */
    fun ratingForOverride(mode: StudyMode, userSaysCorrect: Boolean): Rating =
        if (userSaysCorrect) {
            ratingFor(mode, VerdictKind.CORRECT)
        } else {
            Rating.AGAIN
        }

    private const val TYPED_RANK = 3
}
