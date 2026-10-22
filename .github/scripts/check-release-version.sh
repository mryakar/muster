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
# version is not a snapshot, and that CHANGELOG.md has a section for it. Prints the version.
set -euo pipefail

tag="${1:?usage: check-release-version.sh <tag>}"
version="$(sed -n 's/^version=//p' gradle.properties)"

if [[ "$tag" != "v$version" ]]; then
  echo "tag $tag does not match version $version in gradle.properties" >&2
  exit 1
fi
if [[ "$version" == *-SNAPSHOT ]]; then
  echo "cannot release snapshot version $version" >&2
  exit 1
fi
if ! grep -q "^## \[$version\]" CHANGELOG.md; then
  echo "CHANGELOG.md has no section for $version" >&2
  exit 1
fi
echo "$version"
