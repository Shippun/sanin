<div align="center">
  <img src="https://raw.githubusercontent.com/n4237074-creator/psycho/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Psycho" width="128">
</div>

# Sanin — Anime Streaming app created mostly for Android tv but also works perfectly for phone

An Android app for tracking, discovering, and watching anime through AniList and MyAnimeList. Fork of Sanin with an optional MPV video engine, improved OAuth reliability, and a focus on D-pad/TV navigation.

---

## How It Works

1. **Sign in** with your AniList or MyAnimeList account to sync your watchlist.
2. **Install extensions** (Tachiyomi-compatible addons) to add streaming sources.
3. **Watch** any episode in the built-in player with gesture controls, subtitles, and speed adjustment.

The app handles tracking (marking episodes as watched, updating progress) automatically.

---

## Getting Started

### First Launch

1. Tap **Login** on the welcome screen.
2. Choose **AniList** or **MyAnimeList** (tap the login button to switch).
3. Authorize the app in your browser. You'll be redirected back automatically.

### Installing Extensions

Extensions add streaming sources. Without them, the app is a tracker only.

1. Go to **Settings > Extensions**.
2. Tap the **+** button to browse the repository.
3. Install an extension (e.g. "Gogoanime", "Zoro", etc.).
4. Go back to an anime page and tap an episode — sources from your extensions will appear.

### Playing a Video

- Tap any episode number to see available sources.
- Pick a source to start playback.
- Use the on-screen controls or gestures:

| Action | How |
|---|---|
| Seek | Swipe left/right on bottom half, or double-tap sides |
| Brightness | Swipe up/down on left edge |
| Volume | Swipe up/down on right edge |
| Toggle play/pause | Tap center or press D-pad center |
| Fullscreen | Tap the fullscreen button (bottom-right) |
| Switch player | Tap the sync icon button (far right) — toggles between ExoPlayer and MPV |

---

## Features

### Tracking
- AniList OAuth login (fixed `redirect_url` parameter for reliable redirects)
- MyAnimeList OAuth login (uses standard browser intent, no Chrome CustomTabs)
- Auto-update episode progress
- Auto-skip intros, outros, recaps, and fillers (when timestamps available)
- Multiple tracking options (ask per episode, always update, chapter zero handling)

### Player
- **Dual engine**: ExoPlayer (default) or **MPV** (download native .so libs on demand — no app size increase)
- Gesture controls (brightness, volume, seek)
- D-pad navigation with visible focus borders for TV/remote use
- Playback speed (normal, cursed speeds up to 50x)
- Subtitle styling (font, color, outline, background, position, size)
- Online subtitles from Wyzie and Stremio
- Skip OP/ED buttons
- Picture-in-Picture (Android N+)
- Resize modes: Fit, Zoom, Stretch
- Auto-hide controls

### MPV Engine

Go to **Settings > Player** and toggle "Use MPV Engine". The first time you enable it, the app downloads ~16MB of native libraries for your device's architecture. Once downloaded, you can switch between ExoPlayer and MPV at any time using the **sync icon button** in the player controls. Switching preserves your current playback position.

### Discord
- **Rich Presence**: Show what you're watching on your Discord profile.
- **Community**: Join the server for updates and discussion.

[Join the Discord](https://discord.gg/7Jn5m8rmz)

### Extensions
- Tachiyomi-compatible addon system
- Browse and install from the built-in repo
- Each extension provides its own sources and scrapers
- Community-maintained

---

## Settings Overview

| Category | What you can change |
|---|---|
| Video | Default playback speed, cursed speeds on/off, resize mode |
| Online Subtitles | Enable/disable, choose providers (Wyzie, Stremio), language filter |
| Subtitles | Font, size, primary/secondary colors, outline type, background, window, alpha, stroke, bottom margin, language |
| Timestamps | Timestamp loading, proxy, auto-hide, skip OP/ED, recap, fillers, auto-play next |
| Behavior | Gestures on/off, double-tap seek, fast-forward, seek time, D-pad episode skip, pause on focus loss |
| Player | Additional codec, **MPV Engine toggle** |
| Discord | Rich Presence, button display mode, icon service (AniList, MAL, Sanin) |

---

## Building

```bash
git clone https://github.com/n4237074-creator/psycho.git
cd psycho
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/`

---

## Discord

<div align="center">
  <a href="https://discord.gg/7Jn5m8rmz"><img src="https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"></a>
</div>

---

## Disclaimer

This app does not host, provide, or distribute any copyrighted content. Media sources come from third-party extensions installed by the user. The app solely connects to the official AniList and MyAnimeList APIs for tracking purposes.
