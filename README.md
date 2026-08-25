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

## License

NextVoiceCord is licensed under the **Apache License 2.0** — the same license as
the JMusicBot code it derives from. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).

Files carrying an existing copyright header retain it. Modified files are marked
as such, per Apache 2.0 §4(b).
