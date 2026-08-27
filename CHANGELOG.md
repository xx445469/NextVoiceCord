# Changelog

All notable changes to NextVoiceCord.

The format is loosely [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions
follow [semantic versioning](https://semver.org/spec/v2.0.0.html). Dates are ISO 8601.

## [Unreleased]

## [1.1.0] — 2026-08-27

### Removed

- **`updates.autoUpdate` and `updates.checkIntervalHours` are gone.** Checking now happens every
  hour, always, and is not configurable. An old `config.txt` still carrying either key starts
  normally; the keys are ignored.

  This is a breaking change to the config file, and it changes behaviour for anyone who had
  `autoUpdate = true`: **the bot will no longer restart itself into a new version.** Installing is
  now always a decision someone makes.

### Added

- **Updates is its own place.** A top-level entry in the desktop window's sidebar, and a card in
  the web panel. Both show the current version, check on demand, and offer "Install and restart"
  once a release has been downloaded.

  Pressing check downloads the release as well as looking for it — otherwise a person is told an
  update exists and has no way to act on it until the hourly check happens to catch up.

  If something is playing when install is pressed, both surfaces name the servers that are
  playing and ask whether to continue, rather than refusing silently or cutting listeners off
  without warning. A restart drops every voice connection instantly.

  The web install endpoint is POST, requires the `Authorization: Bearer` header, is refused
  outright when the panel is bound to a non-loopback address unless config editing is already
  permitted, and does nothing without an explicit confirmation in the request body.

### Fixed

- **Commands work in a voice channel's built-in text chat.** They threw before doing anything.
  Messages there arrive as a voice channel, and the command framework's `getTextChannel()` throws
  for one, so every music command failed before validation even ran. Prefix commands, slash
  commands and the queue and playlist buttons were all affected.

  The now-playing message was broken one step further along for the same reason: the channel a
  track was queued from was resolved as a text channel, which returns nothing for voice chat, so
  updates went to the wrong channel or alerted the owner.

- **A Spotify link now says when Spotify is not set up.** Without credentials the source is never
  registered, so links fell through to generic handling and produced "no matches" — the actual
  reason appeared only in a startup log line nobody reads. The reply now names
  `spotify.clientId` and `spotify.clientSecret`.

### Changed

- **Spotify playlists and albums read up to 500 tracks**, up from one page (100 and 50). Anything
  beyond that still reports the true total and says it was capped, as before. The bound stays
  because every Spotify track becomes a YouTube search, so an unbounded playlist would mean an
  unbounded number of searches.

  A page failing part-way through no longer discards the tracks already fetched.

## [1.0.0] — 2026-08-27

First stable release. It carries everything prepared under `0.9.0-beta.1`, which was never
tagged, plus the interface work below.

### Known limitations

Stated here rather than discovered later:

- `youtube-source` is pinned to an unreleased build (commit `f45bbb7`) rather than a published
  release. The pin is exact, so builds are reproducible while that artifact remains on the
  snapshot repository — but snapshot repositories prune, and if it is removed this version can
  no longer be built from source.
- The eleven non-English languages are machine-translated and have not been reviewed by a
  speaker. `_meta.reviewed` is `false` in every one of them.
- `playback.engine = fallback` is accepted, logged as unimplemented, and resolves to
  `lavaplayer`.
- The `lyrics` command is present but does nothing; its dependency was dropped upstream.

### Added

- **The web panel can check for updates.** It never could — the desktop window has had the
  button for a while and the web panel was simply missed. It now has the same card: current
  version, a check button, and the three outcomes reported distinctly, with a link through to
  the release when one exists.

  It checks and links, and does not install. Installing stays with the scheduled updater rather
  than becoming something a network listener will do on request.

  The token is checked before any outbound request is made, so an unauthenticated caller cannot
  use the endpoint to make the bot talk to GitHub. The call runs off the server's thread pool, so
  a stalled connection cannot starve the threads serving every other open tab.

- **Settings categories in the sidebar.** "Preferences" and "Bot config" expand to list their own
  cards, and picking one reveals and scrolls to it. Both pages were previously one long scroll
  behind a single row whose name did not say what it held.

  The categories come from the panels themselves rather than a list kept in step by hand, and a
  test fails the build if a page grows a card the sidebar cannot see.

### Changed

- **The settings pages are considerably denser.** Bot config went from 4246px to 2845px tall at
  940px wide. Short values now pair two to a line once the window is wide enough, falling back to
  one per line when narrow; two long explanatory notes moved behind a details toggle. Every
  category except Lavalink's node editor now fits without scrolling even at the minimum window
  size, and all of them from 1200px up.

  Nothing was removed to achieve this — the token, `dangerous.eval` and `web.allowConfigEdit`
  warnings stay permanently visible.

- **The save bar is one compact row** instead of a card taking roughly a quarter of the content
  area at the minimum window size. The restart hint sits beside the buttons rather than below
  them.

### Fixed

- The German voice-channel card was titled "Sprache" — German for *language* — which collided
  with the Language card and left two sidebar categories reading identically. It is now
  "Sprachkanal".
- In German at the minimum window width, the save bar's restart hint wrapped to a second line
  that was then clipped by the card's edge. The strip is now exactly one row tall at any width.

### Previously prepared under 0.9.0-beta.1


### Added

- **One-click YouTube sign-in.** The desktop window and the web panel now open the Google device
  page with the code already filled in, instead of printing a URL and a code for the owner to
  read out of a console and retype.

  The card reports which of six states the flow is actually in, including the two that used to
  look like a broken button: OAuth switched off (it says what to turn on), and enabled but with
  no code delivered yet. When a browser cannot be opened — a headless host, a Linux box with no
  default handler — the code goes to the clipboard and the link stays on screen.

  It carries the same warning the owner DM has always carried, and shows it before the browser
  opens rather than after: **authorise with a burner Google account, not your main one.** The
  sign-in grants ongoing access to whichever account is used, and the resulting token is stored
  in plain text next to the bot. Signing out deletes it.

  The stored token is never sent to the web panel.

- **Optional Lavalink playback engine.** `playback.engine` selects `lavaplayer` or `lavalink`.
  `lavaplayer` remains the default and is unchanged.

  The two are not one path with a switch: with Lavaplayer the bot holds the Discord voice
  connection and pushes Opus frames itself, while with Lavalink the node joins the voice gateway
  and the bot sends none. The boundary sits at the voice connection and at a per-command flag, so
  a command not yet ported says so rather than reading empty state and showing wrong numbers.

  `fallback` is accepted, logged as unimplemented, and resolves to `lavaplayer`.

  No new dependency: the node's v4 REST and WebSocket API is spoken directly with OkHttp and
  Jackson, both already present.

  The config editor gains an engine selector, a node list editor, and a test-connection button —
  one `GET /v4/info` confirms host, port, TLS and password, where before it meant restarting and
  reading a stack trace.

### Fixed

- **An untranslated string fell back to the configured default language rather than to English.**
  Setting `ui.language` to anything but English meant a missing key fell back to that same
  language, found nothing, and rendered the raw key on screen. The per-key fallback this project
  promises worked only for people who had left the default on English.

- **The proxy and Lavalink sections could be mistaken for each other**, and were — a Lavalink node
  entered as a proxy routed YouTube traffic through it. Each section now says what it is for and
  names the other. Two settings that are silently inert under Lavalink now say so.

- **`ConfigRenderer` dropped the braces around `CONFIG_LIST` entries**, producing HOCON that fails
  to reparse; the exception was swallowed and the default silently replaced the operator's list.

- **The collapsed-section preference moved out of `config.txt`** into a sidecar file. It is a
  window preference, and living in the config meant the repair pass flagged it as unknown on every
  start and wrote another backup each time.


## [0.8.1-beta.1] — 2026-08-26

### Added

- **Spotify links.** Track, album, playlist and artist links are read through Spotify's Web API
  and each track is then found and played from YouTube. It does **not** play Spotify audio and
  cannot — those streams are DRM-protected with no public API, and the Web Playback SDK only
  works inside a browser for a signed-in Premium user. Every message says so.

  Needs `spotify.clientId` and `spotify.clientSecret`; blank leaves the feature off. Both are
  registered as secrets, so the web panel never sends them.

  Playlists read 100 tracks and albums 50 — Spotify's own per-page maxima — and report the real
  total when they stop short rather than silently loading a prefix. Tracks resolve sequentially,
  never in parallel. Tracks with no YouTube match are counted and reported.

  Artist links fall back to search: Spotify closed `/artists/{id}/top-tracks` to applications
  registered after November 2024 while leaving `/artists` and `/search` open to the same token,
  so a correctly configured app fails on exactly that one call. Only 403 triggers the fallback —
  a 500 still fails, rather than hiding a real problem behind substituted results.


### Fixed

- **No playlist could be loaded from YouTube.** Reported as a YouTube Music problem; it was
  neither specific to YouTube Music nor to the playlist in question. Measured directly: a
  known-good public playlist failed identically, on `music.youtube.com` and `www.youtube.com`
  alike, on the pinned snapshot *and* on the released 1.18.2 — so YouTube changed the playlist
  response and `youtube-source` has not caught up on any version. Testing each client
  individually found exactly one that still works, `ANDROIDVR`, and it produces real audio
  frames rather than only loading metadata. It now leads the default client list. `ANDROID` is
  dropped: upstream logs "ANDROID is broken with no known fix" at startup.

  A note in `reference.conf` claiming `ANDROID_VR` played nothing has been corrected — that
  measurement only ever covered single videos.

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

[Unreleased]: https://github.com/xx445469/NextVoiceCord/compare/v0.9.0-beta.1...HEAD
[0.9.0-beta.1]: https://github.com/xx445469/NextVoiceCord/releases/tag/v0.9.0-beta.1
[0.8.1-beta.1]: https://github.com/xx445469/NextVoiceCord/releases/tag/v0.8.1-beta.1
[0.8.0-beta.1]: https://github.com/xx445469/NextVoiceCord/releases/tag/v0.8.0-beta.1
