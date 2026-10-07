
# TMUA

Original practice cards for the Test of Mathematics for University Admission.
Every question here was written for this pack: nothing is taken from a published
past paper, worked solution or preparation book, and every card is marked
`reviewStatus: AI_UNREVIEWED`.

## Structure

TMUA > Paper 1 (Mathematical Thinking), Paper 2 (Mathematical Reasoning) and
Definitions and concepts. 74 cards: 26 on Paper 1, 26 on Paper 2 and 22
definitions.

- **Paper 1** questions are multiple choice on the A Level mathematics the test
  assumes, weighted towards the topics that dominate the released papers:
  algebra and functions, sequences and series, integration, trigonometry,
  differentiation, coordinate geometry, logarithms, graph sketching and
  transformations.
- **Paper 2** questions are the logic and proof half: necessary against
  sufficient, negating quantified statements including nested quantifiers,
  contrapositive and equivalence, inclusive or, vacuous truth, disproof by
  counterexample, choosing a proof method, and a run of cards built on finding
  the invalid step in a written argument.
- **Definitions and concepts** are short cards for the vocabulary both papers
  need, covering the logic terms and the Paper 1 technical vocabulary the harder
  questions lean on.

Every question has a worked explanation. Most say what each distractor comes
from, because the fastest way to improve on this test is to recognise the trap
before falling into it.

## Difficulty calibration

The questions here are deliberately harder than a naive reading of the syllabus
would suggest, and that is a considered choice rather than an accident.

Two separate lines of evidence point the same way:

1. **The released papers were already hardening.** A classification of all 320
   questions in the sixteen published papers, 2016 to 2023, shows the share of
   high-difficulty multi-step questions growing year on year after 2019. On
   Paper 2 specifically, the reasoning topics - logic of arguments,
   mathematical proof and errors in proofs - rose from about 39% of the paper
   across 2016-2019 to about 49% across 2020-2023.
2. **The October 2024 sitting extended that trajectory.** The test moved to
   computer-based delivery via Pearson VUE in October 2024 and no paper from the
   computer-based era has been released. Candidates consistently reported a
   Paper 1 with almost no routine opening questions, difficulty that no longer
   rises through the paper, and questions requiring the method to be found
   rather than recognised - several compared most of the paper to the final
   section of the released papers. The most consistently reported topic
   emphases were circle geometry including the alternate segment theorem, graph
   sketching and root-counting as a parameter varies, demanding surd
   manipulation, integration in unfamiliar surroundings, and on Paper 2 heavy
   use of necessary and sufficient conditions and negation of statements.

What did **not** change is the specification. UAT UK states that the content
specification and question style are unchanged, and nothing above requires
mathematics outside it. What moved is the difficulty mix and the packaging:
fewer template questions with obvious entry points, and more questions that
combine two or three elementary topics into an unfamiliar configuration.

This pack is built to that mix. Concretely, a question here is expected to:

- combine at least two topics, so that no single formula finishes it;
- require interpretation before any algebra can start;
- have no reliable difficulty ramp, so position in the file tells you nothing -
  practise shuffling a session rather than working through in order;
- make the distractors near-misses of the correct method, not obviously wrong
  numbers.

Roughly half the Paper 1 and Paper 2 cards are tagged `difficulty = Hard` and
the rest `difficulty = Very hard`. No card is tagged below Hard: if you want
easier material, the 2016 to 2023 released papers remain the right resource, and
their last third is the best public proxy for the current Paper 1.

## Two things to know before using this pack

Real TMUA questions offer **five** options, A to E. The app's multiple-choice
mode is fixed at four - one correct answer plus three authored distractors - so
each question here has three distractors rather than four. The extra option in a
real paper is usually another near-miss of the same kind.

Scoring also changed for 2024/25. There is now a single score from 1.0 to 9.0
rather than one per paper plus an overall, recentred so a typical candidate
scores about 4.5, and UAT UK states explicitly that post-2024 scores are not
directly comparable with earlier ones. Do not read a score on a released paper
as a prediction of the current scale.

`"structureVerified": false`. The topic split and the difficulty calibration
above were built from published analyses of the released papers and from
candidate reports about the computer-based sittings, not from the published
specification, which was not reachable from the sandbox this content was written
in. So:

- the split of topics between Paper 1 and Paper 2 follows the published
  description of the two papers but has not been checked against a current
  specification;
- no `board` tag is used, because the awarding body was not verified here;
- the relative weight given to each Paper 1 topic follows the historical
  frequency data for 2016 to 2023, which is the best available signal but is not
  a prediction of any future paper;
- the claim that the test has hardened is well evidenced but rests on candidate
  reports for the computer-based era, which are inherently partial - different
  candidates sit different equated versions, so individual recollections
  disagree;
- `specRef` names the paper and the skill rather than quoting a clause number.

## Tagging

`subject = TMUA`, `paper = 1` or `paper = 2`, and `difficulty = Hard` or
`Very hard`. The definitions cards carry both paper tags, since that vocabulary
is used in either paper, and no difficulty tag, since they are recall rather
than problem solving. Tags inside a group are OR-ed together by the app and
groups are AND-ed, so selecting `paper = 1` with `difficulty = Very hard` gives a
session of the hardest Paper 1 questions, while selecting both papers gives the
whole test.
