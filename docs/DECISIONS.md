# Decisions

A running log of every non-obvious choice, in the order it was made. Each entry
states the decision, the reason, and what it costs.

---

## D1. The sandbox cannot compile Kotlin, so CI is the build/test/lint loop

**Decision.** All compilation, testing and linting is verified through GitHub
Actions on the pushed branch, never locally.

**Why.** The workspace has no JDK, no Gradle distribution, no Android SDK and no
Kotlin compiler. The egress proxy allows `github.com`, `api.github.com`,
`codeload.github.com`, `pypi.org` and `registry.npmjs.org`, but blocks
`repo1.maven.org`, `dl.google.com`, `services.gradle.org`,
`raw.githubusercontent.com`, `objects.githubusercontent.com` and
`release-assets.githubusercontent.com`. Verified with `curl` for each host:
Maven Central, Google Maven and the Gradle distribution endpoint all fail with
`SSL_ERROR_SYSCALL`. Without a JDK *and* a Maven mirror there is no way to run
`kotlinc` or Gradle here, so the plan from section 8 of the brief ("after every
meaningful change run the build, tests and lint") is executed as "push, watch
the workflow, fix, repeat".

**What it costs.** Slower feedback (minutes rather than seconds) and a strong
incentive to keep commits small and to write conservative code.

**What still runs locally.** The content validator (`tools/validate`) is Python,
and `pypi.org` is reachable, so every content pack is validated on this machine
before it is pushed. That is the one quality gate that is fully local.

---

## D2. Build tooling versions

**Decision.**

| Tool | Version | Reason |
| --- | --- | --- |
| Gradle wrapper | 9.6.0 | Minimum required by AGP 9.4 |
| Kotlin | 2.4.20 | Latest stable Kotlin |
| Compose Multiplatform | 1.12.1 | Latest stable CMP |
| AGP | 9.4.1 | CMP 1.12.1 is built on Jetpack Compose 1.12.1, whose AARs require `compileSdk 37`; API 37 support starts at AGP 9.1 |
| SQLDelight | 2.4.0 | Latest; 2.3.0 added "full compatibility with AGP 9.0's new DSL" |
| Ktor | 3.6.0 | Latest stable |
| kotlinx-coroutines / serialization / datetime | 1.11.0 / 1.11.0 / 0.8.0 | Latest stable, all Kotlin 2.4 compatible |
| ktlint Gradle plugin | 14.2.0 | Chosen over detekt: ktlint is format-focused and far less likely to fail a first build on a multiplatform source set than detekt's type-resolution rules |
| JDK | 21 (toolchain) | Required for `jpackage`; AGP 9 needs 17+ |

**Cost.** AGP 9 is new enough that the ecosystem still has sharp edges, but
staying on AGP 8.13 would have pinned Compose Multiplatform back to ~1.9, which
is a year old.

---

## D3. Module layout: `composeApp` + `androidApp` + `desktopApp`

**Decision.** Three modules, matching the current JetBrains template layout.

- `composeApp` — Kotlin Multiplatform library: `kotlin { android { ... } }` via
  `com.android.kotlin.multiplatform.library`, plus `jvm("desktop")`. **All**
  domain, data and UI code lives here, in `commonMain`.
- `androidApp` — `com.android.application`, a ~20 line `MainActivity`.
- `desktopApp` — `jvm("desktop")` + `compose.desktop.application`, a ~20 line
  `main()`.

**Why.** This is forced, not stylistic: AGP 9 removed support for applying
`com.android.application` inside a Kotlin Multiplatform module, so the Android
entry point has to be its own module. It also happens to be what the template
wizard generates today.

**Cost.** Two extra build files. No code duplication — the entry points contain
nothing but wiring.

---

## D4. No iOS, no web

Per the brief. `composeApp` declares only the Android and desktop JVM targets.
The `iosApp` directory that the stock template ships is omitted entirely.

---

## D5. Manual constructor injection instead of Koin

**Decision.** A single hand-written `AppGraph` object graph, built once at each
entry point from `PlatformServices`.

**Why.** The dependency graph is small (~15 objects), has no scopes and no
lifecycles to manage, and every object is constructed eagerly at startup. Koin
would add a runtime dependency, a DSL and reflection-free-but-stringly-typed
module declarations for no benefit. Manual injection also keeps `domain` free of
any framework import, which the brief requires.

---

## D6. No navigation library

**Decision.** Navigation is a `sealed interface Route` held in a
`MutableStateFlow` inside `AppState`. No `navigation-compose` dependency.

**Why.** The app has six destinations and no deep links, no back-stack
serialisation and no animated transitions that need a framework. Hand-rolling it
is ~60 lines, keeps the back button handling explicit, and removes a dependency
whose multiplatform API (`org.jetbrains.androidx.navigation`) is still moving.

---

## D7. `collectAsState` instead of `lifecycle-runtime-compose`

**Decision.** UI state holders expose `StateFlow`; screens collect them with
`androidx.compose.runtime.collectAsState`. No `lifecycle-viewmodel-compose`, no
`lifecycle-runtime-compose`.

**Why.** Those artifacts are the main source of version skew between Compose
Multiplatform and Jetpack. `collectAsState` ships in `compose.runtime`, which is
already pinned by the CMP plugin. The trade-off is that collection does not
automatically pause when an Android activity is stopped; for a single-activity
revision app with no background work that is irrelevant.

---

## D8. SHA-256 implemented in `commonMain`

**Decision.** A pure-Kotlin SHA-256 lives in
`composeApp/src/commonMain/.../crypto/Sha256.kt` and is unit-tested against the
standard NIST vectors.

**Why.** Content packs are verified by hash before import, and
`java.security.MessageDigest` is JVM-only — using it would either leak a
platform type into common code or force an `expect`/`actual` pair for something
that is 80 lines of portable integer arithmetic. Both targets are JVM anyway, so
the pure-Kotlin version costs nothing at runtime.

---

## D9. FSRS-4.5 with default weights, no parameter fitting

**Decision.** `domain/srs/Fsrs.kt` implements the FSRS-4.5 formulas
(initial stability, initial difficulty, difficulty mean-reversion, retrievability
`(1 + t / (9S))^-1`, the success and failure stability updates with the hard
penalty / easy bonus, and `interval = 9 * S * (1/R - 1)`) using the published
17 default weights and a desired retention of 0.9.

**Why.** FSRS is the better algorithm and the closed-form scoring part is small
and pure; only the *parameter optimisation* (fitting `w` to a review history) is
heavy, and that needs a corpus we do not have. Weights are stored in a value
class so a user's fitted parameters can be dropped in later without touching the
scheduler.

**Mode weighting.** The brief requires a harder mode to count for more than an
easier one. `StudyMode` therefore maps to a `Rating`: typed `CORRECT` → `Easy`,
tiles `CORRECT` → `Good`, MCQ `CORRECT` → `Good`, anything `INCORRECT` →
`Again`, typed `PARTIAL` → `Hard`. Flashcards use the rating the user picks. In
mixed mode a card that has been answered correctly in an easy mode escalates to
a harder one (`card_state.mode_escalation`), so repeated MCQ success eventually
gets tested by typing.

---

## D10. LaTeX: Unicode/plain-text fallback renderer, no math typesetting

**Decision.** `RichTextRenderer` is an interface in `commonMain` with one
implementation, `UnicodeRichTextRenderer`, which rewrites `$...$` spans into
readable Unicode (`\frac{a}{b}` → `a/b`, `\times` → `×`, `\theta` → `θ`,
`\sqrt{x}` → `√(x)`, `^{2}` → `²`, `\leq` → `≤`, ...).

**Why.** Research on what actually works in Compose Multiplatform on desktop
*and* Android: JLaTeXMath and KaTeX-in-a-WebView are JVM-only or
Android-only respectively, `compose-richtext` has no LaTeX backend, and the
multiplatform MathJax/Skia options are either unmaintained or web-only. A shared
Unicode fallback is the only option that is genuinely multiplatform today, and
the brief explicitly allows shipping it first.

**Cost.** No real fractions, integrals or matrices. The renderer is behind an
interface, so a Skia-backed typesetter can replace it without touching a single
screen.

---

## D11. Content pack hashing covers `pack.json` *and* every card file

**Decision.** `manifest.json`'s per-pack `sha256` is the hash of the
concatenation of `pack.json` followed by each `cards/*.json` in sorted filename
order, with a single `\n` between files. `tools/validate` recomputes it, and
`tools/validate --update-hashes` rewrites the manifest.

**Why.** Hashing only `pack.json` would mean a changed card file was invisible to
the sync, which downloads packs only when the version *or* hash differs. The
concatenation rule is deterministic and cheap, and the validator enforces it.

---

## D12. `pack.json` lists its card files explicitly

**Decision.** Each `pack.json` carries a `cardFiles` array naming every
`cards/<topic-slug>.json` it owns.

**Why.** The app fetches from `raw.githubusercontent.com`, which serves single
files and has no directory listing. Without an explicit manifest of file names
the client cannot discover what to download.

---

## D13. Tag ids are global, not per-pack

**Decision.** Built-in tag ids are `builtin:tag:<group>:<slug>` (for example
`builtin:tag:board:ocr`) and are upserted, never deleted by a sync. Topics and
cards are `builtin:<pack-id>:<slug>[:<nnnn>]` and *are* deleted per pack on
re-import.

**Why.** Pack A (OCR Physics) and Pack C (OCR Computer Science) both declare
`board = OCR`. Per-pack tag ids would either duplicate that tag in the UI or
make one pack's re-import delete the other pack's tag. Tags are tiny and stable,
so never deleting them is the simpler invariant.

---

## D14. Built-in and user content are separated by id namespace *and* by a `source` column

**Decision.** Every `topic`, `tag` and `card` row carries `source` (`BUILTIN` or
`USER`) and, for built-ins, `pack_id`. The sync only ever issues
`DELETE ... WHERE pack_id = ?` and upserts rows it owns.

**Why.** Belt and braces. The namespace alone would be enough for a correct
implementation, but an explicit column means a single stray query cannot touch
user content, and it makes "duplicate built-in card into my content" a trivial
id rewrite.

---

## D15. Study progress lives in its own table, keyed by card id

**Decision.** `card_state` and `review_log` are separate from `card`. A content
update that replaces a built-in card leaves both tables untouched; the schedule
simply carries over because the card id is stable across pack versions.

**Why.** This is the brief's hard requirement — "updating built-in content must
never touch or overwrite user content or study progress" — and separating the
tables makes it structurally impossible to violate rather than merely
unlikely.

---

## D16. `Card.numeric*` fields are an addition to the brief's field list

**Decision.** `Card` gains an optional numeric spec (`numericValue`,
`numericTolerance`, `numericUnit`) beyond the fields listed in section 3.

**Why.** Section 5 requires `NUMERIC` answers to be checked with "tolerance in
the card", which is not expressible in the listed fields. When `numericValue` is
null the expected value is parsed out of `back`, so authors only have to give a
tolerance.

---

## D17. Content validator is Python

**Decision.** `tools/validate` is a single Python 3 script using `jsonschema`.

**Why.** The brief allows "Kotlin script or Python, whichever runs in CI". Python
runs in CI *and* in this sandbox (see D1), so content can be validated locally
before every push. A Kotlin script would need the JDK that D1 says is
unavailable.

---

## D18. Tests run on the JVM target only

**Decision.** CI runs `:composeApp:desktopTest`, which executes every `commonTest`
on the JVM. `withHostTest {}` is not enabled for the Android target.

**Why.** All testable code is in `commonMain` and has no platform dependencies,
so running the same tests twice buys nothing and doubles CI time. The Android
target is still fully compiled (`:androidApp:assembleDebug`), which catches any
`androidMain` regression.

---

## D19. The `content` branch is built in a linked worktree, never by switching this checkout

**Decision.** `git worktree add --detach /tmp/content-wt HEAD`, then
`git checkout --orphan content` inside that worktree and `git rm -rf .` to empty
it. Content is committed there and pushed with
`git push origin content:refs/heads/content`. The main checkout stays on
`arena/ecfda5a7-revisionapp` for the whole project.

**Why.** The session is pinned to one working branch, but the brief requires a
separate orphan branch holding *only* content. A linked worktree gives a real
branch to commit and validate against — `tools/validate` needs files on disk, and
the pack hashes need rewriting between commits — without ever moving this
checkout off its branch. Git plumbing (`hash-object` / `mktree` / `commit-tree`)
was the first idea and would work, but it cannot run the validator, and a content
branch whose hashes are computed by hand is a branch whose hashes are wrong.

**What it costs.** The worktree lives in `/tmp`, which is not part of the
persisted workspace, so the branch has to be pushed after every pack. That is the
right discipline anyway: the remote is the durable copy, and the content branch
was rebuilt from it without loss after the sandbox was re-provisioned mid-project.

---

## D20. No experimental Material 3 or Layout APIs, and no icon set

**Decision.** The UI is built from stable primitives only. No `Scaffold`,
`TopAppBar`, `NavigationBar`, `FilterChip`/`AssistChip` or `FlowRow`; no
`material-icons-extended` dependency. Headers, the tab bar, filter chips and
wrapping rows are hand-rolled from `Row`, `Column`, `Box`, `Text`, `background`,
`border` and `clickable`, and buttons carry words instead of icons.

**Why.** Every one of those APIs is behind `ExperimentalMaterial3Api` or
`ExperimentalLayoutApi`, and an opt-in annotation is a promise that the signature
can change underneath you. This project has no local compiler — see D1 — so the
only way to find out is a CI round trip that costs several minutes. Two hand-rolled
widgets cost about sixty lines and remove the whole class of failure. The icon set
is a separate dependency with no runtime benefit here, and it is large.

**What it costs.** `ui/components/Widgets.kt` exists, and wrapping is done by
chunking a list into rows of three or four instead of letting `FlowRow` measure.
Chunking is fixed-width, so a very long tag name can overflow its row where a real
`FlowRow` would move it to the next line.

---

## D21. `kotlin.time.Clock` and `kotlin.time.Instant`, not the kotlinx-datetime aliases

**Decision.** Clocks and instants are imported from `kotlin.time`.
`TimeZone`, `toLocalDateTime` and `atStartOfDayIn` stay in `kotlinx.datetime`.

**Why.** kotlinx-datetime 0.7.0 moved `Clock` and `Instant` into the standard
library and kept the old names as deprecated type aliases. A type alias cannot
qualify a nested classifier, so `kotlinx.datetime.Clock.System` does not compile
at all — `System` is a nested object of the real `kotlin.time.Clock`. Companion
*members* such as `Instant.fromEpochMilliseconds` do resolve through an alias,
which is why only the three `Clock.System` references failed and the rest of the
codebase compiled untouched. Using the stdlib types removes the deprecation
warnings as well.

**What it costs.** Two import conventions in one codebase, which reads oddly
until you know the history. A one-line comment in each affected file explains it.

---

## D22. Multiple-choice options are de-duplicated by what the student reads

**Decision.** `McqQuestionFactory` decides whether two options are duplicates
from a display key — `toPlainText`, lowercased, whitespace collapsed, nothing
else — rather than from `TextNormaliser.normalise`. This matches what
`Mcq.isUsable` in the domain model already did.

**Why.** `normalise` is built for grading and is deliberately lossy: it drops
apostrophes and, before this change, signs. Written against real Further Maths
content, that collapsed `f(a)` with `f'(a)` and `= -a` with `= a`. With fewer
than four distinct options the factory returns null and the card silently drops
out of multiple-choice mode — correct behaviour for a genuinely thin option set,
catastrophic for one a student can plainly read as four different answers. Two
options are duplicates when they *look* the same, and `\frac{1}{2}` and `(1)/(2)`
still count as one because the display key applies the same LaTeX flattening the
UI renders.

**What it costs.** Two options that differ only in LaTeX spelling that
`toPlainText` happens not to unify, such as `x^{2}` and `x^2`, are now offered
side by side instead of being collapsed.

---

## D23. `normalise` keeps minus signs and primes

**Decision.** In `TextNormaliser.normalise` a hyphen survives whenever it is a
sign rather than a word joiner, and an apostrophe survives whenever the next
character is not a letter.

**Why.** The old rule kept a hyphen only in front of a digit, so `= -a` and
`= a` normalised identically and a signed answer could be graded correct against
an unsigned model answer. The old rule dropped every apostrophe so that
contractions kept their negation — `doesn't` must stay `doesnt` for the negation
guard — but that also turned `f'(a)` into `f a`, which is `f(a)`. Both rules now
turn on what follows and what precedes: between two letters a hyphen joins words
(`well-known`, `centre-seeking`), anywhere else in front of a term it is a sign;
an apostrophe followed by a letter is a contraction, otherwise it is a prime.

**What it costs.** `3-phase` now keeps its hyphen where it previously split. No
existing assertion changed, and the three hyphen tests in
`TextNormalisingTest` (`well-known`, `-4.5`, `5-3`) were ported to Python and
re-checked against the new rule before it was committed.

---

## D24. Overriding a verdict is not weighted by study mode

**Decision.** `ModeGrading.ratingForOverride` returns EASY when the user says
"I was right" and AGAIN when they say "I was wrong", in every mode.

**Why.** Everywhere else a harder mode earns a bigger interval, and that is right:
the app has watched you produce the answer. An override is the one case where it
has not. The user is disputing a grade the app cannot re-check, so weighting the
dispute by the mode it happened in would punish them for the checker's mistake in
a mode it chose itself. EASY is also what a correct typed answer earns, which is
the documented intent the unit test asserted and the implementation did not
deliver.

**What it costs.** A user can inflate their own intervals by overriding
generously. That is unavoidable in any self-grading system, and the alternative —
capping an override at the mode's own weight — only punishes the cases where the
checker was genuinely wrong.

---

## D25. Seed content is original, and every pack declares `structureVerified: false`

**Decision.** All 201 cards across the four seed packs are written from scratch.
Each pack sets `"structureVerified": false` and ships a README listing what to
check against the published specification.

**Why.** The brief forbids reproducing past-paper, mark-scheme, textbook or
specification text, and it asks for accuracy over coverage: anything uncertain is
left out. No awarding-body website was reachable from this sandbox — only
`github.com`, `api.github.com`, `codeload.github.com`, `pypi.org` and
`registry.npmjs.org` are — so the topic hierarchies, the section numbers and the
depth of treatment all come from memory. Declaring that in the data, rather than
quietly presenting it as verified, is the honest option, and the validator
*requires* a README whenever the flag is false so the caveat cannot be lost.

**What it costs.** The packs need a review pass against the real specifications
before anyone relies on them for exam preparation. Every card also carries
`reviewStatus: AI_UNREVIEWED`, which the app surfaces, so nothing here presents
itself as checked.

---

## D26. Packs are generated from a Python authoring script that is not committed

**Decision.** Card data is written as terse Python — a `card(...)` helper with
keyword arguments — and a generator emits the JSON, assigns the four-digit
ordinals, writes the pack README, updates `manifest.json` and runs the validator.
Neither the generator nor the data files are committed to the `content` branch.

**Why.** JSON written by hand for 201 cards is where typos live, and a typo in a
hash or an ordinal is invisible until a client rejects the pack. The generator
makes the ordinals, the manifest entry, the hash and the card count impossible to
get wrong, and it keeps the authored form small enough to review. Neither script
is content, so neither belongs on a branch that promises to contain nothing but
content; the committed artefacts are the JSON files and `tools/validate`, which
checks them independently of how they were made.

**What it costs.** Editing a card means editing a script that is not in the
repository, or editing the JSON and re-running `--update-hashes`. The validator
makes the second path safe, which is why it is strict about the manifest.

---

## D27. Release assets are collected with `find`, not with shell globs

**Decision.** `.github/workflows/release.yml` runs `:desktopApp:packageExe` on
`windows-latest` and `:androidApp:assembleDebug` on `ubuntu-latest`, uploads both
as artifacts, and a third job publishes them. The publish step builds its asset
list with `find ... -name '*.exe' -o -name '*.msi' -o -name '*.apk'`.

**Why.** `packageExe` is what the brief asks for, and the `windows-latest` image
ships WiX Toolset 3.14, which is what `jpackage` needs to produce an installer;
the workflow puts WiX on `PATH` and falls back to installing it through
Chocolatey. Collecting assets with `find` rather than a glob matters because an
unmatched shell glob is passed to `gh` as a literal path and fails the step — the
workflow uploads an `.msi` pattern it does not currently build, and a glob would
have turned that into a broken release instead of a no-op.

**What it costs.** No `.msi` is published until `packageMsi` is added to the
Windows job. The Android asset is a debug APK, which is what the brief asks for
and what CI can build without a signing key.

---

## D28. CI reports failures as commit comments

**Decision.** `.github/report-failure.sh` posts the compiler diagnostics, the
failing test details from the JUnit XML, and the ktlint report as a comment on
the failing commit. `ci.yml` also runs every check even after one fails.

**Why.** This sandbox reaches `api.github.com` but not
`results-receiver.actions.githubusercontent.com` or the Azure blob host behind
artifacts, so neither `gh run view --log` nor `gh run download` works here.
Commit comments travel over `api.github.com`, which does, so they are the only
channel that carries a failure back to the machine that has to fix it. Batching
the checks matters for the same reason: with no local compiler, a round trip costs
several minutes, so one push should report compilation, tests and lint together
rather than revealing them one at a time.

**What it costs.** A comment on every failing commit, which is noise for anyone
watching the repository, and a reporter script that has to be maintained
alongside the workflow. The ktlint section strips ANSI colours and drops
generated sources because the comment body is capped.

---

## D29. The UI has never been executed, only compiled, linted and unit-tested

**Decision.** No screen in this project has ever been rendered. There is no
display, no JDK and no Android emulator in the sandbox it was written in, so
"the desktop app runs" is backed by compilation, linting, the pure-logic tests
and a successful `jpackage` run on `windows-latest` — not by anyone looking at it.

**Why.** D1 explains the toolchain situation: the only compiler available is CI.
Recording this explicitly matters because it changes what the remaining risk is.
The risks that testing *cannot* reach here are layout and interaction problems —
a control that overflows on a narrow phone, a scroll container nested the wrong
way, a text field that cannot be reached. Those are real and they are unfixed
until someone runs the app.

**What stands in for running it.** The parts that carry actual logic are pure
Kotlin in `commonMain` and are tested directly: the answer checker, the FSRS
scheduler, question derivation for every mode, topic-tree aggregation and cycle
handling, filter matching, the SHA-256 implementation against published vectors,
the pack sync including hash mismatch and partial failure, the repositories
against a real in-memory SQLite database, and the session state machine through
all four modes. Screens contain no branching logic of their own: they render a
sealed state with an exhaustive `when` and forward every input to `AppState`, so
there is very little in them that can be wrong without the compiler noticing.

**What it costs.** Expect to fix visual and layout problems on first run. The
narrow-layout branch in `BrowseScreen` (filters above the list instead of beside
it, chosen by `BoxWithConstraints`) and the hand-chunked wrapping rows that stand
in for `FlowRow` are the two places most likely to need adjustment.

---

## D30. Compose Hot Reload is not enabled

**Decision.** Desktop runs through the plain `:desktopApp:run` task. No hot-reload
plugin is applied.

**Why.** The brief allows skipping it if it is not stable in the current
template, and this project is not derived from a template that ships it — the
build files were written from the current JetBrains and AGP 9 conventions. Hot
reload needs its own Gradle plugin and a matching toolchain version, and the one
feedback loop available here is CI, where a misconfigured plugin would show up as
a build failure costing several minutes and delivering no benefit, because there
is no display to reload into.

**What it costs.** Nothing in CI or in a release. A developer with a display who
wants it adds the plugin to `desktopApp` themselves.

---

## D31. The packaged runtime's module list has to be declared, and asserted in CI

**Decision.** `desktopApp` adds `modules("java.sql")` to `nativeDistributions`,
and the release workflow fails if the packaged runtime is missing any module the
app needs — asserted against the `legal/<module>` directories jlink writes, not
by running the bundled launcher.

**Why.** A packaged Compose Desktop app does not run on the machine's JDK. It
runs on a jlink image built from an explicit module list whose default is
`java.base`, `java.desktop`, `java.logging` and `jdk.crypto.ec`. `java.sql` is
not in it, and SQLDelight's desktop driver opens its connection through
`java.sql.DriverManager` in the first thing this app does — so v1.0.0 installed
cleanly and then died with `NoClassDefFoundError: java/sql/DriverManager`
followed by "Failed to launch JVM", before a window ever appeared.

Nothing in the pipeline could see it. The code compiles, `:desktopApp:build`
passes, ktlint passes, and `:desktopApp:run` works because Gradle runs it on the
full JDK. `packageExe` also succeeds, because jlink happily builds a runtime that
cannot run the app. This is a defect class that only a launched installer
reveals, which is why D29's "the UI has never been executed" was not the only
thing that had never been executed: the *packaged* app had never been executed
either.

The first attempt at a guard ran the bundled `java.exe --list-modules`. That was
the wrong thing to depend on — it could not locate the launcher under
`build/compose`, threw before computing anything, and printed `MISSING: (none)`
from an uninitialised variable, so a failed check read like a passing one.
`legal/<module>` is what jlink actually writes for every module it links, so it
is the authoritative record and needs no executable to be found or run.

Windows shortcuts are declared for the same reason: `WindowsPlatformSettings`
defaults both `menu` and `shortcut` to false, so v1.0.0 created no Start Menu
entry and no desktop shortcut, which made the app invisible to Windows Search and
unpinnable. Both are now on under a `RevisionApp` menu group.

**What it costs.** A larger installer, by roughly the size of `java.sql` and the
`java.transaction.xa` it pulls in transitively. Any new dependency that reaches
for a JDK module outside the defaults needs a matching `modules(...)` entry and
an addition to the release check — which is the point of failing the build rather
than shipping.
