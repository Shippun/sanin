<div align="center">
  <img src="https://files.catbox.moe/s3mwfr.png" alt="Sanin Banner" width="830">

  <p align="center">
    <img src="https://img.shields.io/badge/platform-android-06599d?labelColor=7000b7&color=1b1c2a&style=for-the-badge"/>
    <img src="https://img.shields.io/github/downloads/n4237074-creator/psycho/total?style=for-the-badge&color=1b1c2a&labelColor=7000b7" alt="Downloads">
  </p>
</div>

# Sanin — AniList Tracking Client

**Sanin** is an Android app for tracking anime and manga through [AniList](https://anilist.co) and [MyAnimeList](https://myanimelist.net). Browse media, stream from addon sources, customize your player, and keep your watchlists synced — all in one place.

> [!IMPORTANT]
> Sanin does **not** host, provide, or distribute any content. All media sources come from third-party addons installed by the user. The app itself only connects to the official AniList and MyAnimeList APIs for tracking. By using Sanin, you agree to our [Terms of Service](./privacy_policy.md).

---

## Features

- **AniList & MAL Login** — OAuth login to sync your anime/manga lists and activity.
- **Media Browsing** — Search and browse anime, manga, characters, and staff from AniList.
- **Built-in Player** — Video player with gestures (brightness, volume, seek), subtitles, speed control, skip OP/ED, auto-rotate, PiP, and more.
- **MPV Engine** — Optional MPV video backend for better codec support. Download native libs on demand from Settings > Player.
- **Addon System** — Install third-party extensions (Tachiyomi-compatible) to add streaming sources and scrapers.
- **Online Subtitles** — Auto-fetch subtitles from Wyzie and Stremio providers.
- **Discord Rich Presence** — Show what you're watching on your Discord profile.
- **Customizable** — Resize modes, playback speed, seek sensitivity, subtitle styling, auto-skip, focus pause, and more.
- **Download Manager** — Coming soon: offline downloads.

---

## Quick Start

1. **Login** — Open the app and tap **Login with AniList** (or switch to MAL on the login screen).
2. **Browse** — Use the search or explore tabs to find anime/manga.
3. **Add Extensions** — Go to **Settings > Extensions** and install media source addons to enable streaming.
4. **Watch** — Tap an episode on a media page to open the player. Use gestures and the controller overlay for playback controls.

### Adding Extensions

1. Go to **Settings > Extensions**.
2. Tap the **+** button to browse the extension repo, or install an APK directly.
3. After installation, enable the extension. Available sources will appear in the episode list.
4. Extensions are community-maintained and not affiliated with Sanin.

---

## Player Controls

| Gesture / Button | Action |
|---|---|
| Swipe left/right (bottom half) | Seek forward/backward |
| Swipe up/down (left edge) | Brightness |
| Swipe up/down (right edge) | Volume |
| Double-tap left/right | Seek by configured interval |
| D-pad left/right | Seek by configured interval |
| **Switch Player** button (bottom bar) | Toggle between ExoPlayer and MPV engine |

All controller buttons support D-pad navigation for TV/remote use.

---

## Discord

Join the community server for updates, support, and discussion:

<div align="center">
  <a href="https://discord.gg/URnPQKCb"><img src="https://img.shields.io/badge/Join%20Discord-Sanin-5865F2?style=for-the-badge&logo=discord&logoColor=white&labelColor=2b2d31" alt="Discord"></a>
</div>

To enable Discord Rich Presence, go to **Settings > Discord** and connect your Discord account.

---

## Settings Reference

| Section | Options |
|---|---|
| **Video** | Default speed, cursed speeds, resize mode |
| **Subtitles** | Toggle, primary/secondary colors, outline, background, font, size, delay, language |
| **Timestamps** | Skip OP/ED, auto-skip recaps/fillers, auto-play next, timestamp proxy |
| **Behavior** | Always continue, gestures, double-tap, fast-forward, seek sensitivity, D-pad episode skip, PiP |
| **Player** | Additional codec, **MPV Engine** (download native libs toggle) |
| **Discord** | Rich Presence, button display mode, icon service |

---

## Building from Source

```bash
git clone https://github.com/n4237074-creator/psycho.git
cd psycho
./gradlew assembleDebug
```

Requires Android SDK 34+. The debug APK will be at `app/build/outputs/apk/debug/`.

---

## Contributing

Contributions are welcome! Feel free to open issues, submit pull requests, or suggest features.

Join the [Discord server](https://discord.gg/URnPQKCb) for development discussion.

---

## License

Sanin is licensed under the Unabandon Public License (UPL). See [LICENSE.md](LICENSE.md).
