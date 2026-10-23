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
# Checks, on keys.openpgp.org, that the release signing key and its signing subkey are published,
# not revoked and not expired. Gradle signs with an expired key as well, and users verify the
# signatures against the published key, so a release must not start with a key that fails there.
set -euo pipefail

fingerprint="${1:?usage: check-signing-key.sh <key fingerprint> <signing subkey id>}"
subkey="${2:?usage: check-signing-key.sh <key fingerprint> <signing subkey id>}"
fingerprint="${fingerprint^^}"
subkey="${subkey^^}"

home="$(mktemp -d)"
trap 'rm -rf "$home"' EXIT

if ! key="$(curl -fsS --retry 3 "https://keys.openpgp.org/vks/v1/by-fingerprint/$fingerprint")"; then
  echo "key $fingerprint is not on keys.openpgp.org" >&2
  exit 1
fi
if ! errors="$(gpg --homedir "$home" --batch --quiet --import <<<"$key" 2>&1)"; then
  echo "keys.openpgp.org answered with no key that gpg can import for $fingerprint:" >&2
  echo "$errors" >&2
  exit 1
fi
# keys.openpgp.org serves a key without its user IDs until the address is verified, and gpg skips
# such a key, as it would for a user who follows SECURITY.md.
if ! listing="$(gpg --homedir "$home" --batch --with-colons --fixed-list-mode --list-keys "$fingerprint" 2>/dev/null)"; then
  echo "key $fingerprint is not on keys.openpgp.org, or is there without a verified user ID" >&2
  exit 1
fi

now="$(date +%s)"

# Fails unless a pub or sub line of the listing is valid now. Field 2 is the validity, 5 the key
# id, 7 the expiry (empty if none) and 12 the capabilities.
check() {
  local name="$1" line="$2" validity expiry
  validity="$(cut -d: -f2 <<<"$line")"
  expiry="$(cut -d: -f7 <<<"$line")"
  case "$validity" in
    r) echo "$name is revoked" >&2; exit 1 ;;
    e) echo "$name has expired" >&2; exit 1 ;;
    i | d) echo "$name is not valid" >&2; exit 1 ;;
  esac
  if [[ -n "$expiry" && "$expiry" -le "$now" ]]; then
    echo "$name expired on $(date -u -d "@$expiry" +%F)" >&2
    exit 1
  fi
  if [[ -n "$expiry" ]]; then
    echo "$name is valid until $(date -u -d "@$expiry" +%F)"
  else
    echo "$name does not expire"
  fi
}

check "key $fingerprint" "$(grep -m1 '^pub:' <<<"$listing")"
line="$(grep -E "^sub:[^:]*:[^:]*:[^:]*:$subkey:" <<<"$listing" || true)"
if [[ -z "$line" ]]; then
  echo "key $fingerprint has no subkey $subkey" >&2
  exit 1
fi
if [[ "$(cut -d: -f12 <<<"$line")" != *s* ]]; then
  echo "subkey $subkey cannot sign" >&2
  exit 1
fi
check "subkey $subkey" "$line"
