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
  "version": "0.8.0-beta.1",
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
