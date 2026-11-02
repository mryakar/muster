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
# version is a release version (1.2.3, or a pre-release such as 1.2.3-rc.1), that the documentation
# is released with it, and that CHANGELOG.md has a section for it that is not empty. Prints the
# version.
set -euo pipefail
cd "$(dirname "$0")/../.."

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
# The documentation of a stable release is released with it: the release commit drops the
# prerelease key of the main branch and names the version the installation snippets show. A
# pre-release has no version on the site and leaves both alone.
if [[ "$version" != *-* ]]; then
  if grep -qE '^prerelease:' docs/antora.yml; then
    echo "docs/antora.yml marks the documentation as a prerelease; the release commit drops that key" >&2
    exit 1
  fi
  if ! grep -qE "^[[:space:]]*muster-version:[[:space:]]*${version//./\\.}[[:space:]]*$" docs/antora.yml; then
    echo "docs/antora.yml does not set muster-version to $version" >&2
    exit 1
  fi
fi
# The section becomes the notes of the GitHub Release; the script fails if there is none.
notes="$(.github/scripts/release-notes.sh "$version")"
if [[ -z "${notes//[[:space:]]/}" ]]; then
  echo "the section for $version in CHANGELOG.md is empty" >&2
  exit 1
fi
echo "$version"
