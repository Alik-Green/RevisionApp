package com.revisionapp.domain.srs

import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.model.StudyMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModeGradingTest {

    @Test
    fun aCorrectAnswerInAHarderModeCountsForMore() {
        val typed = ModeGrading.ratingFor(StudyMode.TYPED, VerdictKind.CORRECT)
        val tiles = ModeGrading.ratingFor(StudyMode.TILES, VerdictKind.CORRECT)
        val mcq = ModeGrading.ratingFor(StudyMode.MCQ, VerdictKind.CORRECT)

        assertEquals(Rating.EASY, typed)
        assertEquals(Rating.GOOD, tiles)
        assertEquals(Rating.GOOD, mcq)
        assertTrue(typed.value > mcq.value, "typed=$typed mcq=$mcq")
    }

    @Test
    fun aWrongAnswerLapsesInEveryMode() {
        for (mode in StudyMode.Concrete) {
            assertEquals(Rating.AGAIN, ModeGrading.ratingFor(mode, VerdictKind.INCORRECT), "mode=$mode")
        }
    }

    @Test
    fun aPartiallyCorrectTypedAnswerIsHardNotWrong() {
        assertEquals(Rating.HARD, ModeGrading.ratingFor(StudyMode.TYPED, VerdictKind.PARTIAL))
        assertEquals(Rating.HARD, ModeGrading.ratingFor(StudyMode.TILES, VerdictKind.PARTIAL))
        assertEquals(Rating.HARD, ModeGrading.ratingFor(StudyMode.MCQ, VerdictKind.PARTIAL))
    }

    @Test
    fun difficultyRanksAreOrdered() {
        assertTrue(StudyMode.MCQ.difficultyRank < StudyMode.TILES.difficultyRank)
        assertTrue(StudyMode.TILES.difficultyRank < StudyMode.TYPED.difficultyRank)
    }

    @Test
    fun anOverrideIsGradedAsIfItHadBeenTyped() {
        assertEquals(Rating.EASY, ModeGrading.ratingForOverride(StudyMode.TYPED, userSaysCorrect = true))
        assertEquals(Rating.EASY, ModeGrading.ratingForOverride(StudyMode.MCQ, userSaysCorrect = true))
        assertEquals(Rating.AGAIN, ModeGrading.ratingForOverride(StudyMode.TYPED, userSaysCorrect = false))
        assertEquals(Rating.AGAIN, ModeGrading.ratingForOverride(StudyMode.FLASHCARD, userSaysCorrect = false))
    }
}
