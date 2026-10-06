package com.revisionapp.domain.study

import com.revisionapp.domain.check.Verdict
import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.check.VerdictReason
import com.revisionapp.domain.model.AnswerType
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.CardId
import com.revisionapp.domain.model.KeyPoint
import com.revisionapp.domain.model.Mcq
import com.revisionapp.domain.model.StudyMode
import com.revisionapp.domain.model.TopicId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuestionDerivationTest {

    private val topicId = TopicId("builtin:pack:topic")

    private fun card(
        id: String,
        back: String,
        answerType: AnswerType = AnswerType.TEXT,
        keyPoints: List<KeyPoint> = emptyList(),
        tileAnswer: List<String>? = null,
        mcq: Mcq? = null,
    ): Card = Card(
        id = CardId(id),
        topicId = topicId,
        front = "Question about $id?",
        back = back,
        answerType = answerType,
        keyPoints = keyPoints,
        tileAnswer = tileAnswer,
        mcq = mcq,
    )

    private val shortCard = card("builtin:pack:topic:0001", "the centripetal force acts towards the centre")

    private val longCard = card(
        "builtin:pack:topic:0002",
        "this answer is far too long to be turned into a sensible set of word tiles " +
            "because it runs well past the token limit the brief allows",
    )

    private val siblings = listOf(
        card("builtin:pack:topic:0003", "angular velocity is constant"),
        card("builtin:pack:topic:0004", "the period of rotation"),
        card("builtin:pack:topic:0005", "friction provides the force"),
    )

    // ------------------------------------------------------------- tiles ---

    @Test
    fun aShortAnswerQualifiesForTiles() {
        val chunks = TileQuestionFactory.chunksFor(shortCard)

        assertNotNull(chunks)
        assertEquals(listOf("the", "centripetal", "force", "acts", "towards", "the", "centre"), chunks)
        assertTrue(TileQuestionFactory.isEligible(shortCard))
    }

    @Test
    fun aLongParagraphIsNeverTokenisedIntoTiles() {
        assertNull(TileQuestionFactory.chunksFor(longCard))
        assertFalse(TileQuestionFactory.isEligible(longCard))
        assertNull(TileQuestionFactory.create(longCard, siblings))
    }

    @Test
    fun aSingleTokenAnswerIsTooSmallToBeWorthTiling() {
        assertNull(TileQuestionFactory.chunksFor(card("c", "yes")))
    }

    @Test
    fun selfGradeCardsHaveNothingToBuild() {
        val selfGrade = card("c", "explain the reasoning", answerType = AnswerType.SELF_GRADE)
        assertNull(TileQuestionFactory.chunksFor(selfGrade))
    }

    @Test
    fun anAuthoredTileAnswerIsUsedAsIs() {
        val authored = card(
            id = "c",
            back = "the current is the same everywhere in a series circuit",
            tileAnswer = listOf("the current", "is the same", "everywhere", "in a series circuit"),
        )

        assertEquals(
            listOf("the current", "is the same", "everywhere", "in a series circuit"),
            TileQuestionFactory.chunksFor(authored),
        )
    }

    @Test
    fun anAuthoredTileAnswerWithTooManyChunksIsRejected() {
        val tooMany = card("c", "back", tileAnswer = (1..9).map { "chunk$it" })
        val tooFew = card("c", "back", tileAnswer = listOf("only one"))

        assertNull(TileQuestionFactory.chunksFor(tooMany))
        assertNull(TileQuestionFactory.chunksFor(tooFew))
    }

    @Test
    fun tilesContainTheAnswerPlusTwoDecoysAndCanAlwaysBeRebuilt() {
        val question = TileQuestionFactory.create(shortCard, siblings, Random(42))

        assertNotNull(question)
        assertEquals(shortCard.id, question.cardId)
        assertEquals(7 + TileQuestionFactory.DECOY_COUNT, question.tiles.size)
        assertEquals(TileQuestionFactory.DECOY_COUNT, question.decoyCount)
        assertEquals(7, question.solution.size)

        // Ids are handed out in solution order, so the answer is recoverable even
        // after shuffling.
        assertEquals(
            question.solution,
            question.tiles.filter { !it.isDecoy }.sortedBy { it.id }.map { it.text },
        )
        assertEquals(question.tiles.size, question.tiles.map { it.id }.toSet().size)
        // The decoys are not secretly part of the answer.
        val solutionText = question.solution.joinToString(" ")
        question.tiles.filter { it.isDecoy }.forEach {
            assertFalse(solutionText.contains(it.text), "decoy ${it.text} is in the solution")
        }
    }

    @Test
    fun aSeededShuffleIsReproducible() {
        val first = TileQuestionFactory.create(shortCard, siblings, Random(7))
        val second = TileQuestionFactory.create(shortCard, siblings, Random(7))

        assertEquals(first, second)
    }

    @Test
    fun tilesStillWorkWithoutAnyDecoySource() {
        val question = TileQuestionFactory.create(shortCard, emptyList(), Random(1))

        assertNotNull(question)
        assertEquals(7, question.tiles.size)
        assertEquals(0, question.decoyCount)
    }

    // ------------------------------------------------- tile grading ---

    @Test
    fun onlyAnExactOrderingIsCorrect() {
        val solution = listOf("a", "b", "c", "d", "e")

        val exact = TileGrader.grade(solution, solution)
        assertEquals(VerdictKind.CORRECT, exact.kind)
        assertIs<VerdictReason.TilePlacement>(exact.reason)
        assertEquals(5, (exact.reason as VerdictReason.TilePlacement).correctPositions)
    }

    @Test
    fun aNearMissIsPartial() {
        val solution = listOf("a", "b", "c", "d", "e")

        // Three of five tiles are in the right place: 0.6 exactly.
        val nearMiss = TileGrader.grade(solution, listOf("a", "b", "c", "e", "d"))
        assertEquals(VerdictKind.PARTIAL, nearMiss.kind)
        assertEquals(0.6, nearMiss.score, 1e-9)
    }

    @Test
    fun aScrambledOrderIsIncorrect() {
        val solution = listOf("a", "b", "c", "d", "e")
        val verdict = TileGrader.grade(solution, listOf("e", "d", "c", "b", "a"))

        assertEquals(VerdictKind.INCORRECT, verdict.kind)
        assertTrue(verdict.score <= Verdict.INCORRECT_SCORE_CAP)
    }

    @Test
    fun usingADecoyOrDroppingAChunkIsIncorrectEvenIfTheRestIsRight() {
        val solution = listOf("a", "b", "c", "d", "e")

        assertEquals(VerdictKind.INCORRECT, TileGrader.grade(solution, listOf("a", "b", "c", "d", "e", "x")).kind)
        assertEquals(VerdictKind.INCORRECT, TileGrader.grade(solution, listOf("a", "b", "c", "d")).kind)
    }

    @Test
    fun anEmptySelectionIsIncorrect() {
        val verdict = TileGrader.grade(listOf("a", "b"), emptyList())
        assertEquals(VerdictKind.INCORRECT, verdict.kind)
        assertEquals(VerdictReason.EmptyInput, verdict.reason)
    }

    // -------------------------------------------------------------- mcq ---

    @Test
    fun authoredDistractorsAreUsed() {
        val authored = card(
            id = "c",
            back = "centripetal force",
            mcq = Mcq("centripetal force", listOf("centrifugal force", "friction", "weight")),
        )

        val question = McqQuestionFactory.create(authored, emptyList(), Random(3))

        assertNotNull(question)
        assertEquals(4, question.options.size)
        assertEquals(1, question.options.count { it.isCorrect })
        assertEquals("centripetal force", question.correctText)
        assertEquals(
            setOf("centripetal force", "centrifugal force", "friction", "weight"),
            question.options.map { it.text }.toSet(),
        )
    }

    @Test
    fun unusableAuthoredDistractorsFallBackToSiblings() {
        val tooFew = card(
            id = "c",
            back = "centripetal force",
            mcq = Mcq("centripetal force", listOf("friction")),
        )
        val duplicated = card(
            id = "c",
            back = "centripetal force",
            mcq = Mcq("centripetal force", listOf("friction", "friction", "weight")),
        )

        assertNotNull(McqQuestionFactory.create(tooFew, siblings, Random(3)))
        assertNotNull(McqQuestionFactory.create(duplicated, siblings, Random(3)))
    }

    @Test
    fun distractorsAreGeneratedFromSameTypeSiblings() {
        val question = McqQuestionFactory.create(shortCard, siblings, Random(11))

        assertNotNull(question)
        assertEquals(4, question.options.size)
        assertEquals(1, question.options.count { it.isCorrect })
        assertEquals(shortCard.back, question.correctText)
    }

    @Test
    fun fewerThanThreePlausibleSiblingsMeansNoMcq() {
        val thin = siblings.take(2)

        assertNull(McqQuestionFactory.create(shortCard, thin, Random(11)))
        assertFalse(McqQuestionFactory.isEligible(shortCard, thin))
    }

    @Test
    fun siblingsOfADifferentAnswerTypeDoNotCount() {
        val numericSiblings = listOf(
            card("n1", "12", answerType = AnswerType.NUMERIC),
            card("n2", "3.5", answerType = AnswerType.NUMERIC),
            card("n3", "0.25", answerType = AnswerType.NUMERIC),
        )

        assertNull(McqQuestionFactory.create(shortCard, numericSiblings, Random(11)))
        assertNotNull(McqQuestionFactory.create(card("n0", "9.81", answerType = AnswerType.NUMERIC), numericSiblings, Random(11)))
    }

    @Test
    fun aSiblingWithTheSameAnswerIsNotADistractor() {
        val duplicates = listOf(
            card("d1", shortCard.back),
            card("d2", shortCard.back),
            card("d3", shortCard.back),
        )

        assertNull(McqQuestionFactory.create(shortCard, duplicates, Random(11)))
    }

    @Test
    fun mcqGradingIsAllOrNothing() {
        val question = McqQuestionFactory.create(shortCard, siblings, Random(5))

        assertNotNull(question)
        val correct = McqGrader.grade(question, question.correctIndex)
        assertEquals(VerdictKind.CORRECT, correct.kind)
        assertIs<VerdictReason.McqSelection>(correct.reason)

        val wrongIndex = (question.correctIndex + 1) % question.options.size
        assertEquals(VerdictKind.INCORRECT, McqGrader.grade(question, wrongIndex).kind)
        assertEquals(VerdictKind.INCORRECT, McqGrader.grade(question, 99).kind)
    }

    // ------------------------------------------------- mode selection ---

    @Test
    fun theBestFitModeFollowsTheAnswerType() {
        assertEquals(
            StudyMode.FLASHCARD,
            ModeSelector.bestFit(card("c", "explain", answerType = AnswerType.SELF_GRADE), true, true),
        )
        assertEquals(
            StudyMode.TYPED,
            ModeSelector.bestFit(card("c", "9.81", answerType = AnswerType.NUMERIC), true, true),
        )
        assertEquals(
            StudyMode.TYPED,
            ModeSelector.bestFit(card("c", "x^2-1", answerType = AnswerType.EXPRESSION), true, true),
        )
        assertEquals(
            StudyMode.TYPED,
            ModeSelector.bestFit(card("c", "back", keyPoints = listOf(KeyPoint("back"))), true, true),
        )
    }

    @Test
    fun textWithoutKeyPointsFallsBackToTilesThenMcqThenFlashcards() {
        val plain = card("c", "a short answer")

        assertEquals(StudyMode.TILES, ModeSelector.bestFit(plain, true, true))
        assertEquals(StudyMode.MCQ, ModeSelector.bestFit(plain, false, true))
        assertEquals(StudyMode.FLASHCARD, ModeSelector.bestFit(plain, false, false))
    }

    @Test
    fun masteryEscalatesTheModeUpTheLadder() {
        val plain = card("c", "a short answer")

        assertEquals(StudyMode.TILES, ModeSelector.bestFit(plain, true, true, 0))
        assertEquals(StudyMode.TYPED, ModeSelector.bestFit(plain, true, true, 1))
        assertEquals(StudyMode.TYPED, ModeSelector.bestFit(plain, true, true, 9))

        assertEquals(StudyMode.MCQ, ModeSelector.bestFit(plain, false, true, 0))
        assertEquals(StudyMode.TYPED, ModeSelector.bestFit(plain, false, true, 1))
    }

    @Test
    fun theLadderOnlyContainsModesTheCardCanActuallyUse() {
        assertEquals(
            listOf(StudyMode.TYPED),
            ModeSelector.ladder(tilesAvailable = false, mcqAvailable = false),
        )
        assertEquals(
            listOf(
                StudyMode.MCQ,
                StudyMode.TILES,
                StudyMode.TYPED,
            ),
            ModeSelector.ladder(tilesAvailable = true, mcqAvailable = true),
        )
    }

    @Test
    fun mixedModeProducesTheBestFitQuestion() {
        val question = QuestionFactory.create(shortCard, StudyMode.MIXED, siblings, Random(1))

        assertIs<Question.Tiles>(question)
    }

    @Test
    fun mixedModeUsesTypedForCardsWithKeyPoints() {
        val withKeyPoints = card("c", "a short answer", keyPoints = listOf(KeyPoint("short", mustInclude = true)))
        val question = QuestionFactory.create(withKeyPoints, StudyMode.MIXED, siblings)

        assertIs<Question.Typed>(question)
    }

    @Test
    fun mixedModeUsesFlashcardsForSelfGrading() {
        val selfGrade = card("c", "explain the reasoning", answerType = AnswerType.SELF_GRADE)
        val question = QuestionFactory.create(selfGrade, StudyMode.MIXED, siblings)

        assertIs<Question.Flashcard>(question)
    }

    @Test
    fun aModeThatCannotShowTheCardProducesNothingSoTheSessionSkipsIt() {
        assertNull(QuestionFactory.create(longCard, StudyMode.TILES, siblings))
        assertNull(QuestionFactory.create(shortCard, StudyMode.MCQ, siblings.take(1)))
        assertNotNull(QuestionFactory.create(shortCard, StudyMode.FLASHCARD, siblings))
        assertNotNull(QuestionFactory.create(longCard, StudyMode.TYPED, siblings))
    }

    @Test
    fun everyQuestionCarriesItsCardId() {
        val questions = listOf(
            QuestionFactory.create(shortCard, StudyMode.FLASHCARD, siblings),
            QuestionFactory.create(shortCard, StudyMode.TYPED, siblings),
            QuestionFactory.create(shortCard, StudyMode.TILES, siblings, random = Random(2)),
            QuestionFactory.create(shortCard, StudyMode.MCQ, siblings, random = Random(2)),
        )
        questions.forEach { assertEquals(shortCard.id, it?.cardId) }
    }
}
