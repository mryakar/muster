# Security Policy

## Supported versions

Security fixes go into the latest minor release, currently 0.2.x. Upgrade to the latest release
to get them.

## Verifying a release

Releases on Maven Central and on GitHub are signed with this OpenPGP key, published on
[keys.openpgp.org](https://keys.openpgp.org/search?q=4C3AF91B1B7F67BD50D28A406CCE03949E2A4A49):

```text
4C3A F91B 1B7F 67BD 50D2  8A40 6CCE 0394 9E2A 4A49
```

To check a file against its `.asc` signature:

```bash
gpg --keyserver hkps://keys.openpgp.org --recv-keys 4C3AF91B1B7F67BD50D28A406CCE03949E2A4A49
gpg --verify muster-core-0.2.0.jar.asc muster-core-0.2.0.jar
```

Releases are signed with the key's signing subkey, `46BA04A6815A97C7`; `gpg` names the subkey
and reports the fingerprint above as the primary key's.

## Reporting a vulnerability

Please do not report security vulnerabilities in public issues.

Report them privately through GitHub:
[Report a vulnerability](https://github.com/mryakar/muster/security/advisories/new).

Include the affected version, a description of the issue and, if possible, steps to reproduce it.
You will get a response as soon as possible, and you will be kept informed while a fix is prepared.
