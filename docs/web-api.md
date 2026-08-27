# Web panel API contract

Every endpoint requires the token. `GET` accepts it as `?token=…` **or**
`Authorization: Bearer …`; `POST` accepts **only** the header, because a cross-site page can make
a browser send a URL but cannot set a header.

All responses are `application/json; charset=utf-8`.

## GET /api/status

```json
{
  "labels": { "playingNow": "playing now", "...": "..." },
  "language": "ZHTW",
  "version": "0.9.0-beta.1",
  "connected": true,
  "guilds": [
    { "name": "…", "id": "123", "playing": true, "members": 42,
      "track": "…", "author": "…", "position": "1:03", "duration": "3:45",
      "progress": 27, "paused": false, "volume": 100, "queue": 3, "channel": "General" }
  ],
  "guildCount": 1, "playingCount": 1, "listeners": 4,
  "memoryUsedMb": 210, "memoryMaxMb": 2048, "uptime": "3h 12m"
}
```

Non-playing guilds omit the playback fields. Guilds sort playing-first.

## GET /api/labels

```json
{ "language": "ZHTW",
  "available": [ { "code": "ZHTW", "native": "繁體中文", "english": "Traditional Chinese" } ],
  "labels": { "gui.nav.overview": "總覽", "web.control.pause": "暫停", "...": "..." } }
```

Every string the page's own chrome needs (nav, headings, table columns, empty states, button
captions), resolved for the caller's language the same way `/api/status`'s `labels` field is.
`available` lists only languages that actually loaded. The page has no translation data of its
own — it fetches this once at startup and again whenever the viewer picks a different display
language, then renders from what comes back.

## GET /api/console?since=<seq>&limit=<n>

```json
{ "lines": [ { "seq": 41, "text": "…", "level": "INFO" } ],
  "latest": 41, "dropped": 0 }
```

`since` defaults to 0, `limit` defaults to 500 and is capped at 2000. `dropped` is how many
lines fell out of the ring buffer since start, so the page can say output was discarded rather
than implying it shows everything.

## GET /api/performance?window=<seconds>

```json
{
  "available": true,
  "guilds": [ { "id": "…", "name": "…", "state": "playing",
                "framesSent": 0, "framesMissed": 0, "missRate": 0.0,
                "stutters": 0, "stuck": 0 } ],
  "events": [ { "time": "01:02:03", "guild": "…", "type": "stutter", "detail": "…" } ],
  "totals": { "framesSent": 0, "framesMissed": 0, "missRate": 0.0 }
}
```

`available: false` with an empty rest when no metrics have been collected yet.

## GET /api/system?window=<seconds>

```json
{
  "available": true,
  "cpuProcess": 4.1, "cpuSystem": 18.0,
  "heapUsedMb": 210, "heapMaxMb": 2048,
  "threads": 48, "gcCount": 12, "gcTimeMs": 340,
  "driftAvgMs": 0.8, "driftMaxMs": 14,
  "samples": [ { "t": -60, "cpu": 3.2, "heapMb": 200 } ]
}
```

`samples` is oldest-first, `t` seconds relative to now (negative). `cpuProcess`/`cpuSystem` are
`null` when the JVM cannot report them — do not substitute 0, the page distinguishes them.

## GET /api/sources?window=<seconds>

```json
{
  "sources": [ { "name": "youtube", "loaded": 90, "failed": 2, "noMatches": 1,
                 "successPercent": 96.8, "avgMs": 412, "p95Ms": 900 } ],
  "recent": [ { "time": "01:02:03", "source": "youtube", "result": "LOADED",
                "ms": 380, "query": "…" } ],
  "totals": { "loaded": 90, "failed": 2, "noMatches": 1, "successPercent": 96.8 }
}
```

## GET /api/config

```json
{
  "editable": false,
  "sections": [
    { "name": "commands",
      "options": [ { "key": "commands.prefix", "leaf": "prefix", "type": "STRING",
                     "value": "!", "secret": false, "description": "…" } ] }
  ]
}
```

`editable` mirrors `web.allowConfigEdit`. **Secrets are never sent**: `value` is `"••••••••"`
when set and `""` when not, and `secret` is `true`. `discord.token`, `web.bindAddress` and
`web.allowConfigEdit` additionally carry `"writable": false`.

## POST /api/config

Body is a flat `{"commands.prefix": "!", …}` of only what changed. A value equal to the mask is
ignored rather than written.

```json
{ "ok": true, "written": ["commands.prefix"], "refused": [], "message": "Saved. Most settings need a restart." }
```

Returns 403 with `"reason": "disabled"` when config editing is off.

## POST /api/prefs

Body accepts `language`, `theme`, `fontSize`. Same result shape.

## POST /api/control

Body `{"action": "pause|resume|skip|volume", "guild": "<id>", "value": "<0-150>"}`.
Same result shape.

## GET /api/update-check

Checks GitHub for a newer release and, if one exists, downloads it — the same check-then-download
`SelfUpdater.checkAndStage()` the hourly background timer runs, triggered on request instead
of waiting for the timer. Telling someone a newer version exists with no way to act on it until
the hourly pass caught up was worse than not telling them at all, so a successful check here also
leaves something for `POST /api/update-install` to install, the same as the hourly timer would.
Never installs anything by itself.

A ~68 MB transfer is not instant; this can take a while to answer when an update exists, and
refuses (409) before doing anything if the bot has not connected to Discord yet — the same
`notReady` state `POST /api/update-install` reports.

```json
{ "status": "upToDate", "currentVersion": "0.10.0" }
{ "status": "staged", "version": "0.11.0", "url": "https://github.com/…/releases/tag/v0.11.0" }
{ "status": "downloadFailed", "version": "0.11.0", "url": "https://github.com/…/releases/tag/v0.11.0" }
{ "status": "failed", "detail": "…" }
```

`staged` means `POST /api/update-install` now has something to install. `downloadFailed` means a
newer release was found but the transfer itself did not complete — nothing is staged. `failed`
means the check itself could not be completed (network error, rate limit, and so on).

## POST /api/update-install

Installs whatever the bot's own hourly background check has already staged, restarting the
process into it. The riskiest endpoint here: refused outright (403) unless the panel is bound to
loopback or `web.allowConfigEdit` is on, refused (409) if the bot has not connected to Discord
yet, and — even once past both of those — never installs from one call alone.

Body `{"force": "true"|"false", "confirm": "true"|"false"}`, both optional and defaulting to
`false`. Every call answers with what would happen; only a call with `"confirm":"true"` actually
schedules it:

```json
{ "status": "notStaged" }
{ "status": "blocked", "playing": ["Guild A", "Guild B"] }
{ "status": "installing", "version": "0.10.0" }
```

`blocked` means something is playing and `force` was not set — installing now would cut it off
without warning, so nothing happens. Sending the same request again with `"force":"true"` and
`"confirm":"true"` installs anyway. `installing` with `"confirm":"true"` is the only case that
actually schedules the restart; every other combination is a dry run.

