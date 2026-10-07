package com.revisionapp.domain.check

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TfIdfTest {

    private val corpus = InMemoryTermCorpus(
        listOf(
            "the force acts",
            "the mass is constant",
            "the acceleration changes",
            "centripetal force acts towards the centre",
        ),
    )

    @Test
    fun theCorpusCountsDocumentsNotOccurrences() {
        assertEquals(4, corpus.documentCount)
        assertEquals(4, corpus.documentFrequency("the"))
        assertEquals(2, corpus.documentFrequency("forc"))
        assertEquals(1, corpus.documentFrequency("centripetal"))
        assertEquals(0, corpus.documentFrequency("zzz"))
    }

    @Test
    fun aDistinctiveSharedTermScoresHigherThanACommonOne() {
        val scorer = TfIdfScorer(corpus)

        val distinctive = scorer.cosine("centripetal", "centripetal force")
        val common = scorer.cosine("the", "the force")

        assertTrue(
            distinctive > common,
            "a rare shared term should carry more signal: distinctive=$distinctive common=$common",
        )
    }

    @Test
    fun withNoCorpusItDegradesToPlainCosineSimilarity() {
        val scorer = TfIdfScorer(null)

        assertEquals(1.0, scorer.cosine("force acts", "force acts"), 1e-9)
        assertEquals(0.0, scorer.cosine("", "force acts"), 1e-9)
        assertEquals(0.0, scorer.cosine("force", "banana"), 1e-9)
    }

    @Test
    fun similarityIsAlwaysBetweenZeroAndOne() {
        val scorer = TfIdfScorer(corpus)
        val value = scorer.cosine("the force acts towards the centre", "force acts away")
        assertTrue(value in 0.0..1.0, "cosine out of range: $value")
    }

    @Test
    fun repeatedTermsIncreaseTermFrequencyButStayBounded() {
        val scorer = TfIdfScorer(null)
        val once = scorer.cosine("force", "force acts")
        val twice = scorer.cosine("force force", "force acts")
        assertTrue(once in 0.0..1.0 && twice in 0.0..1.0)
    }

    @Test
    fun anEmptyCorpusBehavesLikeNoCorpus() {
        assertEquals(0, InMemoryTermCorpus.Empty.documentCount)
        val scorer = TfIdfScorer(InMemoryTermCorpus.Empty)
        assertEquals(1.0, scorer.cosine("force acts", "acts force"), 1e-9)
    }
}
