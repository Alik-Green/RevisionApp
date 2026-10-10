# V2 course content

V2 course JSON is separate from the legacy `content` branch's card packs. It lives
only on the content-only `course-content` branch, under `content-v2/`. That branch
must contain course content, not app code or legacy packs. The app's source is fixed
to:

`https://raw.githubusercontent.com/Alik-Green/RevisionApp/course-content/content-v2`

A fresh install has no V2 courses. Startup does not request the V2 manifest or any
course file. When a learner opens **Study > Store**, the app fetches the manifest
to list available courses. It downloads one course JSON only after the learner
chooses **Download course**. Installed courses are saved locally for offline study.
The Store offers an explicit **Update course** action; changing a branch file never
silently replaces an installed course.

Editing JSON on `course-content` does not require an app rebuild. New or changed
manifest entries appear the next time the Store is opened or refreshed; an already
installed course is refreshed only when **Update course** is selected. Legacy
card-pack sync remains separate and is not used by V2 courses.

## Shape

- `manifest.json` declares schema version 2 and lists course IDs, names,
  descriptions and individual JSON file paths relative to `content-v2/`.
- A course contains ordered sections, ordered lessons and a reusable question bank.
- Sections define the topic-shaped path. Each section lists lesson IDs in path
  order and the topic IDs it introduces.
- A lesson lists `questionIds` in the exact order the learner first sees them. The
  same question ID may appear in a later lesson, which is how spiral review
  revisits an earlier topic without copying the question. Set `isCuratedReview`
  when the lesson is intentionally authored as practice/review; that marker, not
  lesson age or completion, controls the weight icon on the path. A question
  missed in a lesson is appended to that lesson once for an end-of-lesson retry;
  it is not queued again if the retry is also missed.
- Each question has its own `topicId`, answer type and explanation. Prefer
  multiple choice whenever a short free-text response would be hard to grade
  robustly. Text-input prompts must be self-explanatory; do not rely on
  `answerInstruction`, which is intentionally not shown to learners. If text
  entry is appropriate, `acceptedAnswers` should include reasonable variants.
  The checker normalises case and spacing and tolerates small typos, but it does
  not understand arbitrary semantic paraphrases; use an objective question type
  for logic statements whose meaning must be checked exactly.

The loader validates the manifest before showing its course list, then validates
each selected course before saving it. A failed download leaves an installed copy
in place. The Store's current TMUA and Further Maths files are prototypes, not
verified exam content. A separate TMUA expansion patch has been prepared for
publication; it is not Store content until applied to `course-content`, and it
should be reviewed before being treated as authoritative exam material.
