package com.revisionapp.domain.check

import kotlin.math.abs

/**
 * Levenshtein distance with a length-scaled acceptance threshold.
 *
 * The threshold is deliberately tight for short tokens: allowing a single edit
 * on a four-letter word would make `increas` and `decreas` interchangeable,
 * which is exactly the failure mode [NegationGuard] exists to prevent.
 */
object EditDistance {

    /** Edits tolerated for a token of [length] (uses the longer of the pair). */
    fun thresholdFor(length: Int): Int = when {
        length <= 4 -> 0
        length <= 7 -> 1
        else -> 2
    }

    fun isClose(a: String, b: String): Boolean {
        if (a == b) return true
        val threshold = thresholdFor(maxOf(a.length, b.length))
        if (threshold == 0) return false
        if (abs(a.length - b.length) > threshold) return false
        return between(a, b) <= threshold
    }

    fun between(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            val left = a[i - 1]
            for (j in 1..b.length) {
                val substitutionCost = if (left == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + substitutionCost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
