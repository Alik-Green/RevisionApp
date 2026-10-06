package com.revisionapp.domain.check

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * TF-IDF cosine similarity between two pieces of text.
 *
 * This is the *fallback* signal, used only for cards that have no key points:
 * there is nothing structured to check against, so the best available evidence is
 * "how much of the model answer's vocabulary did the answer use, weighted by how
 * distinctive each word is across the whole card corpus".
 *
 * Inverse document frequencies come from [TermCorpus]; with no corpus (or an
 * empty one) every term gets an IDF of 1 and this degrades to plain cosine
 * similarity over stemmed term frequencies.
 */
class TfIdfScorer(private val corpus: TermCorpus? = null) {

    fun cosine(left: String, right: String): Double {
        val leftTokens = TextNormaliser.matchTokens(left)
        val rightTokens = TextNormaliser.matchTokens(right)
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0.0

        val leftVector = vector(leftTokens)
        val rightVector = vector(rightTokens)

        val terms = LinkedHashSet<String>(leftVector.keys)
        terms.addAll(rightVector.keys)

        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0
        for (term in terms) {
            val x = leftVector[term] ?: 0.0
            val y = rightVector[term] ?: 0.0
            dot += x * y
            leftNorm += x * x
            rightNorm += y * y
        }
        if (leftNorm <= 0.0 || rightNorm <= 0.0) return 0.0
        return dot / (sqrt(leftNorm) * sqrt(rightNorm))
    }

    private fun vector(tokens: List<String>): Map<String, Double> {
        val counts = HashMap<String, Int>(tokens.size)
        for (token in tokens) counts[token] = (counts[token] ?: 0) + 1

        val vector = HashMap<String, Double>(counts.size)
        for ((term, count) in counts) vector[term] = count * inverseDocumentFrequency(term)
        return vector
    }

    private fun inverseDocumentFrequency(term: String): Double {
        val source = corpus ?: return 1.0
        val documents = source.documentCount
        if (documents <= 0) return 1.0
        val frequency = source.documentFrequency(term)
        // Smoothed so that a term appearing in every document still contributes.
        return ln((documents + 1.0) / (frequency + 1.0)) + 1.0
    }
}
