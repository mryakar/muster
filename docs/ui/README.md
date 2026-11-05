# UI bundle

`ui-bundle.zip` is the [default UI of Antora](https://gitlab.com/antora/antora-ui-default), as its
`bundle-stable` job built it from commit `0e38223adfd81eb74d4b1779e158f8ad05ff8923` (16 July 2026).
It is committed so that every build of the site uses the same UI, offline and without trusting a
download at build time.

SHA-256: `e0ac81aad26961a9cd3cf9bce692a598eff4a1bd2822ce73c9cdc13a15a1e70d`

To update it, download the bundle of the stable job, record its commit and checksum here, and
check that the site still builds and reads well:

```bash
curl -sSL -o docs/ui/ui-bundle.zip \
  'https://gitlab.com/antora/antora-ui-default/-/jobs/artifacts/HEAD/raw/build/ui-bundle.zip?job=bundle-stable'
sha256sum docs/ui/ui-bundle.zip
```

`../supplemental-ui` replaces three files of the UI: the header (`partials/header-content.hbs`:
links to the repository, its releases, Maven Central and the same page in the other language, whose
URL the helper `helpers/other-language.js` gives), the article (`partials/article.hbs`: a note at
the top of each page of a prerelease) and the layout (`layouts/default.hbs`: the language of the
page).
