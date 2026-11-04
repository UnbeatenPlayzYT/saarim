<div align="center">
<img src="docs/logo.png" width="128" alt="Sarim logo">

# Sarim · صارم

**A hard-to-bypass Android barrier against pornography and digital addiction.**

Device Owner restrictions, an Accessibility Service and an on-device ML model working together,<br>
built so that your future self can't simply turn it off.

[![Android](https://img.shields.io/badge/Android-11%2B-3ddc84?style=flat-square&logo=android&logoColor=white)](#installation)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7f52ff?style=flat-square&logo=kotlin&logoColor=white)
[![License](https://img.shields.io/badge/License-Apache%202.0-b8860b?style=flat-square)](LICENSE)
![Languages](https://img.shields.io/badge/Languages-English%20%C2%B7%20%D8%A7%D9%84%D8%B9%D8%B1%D8%A8%D9%8A%D8%A9%20%C2%B7%20Portugu%C3%AAs-2e6b3f?style=flat-square)

[How it works](#how-it-works) · [Features](#features) · [Installation](#installation) · [Recovery](#recovery-and-safety) · [Privacy](#privacy)

</div>

>[!TIP]  
>"In your struggle against sin, you have not yet resisted to the point of shedding your blood." - Hebrews 12:4

<br>

<table>
  <tr>
    <td align="center"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_lock_screen.png" width="230"><br><sub><b>The gate</b> — locked until your session starts</sub></td>
    <td align="center"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_tetris_challenge.png" width="230"><br><sub><b>Tetris challenge</b> before entering</sub></td>
    <td align="center"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_reading_challenge.png" width="230"><br><sub><b>Reflective reading</b>, word by word</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4_restrictions.png" width="230"><br><sub><b>System restrictions</b> via Device Owner</sub></td>
    <td align="center"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5_apps.png" width="230"><br><sub><b>Block or suspend</b> any app</sub></td>
    <td align="center"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6_config.png" width="230"><br><sub><b>All protections</b> in one place</sub></td>
  </tr>
  <tr>
    <td align="center" colspan="3"><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7_visual_blocking.png" width="230"><br><sub><b>Visual blocking</b> with on-device ML</sub></td>
  </tr>
</table>

> [!CAUTION]
> This software implements deep system-level modifications and is designed to be difficult to bypass. Proceed only if you fully understand the implications of Device Owner privileges. Read [Recovery and Safety](#recovery-and-safety) before installing.

## Why Sarim?

**Sarim** (Arabic: **صارم**, pronounced [/sˤaː.rim/](https://en.wiktionary.org/wiki/%D8%B5%D8%A7%D8%B1%D9%85)) names *the one who abstains* — and also, in the same root, *the one who is hard to bypass*. Both readings are the point. Most blockers are one settings screen away from being disabled in a weak moment. Sarim is built the other way around:

| | Typical blocker | Sarim |
|---|---|---|
| Can be uninstalled on impulse | Yes | No, Device Owner prevents it |
| VPN / private DNS can be changed to get around it | Usually | Blocked at the OS level |
| Detects explicit images and video | Rarely, often in the cloud | Yes, 100% on-device |
| Blocks words typed or shown inside apps | Rarely | Yes, in the apps you choose |
| Turning it off | A toggle | A 16-character recovery code stored on paper |
| Your data | Often sent to a server | Never leaves the device |

## How it works

A regular Android app lives in a sandbox. It can't see what other apps show, can't stop you from changing system settings, and can be uninstalled with two taps. That's why most blockers are so easy to get around.

Sarim is granted two special powers that ordinary apps never get:

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>1. Device Owner</h3>
      <p>The same level of control companies use to manage work phones. It lets Sarim:</p>
      <ul>
        <li>lock system settings such as VPN, private DNS, factory reset and app installs</li>
        <li>force SafeSearch and site blocklists on browsers</li>
        <li>suspend apps, and stop any app from being uninstalled, including itself</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <h3>2. Accessibility Service</h3>
      <p>The same access screen readers use to help blind users. It lets Sarim:</p>
      <ul>
        <li>read text on screen and as it's being typed</li>
        <li>look at images and video on screen, using an on-device ML model</li>
        <li>know which app is open, to suspend the ones that lead to adult content</li>
      </ul>
    </td>
  </tr>
</table>

Both are granted once, during setup, through [Shizuku](https://shizuku.rikka.app/). From then on, the Accessibility Service is protected against being turned off, and only your **recovery code** can take either power away. That code lives on a piece of paper, not in your head.

## Highlights

| | |
|---|---|
| 🔒 **Commitment** | Chosen at setup: at least 90 days, or permanent. Nothing in the app — including the recovery code — can end it early. |
| 🧠 **On-device visual blocking** | A local TensorFlow Lite model detects explicit images and video on screen and closes the app. Nothing is uploaded. |
| ⌨️ **Word blocking** | Erases forbidden adult words as they're typed, or closes the app when they appear anywhere on screen. A built-in list can't be removed. |
| 🌐 **Adult-site blocking** | A built-in domain list plus forced SafeSearch pushed into every browser, and a filtering private DNS that resolves nothing else. |
| 🛡️ **Device Owner restrictions** | VPN, private DNS, sideloading, uninstalling, safe boot, USB debugging and clock changes are closed at the OS level. |
| 🚫 **Tor, torrents and 18+ apps** | Tor/Orbot and torrent clients are suspended on sight; apps the store rates 18+ are suspended on install. |

## Features

<details>
<summary><b>System restrictions (Device Owner)</b></summary>

- The bypass-preventing restrictions are always on: block VPN configuration, block private DNS configuration, block unknown-source installs, block uninstalling apps, block app control (force-stop/uninstall), block safe boot, block USB debugging, and block clock changes — the last one so a commitment deadline can't be moved.
- Every other Android system restriction (`UserManager` policy) can additionally be toggled on from a searchable list (blocking factory reset, adding accounts, and more, depending on Android version). The mandatory ones cannot be turned off.
- Per-app restriction editor: any restriction an individual app declares (via `RestrictionsManager`) can be inspected and applied to that app specifically.

</details>

<details>
<summary><b>Browser and network</b></summary>

- Pins a family/adult-filtering private DNS (Cloudflare Family, AdGuard Family or CleanBrowsing Adult). There is no "automatic", no "off" and no custom host: any other resolver is replaced on the next launch.
- Applies a built-in adult-site `URLBlocklist` plus `ForceGoogleSafeSearch` to every browser that supports managed restrictions. The built-in list is re-applied on every launch and cannot be deleted.
- Any newly installed browser that doesn't support these policies is suspended outright instead of being left unrestricted.
- Blocks Tor and Orbot: every installed Tor component is suspended, and so is anything installed later. Detection isn't a fixed list of package ids — the package name *and* the visible app name are checked, so a Tor client published under a different id (a rebuild, a rebrand, a future Tor Project app) is caught too. Turning the block on sweeps the apps already on the phone too, so a client installed beforehand can't slip past it.

</details>

<details>
<summary><b>App management</b></summary>

- Hide/block or instantly suspend individual apps.
- Block uninstallation on a per-app basis.
- Filter by blocked or 18+-rated apps, and search the list.
- Automatically suspends apps rated 18+ or flagged with explicit content, checked against the Play Store's content rating page when an app is installed or first opened.
- Automatically blocks torrent apps (detected by their ability to handle magnet links or `.torrent` files), including the ones already installed when the block is turned on.

</details>

<details>
<summary><b>Word blocking</b></summary>

- **Active blocking:** erases a forbidden word as it's typed into a selected app and shows a block screen.
- **Passive blocking:** scans on-screen text (not just typed input) per app, useful for search results and feeds, and closes the app when a configured word appears anywhere on screen.

</details>

<details>
<summary><b>Visual blocking (on-device machine learning)</b></summary>

- A local TensorFlow Lite model (MobileNetV2) classifies on-screen images and video into drawings, hentai, neutral, porn, and sexy. No data leaves the device.
- The Accessibility Service scans the screen of selected apps for likely media regions, captures them, and runs the classifier; the app is closed when the combined score for the selected categories crosses a configurable threshold.
- Escalation is always on: after a set number of blocks within a time window, the app is suspended for a configurable duration instead of just being closed.
- Visual blocking itself is not optional: it is on from the first launch, with the porn, hentai, sexy and drawings categories always classified, and the built-in monitored app is never removable. You can add more apps, tune the sensitivity (30–70 %) and the suspension thresholds.
- **Your commitment** pins the whole stack — visual blocking, forbidden words, browser lists, SafeSearch, DNS, Tor/torrents and the system restrictions — for the period you chose at setup (90 days minimum, or permanent). Any attempt to clear the stored settings is undone on the next launch. It survives reboots and resists system clock changes in both directions.
- A timed commitment can still be ended through the 48-hour forced removal. A permanent one cannot — that is the difference between the two choices.

</details>

<details>
<summary><b>Getting in: access challenges</b></summary>

- Opening the settings requires biometric or device authentication, then the 32-word challenge by default: type a string of random letters correctly, once, to prove you meant to open it. Math, Tetris and timed reflective reading are also available.
- **Tetris:** playing for a configurable number of minutes (time only counts while actually playing). Gravity speeds up as levels rise, with a lock delay, line clear animations and sound effects that can be muted.
- **Reflective reading:** reading a configurable number of texts (5 by default) about lust and self-control, picked at random from the Bible, the Quran, quotes from philosophers and writers, or your own phrases, at a slow reading pace with words highlighted one by one.
- Every challenge screen has a "give up" button that goes back to the lock screen.

</details>

<details>
<summary><b>Other safeguards</b></summary>

- Optional shuffled keypad, so the recovery code can't be memorized by watching finger position.
- The recovery code unlocks a 10-minute session (renewable) for changing settings. It never unlocks the commitment: whatever the session, the protection layers stay on and cannot be weakened.

</details>

## Installation

**Requirements:** Android 11 or newer.

Becoming Device Owner requires a bridge between user space and system space. Follow these steps precisely:

1. **Install [Shizuku](https://shizuku.rikka.app/).** It's used to run the required ADB commands on the device itself.
2. **Enable Wireless Debugging** in Developer Options and pair it with Shizuku.
3. **Install and open Sarim**, then grant it access to Shizuku.
4. **Become Device Owner.** In the Config tab, follow the prompts to register the app as Device Owner. This runs `dpm set-device-owner` through Shizuku.
5. **Enable the Accessibility Service.** Still in the Config tab, turn on *Advanced blocking* (also applied through Shizuku). It's required for word blocking, visual blocking, blocked-app enforcement and Tor/torrent suspension. App installation is blocked while this service is off, so an unrestricted browser can't be installed in the meantime.

**Building from source?** `make help` lists the automated path — build, test, install and Device Owner setup, including a Waydroid container with no phone at hand. See [docs/BUILD_AND_RUN.md](docs/BUILD_AND_RUN.md).

**What does it actually do?** [docs/FEATURES.md](docs/FEATURES.md) is the full feature reference: every feature, its defaults, what it needs (device owner, advanced blocking) and which settings the recovery code guards.

## Recovery and Safety

During setup, Sarim generates a unique **16-character recovery code**. It's the only ordinary way to disable restrictions or uninstall the app without a full device wipe (if a wipe is even permitted by your active settings).

A step-by-step wizard sets it up, both on first setup and whenever you generate a new code: write it down on paper, type it back from the paper to confirm every letter, then choose a safe place to store it.

The last step points out the option that works best: handing the code to an **accountability partner** — a friend, a parent, a spouse — rather than keeping it in reach. The wizard can hand it straight to the share sheet with a ready-written message, so it leaves your phone in one step and you're not able to look at it when temptation arrives.

> [!IMPORTANT]
> - **Write it on paper.** The key must be recorded by hand, on a physical piece of paper.
> - **Store it somewhere hard to reach**, such as a high shelf or a different building.
> - **Never store it digitally.** Not in notes, emails or cloud storage: you may end up blocking the very tools needed to retrieve it.

### If the code is lost

The Config tab offers a **forced removal** option as a deliberate last resort. It starts a 48-hour timer, and once it expires, Device Owner privileges can be removed without the recovery code. The delay exists so it can't be used as a quick bypass, and it can be cancelled at any time before it completes.

> [!WARNING]
> **Sarim is designed to be uncompromising.** Activating full system restrictions and then losing your recovery code may leave you permanently unable to change system settings or restore the device until the 48-hour forced removal completes. This self-lockout is intentional: it's what stops your future self from relapsing. Besides the recovery code and forced removal, there is no other backdoor.

> [!NOTE]
> **Choose your commitment deliberately.** The recovery code cannot end it: during a timed commitment the 48-hour forced removal is the only way out early, and a **permanent** commitment has no way out at all. Choose a period you can live with before starting it.

## Privacy

**Your screen, your words and your settings never leave the device.** Sarim has no ads, no ad SDKs, no analytics, no crash-reporting SDK, and no telemetry.

- The visual-blocking model (`nsfw_model.tflite`) runs entirely on-device. Screens are never captured to disk and never uploaded.
- Word blocking, adult-site blocking, the classifier, the commitment and the recovery code all run and stay on the phone.
- **The only outbound request** is the age-rating check: when an app is installed or first opened, Sarim downloads that app's public Play Store listing to see whether it is rated 18+ and flagged explicit. The package name is the only thing sent; nothing about you, your screen or your usage is included, and the result is cached on the device.
- [Shizuku](https://shizuku.rikka.app/) is used locally to run the Device Owner setup commands. Shizuku runs on your device; it is not a server.
- No account is required. No data is collected, sold or shared.

The full text is in the [privacy policy](https://sarim.app/privacy).

## Fork changes

Sarim is a fork of [Déchaîner](https://github.com/warleysr/dechainer) (Apache 2.0). Changes made in this fork:

- Renamed the app to Sarim / صارم, with the application ID and namespace moved to `com.sarim.app`.
- Added Arabic as the app's default language — full `values-ar` translations, RTL layout support, and an Arabic Quran (Mus'haf) source for the reading challenge. Config → Language switches to English or Portuguese.
- Added Arabic root/stem matching to the word blocker, since single-word matching does not work well for a morphological language.
- Added unit test coverage for the security manager, and made Device Owner setup wait for the authoritative policy broadcast instead of guessing.
- Added a first-run "How it works" carousel (5 slides, en/ar/pt) that appears once after the recovery code is set and can be re-opened any time from the Config tab.
- Added a `-PplayStoreBuild=true` build flag (`BuildConfig.PLAY_STORE_BUILD`) that removes the self-update-by-APK path for the Play-distributed APK, per Google Play policy.
- Narrowed the app to one job — blocking pornography and adult content — and removed focus mode, time limits, usage warnings, app groups, colour filters, impulse lock and the activity blocker.
- Added the commitment (90 days minimum or permanent, chosen at setup, with no in-app release), the built-in adult-site list and forbidden words, the family-DNS default, the hardening restrictions (uninstall, app control, safe boot, USB debugging, clock changes), and identity-based Tor/Orbot blocking with an install-time sweep.

## License

Released under the [Apache License 2.0](LICENSE). The full license text is kept verbatim; no additional terms are imposed.

This project is a fork. It is derived from [Déchaîner](https://github.com/warleysr/dechainer), Copyright the Déchaîner authors, licensed under the Apache License 2.0. The original copyright and attribution notices are retained — see [NOTICE](NOTICE).

**Disclaimer:** this software is provided "as is", without warranty of any kind. The developers are not liable for any data loss, system instability, or permanent device lockouts resulting from the use of Device Owner privileges.
