# RevisionApp

A cross-platform revision app: flashcards and four question modes over a topic
tree, with FSRS spaced repetition, tag filtering, your own editable content and
built-in content packs that sync from a branch of this repository and then work
offline forever.

Built with Kotlin Multiplatform and Compose Multiplatform for **desktop (JVM)**
and **Android**. Every line of logic and every screen is in `commonMain`; the two
platform source sets contain nothing but a database driver, an HTTP engine and an
entry point.

---

## Download

The latest Windows x86-64 installer and Android debug APK are attached to the
newest entry under [Releases](../../releases):

| Asset | What it is |
| --- | --- |
| `RevisionApp-1.0.0.exe` | Windows x86-64 installer, built by `jpackage` through the WiX toolset |
| `RevisionApp-android-debug.apk` | Android debug build, installable with `adb install` |
| `SHA256SUMS.txt` | Checksums for both |

The Android build is a **debug** APK: it is signed with the debug key, so it is
fine for installing on your own device but not for publishing.

---

## Requirements

| Tool | Version | Needed for |
| --- | --- | --- |
| JDK | 21 | Everything |
| Gradle | 9.6.0 | Supplied by the wrapper, do not install it |
| Android SDK | platform 37 | The Android target only |

The desktop target needs nothing beyond a JDK. For Android, set `ANDROID_HOME`
(or install Android Studio); AGP will otherwise try to download the SDK itself.

---

## Commands

All commands run from the repository root.

| Task | Command |
| --- | --- |
| Run the desktop app | `./gradlew :desktopApp:run` |
| Run every test | `./gradlew :composeApp:desktopTest` |
| Lint | `./gradlew ktlintCheck` |
| Build the Android debug APK | `./gradlew :androidApp:assembleDebug` |
| Build the Windows installer | `./gradlew :desktopApp:packageExe` |
| Build everything CI builds | `./gradlew :composeApp:desktopTest ktlintCheck :desktopApp:build :androidApp:assembleDebug` |

On Windows use `gradlew.bat` instead of `./gradlew`.

Outputs land in:

