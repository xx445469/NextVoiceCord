# Third-Party Notices

NextVoiceCord incorporates material from the projects listed below. Full license
texts are reproduced in their entirety as required by their respective terms.

---

## 1. JMusicBot

- **Upstream:** https://github.com/jagrosh/MusicBot
- **Fork base:** https://github.com/arif-banai/MusicBot
- **License:** Apache License 2.0
- **Copyright:** 2016–2017 John Grosh (jagrosh); Arif Banai (arif-banai)

**Scope of use:** NextVoiceCord is a derivative work. The audio playback engine,
Lavaplayer integration, queue and playlist management, command framework,
configuration system, and settings persistence all originate from this project.

The full Apache License 2.0 text is in [`LICENSE`](LICENSE) at the repository root,
which is also the license governing NextVoiceCord as a whole.

Per Apache License 2.0 §4(b), files modified by this project carry a
modification notice in their header. Per §4(c), all pre-existing copyright,
patent, trademark, and attribution notices have been retained.

Per Apache License 2.0 §6, no trademark rights in "JMusicBot" are granted or
claimed. NextVoiceCord is an independent, unaffiliated project.

---

## 2. Vocard

- **Upstream:** https://github.com/ChocoMeow/Vocard
- **License:** MIT License
- **Copyright:** (c) 2023 Choco

**Scope of use:**

| Component | Relationship |
|---|---|
| `src/main/resources/langs/*.json` | **Derived directly** from Vocard's `langs/` translation files. Keys have been reorganized and extended to match NextVoiceCord's message set; translated strings originate from Vocard. |
| Controller panel layout system | **Independently reimplemented** in Java. Design and data-model concepts inspired by Vocard's `voicelink/views/controller.py`. |
| Placeholder template engine | **Independently reimplemented** in Java. The `@@var@@` / `@@t_key@@` / `{{cond ?? a // b}}` syntax is adopted for template compatibility. |
| i18n architecture | **Independently reimplemented** in Java. |

No Python source code from Vocard is included or distributed in this project.

### MIT License

```
MIT License

Copyright (c) 2023 Choco

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 3. Bundled dependencies

Runtime dependencies (JDA, Lavaplayer, `youtube-source`, Typesafe Config, Jackson,
Logback, Rhino, and others) are resolved by Maven and shaded into the distributed
JAR. Their licenses are their own; consult `pom.xml` for the authoritative
dependency list and each project's repository for its license terms.

`youtube-source` (https://github.com/lavalink-devs/youtube-source) is updated
automatically by this project's CI. See `.github/workflows/` for the update
pipeline.
