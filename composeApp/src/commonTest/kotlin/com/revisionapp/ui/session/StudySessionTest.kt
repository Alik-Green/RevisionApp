package com.revisionapp.ui.session

import com.revisionapp.domain.check.AnswerChecker
import com.revisionapp.domain.check.Verdict
import com.revisionapp.domain.check.VerdictKind
import com.revisionapp.domain.check.VerdictReason
import com.revisionapp.domain.model.Card
import com.revisionapp.domain.model.CardId
import com.revisionapp.domain.model.StudyMode
import com.revisionapp.domain.model.TopicId
import com.revisionapp.domain.repository.ReviewEntry
import com.revisionapp.domain.srs.FsrsScheduler
import com.revisionapp.domain.srs.Rating
import com.revisionapp.domain.srs.ScheduleState
import com.revisionapp.domain.study.McqOption
import com.revisionapp.domain.study.Question
import com.revisionapp.domain.study.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The claim the whole design rests on: all four modes feed one schedule.
 *
 * These tests drive [StudySession] through its events and assert on what reaches
 * the two callbacks a real app wires to the database — the schedule write and the
 * review log entry — rather than on any UI.
 */
class StudySessionTest {

    private val now: Instant = Instant.fromEpochMilliseconds(1_800_000_000_000)
    private val cardId = CardId("builtin:test:topic:0001")
    private val secondCardId = CardId("builtin:test:topic:0002")
    private val saved = mutableListOf<Pair<CardId, ScheduleState>>()
    private val entries = mutableListOf<ReviewEntry>()
    private val learned = mutableListOf<Pair<CardId, String>>()

    private fun card(id: CardId = cardId, back: String = "model answer"): Card = Card(
        id = id,
        topicId = TopicId("builtin:test:topic"),
        front = "question",
        back = back,
    )

    private fun item(mode: StudyMode, question: Question, id: CardId = cardId): SessionCard =
        SessionCard(card(id), mode, question, ScheduleState.new(now))

    private fun mcqQuestion(): Question.MultipleChoice = Question.MultipleChoice(
        cardId = cardId,
        options = listOf("model answer", "one", "two", "three").mapIndexed { index, text ->
            McqOption(index = index, text = text, isCorrect = index == 0)
        },
    )

    private fun tileQuestion(): Question.Tiles = Question.Tiles(
        cardId = cardId,
        tiles = listOf(Tile(1, "model", false), Tile(2, "answer", false), Tile(3, "decoy", true)),
        solution = listOf("model", "answer"),
    )

    private fun session(
        items: List<SessionCard>,
        verdict: Verdict = Verdict.correct(VerdictReason.ExactMatch),
    ): StudySession {
        saved.clear()
        entries.clear()
        learned.clear()
        return StudySession(
            items = items,
            checker = StubChecker(verdict),
            scheduler = FsrsScheduler(),
            clock = FixedClock(now),
            persistSchedule = { id, state -> saved.add(id to state) },
            recordReview = { entry -> entries.add(entry) },
            onLearn = { id, answer -> learned.add(id to answer) },
        )
    }

    @Test
    fun aCorrectMcqAnswerWritesOneScheduleUpdateAndOneLogEntry() {
        val session = session(listOf(item(StudyMode.MCQ, mcqQuestion())))

        session.onEvent(SessionEvent.ChooseOption(0))
        assertIs<SessionState.Reviewing>(session.state)
        session.onEvent(SessionEvent.Next)

        assertEquals(1, saved.size)
        assertEquals(cardId, saved.single().first)
        assertTrue(saved.single().second.dueAt > now)

        val entry = entries.single()
        assertEquals(StudyMode.MCQ, entry.mode)
        assertEquals(Rating.GOOD, entry.rating)
        assertEquals(VerdictKind.CORRECT, entry.verdict)
        assertTrue(entry.correct)
        assertIs<SessionState.Finished>(session.state)
    }

    @Test
    fun aCorrectTypedAnswerIsWorthMoreThanACorrectMcqAnswer() {
        val typed = session(listOf(item(StudyMode.TYPED, Question.Typed(cardId))))
        typed.onEvent(SessionEvent.Type("model answer"))
        typed.onEvent(SessionEvent.SubmitText)
        typed.onEvent(SessionEvent.Next)
        assertEquals(Rating.EASY, entries.single().rating)

        val mcq = session(listOf(item(StudyMode.MCQ, mcqQuestion())))
        mcq.onEvent(SessionEvent.ChooseOption(0))
        mcq.onEvent(SessionEvent.Next)
        assertEquals(Rating.GOOD, entries.single().rating)
    }

    @Test
    fun flashcardRatingsComeFromTheUserAndCarryNoVerdict() {
        val session = session(listOf(item(StudyMode.FLASHCARD, Question.Flashcard(cardId))))

        session.onEvent(SessionEvent.Reveal)
        session.onEvent(SessionEvent.Rate(Rating.HARD))

        assertEquals(Rating.HARD, entries.single().rating)
        assertNull(entries.single().verdict)
        assertTrue(entries.single().correct)
        assertEquals(1, saved.size)
    }

    @Test
    fun aWrongFlashcardRatingCountsAsIncorrectInTheSummary() {
        val session = session(listOf(item(StudyMode.FLASHCARD, Question.Flashcard(cardId))))

        session.onEvent(SessionEvent.Reveal)
        session.onEvent(SessionEvent.Rate(Rating.AGAIN))

        assertFalse(entries.single().correct)
        val finished = assertIs<SessionState.Finished>(session.state)
        assertEquals(1, finished.summary.incorrect)
        assertEquals(0, finished.summary.correct)
    }

