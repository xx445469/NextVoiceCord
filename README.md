# NextVoiceCord

A self-hosted Discord music bot — the playback core of **JMusicBot**, wearing a
**Vocard**-style interface, with full multi-language support and a
continuously self-updating YouTube extraction layer.

> **Status:** early development. Not yet ready for general use.

---

## Why this project exists

Three things I wanted that no single existing bot gave me at once:

| | |
|---|---|
| **A playback core that just works** | JMusicBot embeds Lavaplayer directly — no separate Lavalink server to run or babysit. |
| **An interface worth looking at** | Vocard's data-driven controller panel, template-driven embeds, and 12-language support set the bar for how a music bot should *feel*. |
| **YouTube extraction that doesn't rot** | `youtube-source` gets patched constantly because Google keeps changing the rules. Pinning it to a version means the bot breaks every few weeks. This project tracks it automatically. |

## Planned features

- **Vocard-style controller panel** — data-driven button layout, multi-state buttons, template-rendered embeds
- **Per-guild customizable layouts** — editable via slash command, not just a global config file
- **12 languages** — DE, EN, ES, FR, JA, KO, PL, RU, UA, VN, ZH-CN, ZH-TW
- **Daily automated `youtube-source` updates** — CI bumps, smoke-tests against real playback, and publishes only if the new version genuinely works
- **Self-updating bot** — detects new releases, verifies, and restarts while idle

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

## License

NextVoiceCord is licensed under the **Apache License 2.0** — the same license as
the JMusicBot code it derives from. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).

Files carrying an existing copyright header retain it. Modified files are marked
as such, per Apache 2.0 §4(b).
