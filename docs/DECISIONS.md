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

---

## D32. Maths is typeset by a small custom engine, not by JLatexMath

**Decision.** `com.revisionapp.math` holds an AST (`MathNode`), a recursive-descent
`MathParser`, the `MathUnicode` tables and `MathPlainText`. `ui/components/MathText`
lays the AST out as an `AnnotatedString`: Unicode scripts where every character has
one, smaller baseline-shifted text where it does not, and stacked fractions placed
as inline content sized by exact `TextMeasurer` measurement. There is no new
dependency, no `expect`/`actual` and no platform code.

**Why not a LaTeX library.** The obvious candidates were rejected on evidence, not
taste:

- *JLatexMath* (`org.scilab.forge:jlatexmath`) with `ru.noties:jlatexmath-android`.
  Both are unmaintained (2017 and 2018). They draw into an AWT `Graphics2D` or an
  Android `Canvas`, so each platform needs a bitmap bridge to a Compose
  `ImageBitmap`, which means `expect`/`actual` and two failure modes instead of
  none. The output is a raster, so following the theme's colour, text size and
  light/dark scheme means re-rendering and caching bitmaps per style — the thing
  text layout gets for free. And Maven Central is unreachable from this sandbox
  (D1), so the API surface could not be checked before shipping: an unverifiable
  dependency, on two platforms, behind the one feature that cannot be seen during
  development, is the least robust option available.
- *A WebView running KaTeX.* Compose Multiplatform has no common WebView, and
  bridging one would put platform types in the way of `commonMain`.
- *Skia paragraph APIs directly.* Desktop-only.

**Why a subset is enough.** The four content packs were measured rather than
guessed at: 465 `$...$` spans across 201 cards use exactly **39 distinct
commands** — `frac`, `sqrt`, `theta`, `alpha`, `beta`, `gamma`, `det`, `lambda`,
`int`, `cos`, `sin`, `pi`, `cosh`, `sinh`, `Rightarrow`, `left`, `right`, `neg`,
`mathbf`, `ge`, `le`, `ne`, `neq`, `times`, `sum`, `pm`, `forall`, `exists`,
`arctan`, `log`, `ln`, `tan`, `circ`, `text`, `Delta`, `Sigma`, `approx`, `cdot`,
`wedge`. `MathUnicode` covers all 39, plus the arrows, relations, logic symbols
and both Greek cases that were missing before and caused `\Rightarrow` to render
as the word "Rightarrow". Anything unlisted degrades to its name, so new content
can never produce a raw backslash.

**One parser, two outputs.** `TextNormaliser.toPlainText` runs the same parser and
re-emits it in ASCII, instead of keeping the 124-line replacement table it had.
That is what makes "what the user sees is what gets compared" true: the two had
drifted apart, and drift is how a symbol ends up displayed as a word. It also
removes the ordering hazard that made `\leq` have to be listed before `\le` —
commands are now looked up by parsed name.

**Caching.** `remember(source)` per composition slot. A process-wide cache would
need synchronisation `commonMain` has no primitive for, and parsing a card-sized
string is microseconds.

**What it costs.** Fractions nested inside fractions or radicals degrade to
`(a)/(b)` rather than stacking twice; radicals get no vinculum (the bar over the
radicand); there are no matrices, no `\begin{...}` environments and no italic
variables. Every one of those is absent from the measured content, and each
degrades to readable text rather than to nothing. `$` is also stripped everywhere,
so a literal currency amount in a card would lose its symbol — the content README
now says to write prices out.

---

## D33. The full Material icon set, reversing D20's "no icon set"

**Decision.** `composeApp` declares `api(compose.materialIconsExtended)`.

**Why.** D20 declined an icon set because nothing needed one: the first UI used
words on buttons and had no folders, no chevrons and no status glyphs. The
redesign does — a Library of folders needs a folder, a breadcrumb needs a
chevron, a sync status needs three distinguishable states, and a navigation rail
with labels but no icons looks unfinished. Hand-drawing those as vectors would be
more code than the dependency and less consistent than the set every other
Material app uses.

The accessor is pinned by the Compose plugin to a frozen
`org.jetbrains.compose.material:material-icons-extended:1.7.3` and, unlike the
other `compose.*` accessors in ComposePlugin 1.12.1, is *not* marked deprecated —
checked in the plugin source rather than assumed, because the alternatives were
guessing at a coordinate Maven Central could not be reached to verify.

**What it costs.** A large artifact, most of which tree-shaking removes at the
R8/jlink stage but which still lengthens dependency resolution. D20's other half —
no experimental Material 3 APIs — still stood when this was written, and is
revisited in D34.

---

## D34. Ink & Paper, an adaptive shell, and one deliberate experimental opt-in

