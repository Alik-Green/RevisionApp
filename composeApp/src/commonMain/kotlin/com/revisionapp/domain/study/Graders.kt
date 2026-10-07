package com.revisionapp.domain.study

import com.revisionapp.domain.check.Verdict
import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.check.VerdictReason

/** Grades a tile answer: only an exact ordering is fully correct. */
object TileGrader {

    /** Fraction of the answer that must be in the right relative order for PARTIAL. */
    const val PARTIAL_POSITION_THRESHOLD: Double = 0.6

    /**
     * Length of the longest common subsequence of [chosen] and [solution]: how many
     * chosen tiles appear in the solution in the right relative order. Two rows and
     * a rolling array, so a tile answer costs O(n*m) time and O(m) space.
     */
    private fun longestCommonSubsequence(chosen: List<String>, solution: List<String>): Int {
        val previous = IntArray(solution.size + 1)
        val current = IntArray(solution.size + 1)
        for (i in 1..chosen.size) {
            for (j in 1..solution.size) {
                current[j] = if (chosen[i - 1] == solution[j - 1]) {
                    previous[j - 1] + 1
                } else {
                    maxOf(previous[j], current[j - 1])
                }
            }
            for (j in 0..solution.size) {
                previous[j] = current[j]
                current[j] = 0
            }
        }
        return previous[solution.size]
    }

    fun grade(solution: List<String>, chosen: List<String>): Verdict {
        if (chosen.isEmpty() || solution.isEmpty()) {
            return Verdict.incorrect(VerdictReason.EmptyInput)
        }
        if (chosen == solution) {
            return Verdict.correct(VerdictReason.TilePlacement(solution.size, solution.size))
        }

        // Scored by longest common subsequence, not by absolute position. Dropping
        // one leading tile used to shift every tile after it out of place and score
        // 0%, which reads as "nothing right" for an answer that was right apart from
        // one word. LCS counts the tiles that are in the right *relative* order, and
        // dividing by the longer of the two lists charges for a dropped chunk and for
        // a decoy that was used, without either one zeroing the answer.
        val inOrder = longestCommonSubsequence(chosen, solution)
        val score = inOrder.toDouble() / maxOf(chosen.size, solution.size)
        val kind = if (score >= PARTIAL_POSITION_THRESHOLD) VerdictKind.PARTIAL else VerdictKind.INCORRECT
        val capped = if (kind == VerdictKind.INCORRECT) minOf(score, Verdict.INCORRECT_SCORE_CAP) else score
        return Verdict(
            kind = kind,
            score = capped,
            matchedKeyPoints = emptyList(),
            missedKeyPoints = emptyList(),
            reason = VerdictReason.TilePlacement(inOrder, solution.size),
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
