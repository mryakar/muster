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
# Runs the consumer test against the Muster artifacts in the local Maven repository, once with the
# Gradle module metadata and once with the POMs only, as Maven reads them. The version defaults to
# the one in gradle.properties.
set -euo pipefail

version="${1:-$(sed -n 's/^version=//p' gradle.properties)}"
./gradlew -p consumer-test run -PmusterVersion="$version"
./gradlew -p consumer-test run -PmusterVersion="$version" -PpomOnly