**Decision.** `ui/theme/InkPaper.kt` holds the light and dark `ColorScheme` and a
separate `ExtendedColors` for the three verdicts, exposed through a
`staticCompositionLocalOf`. `AppShell` renders a `NavigationBar` below 600dp and a
`NavigationRail` from 600dp up, both driven by one `Destination` enum, and renders
nothing at all during a live session. The due count is a badge on the Study
destination.

**Why the verdict colours are not in `ColorScheme`.** Material 3 has no slot for
"correct / partly right / incorrect". Putting them in secondary and tertiary would
make amber mean both "due soon" and "partly right", which is exactly the ambiguity
a semantic palette exists to remove. `ExtendedColors` keeps them separate and
gives each a container and an on-container colour, so coloured text always sits on
its own container at AA contrast rather than as white on a mid-tone — the failure
mode of a hand-picked green or amber.

**Why the due count moved.** Floating at the top right of every screen it read as
a global notification, unattached to anything, and it competed with each screen's
own title. As a badge on Study it says what it means: this is how much work that
tab holds. It also disappears when there is none, instead of showing "0 due"
forever.

**Why `NavigationBar` and `NavigationRail` but not `Scaffold`.** Both items are
stable API and both take a `badge` slot, which is what the due count needs.
`Scaffold` would add a slot layout this app does not want: each screen owns its
own header, because a Library header carries a search field and a breadcrumb while
a session header carries a progress bar and an exit button. The shell therefore
composes the bar or rail around the screen directly.

**The opt-in.** Bottom sheets for scoped filters and for post-answer feedback need
`ModalBottomSheet`, which is `@ExperimentalMaterial3Api`. D20 avoided every
experimental API because there is no local compiler and each opt-in is a chance to
break the build invisibly; a sheet is worth one, taken explicitly at the call site
rather than file-wide, so the blast radius is named.

---

## D35. Search scans the snapshot instead of using SQLite FTS5

**Decision.** `LibrarySearch` filters the in-memory `LibrarySnapshot`. There is no
FTS table, no index and no query.

**Why.** The brief prefers FTS5 "if available on both platforms". It is — SQLite
ships it on Android and in the desktop JDBC driver — but it buys nothing here and
costs a way to be wrong. Every topic, tag and card is already loaded into the
snapshot, because the topic tree needs aggregate counts and the filter sheet needs
tag frequencies over the current subtree. So the data is in memory before any
search happens, and a scan of a few hundred cards is faster than a round trip to
the database.

The real argument is staleness. An FTS index is a second copy of the truth that
has to be kept in step with `card`, `topic` and `tag` through every path that
writes them: user edits, deletions, re-parenting, and above all a content sync,
which deletes and re-inserts a whole pack inside one transaction. Miss one path
and search quietly returns cards that no longer exist, or misses cards that do —
a bug nobody notices until they search for something. A scan over the snapshot
cannot disagree with the library, because it *is* the library.

It also keeps the diacritic folding in one place. FTS5's default tokenizer does
not fold `é` to `e`, so "resume" would not find "résumé" without a custom
tokenizer, which SQLDelight cannot declare portably.

**What it costs.** Search is O(cards) per keystroke rather than O(log n). At this
scale that is invisible — the four seed packs are 201 cards, and the results are
capped at 40 per section. It would need revisiting at roughly ten thousand cards,
and the seam is already in the right place: `LibrarySearch.search` takes a
snapshot and returns sections, so swapping its body for a query changes nothing at
the call site.

---

## D36. The checker learns from an override, and rescues a close paraphrase

**Decision.** Saying "I was right" on a typed answer stores that wording beside the
card in a new `learned_answer` table, and it is matched ahead of key points from
then on. Separately, when key-point coverage says INCORRECT for a card that *has*
key points, the answer is compared with the model answer as a whole and rescued to
PARTIAL if the cosine similarity reaches 0.75.

**Why learning belongs beside the card, not in it.** A built-in card is read-only
and a pack re-import replaces its rows, so anything stored on the card would be
wiped by the next sync — the exact data-loss the separation of built-in and user
content exists to prevent. `learned_answer` is keyed by card id, is written by the
user and never by a sync, and survives both a content update and an edit of the
card itself.

**Why only typed answers learn.** A multiple-choice or tile verdict is not in
doubt: the app knows which option was tapped and whether the tiles were in order,
so an override there is the user disputing something the app can verify. Learning
from it would store noise. A typed answer is the only case where the app is
guessing and the user has information it does not.

**Why the rescue stops at PARTIAL.** Key points are authored evidence about what an
answer must contain. A high whole-answer similarity is evidence the answer is
*probably* right, which is worth not marking wrong, but it is not evidence that it
is right — otherwise a card with carefully chosen key points would grade no
differently from one with none. The negation guard still runs afterwards, so a
similarly worded contradiction is capped back at INCORRECT; there is a test for
exactly that.

