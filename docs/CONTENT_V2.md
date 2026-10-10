# V2 course content

V2 course JSON is stored in the repository-root `content-v2/` directory, separate
from the legacy `content` branch's card packs. The Course Store fetches only
`manifest.json` when opened. It fetches an individual course file only after the
learner chooses Download, then caches that course for offline study. App startup
does not fetch the manifest or download course files.

The app reads from this repository's raw GitHub source:

`https://raw.githubusercontent.com/Alik-Green/RevisionApp/arena/299725bb-revisionapp/content-v2`

The source is fixed in the app and is not user-editable. This checkout is pinned
to its app branch, so the requested dedicated content branch has not been created;
the separate `content-v2/` folder still keeps the new course format distinct from
the legacy card packs. Course files are referenced relative to this directory.

## Shape

- `manifest.json` declares schema version 2 and lists course ids, names,
  descriptions and individual JSON file paths.
- A course contains an ordered list of sections, ordered lessons and a reusable
  question bank.
- Sections define the topic-shaped path. Each section lists lesson IDs in path
  order and topic IDs it introduces.
- A lesson lists `questionIds` in the exact order the learner sees them. The same
  question ID may appear in a later lesson, which is how spiral review revisits an
  earlier topic without copying the question.
- Each question has its own `topicId`, answer type and explanation. Text-input
  questions also have an `answerInstruction` that tells the learner what form to
  enter; `acceptedAnswers` lists reasonable variants. A question answered
  incorrectly is appended once to that lesson's queue for a single retry.

The loader validates the manifest before showing its course list, then validates
each selected course before saving it. A failed download leaves the existing local
course cache in place. The TMUA and Further Maths examples are small product
prototypes, not verified exam content. Review them before treating either course
as authoritative.
