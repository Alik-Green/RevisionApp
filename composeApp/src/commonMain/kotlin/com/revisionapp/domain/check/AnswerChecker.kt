package com.revisionapp.domain.check

import com.revisionapp.domain.model.Card

/**
 * Judges a typed answer against a card. Pure Kotlin, no framework imports, and
 * fully unit-tested — see `commonTest/.../check`.
 */
interface AnswerChecker {
    fun check(card: Card, input: String): Verdict
}

/**
 * Optional plug-in point for an on-device embedding model.
 *
 * Returning null means "no opinion", which is what the shipped implementation
 * does: the brief explicitly rules out bundling an ML model now, but the seam is
 * here so one can be added without touching the checker or the UI.
 */
interface SemanticScorer {
    /** A 0..1 similarity, or null when this scorer declines to judge. */
    fun score(modelAnswer: String, userAnswer: String): Double?
}

/** The default [SemanticScorer]: never contributes. */
object NoSemanticScorer : SemanticScorer {
    override fun score(modelAnswer: String, userAnswer: String): Double? = null
}

/**
 * The corpus the TF-IDF fallback computes inverse document frequencies over.
 * Implemented by the card repository, which sees every card in the database.
 */
interface TermCorpus {
    /** How many documents the frequencies were counted over. */
    val documentCount: Int

    /** In how many documents [term] appears (already stemmed). */
    fun documentFrequency(term: String): Int
}

/**
 * A [TermCorpus] built from a fixed list of documents. Used by the repository
 * and directly by tests; `documentCount == 0` makes the scorer fall back to
 * plain term frequency.
 */
class InMemoryTermCorpus(documents: List<String>) : TermCorpus {

    private val frequencies: Map<String, Int> = documents
        .map { TextNormaliser.matchTokenSet(it) }
        .fold(mutableMapOf<String, Int>()) { accumulator, tokens ->
            for (token in tokens) accumulator[token] = (accumulator[token] ?: 0) + 1
            accumulator
        }

    override val documentCount: Int = documents.size

    override fun documentFrequency(term: String): Int = frequencies[term] ?: 0

    companion object {
        val Empty: InMemoryTermCorpus = InMemoryTermCorpus(emptyList())
    }
}
