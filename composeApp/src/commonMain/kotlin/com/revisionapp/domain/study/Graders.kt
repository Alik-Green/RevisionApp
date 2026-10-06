package com.revisionapp.domain.study

import com.revisionapp.domain.check.Verdict
import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.check.VerdictReason

/** Grades a tile answer: only an exact ordering is fully correct. */
object TileGrader {

    /** Fraction of tiles that must already be in the right place for PARTIAL. */
    const val PARTIAL_POSITION_THRESHOLD: Double = 0.6

    fun grade(solution: List<String>, chosen: List<String>): Verdict {
        if (chosen.isEmpty() || solution.isEmpty()) {
            return Verdict.incorrect(VerdictReason.EmptyInput)
        }
        if (chosen == solution) {
            return Verdict.correct(VerdictReason.TilePlacement(solution.size, solution.size))
        }

        val inPlace = solution.indices.count { index -> chosen.getOrNull(index) == solution[index] }
        val score = inPlace.toDouble() / solution.size
        // A wrong number of tiles means a decoy was used or a chunk was dropped.
        val wrongLength = chosen.size != solution.size
        val kind = when {
            !wrongLength && score >= PARTIAL_POSITION_THRESHOLD -> VerdictKind.PARTIAL
            else -> VerdictKind.INCORRECT
        }
        val capped = if (kind == VerdictKind.INCORRECT) minOf(score, Verdict.INCORRECT_SCORE_CAP) else score
        return Verdict(
            kind = kind,
            score = capped,
            matchedKeyPoints = emptyList(),
            missedKeyPoints = emptyList(),
            reason = VerdictReason.TilePlacement(inPlace, solution.size),
        )
    }
}

/** Grades a multiple-choice answer: right option or not. */
object McqGrader {

    fun grade(question: Question.MultipleChoice, selectedIndex: Int): Verdict {
        val reason = VerdictReason.McqSelection(selectedIndex, question.correctIndex)
        val chosen = question.options.getOrNull(selectedIndex)
        return if (chosen?.isCorrect == true) Verdict.correct(reason) else Verdict.incorrect(reason)
    }
}
