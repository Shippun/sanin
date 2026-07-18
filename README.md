<div align="center">
  <img src="https://raw.githubusercontent.com/n4237074-creator/psycho/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Psycho" width="128">
</div>

# Sanin — Anime streaming app for Android TV and Phone

Anime tracking, discovery, and streaming app with AniList/MyAnimeList sync, extension-based sources, dual video engines, and full D-pad/TV navigation.

---

## Getting Started

1. **Sign in** with AniList or MyAnimeList to sync your watchlist.
2. **Install extensions** from **Settings > Extensions > +** to add streaming sources.
3. **Tap an episode** to pick a source and start playback.

---

## Features

### Player
- Dual engine: ExoPlayer (default) or **MPV** — toggle mid-playback without losing position
- Gesture controls: swipe for seek, brightness, volume; double-tap sides to skip
- Subtitle styling: font, color, outline, background, position, size
- Online subtitles from Wyzie and Stremio
- Skip OP/ED buttons, Picture-in-Picture (Android N+)
- Resize modes: Fit, Zoom, Stretch
- Auto-hide controls with configurable delay
- Playback speed up to 50x (cursed speeds)

### Tracking
- AniList and MyAnimeList OAuth with reliable redirects
- Auto-update episode progress
- Auto-skip intros, outros, recaps, fillers
- Multiple tracking modes: ask per episode, always update, chapter zero handling
- List status change notifications

### Navigation
- Full D-pad/remote support with visible focus borders
- Auto-focus on first interactive element
- Keyboard back-dismiss: back button hides keyboard before navigating
- Contextual side rail with glass effect backdrop

### Display
- **Accent colors**: 9 themes — Sanin (default deep icy blue), Ocean, Blood, Lime, Sun, Kurama, Saikou, Indigo, Monochrome
- **Swap Colors**: toggle to swap primary/secondary role pairs
- **Glass effect**: frosted blur on nav rail and pills with real-time scroll capture
- Light, dark, and OLED variants for each theme
- Landscape anime cards with AniZip banner backdrop images
- Animation master switch with per-category toggles (Display, Navigation, Player)

### Extensions
- Tachiyomi-compatible addon system
- Built-in repository browser
- Community-maintained sources

### Logging & Debug
- Background log capture with circular buffer (50K lines)
- Live logcat viewer with pause/play
- Master logging switch (default off)
- Cache clearing covers app cache, Glide disk cache, LogoApi cache, subtitle files

### Other
- Discord Rich Presence (shows what you're watching)
- Subtitle sync dialog with cue viewer
- Notification popups (works on phone; disabled on TV)
- Proxy support for timestamp loading

---

## Player Controls

| Action | How |
|---|---|
| Seek | Swipe left/right on bottom half, or double-tap sides |
| Brightness | Swipe up/down on left edge |
| Volume | Swipe up/down on right edge |
| Play/pause | Tap center or press D-pad center |
| Fullscreen | Tap fullscreen button (bottom-right) |
| Switch player | Tap sync icon (far right) — ExoPlayer ↔ MPV |

---

## MPV Engine

Go to **Settings > Player** and toggle "Use MPV Engine". On first enable, the app downloads native libraries (~16MB). Switch between engines at any time using the sync icon — position is preserved.

---

## Building

```bash
git clone https://github.com/Shippun/sanin.git
cd sanin
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