    @Test
    fun tileAnswersFeedTheSameSchedule() {
        val session = session(listOf(item(StudyMode.TILES, tileQuestion())))

        session.onEvent(SessionEvent.TapTile(1))
        session.onEvent(SessionEvent.TapTile(2))
        session.onEvent(SessionEvent.SubmitTiles)

        val reviewing = assertIs<SessionState.Reviewing>(session.state)
        assertEquals(VerdictKind.CORRECT, reviewing.verdict.kind)

        session.onEvent(SessionEvent.Next)
        assertEquals(Rating.GOOD, entries.single().rating)
        assertEquals(1, saved.size)
    }

    @Test
    fun aDecoyTileMakesTheAnswerWrong() {
        val session = session(listOf(item(StudyMode.TILES, tileQuestion())))

        session.onEvent(SessionEvent.TapTile(1))
        session.onEvent(SessionEvent.TapTile(3))
        session.onEvent(SessionEvent.SubmitTiles)
        session.onEvent(SessionEvent.Next)

        assertEquals(VerdictKind.INCORRECT, entries.single().verdict)
        assertEquals(Rating.AGAIN, entries.single().rating)
    }

    @Test
    fun anOverrideReplacesTheAutomaticVerdictWithoutRewritingIt() {
        val session = session(
            items = listOf(item(StudyMode.TYPED, Question.Typed(cardId))),
            verdict = Verdict.incorrect(VerdictReason.EmptyInput),
        )

        session.onEvent(SessionEvent.Type(""))
        session.onEvent(SessionEvent.SubmitText)
        session.onEvent(SessionEvent.Override(true))

        val entry = entries.single()
        // The log keeps what the checker said and what the user said separately.
        assertEquals(VerdictKind.INCORRECT, entry.verdict)
        assertEquals(Rating.EASY, entry.rating)
        assertTrue(entry.correct)
    }

    @Test
    fun anOverrideOnATypedAnswerTeachesTheChecker() {
        val session = session(
            items = listOf(item(StudyMode.TYPED, Question.Typed(cardId))),
            verdict = Verdict.incorrect(VerdictReason.EmptyInput),
        )

        session.onEvent(SessionEvent.Type("the force unit"))
        session.onEvent(SessionEvent.SubmitText)
        assertEquals(0, learned.size, "nothing is learned until the user disputes the verdict")

        session.onEvent(SessionEvent.Override(true))

        assertEquals(listOf(cardId to "the force unit"), learned)
    }

    @Test
    fun sayingYouWereWrongTeachesNothing() {
        val session = session(
            items = listOf(item(StudyMode.TYPED, Question.Typed(cardId))),
            verdict = Verdict.correct(VerdictReason.ExactMatch),
        )

        session.onEvent(SessionEvent.Type("the force unit"))
        session.onEvent(SessionEvent.SubmitText)
        session.onEvent(SessionEvent.Override(false))

        assertEquals(0, learned.size)
    }

    @Test
    fun anOverrideOnATapAnswerTeachesNothing() {
        // A multiple-choice verdict is not in doubt, so an override there carries no
        // information about wording and must not pollute the learned answers.
        val session = session(listOf(item(StudyMode.MCQ, mcqQuestion())))

        session.onEvent(SessionEvent.ChooseOption(1))
        session.onEvent(SessionEvent.Override(true))

        assertEquals(0, learned.size)
    }

    @Test
    fun aSelfGradeVerdictCannotBeSkippedWithNext() {
        val session = session(
            items = listOf(item(StudyMode.TYPED, Question.Typed(cardId))),
            verdict = Verdict.selfGrade(),
        )

        session.onEvent(SessionEvent.SubmitText)
        session.onEvent(SessionEvent.Next)

        assertIs<SessionState.Reviewing>(session.state)
        assertEquals(0, entries.size)
        assertEquals(0, saved.size)

        session.onEvent(SessionEvent.Override(false))
        assertEquals(Rating.AGAIN, entries.single().rating)
        assertFalse(entries.single().correct)
    }

    @Test
    fun theSummaryCountsEveryCardAndSavesEachOne() {
        val session = session(
            listOf(
                item(StudyMode.MCQ, mcqQuestion()),
                item(StudyMode.TYPED, Question.Typed(secondCardId), secondCardId),
            ),
        )

        session.onEvent(SessionEvent.ChooseOption(0))
        session.onEvent(SessionEvent.Next)
        session.onEvent(SessionEvent.Type("model answer"))
        session.onEvent(SessionEvent.SubmitText)
        session.onEvent(SessionEvent.Next)

        val finished = assertIs<SessionState.Finished>(session.state)
        assertEquals(2, finished.summary.reviewed)
        assertEquals(2, finished.summary.correct)
        assertEquals(1.0, finished.summary.accuracy)
        assertEquals(setOf(cardId, secondCardId), saved.map { it.first }.toSet())
    }

    @Test
    fun anEmptyQueueProducesAnInertSession() {
        val session = session(emptyList())

        assertIs<SessionState.Empty>(session.state)
        session.onEvent(SessionEvent.Next)
        session.onEvent(SessionEvent.Rate(Rating.EASY))

        assertIs<SessionState.Empty>(session.state)
        assertEquals(0, saved.size)
        assertEquals(0, entries.size)
    }
}

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private class StubChecker(private val verdict: Verdict) : AnswerChecker {
    override fun check(card: Card, input: String): Verdict = verdict
}
