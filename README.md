<div align="center">

# Mooring

**Moor your phone to your desk — PC-enforced screen time for Android.**

Any app. Even the one inside WeChat.

Rules live on your computer. Enforcement runs on your phone.

[English](README.md) | [简体中文](README.zh-CN.md)

![License](https://img.shields.io/badge/license-GPL--3.0-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple)
![Android](https://img.shields.io/badge/Android-8.0%2B-green)

</div>

---

## The Problem

Short-video apps are engineered to beat willpower. You deleted Douyin, TikTok, maybe YouTube too — but some apps cannot be deleted at all. WeChat stays because it's your work tool, and buried inside it sits **Channels**, a full-blown short-video feed with no off switch. The same story repeats worldwide: Instagram is a chat app with Reels welded inside.

Every screen-time app fails the same way: **the key and the lock live on the same device.** When the urge hits, you tap "ignore limit" twice and you're scrolling again.

## How Mooring Works

Mooring separates the key from the lock:

- **Your phone is the enforcement agent.** It detects and blocks the apps and feeds you chose. Its UI offers no button to change any rule.
- **Your PC is the console.** Open a browser, scan a QR code once, and every rule is managed from your desk.

```
PC Browser ──LAN──► Embedded Console (Ktor, :8765)
                          │  rules / groups / cooldown / focus
                          ▼
              AccessibilityService
                 ├── T1 · foreground-app match  (any app, by package name)
                 └── T2 · in-app page detection (WeChat Channels, live)
                          │
                          ▼
                   Rein Page (full-screen block)
```

The core psychological asymmetry:

| Action | Effect |
|---|---|
| **Tighten** (lower quota, add blocks) | Takes effect **immediately** |
| **Relax** (raise quota, remove blocks) | **10-min cooldown + 120s confirm window** |

Restraining yourself is always frictionless. Indulging always costs a walk to your desk and a ten-minute wait. Most urges die in the hallway.

## Two Levels of Control

| Level | Covers | How | Reliability |
|---|---|---|---|
| **T1 · App-level** | Any standalone app — Douyin, Bilibili, YouTube, TikTok, Instagram, games, anything | Foreground package-name matching | **Never breaks** — app redesigns don't change package names; no screen content is read |
| **T2 · Function-level** | Feeds embedded in undeletable super-apps — WeChat Channels & live, next: Instagram Reels | Window-class regex + on-screen keyword heuristics, both hot-updatable | Depends on a detection config, updated from the console when WeChat changes |

T1 covers everyone; T2 exists for the hardest case. Even if a WeChat update temporarily breaks T2, all T1 quotas keep working.

## Features

- 🎯 **Any-app quotas** — daily limits and schedule blocks for any installed app; built-in catalog with one-tap enable, or add any package name
- 🧺 **Shared group quotas** — one daily budget for the whole "video group" (e.g. Douyin + Bilibili + Channels share 45 min); time spent in one counts against all; optional per-app caps on top
- 🕳️ **Function-level blocking** — blocks Channels only; WeChat chat, Moments, and Official Accounts are untouched
- 🖥️ **PC web console** — self-hosted by your phone on LAN (`http://<phone-ip>:8765`); no cloud, no account, no telemetry
- ❄️ **Cooldown on relaxation** — the anti-impulse core, with a 120-second confirm window that expires
- 🔕 **Focus mode** — one click on PC hard-locks your targets for 45 minutes; relaxation attempts rejected
- 📊 **Usage dashboard** — per-app and per-group charts, block events, CSV export
- 🔧 **Hot-updatable detection config** — when WeChat renames internal pages, paste new patterns into the console; no reinstall needed
- 🔒 **Phone-side read-only** — mobile browsers get a read-only console; write APIs require a paired desktop client

## Built-in App Catalog

| App | Package | Level |
|---|---|---|
| 抖音 Douyin | `com.ss.android.ugc.aweme` | T1 |
| 快手 Kuaishou | `com.smile.gifmaker` | T1 |
| 哔哩哔哩 Bilibili | `tv.danmaku.bili` | T1 |
| 小红书 RED | `com.xingin.xhs` | T1 |
| YouTube | `com.google.android.youtube` | T1 |
| TikTok | `com.zhiliaoapp.musically` | T1 |
| Instagram | `com.instagram.android` | T1 |
| WeChat Channels 视频号 / Live | `com.tencent.mm` | **T2** |

Any other app: toggle "Add app" in the console and enter its package name.

## Requirements

- Android 8.0+ (minSdk 26) — developed and tested on HarmonyOS / EMUI (Huawei)
- WeChat 8.x (only for T2 function-level blocking)
- A PC or Mac on the same Wi-Fi network

## Getting Started

### 1. Install

Grab the APK from [Releases](../../releases), or build from source:

```bash
git clone https://github.com/marshsong/mooring.git
# Open in Android Studio → Run on device
```

### 2. On-phone setup (once)

The in-app guide walks you through three steps and verifies each one:

1. Enable **Accessibility** for Mooring (Settings → Accessibility → Installed apps)
2. **Keep-alive** (Huawei): disable "auto manage" in App Launch Management, allow all three; lock the app card in Recents
3. A **QR code** appears — keep this screen open

### 3. Pair your PC

On your computer (same Wi-Fi), open `http://<phone-ip>:8765`. The page asks to scan the QR code with your webcam — point it at the phone screen. Pairing is fully local (jsQR); the token is saved in your browser only.

### 4. Done

Default rules work out of the box: **the video group — every enabled video app plus WeChat Channels — shares 45 min/day, blocked 09:00–22:00; Channels live streams are always blocked; WeChat chat is never touched.**

Deleted Douyin out of despair? Reinstall it and give the whole group a shared budget. Mooring is pro-quota, not anti-app.

## How Detection Works

Both levels are data-driven from `detector_config.json`:

- **T1** listens for foreground window changes and matches package names — nothing on your screen is ever read.
- **T2** (WeChat only) uses two signals: window-class regex matching WeChat's Finder activity names (<100ms), and content heuristics matching screen-node keywords like "视频号 / 推荐 / 直播中" as a fallback for embedded entries (e.g. videos inside Moments). Node text is matched and immediately discarded — never stored, never sent anywhere.

## Roadmap

- [x] v0.1 — any-app quotas + group quotas + LAN console + WeChat Channels deep-block (MVP, see [docs/PRD.md](docs/PRD.md))
- [ ] v0.2 — T2 framework extended to Instagram Reels; PC work-state awareness (auto-tighten while you work)
- [ ] v0.3 — optional cloud sync, iOS via Screen Time API
- [ ] v0.4 — delegated supervision: hand the keys to a friend

## Troubleshooting

| Symptom | Fix |
|---|---|
| Blocking stopped | Check the Accessibility toggle — vendors sometimes silently disable it; also check keep-alive settings |
| WeChat update broke Channels detection | Upload a new `detector_config.json` from the console (Settings → Detector config); T1 quotas keep working meanwhile |
| Console unreachable | Same Wi-Fi? Port 8765 open? Phone IP changed (DHCP) — rescan the QR |

## Privacy

- **Zero cloud, zero accounts, zero telemetry.** All data lives in a local Room database.
- T1 never reads screen content — it only knows which package is in the foreground.
- T2 reads screen nodes in memory for keyword matching only, and discards them instantly.
- The embedded server listens on LAN only and rejects unpaired clients.

## Disclaimer

Mooring is an independent open-source project, **not affiliated with Tencent, WeChat, ByteDance, Google, or Meta**. Use it on your own device, for yourself. Deploying it on another adult's device without consent is surveillance, not discipline. Provided as-is under MIT license.

## License

GPL-3.0 © 2026 <marshsong>
