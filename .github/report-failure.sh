#!/usr/bin/env bash
# Posts the interesting parts of the captured Gradle logs as a commit comment.
#
# The CI sandbox this project is developed from can reach api.github.com but not
# results-receiver.actions.githubusercontent.com, so `gh run view --log` cannot
# be used to read a failed build. Commit comments travel over api.github.com and
# are therefore the one channel that works in both directions.
set -uo pipefail

JOB="${1:-unknown}"
MAX_BYTES=60000

BODY="$(mktemp)"

{
  echo "### CI failure — \`${JOB}\`"
  echo
  echo "Run: ${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
  echo

  shopt -s nullglob
  for f in logs/*.log; do
    if grep -qE "BUILD SUCCESSFUL" "$f" 2>/dev/null; then
      continue
    fi
    echo "#### \`$f\`"
    echo
    echo '```text'
    # Kotlin/Java compiler errors, Gradle's own diagnostics and the failure
    # summary are the parts worth reading; everything else is progress noise.
    grep -nE "^(e|w): |error:|FAILURE:|What went wrong|Caused by:|Execution failed|A problem occurred|Could not |Unresolved reference|Deprecated Gradle|Compilation error|FAILED|^\* |^> Task .* FAILED" "$f" \
      | head -n 80
    echo "--- tail ---"
    tail -n 100 "$f"
    echo '```'
    echo
  done
} > "$BODY"

# GitHub caps comment bodies; keep the head (which holds the compiler errors).
if [ "$(wc -c < "$BODY")" -gt "$MAX_BYTES" ]; then
  head -c "$MAX_BYTES" "$BODY" > "$BODY.trim" && mv "$BODY.trim" "$BODY"
fi

gh api "repos/${GITHUB_REPOSITORY}/commits/${GITHUB_SHA}/comments" \
  -f body="$(cat "$BODY")" >/dev/null && echo "Posted failure log to ${GITHUB_SHA}"

rm -f "$BODY"
