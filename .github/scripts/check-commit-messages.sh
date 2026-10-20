#!/usr/bin/env bash
# Checks that every commit in <base>..<head> follows Conventional Commits
# and that the range contains no merge commits.
set -euo pipefail

base=$1
head=$2
pattern='^(feat|fix|docs|refactor|chore|test|build|ci|perf)(\([a-z0-9-]+\))?!?: [^ ].*$'
failed=0

while IFS=$'\x1f' read -r sha parents subject; do
  if [[ $parents == *" "* ]]; then
    echo "::error::$sha is a merge commit; rebase onto the base branch instead."
    failed=1
  elif ! [[ $subject =~ $pattern ]]; then
    echo "::error::$sha does not follow Conventional Commits: $subject"
    failed=1
  fi
done < <(git log --format='%H%x1f%P%x1f%s' "$base..$head")

if ((failed)); then
  echo "Allowed types: feat, fix, docs, refactor, chore, test, build, ci, perf."
  echo "Format: <type>(<optional-scope>)!: <description>"
fi
exit "$failed"
