# V2 course content

V2 course JSON is stored in the repository-root `content-v2/` directory, separate
from the legacy `content` branch's card packs. The app fetches the manifest and
its referenced course files from a raw GitHub URL at launch, then saves the last
valid catalog locally for offline use. Updating these JSON files on the configured
branch does not require rebuilding the app.

The default URL points to this session's pinned branch:

`https://raw.githubusercontent.com/Alik-Green/RevisionApp/arena/299725bb-revisionapp/content-v2`

Settings > V2 course content lets the user change the base URL. It should point to
the directory containing `manifest.json`; manifest file references are relative to
that directory. This allows a dedicated content branch or repository to be used
later without changing the app, while keeping the legacy card-pack URL separate.

## Shape

- `manifest.json` declares schema version 2 and points to individual course JSON
  files such as `courses/tmua.json`.
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

The loader validates identifiers and references before accepting a download. A
failed download or invalid catalog leaves the last valid local catalog in place.
The bundled TMUA and Further Maths examples are small product prototypes, not
verified exam content. Review them before treating either course as authoritative.
