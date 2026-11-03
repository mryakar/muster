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
# Puts the Javadoc of the documentation site under <site>/api/<version>/<module>/: for each stable
# release whose tag has documentation, the javadoc jars of its GitHub Release, which the release
# built from the tag and checked against the tested build, so the site never builds them again; for
# next, the Javadoc of the checked-out commit. Needs the tags, and gh with a token that can read the releases.
set -euo pipefail

site="$(realpath -m "${1:?usage: javadoc-site.sh <site directory>}")"
cd "$(dirname "$0")/../.."
modules=(muster-core muster-spring-boot-starter)

tasks=()
for module in "${modules[@]}"; do
  tasks+=(":$module:javadoc")
done
./gradlew --quiet "${tasks[@]}"
for module in "${modules[@]}"; do
  rm -rf "$site/api/next/$module"
  mkdir -p "$site/api/next/$module"
  cp -R "$module/build/docs/javadoc/." "$site/api/next/$module/"
done

jars="$(mktemp -d)"
trap 'rm -rf "$jars"' EXIT
gh release list --exclude-drafts --exclude-pre-releases --limit 1000 --json tagName --jq '.[].tagName' |
  while read -r tag; do
    # A release from before the documentation site has no version on the site.
    if ! git cat-file -e "$tag:docs/antora.yml" 2>/dev/null; then
      continue
    fi
    version="${tag#v}"
    assets="$(gh release view "$tag" --json assets --jq '.assets[].name' </dev/null)"
    for module in "${modules[@]}"; do
      jar="$module-$version-javadoc.jar"
      if ! grep -qxF "$jar" <<< "$assets"; then
        echo "the release $tag has no $jar" >&2
        exit 1
      fi
      gh release download "$tag" --pattern "$jar" --dir "$jars" --clobber </dev/null
      rm -rf "$site/api/$version/$module"
      mkdir -p "$site/api/$version/$module"
      unzip -q "$jars/$jar" -x "META-INF/*" -d "$site/api/$version/$module"
    done
  done
