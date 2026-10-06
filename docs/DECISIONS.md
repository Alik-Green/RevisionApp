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

## D19. The `content` branch is created with git plumbing from this working branch

**Decision.** The orphan `content` branch is built with `git hash-object` /
`git mktree` / `git commit-tree` and pushed as `refs/heads/content`. The working
checkout never leaves `arena/ecfda5a7-revisionapp`.

**Why.** The session is pinned to one working branch, but the brief requires a
separate orphan branch that contains *only* content. Plumbing commands produce
exactly that — a rootless commit whose tree has no app code — without switching
or creating a second working checkout.
