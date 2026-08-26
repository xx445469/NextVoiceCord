# NextVoiceCord

**繁體中文** | [English](README.md)

[![Build](https://github.com/xx445469/NextVoiceCord/actions/workflows/build-and-test.yml/badge.svg?branch=main)](https://github.com/xx445469/NextVoiceCord/actions/workflows/build-and-test.yml)
[![Release](https://img.shields.io/github/v/release/xx445469/NextVoiceCord?include_prereleases&sort=semver&label=release)](https://github.com/xx445469/NextVoiceCord/releases)
[![Downloads](https://img.shields.io/github/downloads/xx445469/NextVoiceCord/total?label=downloads)](https://github.com/xx445469/NextVoiceCord/releases)
[![Java](https://img.shields.io/badge/Java-25-orange)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/xx445469/NextVoiceCord)](LICENSE)
[![Changelog](https://img.shields.io/badge/changelog-CHANGELOG.md-blue)](CHANGELOG.md)

一個自架的 Discord 音樂機器人——以 **JMusicBot** 的播放核心為基礎，套上
**Vocard** 風格的介面，具備完整的多語言支援，以及一個持續自我更新的
YouTube 擷取層。

> **狀態：** 測試版（beta）。可正常使用，但目前釘選在一個尚未正式發行的
> `youtube-source` 建置版本——詳見[測試版說明](#測試版說明)。

---

## 快速開始

需要 Java 25 或以上版本。

```bash
# 1. Get the jar from the releases page, then generate a config
java -jar NextVoiceCord.jar generate-config

# 2. Put your bot token and owner ID in config.txt, then run it
java -jar NextVoiceCord.jar
```

| 參數 | 作用 |
|---|---|
| *（無）* | 開啟桌面視窗。 |
| `--nogui` | 以無介面模式執行。在伺服器或透過 SSH 使用時選這個。 |
| `--web <port>` | 在指定的連接埠上提供網頁面板。搭配或不搭配 `--nogui` 都可以。 |
| `generate-config` | 寫出一份預設的 `config.txt` 後結束執行。 |

網頁面板預設綁定在 `127.0.0.1`。若要從其他裝置連線，請將
`web.bindAddress` 設為 `"0.0.0.0"`——但要注意它走的是明文 HTTP，因此啟動時
印出的 token 會在該網路上以明文傳輸。請不要把這個連接埠轉發出去。

### Docker

每次發行都會將映像檔發布到 GHCR。

```bash
mkdir -p ./nextvoicecord && sudo chown 10001:10001 ./nextvoicecord

docker run -d --name nextvoicecord \
  -v "$PWD/nextvoicecord:/musicbot" \
  -p 8080:8080 \
  ghcr.io/xx445469/nextvoicecord:latest
```

容器是以 UID 10001 執行的，所以掛載的目錄必須讓這個 UID 可以寫入——
這就是那行 `chown` 的用途。把 `config.txt` 放在這個目錄裡。只有在你想使用
網頁面板時才需要 `-p`，而且若要從容器外部連線，設定檔裡也需要加上
`web.bindAddress = "0.0.0.0"`。

有一份 [`docker-compose.example.yml`](docker-compose.example.yml) 可以直接複製使用。

### 從原始碼建置

```bash
git clone https://github.com/xx445469/NextVoiceCord.git
cd NextVoiceCord
mvn clean package           # jar lands in target/
```

---

## 設定

所有設定都在 `config.txt` 裡，產生時已附上說明每個選項用途的註解。
以下兩項在機器人啟動前必須設定：

| 鍵值 | 說明 |
|---|---|
| `discord.token` | 從你的 [Discord 應用程式](https://discord.com/developers/applications)的 Bot 分頁取得 |
| `discord.owner` | 你自己的 Discord 使用者 ID |

以下這些也值得了解：

| 鍵值 | 預設值 | 作用 |
|---|---|---|
| `ui.language` | `EN` | 機器人回覆時使用的語言。個別伺服器和使用者可以覆寫這個設定。 |
| `gui.language` | *（跟隨 `ui.language`）* | 桌面視窗所使用的語言——操作者不一定身處任何一個伺服器中。 |
| `web.bindAddress` | `127.0.0.1` | 設為 `0.0.0.0` 可從其他裝置連上面板。請參閱上方的警告。 |
| `web.allowConfigEdit` | `false` | 允許網頁面板寫入 `config.txt`。除非你需要，否則保持關閉。 |
| `updates.autoUpdate` | `false` | 在閒置時自動更新到新版本。 |

在 Discord 裡輸入 `/help` 即可查看所有可用指令——它會依類別列出全部指令，
並標示出哪些是你的身分組沒有權限執行的。

---

## 測試版說明

`youtube-source` 目前已正式發行的版本，有相當大比例的影片無法播放，
因此本專案釘選了一個尚未發行、但可以播放的快照版本。這也是為什麼
版本號會帶有 `-beta`。

這個釘選的建置版本會在每次 CI 執行時，針對真實的播放情境進行驗證；
一旦上游有正式發行版本通過相同的檢查，就會立刻取消釘選。

---

## 為什麼會有這個專案

有三件事，是我想要、但當時沒有任何一個現成的機器人能同時做到的：

| | |
|---|---|
| **一個真正好用的播放核心** | JMusicBot 直接內嵌了 Lavaplayer——不需要另外架設或維護一台 Lavalink 伺服器。 |
| **一個值得一看的介面** | Vocard 的資料驅動控制面板、範本驅動的嵌入訊息，以及 12 種語言支援，為音樂機器人該有的「使用體驗」立下了標竿。 |
| **不會腐朽的 YouTube 擷取機制** | 因為 Google 不斷改變規則，`youtube-source` 也就持續在被修補。若把它釘選在某個版本，機器人每隔幾週就會壞掉。這個專案會自動追蹤它的更新。 |

## 這個專案能做什麼

- **Vocard 風格的控制面板**——資料驅動的按鈕版面、多狀態按鈕、範本渲染的嵌入訊息
- **每個伺服器可自訂版面**——用 `/controller` 編輯，而不只是一份全域設定檔
- **12 種語言**——DE、EN、ES、FR、JA、KO、PL、RU、UA、VN、ZH-CN、ZH-TW，可依伺服器和依使用者個別設定
- **一個桌面視窗**——即時狀態、主控台、各伺服器的播放診斷、系統健康狀態，以及設定編輯
- **一個網頁面板**——在瀏覽器裡提供同樣的八個檢視畫面，並可控制播放
- **每日自動更新 `youtube-source`**——CI 會自動升版、以真實播放情境進行冒煙測試，只有在新版本確實可用時才會發布
- **自我更新的機器人**——偵測新版本、驗證後，在閒置時重新啟動

---

## 翻譯

NextVoiceCord 提供 12 種語言：DE、EN、ES、FR、JA、KO、PL、RU、UA、VN、ZH-CN、ZH-TW。

**只有英文版本經過驗證。** 其他每一種翻譯都是機器產生的，尚未經過任何母語者
檢查。它們是完整的，讀起來也很流暢——而這正是問題所在，因為這樣的翻譯和
「正確」是無法區分的。請預期會遇到不自然的措辭、錯誤的文法性別，以及被
誤譯的技術術語。

機器人本身也會這麼提醒：它在啟動時會記錄哪些語言尚未經過審閱，每個語言
檔案也都帶有 `_meta.reviewed: false`。在有人實際驗證之前，沒有任何語言會
被宣稱是已驗證的。

**非常歡迎大家協助修正。** 就算只是修正你所熟悉語言裡的幾個字串，也是
實實在在有用的貢獻。翻譯檔案位於
[`src/main/resources/langs/`](src/main/resources/langs/)，是純粹的巢狀
JSON：

```json
"player": {
  "skipped": "Skipped!",
  "volumeChanged": "Volume changed from `{0}` to `{1}`"
}
```

三條規則，全部由 CI（`python3 scripts/validate-langs.py`）強制檢查：

1. **保留每一個 `{0}`、`{1}` 佔位符。** 可以自由調整順序——畢竟不同語言
   的語序不同——但出現的集合必須一致，否則訊息會顯示出一個字面上的 `{1}`。
2. **`**` 和 `` ` `` 的數量要和英文版一致。** 沒有配對閉合的符號，會讓
   Discord 把訊息剩下的部分整段吞掉。
3. **指令名稱、設定路徑和列舉值一律不要動。** `/settc`、`` `linear` `` 和
   `playback.maxHistorySize` 這些是使用者要輸入、或機器要解析的東西，
   翻譯了就會破壞它們所在的那句指示。

你不需要翻譯整份檔案。回退機制是以每個鍵值為單位的，所以尚未翻譯的字串
會顯示英文，其餘部分則維持你的語言。當某個語言的檔案已經被會說該語言的
人通讀過一遍後，請設定 `_meta.reviewed: true`。

---

## 致謝

這個專案建立在兩份他人心血之上，請一定要去幫他們的專案按星星。

### JMusicBot——基礎

NextVoiceCord 是
[jagrosh/MusicBot](https://github.com/jagrosh/MusicBot) 的**衍生作品**，
經由 [arif-banai/MusicBot](https://github.com/arif-banai/MusicBot) 這個分支而來。
幾乎所有播放、音訊、佇列與指令相關的基礎架構都源自於此。

- Copyright 2016–2017 John Grosh (jagrosh)
- Copyright Arif Banai (arif-banai)
- 採用 **Apache License 2.0** 授權

NextVoiceCord **並非** JMusicBot 的附屬專案、未獲其背書，也不是它的官方
發行版本。依照 Apache 2.0 第 6 條，本專案未被授予、也未主張任何商標權利
——這也是為什麼採用了不同的名稱。

### Vocard——介面與翻譯

控制面板的設計、佔位符範本系統，以及國際化（i18n）架構，是**以 Java
重新實作**，靈感來自 [ChocoMeow/Vocard](https://github.com/ChocoMeow/Vocard)。

`src/main/resources/langs/` 底下的**翻譯檔案**，則是**直接衍生自
Vocard 的** `langs/` 目錄。

- Copyright (c) 2023 Choco
- 採用 **MIT License** 授權

完整授權條文請見 [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md)。

---

## 支持這個專案

NextVoiceCord 是免費且自架的；沒有付費方案，也沒有任何功能被鎖住。
如果它幫你省下了架設 Lavalink 的麻煩，歡迎請我喝杯咖啡：

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-adantw-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/adantw)

GitHub Sponsors 正在設定中；上線後 Sponsor 按鈕會出現在這個版本庫的頁面
最上方。

比起金錢，貢獻更有價值，而其中最有用的就是翻譯審閱——詳見上方的
[翻譯](#翻譯)章節。這十二種語言裡，有十一種從來沒有被會說該語言的人讀過。

---

## 授權

NextVoiceCord 採用 **Apache License 2.0** 授權——與它所衍生自的 JMusicBot
程式碼使用相同的授權。詳見 [`LICENSE`](LICENSE) 與 [`NOTICE`](NOTICE)。

已附有原始版權標頭的檔案將保留該標頭。經過修改的檔案，依 Apache 2.0
第 4(b) 條標示為已修改。
