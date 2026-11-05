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
# Checks that the Turkish documentation in docs/tr has the same pages under the same paths and the
# same navigation as the English one in docs, and the same version keys, since the header of each
# page links to the same page in the other language, and the release commit changes both. Also
# checks that no Turkish suffix follows code with a straight apostrophe, as in `muster-core`'u,
# which AsciiDoc reads as a curved quote and renders with a stray backtick: the suffix takes the
# typographic apostrophe, as in `muster-core`’u.
set -euo pipefail
cd "$(dirname "$0")/../../docs"

pages() { (cd "$1/modules/ROOT/pages" && find . -name '*.adoc' | sort); }
nav() { grep -o 'xref:[^[]*' "$1/modules/ROOT/nav.adoc"; }
keys() { grep -v -e '^ *#' -e '^name:' -e '^title:' "$1/antora.yml"; }

status=0
for part in pages nav keys; do
  if ! diff -u --label "docs ($part)" --label "docs/tr ($part)" <("$part" .) <("$part" tr); then
    echo "The English and the Turkish documentation differ in their $part." >&2
    status=1
  fi
done
if grep -rnE "\`'[^[:space:]]" tr/modules; then
  echo "A Turkish suffix follows code with a straight apostrophe; use ’ instead." >&2
  status=1
fi
exit "$status"
