# NextVoiceCord

**English** | [繁體中文](README.zh-TW.md)

[![Build](https://github.com/xx445469/NextVoiceCord/actions/workflows/build-and-test.yml/badge.svg?branch=main)](https://github.com/xx445469/NextVoiceCord/actions/workflows/build-and-test.yml)
[![Release](https://img.shields.io/github/v/release/xx445469/NextVoiceCord?include_prereleases&sort=semver&label=release)](https://github.com/xx445469/NextVoiceCord/releases)
[![Downloads](https://img.shields.io/github/downloads/xx445469/NextVoiceCord/total?label=downloads)](https://github.com/xx445469/NextVoiceCord/releases)
[![Java](https://img.shields.io/badge/Java-25-orange)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/xx445469/NextVoiceCord)](LICENSE)
[![Changelog](https://img.shields.io/badge/changelog-CHANGELOG.md-blue)](CHANGELOG.md)

A self-hosted Discord music bot — the playback core of **JMusicBot**, wearing a
**Vocard**-style interface, with full multi-language support and a
continuously self-updating YouTube extraction layer.

> **Status:** beta. Usable, but pinned to an unreleased `youtube-source` build —
> see [Beta caveat](#beta-caveat).

---

## Quick start

Java 25 or newer is required.

```bash
# 1. Get the jar from the releases page, then generate a config
java -jar NextVoiceCord.jar generate-config

# 2. Put your bot token and owner ID in config.txt, then run it
java -jar NextVoiceCord.jar
```

| Flag | What it does |
|---|---|
| *(none)* | Opens the desktop window. |
| `--nogui` | Runs headless. Use this on a server or over SSH. |
| `--web <port>` | Serves the web panel on that port. Works with or without `--nogui`. |
| `generate-config` | Writes a default `config.txt` and exits. |

The web panel binds `127.0.0.1` by default. To reach it from another device set
`web.bindAddress = "0.0.0.0"` — but note it speaks plain HTTP, so the token it
prints at startup travels in the clear on that network. Do not forward the port.

### Docker

Images are published to GHCR on every release.

```bash
mkdir -p ./nextvoicecord && sudo chown 10001:10001 ./nextvoicecord

docker run -d --name nextvoicecord \
  -v "$PWD/nextvoicecord:/musicbot" \
  -p 8080:8080 \
  ghcr.io/xx445469/nextvoicecord:latest
```

The container runs as UID 10001, so the mounted directory has to be writable by it —
that is what the `chown` is for. Put `config.txt` in that directory. `-p` is only
needed if you want the web panel, and reaching it from outside the container also
needs `web.bindAddress = "0.0.0.0"` in the config.

There is a [`docker-compose.example.yml`](docker-compose.example.yml) to copy from.

### Building from source

```bash
git clone https://github.com/xx445469/NextVoiceCord.git
cd NextVoiceCord
mvn clean package           # jar lands in target/
```

---

## Configuration

Everything lives in `config.txt`, which is generated with comments explaining each
option. The two that must be set before the bot will start:

| Key | What it is |
|---|---|
| `discord.token` | From the Bot tab of your [Discord application](https://discord.com/developers/applications) |
| `discord.owner` | Your own Discord user ID |

Worth knowing about:

| Key | Default | What it does |
|---|---|---|
| `ui.language` | `EN` | What the bot replies in. Individual servers and users can override it. |
| `gui.language` | *(follows `ui.language`)* | What the desktop window is in — the operator is not necessarily in any of the servers. |
| `web.bindAddress` | `127.0.0.1` | `0.0.0.0` to reach the panel from another device. See the warning above. |
| `web.allowConfigEdit` | `false` | Lets the web panel write `config.txt`. Off unless you want it. |
| `updates.autoUpdate` | `false` | Self-update to new releases while idle. |

Commands are discoverable from inside Discord with `/help` — it lists everything by
category and marks what your roles do not let you run.

---

## Beta caveat

`youtube-source`'s published releases currently cannot play a large share of
videos, so this build pins an unreleased snapshot that can. That is why the
version carries `-beta`.

The pinned build is verified on every CI run against real playback, and the
pin is dropped as soon as an upstream release passes the same check.

---

## Why this project exists

Three things I wanted that no single existing bot gave me at once:

| | |
|---|---|
| **A playback core that just works** | JMusicBot embeds Lavaplayer directly — no separate Lavalink server to run or babysit. |
| **An interface worth looking at** | Vocard's data-driven controller panel, template-driven embeds, and 12-language support set the bar for how a music bot should *feel*. |
| **YouTube extraction that doesn't rot** | `youtube-source` gets patched constantly because Google keeps changing the rules. Pinning it to a version means the bot breaks every few weeks. This project tracks it automatically. |

## What it does

- **Vocard-style controller panel** — data-driven button layout, multi-state buttons, template-rendered embeds
- **Per-guild customizable layouts** — edited with `/controller`, not just a global config file
- **12 languages** — DE, EN, ES, FR, JA, KO, PL, RU, UA, VN, ZH-CN, ZH-TW, set per server *and* per user
- **A desktop window** — live status, console, per-guild playback diagnostics, system health, and config editing
- **A web panel** — the same eight views in a browser, with playback control
- **Daily automated `youtube-source` updates** — CI bumps, smoke-tests against real playback, and publishes only if the new version genuinely works
- **Self-updating bot** — detects new releases, verifies, and restarts while idle

---

## Translations

NextVoiceCord ships 12 languages: DE, EN, ES, FR, JA, KO, PL, RU, UA, VN, ZH-CN, ZH-TW.

**Only English is verified.** Every other translation is machine-generated and has not been
checked by a native speaker. They are complete and they read fluently — which is exactly the
problem, because that is indistinguishable from being correct. Expect unnatural phrasing,
wrong grammatical gender, and mistranslated technical terms.

The bot says so too: it logs which languages are unreviewed at startup, and each file carries
`_meta.reviewed: false`. Nothing claims to be verified until a person verifies it.

**Corrections are very welcome.** Fixing even a handful of strings in a language you speak is
genuinely useful. Translation files live in [`src/main/resources/langs/`](src/main/resources/langs/)
and are plain nested JSON:

```json
"player": {
  "skipped": "Skipped!",
  "volumeChanged": "Volume changed from `{0}` to `{1}`"
}
```

Three rules, all enforced by CI (`python3 scripts/validate-langs.py`):

1. **Keep every `{0}` `{1}` placeholder.** Reorder them freely — word order differs between
   languages — but the same set must appear, or the message renders a literal `{1}`.
2. **Keep `**` and `` ` `` counts matching English.** An unclosed pair swallows the rest of
   the message in Discord.
3. **Leave command names, config paths and enum values alone.** `/settc`, `` `linear` `` and
   `playback.maxHistorySize` are things people type or machines parse; translating them
   breaks the instruction they appear in.

You do not have to translate a whole file. Fallback is per key, so an untranslated string
shows in English while everything around it stays in your language. Once a language has been
read through by someone who speaks it, set `_meta.reviewed: true`.

---

## Attribution

This project stands on two pieces of other people's work. Please go star them.

### JMusicBot — the foundation

NextVoiceCord is a **derivative work** of
[jagrosh/MusicBot](https://github.com/jagrosh/MusicBot), by way of the
[arif-banai/MusicBot](https://github.com/arif-banai/MusicBot) fork.
Essentially all playback, audio, queue, and command infrastructure originates there.

- Copyright 2016–2017 John Grosh (jagrosh)
- Copyright Arif Banai (arif-banai)
- Licensed under the **Apache License 2.0**

NextVoiceCord is **not** affiliated with, endorsed by, or an official release of
JMusicBot. Per Apache 2.0 §6, no trademark rights are granted or claimed — hence
the distinct name.

### Vocard — the interface and translations

The controller panel design, placeholder template system, and i18n architecture
are **reimplemented in Java**, inspired by
[ChocoMeow/Vocard](https://github.com/ChocoMeow/Vocard).

The **translation files** under `src/main/resources/langs/` are **derived
directly from Vocard's** `langs/` directory.

- Copyright (c) 2023 Choco
- Licensed under the **MIT License**

See [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md) for full license texts.

---

## Support this project

NextVoiceCord is free and self-hosted; there is no paid tier and nothing is gated.
If it saves you the trouble of running Lavalink, you can buy me a coffee:

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-adantw-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/adantw)

GitHub Sponsors is being set up; the Sponsor button will appear at the top of this
repository once it is live.

Contributions are worth more than money, and the most useful one is a translation
review — see [Translations](#translations) above. Eleven of the twelve languages
have never been read by someone who speaks them.

---

## License

NextVoiceCord is licensed under the **Apache License 2.0** — the same license as
the JMusicBot code it derives from. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).

Files carrying an existing copyright header retain it. Modified files are marked
as such, per Apache 2.0 §4(b).
