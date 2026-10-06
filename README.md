# RevisionApp content branch

This branch holds **content only** - no app code, no build files. The app on
`main` downloads these files over raw GitHub and imports them into its local
database, then works offline.

Everything here is original writing. No past-paper questions, mark-scheme wording,
textbook prose or specification text is reproduced. Every card is marked
`reviewStatus: AI_UNREVIEWED`, which means a human has not yet checked it.

## Layout

```
manifest.json                     index the app fetches first
schema/manifest.schema.json       JSON Schema for manifest.json
schema/pack.schema.json           JSON Schema for packs/<id>/pack.json
schema/cards.schema.json          JSON Schema for packs/<id>/cards/*.json
tools/validate                    the checker GitHub Actions runs on every push
packs/<pack-id>/pack.json         topic tree, tags and the list of card files
packs/<pack-id>/cards/<topic>.json  every card in one topic
packs/<pack-id>/README.md         notes, sources and anything still to verify
```

## Identifiers

The app namespaces built-in content so a sync can never collide with user data:

| thing | id |
| --- | --- |
| topic | `builtin:<pack-id>:<topic-slug>` |
| card  | `builtin:<pack-id>:<topic-slug>:<ordinal>` |
| tag   | `builtin:tag:<group>:<slug>` |

Tags are deliberately **not** namespaced per pack: `board = OCR` means the same
thing in every pack, so the app upserts them by group and slug. Tag slugs must
therefore be unique across all groups inside a pack, because card files refer to
tags by slug alone.

Card `id` values are four-digit ordinals that only have to be unique within
their own topic - `0001`, `0002`, ... in each card file.

## Checksums

`manifest.json` promises a `sha256` and a `cardCount` for every pack. The app
downloads `pack.json` plus each declared card file, recomputes the hash and
refuses to import if it disagrees, leaving the previously installed version
untouched.

The hash covers `pack.json` followed by every card file in sorted file-name
order, each terminated by a single newline:

```
sha256( pack.json + "\n" + cards/a.json + "\n" + cards/b.json + "\n" )
```

Files are hashed byte for byte as served, so never let an editor rewrite line
endings or add a trailing blank line without re-running the updater. Both the
app (`composeApp/.../crypto/PackHasher.kt`) and `tools/validate` implement this
identically.

## Escaping LaTeX in JSON

This is the one rule that silently corrupts content, so it is worth stating twice.

**A LaTeX backslash must be written `\\` inside a JSON string.** JSON reads `\`
as the start of an escape sequence, and several LaTeX commands begin with a letter
that is also a JSON escape:

| written in JSON | JSON decodes it to | the card then says |
| --- | --- | --- |
| `"\theta"` | tab + `heta` | a gap, then "heta" |
| `"\nu"` | line feed + `u` | a line break, then "u" |
| `"\frac{a}{b}"` | form feed + `rac{a}{b}` | "rac{a}{b}" |
| `"\beta"` | backspace + `eta` | "eta" |
| `"\Rightarrow"` | **invalid escape** | the file will not parse at all |

Correct:

```json
"back": "$\\frac{x^{2}}{2}$, so $P \\Rightarrow Q$"
```

The corruption is invisible in a diff and easy to miss by eye, so `tools/validate`
checks for it after decoding: backspace, form feed and carriage return are errors
anywhere in authored text, tab and line feed are errors inside a `$...$` span and
warnings elsewhere, an odd number of `$` is an error, and unbalanced braces inside
a span are an error. `tools/fixtures/` holds one file per defect plus a valid
control, and every run of the validator checks them, so a rule that stops firing
fails CI rather than quietly letting corrupt content through.

Two further conventions:

- Bare `x^2` and `log_3` outside `$...$` are rendered correctly - the app's parser
  does not require delimiters - but wrapping real maths in `$...$` says plainly
  what is maths and what is prose, and is what a future typesetter would need.
- `$` is also the currency symbol. The app strips every `$` when it parses, so a
  price in a card should be written out ("5 dollars") rather than as "$5".

## Validating

```bash
python3 -m pip install jsonschema     # optional but recommended
tools/validate                        # check everything, fails on errors
tools/validate --pack tmua            # check one pack
tools/validate --strict               # also fail on warnings
tools/validate --selftest             # only prove the text rules fire on the fixtures
tools/validate --update-hashes        # recompute sha256 and cardCount
```

The GitHub Action `Content` runs `tools/validate` on every push and pull request
to this branch, then recomputes the hashes and fails if `manifest.json` is
stale.

Errors (these fail CI):

- a file that does not match its schema
- a `packId`, `name` or `version` that disagrees with the manifest
- duplicate or malformed topic slugs, tag slugs or card ordinals
- a topic parent that does not exist, or a parent cycle
- a card file whose name does not match its `topic` field
- a card file on disk that `cardFiles` does not declare (the app would never see it)
- a tag reference that resolves to nothing
- multiple choice without exactly one correct answer and three distinct distractors
- a `tileAnswer` that does not reconstruct the model answer
- a `NUMERIC` card with no expected value anywhere
- fewer than five cards in a leaf topic
- a `sha256` or `cardCount` that does not match the files
- a control character in authored text, which means a LaTeX backslash was written
  single and JSON ate it
- an odd number of `$`, or unbalanced braces inside a `$...$` span
- a fixture in `tools/fixtures/` that no longer behaves as intended

Warnings (reported, and fatal under `--strict`):

- a `TEXT` card with no key points, which falls back to similarity grading
- a multiple-choice card with no explanation
- a model answer long enough that the app will skip tile mode
- a card marked `REVIEWED`

## Adding a pack

1. Pick an id: lowercase, digits and hyphens, e.g. `aqa-a-level-chemistry`.
2. Create `packs/<id>/pack.json` with `packId`, `name`, `version: 1`, the topic
   tree (`slug`, `name`, `parent`, `sortOrder`), the `tags` the pack uses
   (`slug`, `group`, `name`) and `cardFiles`.
3. Create one `packs/<id>/cards/<topic-slug>.json` per topic that holds cards.
   The file name must be the topic slug, and the `topic` field must repeat it.
   Give every card a four-digit `id`, a `front`, a `back` and an `answerType`.
   Add `keyPoints` for `TEXT` cards, `numeric` for `NUMERIC` cards, `mcq` with
   three authored distractors where a plausible wrong answer exists, and an
   `explanation` for anything non-obvious.
4. Aim for at least five cards per leaf topic, more for the core ones, and mix
   definitions, equations with units, "explain why" questions, worked numerics
   and common misconceptions.
5. Write `packs/<id>/README.md` saying what the pack covers and, if the topic
   tree was built from memory rather than from the published specification, set
   `"structureVerified": false` and list what needs checking. The validator
   requires that README when the flag is false.
6. Add the pack to `manifest.json` (`id`, `name`, `version`, `path`,
   `sha256`, `cardCount`) and run `tools/validate --update-hashes` to fill the
   hash and count in.
7. Run `tools/validate` until it is clean, then commit and push. The `Content`
   action will check it again.

To change a pack later, edit its files, bump its `version` in both `pack.json`
and `manifest.json`, and re-run `--update-hashes`. The app compares version and
hash, downloads only what changed, and never touches user content or study
progress while importing.

## Private repositories

The app's default base URL is
`https://raw.githubusercontent.com/Alik-Green/RevisionApp/content`, which only
works while this repository is public. Serving a private branch needs an
authenticated URL - a fine-grained personal access token embedded in the base
URL, or a proxy that adds the header. The base URL is editable on the app's
settings screen; the app itself stores no credentials.
