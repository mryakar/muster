#!/usr/bin/env bash
#
# Copyright 2026 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
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
