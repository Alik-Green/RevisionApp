package com.revisionapp.domain.srs

import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.model.StudyMode

/**
 * Turns what happened in a study mode into an FSRS [Rating].
 *
 * All modes feed the same schedule, and the brief requires a correct answer in a
 * harder mode to count for more than in an easier one. That is driven by
 * [StudyMode.difficultyRank]: a correct *typed* answer earns EASY (the biggest
 * interval increase), a correct multiple-choice answer earns GOOD, and anything
 * wrong earns AGAIN in every active mode. Historical tile reviews keep their old
 * grading rank so imported review logs remain interpretable.
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
     * "I was right" / "I was wrong".
     *
     * An override is deliberately *not* weighted by [mode]. The user is disputing
     * a grade the app has no way to re-check, so a claimed correct answer earns
     * the same increase a correct typed answer would, and a claimed wrong one
     * earns AGAIN in every mode. [mode] is still taken because a session always
     * has it at the call site and the two functions are used side by side.
     */
    fun ratingForOverride(mode: StudyMode, userSaysCorrect: Boolean): Rating =
        if (userSaysCorrect) Rating.EASY else Rating.AGAIN

    private const val TYPED_RANK = 3
}
