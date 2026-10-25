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
# Checks that a release tag such as v1.2.3 matches the version in gradle.properties, that the
# version is a release version (1.2.3, or a pre-release such as 1.2.3-rc.1), and that CHANGELOG.md
# has a section for it that is not empty. Prints the version.
set -euo pipefail

tag="${1:?usage: check-release-version.sh <tag>}"
version="$(sed -n 's/^version=//p' gradle.properties)"

if [[ "$tag" != "v$version" ]]; then
  echo "tag $tag does not match version $version in gradle.properties" >&2
  exit 1
fi
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-(alpha|beta|rc)\.[0-9]+)?$ ]]; then
  echo "$version is not a release version such as 1.2.3 or 1.2.3-rc.1" >&2
  exit 1
fi
if ! awk -v heading="## [$version]" 'index($0, heading) == 1 { found = 1 } END { exit !found }' \
  CHANGELOG.md; then
  echo "CHANGELOG.md has no section for $version" >&2
  exit 1
fi
# The section becomes the notes of the GitHub Release.
notes="$("$(dirname "$0")/release-notes.sh" "$version")"
if [[ -z "${notes//[[:space:]]/}" ]]; then
  echo "the section for $version in CHANGELOG.md is empty" >&2
  exit 1
fi
echo "$version"
