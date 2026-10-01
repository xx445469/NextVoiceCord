# Vendored Maven artifacts

A byte-exact copy of the one dependency this project cannot re-obtain if its
upstream repository goes away: the `youtube-source` build that `pom.xml` pins.

## Why this exists

`pom.xml` pins `youtube-source.version` to
`f45bbb7aebfcbc1c553769e04af6cd43afa8b7c3-SNAPSHOT`, because the published
releases cannot play a large share of videos and this commit can. That pin is
exact, so builds are reproducible — but only while the artifact stays on
`maven.lavalink.dev/snapshots`, and snapshot repositories prune.

Two things made that stop being a theoretical risk:

1. **`maven.lavalink.dev/releases` is gone.** Not one missing file — the whole
   repository. Reposilite answers
   `{"status":404,"message":"Repository releases not found"}` for it, while
   `/snapshots` serves normally. Upstream's README still documents the
   `/releases` URL, so this looks like breakage rather than a migration, and it
   has happened before (youtube-source#100, opened and closed the same day in
   January 2025).
2. **Releases have stalled.** The last one was `1.18.2` on 2026-07-27, while
   commits continued into September. Asked about it in youtube-source#244, the
   maintainer explained there is no time to test or review, and no fixed release
   schedule. The project is alive, but the release channel is not moving.

So the artifact this build depends on lives in exactly one place, published by a
project whose other repository already vanished once. This directory is the
second place.

## What is here

`dev.lavalink.youtube:v2` and the `dev.lavalink.youtube:common` it depends on,
each as `.jar`, `-sources.jar` and `.pom`, in standard Maven repository layout.
Javadoc is omitted — it has no bearing on a build.

Every file sits next to the `.sha256` published alongside it upstream, and each
one was verified against that checksum when it was downloaded. To re-verify:

```bash
cd vendor/m2
find . -name '*.sha256' | while read -r f; do
  shasum -a 256 -c <(printf '%s  %s\n' "$(tr -d ' \n' < "$f")" "${f%.sha256}")
done
```

The other two runtime dependencies `v2` declares — `org.mozilla:rhino-engine`
and `com.grack:nanojson` — are on Maven Central and are not vendored.

## Using it

Nothing in `pom.xml` points here yet; the build still resolves from
`maven.lavalink.dev/snapshots` as before. This directory is insurance, not a
change in how the project builds.

If upstream does prune the artifact, either add this as a repository:

```xml
<repository>
    <id>vendored</id>
    <url>file://${project.basedir}/vendor/m2</url>
    <snapshots><enabled>true</enabled></snapshots>
</repository>
```

or install the files into a local repository directly:

```bash
mvn install:install-file \
  -Dfile=vendor/m2/dev/lavalink/youtube/common/f45bbb7aebfcbc1c553769e04af6cd43afa8b7c3-SNAPSHOT/common-f45bbb7aebfcbc1c553769e04af6cd43afa8b7c3-20260819.163356-1.jar \
  -DpomFile=vendor/m2/dev/lavalink/youtube/common/f45bbb7aebfcbc1c553769e04af6cd43afa8b7c3-SNAPSHOT/common-f45bbb7aebfcbc1c553769e04af6cd43afa8b7c3-20260819.163356-1.pom
# then the same for v2
```

## Licence

`youtube-source` is MIT, Copyright (c) 2024 devoxin. The licence text is in
`dev/lavalink/youtube/LICENSE`, as MIT requires it to accompany copies. These
files are redistributed unmodified.