**What it costs.** A wrong "I was right" is now permanent for that card until the
row is deleted, and there is no UI yet for reviewing or clearing what has been
learned. That belongs in Settings, beside the card's other data. The rescue also
means two answers can reach PARTIAL by different routes, so a verdict's `reason`
has to be read to know which — `Similarity` rather than `KeyPointCoverage`.

**The schema.** `learned_answer` is created by `Schema.create` on a fresh database
and by an idempotent `CREATE TABLE IF NOT EXISTS` in `AppGraph`'s init for one that
already exists, because `Schema.create` only runs against an empty file and an
existing install would otherwise fail with "no such table" the first time an
override was learned. A SQLDelight `.sqm` migration is the orthodox answer and was
not used: versioned migrations need a checked-in schema directory and a migration
task that verifies against it, and neither can be run or verified here (D1). The
DDL is duplicated in `AppGraph` with a comment saying it must match the `.sq` file,
which is the honest cost of that choice.

## D37. Session writes happen off the UI thread

**Decision.** `persistSchedule`, `recordReview` and `onLearn` are handed to
`Dispatchers.Default` inside `AppState`, rather than being called directly by
`StudySession.commit`.

**Why.** Session events arrive from the UI thread, so every answer was doing SQLite
writes on the thread that is animating the feedback. It was invisible in tests,
which have no UI thread, and would have shown up as jank on a slow Android device
at exactly the moment the user is reading their result. The counters and the state
machine stay synchronous, so the screen updates immediately; only the disk moves.

**What it costs.** Writes for two very fast answers could in principle complete out
of order. Each is an independent row keyed by card id and each review log entry is
append-only, so ordering between them carries no meaning.

## D38 — Tiles are graded by order, not by absolute position (2026-10-07)

A reported card had the answer `The mole mol`. Choosing `mole mol` scored 0% and was
marked Incorrect: `TileGrader` compared each chosen tile with the solution tile at the
same *index*, so omitting one leading word shifted every remaining tile out of place.
That was the worst possible feedback for a substantially right answer.

It now scores the longest common subsequence of the chosen tiles against the solution,
divided by the longer of the two lists. Dropping or repeating a word costs one step;
reordering does not score at all (reversing `a b c` gives 1/3). `CORRECT` is still only
an exact match, and the extra decoy no longer forces `INCORRECT` — it lands in
`PARTIAL`, which is what the verdict text and the review screen describe as "the right
answer plus an extra". The reason still reports `correctPositions`, which is now the
subsequence length, and the message was reworded to "tiles were in the right order" so
the number means what it says.

## D39 — Multiple-choice feedback keeps the options on screen (2026-10-07)

