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
  revisits an earlier topic without copying the question. A question missed in a
  lesson is appended to that lesson once for an end-of-lesson retry; it is not
  queued again if the retry is also missed.
- Each question has its own `topicId`, answer type and explanation. Text-input
  questions should include an `answerInstruction` that clearly says whether to
  enter a word, phrase, number or expression. `acceptedAnswers` should include
  reasonable variants. The checker accepts case and spacing variations, and
  normalises the common `counterexample` / `counter example` spelling.

The loader validates the manifest before showing its course list, then validates
each selected course before saving it. A failed download leaves an installed copy
in place. The TMUA and Further Maths examples are small product prototypes, not
verified exam content; review them before treating either course as authoritative.
