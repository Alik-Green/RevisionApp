# V2 course content

The first V2 sample is bundled under
`composeApp/src/commonMain/composeResources/files/content-v2/`. It is deliberately
separate from the legacy `content` branch packs and does not rewrite or migrate
those cards.

## Shape

- `manifest.json` declares schema version 2 and points to individual course JSON
  files.
- A course contains an ordered list of sections, ordered lessons, and a reusable
  question bank.
- Sections define the topic-shaped path. Each section lists lesson IDs in path
  order and topic IDs it introduces.
- A lesson lists `questionIds` in the exact order the learner sees them. The same
  question ID may appear in a later lesson, which is how spiral review revisits an
  earlier topic without copying the question.
- Each question has its own `topicId` and an answer type. The initial pilot supports
  `multiple_choice` (stable option IDs plus a correct option ID) and `text_input`
  (accepted answer variants). Explanations are shown after each answer.

The app validates identifiers and references before showing a path. The bundled
TMUA and Further Maths examples are small product prototypes, not verified exam
content. Review them before treating either course as authoritative.

The Arena session is pinned to `arena/299725bb-revisionapp`, so this pilot is
stored as an isolated V2 content directory on that branch rather than switching
or publishing a separate Git branch. The existing remote `content` branch remains
unchanged.
