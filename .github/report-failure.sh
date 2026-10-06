#!/usr/bin/env bash
# Posts the interesting parts of a failed CI run as a commit comment.
#
# The sandbox this project is developed from can reach api.github.com but not
# results-receiver.actions.githubusercontent.com, so `gh run view --log` cannot
# read a failed build. Commit comments travel over api.github.com and are
# therefore the one channel that works in both directions.
set -uo pipefail

JOB="${1:-unknown}"
MAX_BYTES=60000

BODY="$(mktemp)"

{
  echo "### CI failure — \`${JOB}\`"
  echo
  echo "Run: ${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
  echo

  # Test failures straight out of the JUnit XML: message plus the first lines of
  # the stack, which is where the assertion detail lives.
  shopt -s nullglob
  for xml in composeApp/build/test-results/*/TEST-*.xml; do
    python3 - "$xml" <<'PY'
import sys
import xml.etree.ElementTree as ET

tree = ET.parse(sys.argv[1])
for case in tree.getroot().iter("testcase"):
    problems = list(case.iter("failure")) + list(case.iter("error"))
    if not problems:
        continue
    print(f"FAILED TEST {case.get('classname')}.{case.get('name')}")
    for problem in problems:
        text = (problem.get("message") or "") + "\n" + (problem.text or "")
        print("\n".join(text.splitlines()[:10]))
    print()
PY
  done

  # ktlint writes one line per violation into its own report, and none of them
  # match the compiler-error patterns below, so they need their own section.
  # Colours are stripped and generated sources dropped: the report is capped, and
  # spending that budget on build/generated would hide real violations.
  shopt -s nullglob
  for report in */build/reports/ktlint/*/*.txt; do
    [ -s "$report" ] || continue
    echo "#### \`$(basename "$report")\`"
    echo
    echo '```text'
    sed -e "s|${GITHUB_WORKSPACE:-/home/runner/work/RevisionApp/RevisionApp}/||" \
        -e 's/\x1b\[[0-9;]*m//g' "$report" |
      grep -v "/build/generated/" | head -n 400
    echo '```'
    echo
  done

  for f in logs/*.log; do
    if grep -qE "BUILD SUCCESSFUL" "$f" 2>/dev/null && ! grep -qE "FAILED" "$f" 2>/dev/null; then
      continue
    fi
    echo "#### \`$f\`"
    echo
    echo '```text'
    grep -nE "^(e|w): |error:|FAILURE:|What went wrong|Caused by:|Execution failed|A problem occurred|Could not |Unresolved reference|Compilation error|FAILED|Lint error|MISSING:|^\* " "$f" \
      | head -n 100
    # Diagnostic logs written by the workflows themselves put their conclusion at
    # the top, and the grep above matches none of their lines, so show the head as
    # well as the tail or the useful part is lost to the byte cap.
    echo "--- head ---"
    head -n 60 "$f"
    echo "--- tail ---"
    tail -n 80 "$f"
    echo '```'
    echo
  done
} > "$BODY"

# GitHub caps comment bodies; keep the head, which holds the compiler errors.
if [ "$(wc -c < "$BODY")" -gt "$MAX_BYTES" ]; then
  head -c "$MAX_BYTES" "$BODY" > "$BODY.trim" && mv "$BODY.trim" "$BODY"
fi

gh api "repos/${GITHUB_REPOSITORY}/commits/${GITHUB_SHA}/comments" \
  -f body="$(cat "$BODY")" >/dev/null && echo "Posted failure log for ${GITHUB_SHA}"

rm -f "$BODY"