After answering an MCQ the options were replaced by a one-line verdict ("Correct.
Option 1 is correct.") with no way to read what option 1 was. The review state now
re-renders all four options: the correct one in the success colours with a tick, the
one that was chosen in the error colours with a cross if it was wrong, the rest muted.
The selection comes from `VerdictReason.McqSelection`. The "Model answer" panel also
shows `mcq.correct` rather than `card.back` for MCQs, since an authored pack is allowed
to make them differ.

## D40 — The whole app is padded by the safe-drawing insets (2026-10-07)

On Android the shell drew under the status bar and the display cut-out. `App` now wraps
`AppShell` in `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` *inside* the
full-bleed `Surface`, so the background still runs edge to edge while the content stays
clear of the system bars. The inset is zero on desktop and JVM, so the one code path
serves both and no `expect`/`actual` was needed.

## D41 — Device identity, and why last-write-wins needs it (2026-10-07)

Sync resolves a disagreement between two copies of a record by `updatedAt`, with
`deviceId` as the tiebreak. The tiebreak is not a formality: two devices can
write in the same millisecond, and if each then decided that *it* had won, the
record would be re-uploaded by both forever. Comparing device ids is arbitrary
but total and symmetric, so both devices reach the same verdict from the same
two copies. That symmetry, plus comparing content hashes before comparing
clocks, is what makes a second sync run a no-op.

## D42 — What syncs, and what deliberately does not (2026-10-07)

`RecordType` covers cards, topics, tags, card-tag links, card state and learned
answers. Two things are excluded on purpose. Content packs never sync: they are
fetched from the `content` branch, and letting user data carry them would break
the guarantee that a sync cannot touch installed content (or that a content
update cannot touch progress). Device settings never sync either — a retention
target, a theme choice and a last-sync timestamp belong to the device, and
syncing them would make one phone's preference silently override another's. A
test asserts no future record type can add `pack` or `setting` without someone
noticing.

## D43 — `PayloadCodec` ships as the identity (2026-10-07)

Records sit in a folder the user chose, on a machine they own, so the codec that
ships is `PlainPayloadCodec`: payloads are stored as produced. Keeping them
readable means a sync problem can be diagnosed by opening a file, which matters
more than confidentiality for data that never leaves the device. The interface
exists because that trade reverses the moment a provider puts bytes on someone
else's hardware; the codec id is written into the manifest so a location stored
one way is refused rather than misread by a build configured another way.

## D44 — The test double is a real provider, not a mock (2026-10-07)

`InMemorySyncProvider` implements `SyncProvider` for real and is injected
wherever one is wanted. A mock told which answers to return would have proved
only that the engine calls the methods the test expected; this proves the merge
rules against something with the same behaviour as a backend. It also carries
the two things a stub usually forgets and a real backend always does: a `fault`
hook that makes any verb fail on demand (so a run that dies mid-upload is
tested, not assumed safe), and `hiddenIds`, which makes a record appear in a
listing but vanish when fetched — exactly what an eventually consistent store,
or a concurrent delete on the other device, looks like.

## D45 — The review log is the authority; the schedule is a cache (2026-10-07)

Reviews are stored as append-only `.jsonl` segments, one file per device per
month, and a card's schedule can always be thrown away and recomputed from the
merged log by `ScheduleRebuilder`. That inverts the usual priority: instead of
merging `card_state` rows carefully and hoping, the app merges events (which
only ever grow, so merging is deduplication) and derives state from the result.
Two devices that end up with the same history necessarily end up with the same
schedule, which is the property that makes sync converge rather than drift.

Monthly segments rather than one file per device because appending a new month
can never conflict with another device editing the same bytes -- the property
that matters most on the worst transport supported, a folder watched by a cloud
desktop client. A partially copied segment loses its tail, not its head.

`LoggedReview.eventId` is derived from the review (device, card, instant,
rating, mode) rather than generated, so the same review arriving twice -- a
re-uploaded segment, or two devices that both hold it -- is stored once. Without
that, every replay would double the interval. `escalation` is carried in the
line because the mode ladder is advanced by the study session rather than the
scheduler, so replaying ratings alone would not reproduce it.

Log lines carry enums as strings resolved by hand rather than by the
serializer, and a line that cannot be understood is skipped and counted in
`unreadable`. A review from a newer build, or a truncated tail from an
interrupted copy, must cost one review rather than the history.

## D46 — A folder is a legitimate sync backend, and needs no API (2026-10-07)

`LocalFolderSyncProvider` implements `SyncProvider` over a directory the user
chose. Pointed at a Google Drive, OneDrive, Dropbox or Syncthing folder it gives
cloud sync without this app ever speaking to a cloud API -- the user's own sync
client moves the bytes. That is why Google Drive is not a priority: the case it
serves is already covered, and covered without an OAuth client id, a consent
screen, or a token in the Keystore.

`SyncStorage` is the whole of the platform-specific part: relative paths, list,
read, write, delete, append. No `File`, `Uri` or `ContentResolver` reaches
`commonMain`, so the provider is written once and tested against a map.

Two things follow from a folder having no API, and both are handled rather than
hoped away. Listing headers means reading the files, because a filename carries
no timestamp -- the price of the transport, and the reason records are small and
sharded. And a file can be listed before its bytes arrive, or vanish between a
listing and a read, so `recordAt` returns null for anything missing, empty,
corrupt or from a newer build: on a folder watched by a cloud client that is
normal, and one unreadable record must not abort a sync.

Appending a log segment is a read-modify-write, which would be unsafe under
concurrent writers and is safe here because segments are per device: exactly one
device ever appends to a given segment. That is a second reason the log is split
per device rather than per library.

## D47 — Lessons choose their format; word tiles are retired (2026-10-10)

The learner chooses a topic scope and session size, not a question mode. The
session builder selects per card: typed answers for numeric, expression and
key-point cards; multiple choice for plain-text cards with plausible options; and
self-rated flashcards when automatic grading would be weak. Successful MCQ
reviews can still progress to typed recall. Word-tile answers are no longer
available in the editor or study UI, are never selected automatically, and are
rejected by the active question factory. The legacy enum, database column, card
field and stored history are left in place so pre-existing packs and local data
continue to load without conversion.

The default theme is now the brighter Playful palette, with Ink & Paper as an
alternative and independent system/light/dark appearance controls. Weekly quest
points are derived from distinct review days rather than stored as a second
wallet, and the Stats topic tree is explicitly a practice-coverage prototype,
not a curriculum mastery score. No existing question packs or V2 lesson format
are changed here.