- APK — `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
- Windows installer — `desktopApp/build/compose/binaries/main/exe/`
- Test report — `composeApp/build/reports/tests/desktopTest/`

Configuration cache, build cache and parallel execution are all enabled in
`gradle.properties`.

---

## What the app does

### Study modes

All four modes are derived from the *same* cards — there is no separate question
entity, so authoring once powers every mode.

| Mode | What happens | How it is graded |
| --- | --- | --- |
| **Flashcards** | Flip the card, then rate yourself Again / Hard / Good / Easy | Your rating |
| **Typed answer** | Type the answer; the checker returns a verdict, the key points you hit and missed, and the model answer | Automatic, and you can always override with "I was right" / "I was wrong" |
| **Word tiles** | Put shuffled tiles back in order; a couple of decoys are mixed in | Exact order is CORRECT, 60% of positions is PARTIAL |
| **Multiple choice** | Four options with immediate feedback and the explanation | All or nothing |
| **Mixed** | Picks the best-fit mode per card, and escalates to a harder mode after a mistake | As the mode it resolved to |

Cards a mode cannot present drop out silently: a long paragraph never becomes a
tile question, and a topic with fewer than three plausible sibling answers never
becomes multiple choice.

Every mode writes to **one** schedule, and a correct answer in a harder mode
counts for more: correct typed answers earn Easy, correct tile or multiple-choice
answers earn Good, anything wrong earns Again.

### Spaced repetition

FSRS-4.5 with its published default weights and a user-adjustable desired
retention (default 0.90, on the settings screen). Parameters are not fitted to
your history — that needs an optimiser and a review log long enough to train on.

### Browsing and filters

The topic tree nests to any depth and shows a card count and a due count for each
node, aggregated over its whole subtree. Breadcrumbs show where the selected topic
sits. Selecting a topic always includes its descendants.

Tags belong to groups (`board`, `level`, `subject`, `paper`, `difficulty`, or any
group you invent). Within a group selected tags are OR-ed; across groups they are
AND-ed, so "OCR" + "A Level" + "Physics" narrows rather than widens. A "match all
tags" toggle switches to a strict AND everywhere. The same filter drives the
browse list *and* study sessions.

### Your own content

Create, edit and delete topics at any depth, tags in any group, and cards with
key points, synonyms, must-include flags, weights, accepted aliases, authored tile
chunks, authored multiple-choice distractors, explanations, numeric tolerances and
specification references.

Built-in content is read-only — a sync has to be able to replace it — but every
built-in card has a **Duplicate as mine** button that copies it into your own
content, where it becomes fully editable.

### Stats

Due today, due now, current day streak, days studied, total reviews and accuracy
per topic, counting every mode together.

---

## Built-in content and the `content` branch

Built-in content lives on an orphan branch called
[`content`](../../tree/content) that contains **no app code**. The app fetches it
over raw GitHub from

```
https://raw.githubusercontent.com/Alik-Green/RevisionApp/content
```

which is editable on the settings screen. Syncing downloads the manifest, compares
each pack's version and checksum with what is already installed, fetches only the
packs that changed, verifies the SHA-256 of each one, and imports it in a single
database transaction. A pack that fails any check is left exactly as it was, so a
truncated download cannot damage your library. After one successful sync the app
works offline indefinitely.

**Content sync never touches your content or your study progress.** Built-in rows
are namespaced by id *and* carry a `source` column, progress lives in a separate
table keyed by card id, and a re-import deletes only the rows belonging to that
pack.

### Identifiers

| Thing | Id |
| --- | --- |
| Built-in topic | `builtin:<pack-id>:<topic-slug>` |
| Built-in card | `builtin:<pack-id>:<topic-slug>:0012` |
| Built-in tag | `builtin:tag:<group>:<slug>` |
| Anything you create | `user:<uuid>` |

Tags are deliberately not namespaced per pack: `board = OCR` means the same thing
in every pack, so they are shared. Tag slugs must therefore be unique across all
groups *within* a pack, because card files refer to tags by slug alone.

### Layout

```
manifest.json                         index the app fetches first
schema/manifest.schema.json           JSON Schema for manifest.json
schema/pack.schema.json               JSON Schema for packs/<id>/pack.json
schema/cards.schema.json              JSON Schema for packs/<id>/cards/*.json
tools/validate                        the checker CI runs on every push
packs/<pack-id>/pack.json             topic tree, tags, list of card files
packs/<pack-id>/cards/<topic>.json    every card in one topic
packs/<pack-id>/README.md             scope, sources, anything still to verify
```

`raw.githubusercontent.com` serves single files and cannot list a directory, so
`pack.json` declares its `cardFiles` explicitly. Each file is named after its
topic slug and repeats it in a `topic` field — both are enforced.

### Checksums

`manifest.json` promises a `sha256` and a `cardCount` per pack. The hash covers
`pack.json` followed by every card file in sorted file-name order, each
terminated by a single newline:

```
sha256( pack.json + "\n" + cards/a.json + "\n" + cards/b.json + "\n" )
```

The app (`composeApp/.../crypto/PackHasher.kt`, a pure-Kotlin SHA-256) and
`tools/validate` compute it identically, byte for byte as served.

### Validating

```bash
python3 -m pip install jsonschema   # optional but recommended
tools/validate                      # fails on errors
tools/validate --pack tmua          # one pack
tools/validate --strict             # also fail on warnings
tools/validate --update-hashes      # recompute sha256 and cardCount
```

The `Content` workflow runs this on every push and pull request to the `content`
branch, then recomputes the hashes and fails if the committed manifest is stale.

### Adding a pack

1. Pick an id: lowercase letters, digits and hyphens, e.g.
   `aqa-a-level-chemistry`.
2. Create `packs/<id>/pack.json` with `packId`, `name`, `version: 1`, the topic
   tree (`slug`, `name`, `parent`, `sortOrder`), the `tags` (`slug`, `group`,
   `name`) and `cardFiles`.
3. Create one `packs/<id>/cards/<topic-slug>.json` per topic. The file name must
   be the topic slug and the `topic` field must repeat it. Every card needs a
   four-digit `id`, a `front`, a `back` and an `answerType` of `TEXT`,
   `NUMERIC`, `EXPRESSION` or `SELF_GRADE`.
4. Add `keyPoints` (with `synonyms`, `mustInclude` and `weight`) for text cards,
   `numeric` for numeric cards, `mcq` with three authored distractors where a
   plausible wrong answer exists, `tileAnswer` chunks for short answers, and an
   `explanation` for anything non-obvious. Keep `reviewStatus` at
   `AI_UNREVIEWED` until a human has checked the card.
5. Aim for at least five cards per leaf topic — the validator enforces it — and
   mix definitions, equations with units, "explain why" questions, worked
   numerics and common misconceptions.
6. Write `packs/<id>/README.md`. If the topic tree came from memory rather than
   the published specification, set `"structureVerified": false` and list what
   needs checking; the validator requires that README when the flag is false.
7. Add the pack to `manifest.json`, run `tools/validate --update-hashes` to fill
   in the hash and count, then `tools/validate` until it is clean.
8. Commit and push. To change a pack later, edit its files, bump `version` in
   both `pack.json` and `manifest.json`, and re-run `--update-hashes`.

The four seed packs currently on the branch — OCR A Level Physics (H556),
Edexcel A Level Further Maths (9FM0), OCR A Level Computer Science (H446) and
TMUA, 201 cards in total — are original writing. No past-paper question,
mark-scheme wording, textbook sentence or specification text is reproduced. All
of them have `structureVerified: false` because no awarding-body website was
reachable from the environment they were written in; each pack README lists
exactly what to check.

### Private repositories

The default base URL only works while this repository is public. Serving a
private branch needs an authenticated URL — a fine-grained personal access token
embedded in the base URL, or a proxy that adds the header. The base URL is
editable on the settings screen; the app stores no credentials.

---

## Architecture

```
domain/     pure Kotlin: models, answer checking, FSRS, question derivation
data/       SQLDelight repositories, content DTOs, the pack sync
ui/         Compose screens, one state holder, the LaTeX renderer
platform/   expect/actual: database driver, HTTP engine, data directory
di/         the object graph, wired by hand
crypto/     SHA-256 in pure Kotlin
```

Data flows one way. `AppState` exposes `StateFlow`s and intent methods; screens
collect the flows and call the intents; nothing else writes state. `StudySession`
is a sealed state machine driven by `SessionEvent`, and every path that ends a
card funnels through a single `commit()`, which is what makes "all four modes
feed one schedule" true rather than aspirational.

`domain` imports no framework at all — no Compose, no SQLDelight, no Ktor — so
the answer checker, the scheduler and the topic tree are unit-testable as plain
Kotlin, and that is where almost all of the tests are.

Database tables: `topic`, `tag`, `card`, `card_tag`, `card_state`, `review_log`,
`pack`, `setting`, `study_day`. Complex card fields (key points, aliases, tile
chunks, MCQ options, numeric specs) are stored as JSON in a column, because they
are always read and written whole and never queried by their contents.

---

## Answer checking

`AnswerChecker.check(card, input)` returns a `Verdict` of CORRECT, PARTIAL or
INCORRECT with a 0..1 score and the key points matched and missed. The pipeline:

1. **Normalise** both sides — LaTeX and unicode maths flattened to readable text,
   case and punctuation removed, minus signs and primes kept because they change
   meaning.
2. **Exact and alias match.**
3. **Key-point coverage** — stemmed tokens, synonyms, edit-distance fuzzy
   matching, weights, and `mustInclude` points required for CORRECT.
4. **Direction and negation guard** — an answer that says the opposite of the
   model answer is capped at INCORRECT, no matter how many tokens overlap.
5. **TF-IDF cosine similarity**, only for cards with no key points at all, with
   IDF computed over the whole library and the result capped at PARTIAL.
6. **Numeric** — standard form, optional units, tolerance.
7. **Expression** — structural comparison of canonicalised terms, falling back to
   self-grading when that is undecidable.
8. **Self-grade** — the model answer is shown and you judge.

A `SemanticScorer` interface exists for a future model-backed scorer; the default
implementation is a no-op and nothing is downloaded.

LaTeX in `$...$` goes through a `RichTextRenderer` interface. The shipped
implementation converts it to readable Unicode (`x^{2}` becomes a real
superscript, `\frac{a}{b}` becomes `(a)/(b)`, `\theta` becomes `theta`) rather
than typesetting it: no multiplatform LaTeX renderer exists that does not need a
WebView or a JVM-only maths font. The same flattening feeds the answer checker,
so what you see is what gets compared.

---

## Development

`docs/DECISIONS.md` records every non-obvious choice — why there is no DI
framework, no navigation library, no experimental Material 3 API, why SHA-256 is
hand-written in `commonMain`, what the pack hash covers and why, and what the
sandbox this was developed in could and could not do. Read it before changing
build files.

CI (`.github/workflows/ci.yml`) runs on every push to `main` and to `arena/**`:
configure, `:composeApp:desktopTest`, `ktlintCheck`, `:desktopApp:build` and
`:androidApp:assembleDebug`. Every check runs even if an earlier one fails, so
one push reports compilation, test and lint problems together, and a failure
posts the compiler diagnostics, the failing test details and the ktlint report as
a commit comment.

`.github/workflows/release.yml` runs on a `v*` tag and publishes the Windows
installer and the Android APK to a GitHub Release.

`.github/workflows/content-ci.yml` lives on the `content` branch and validates
every pack on push.

### Code style

ktlint with `intellij_idea` layout, configured in `.editorconfig`. Value classes
for ids (a `TopicId` can never be passed where a `CardId` is wanted), sealed
hierarchies with exhaustive `when` for everything the UI has to render, no `!!`,
no platform types in common code, and no stringly-typed identifiers.

A handful of formatting rules that fight hand-written Compose layout are disabled
in `.editorconfig`, each with a reason in the file. Correctness rules — unused
imports, wildcard imports, indentation, modifier order — are all on.
