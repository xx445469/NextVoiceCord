# Changelog

All notable changes to NextVoiceCord.

The format is loosely [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions
follow [semantic versioning](https://semver.org/spec/v2.0.0.html). Dates are ISO 8601.

## [Unreleased]

### Fixed

- **The desktop window rendered Chinese, Japanese and Korean as empty boxes on Linux.** The
  interface font was chosen from a list of platform families — Inter, Ubuntu, Cantarell, Noto
  Sans — none of which contain CJK glyphs, and nothing checked whether the chosen font could
  draw the language on screen. (Noto Sans CJK is a different family from Noto Sans; having the
  latter says nothing about the former.) macOS was unaffected only because its system font
  happens to cover CJK, which is why it went unnoticed. The font is now validated against real
  text in the selected language, falls through to CJK-capable families, and finally to the
  logical family — the only one the JVM composes across several fonts to cover missing glyphs.
  The same check applies on Windows, where Segoe UI has the same gap.

- **The server-wide language could not be set at all.** Folding `/language` into `/settings`
  moved the personal half and left the server half behind, so `Settings.setLanguage` had no
  caller. `/settings` now carries a language menu in its server view. A test names that control
  specifically, because counting components tells you a row went missing but not which one.

## [0.8.0-beta.1] — 2026-08-26

First release under this name. Beta because it pins an unreleased `youtube-source` build: the
published releases cannot play a large share of videos.

### Added

- **Vocard-style controller panel.** Data-driven button layout, multi-state buttons and
  template-rendered embeds, editable per guild with `/controller`. The template syntax is
  Vocard's, so a layout written for Vocard renders here unchanged.
- **Twelve languages**, resolved per user, then per guild, then the global default. Eleven are
  machine-generated and carry `_meta.reviewed: false`; only English has been verified.
- **A rebuilt desktop window** — live status, console, per-guild playback diagnostics, system
  health, source health, preferences and config editing.
- **A web panel** (`--web <port>`) with the same eight views, plus playback control. It binds
  `127.0.0.1` by default; config editing is off by default and can never write the Discord
  token, the bind address, or its own permission to write.
- **`/help` as a category menu** rather than one flat block of text.
- **Self-updating releases**, off by default.
- **Daily automated `youtube-source` updates**, gated on real playback rather than on the build
  merely compiling.
- **`--nogui` and `--web`** launch flags.
- Desktop preferences (theme, font size, language) are saved to `config.txt` instead of being
  discarded at exit.

### Changed

- `/settings` is no longer admin-only. What someone may change is decided per control: server
  settings for anyone who can manage the server, personal settings for everyone else.
- Releases are published by pushing a `v*` tag. The tag is checked against `pom.xml` and the
  full suite runs first; a mismatch fails loudly rather than shipping a jar whose version
  contradicts its tag.

### Removed

- `/language`. Its two halves live in `/settings` now — one command for settings, not two.

### Security

- `config.txt` backups are excluded from version control. The bot writes one on every save and
  they contain the Discord token verbatim; `/config.txt` alone did not match them.
- The web panel accepts a query-string token for reads but requires an `Authorization` header
  for anything that changes state, so a link cannot rewrite someone's configuration.

[Unreleased]: https://github.com/xx445469/NextVoiceCord/compare/v0.8.0-beta.1...HEAD
[0.8.0-beta.1]: https://github.com/xx445469/NextVoiceCord/releases/tag/v0.8.0-beta.1
