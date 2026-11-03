# Sarim — feature reference

Sarim does one job: keep pornography and adult content off an Android phone, and keep the way back
in closed. Every other kind of self-control feature — focus timers, app time limits, usage
warnings, app groups, colour filters, an impulse button — was removed on purpose. What is left is
a small number of layers that are all on from the first launch, plus a commitment that freezes them
for a period the user cannot shorten.

This document describes the app as it is built: what each layer does, its defaults, what it
needs, and exactly which actions are gated by the recovery code and which by the commitment.
Each layer that does not touch adult-content protection no longer needs the recovery code
to adjust it.

### The hardening-first design

The app no longer asks for the recovery code for anything except the layer that actually keeps
adult content out. Concretely:

- **No secret code needed** — visual blocking, forbidden words, browser restrictions, private DNS,
  system restrictions, app blocking/suspending, the accessibility goal, the DNS choice, the
  built-in blocking, the commitment itself, language, keyboard layout and the forced-removal timer
  are all reachable in Settings without the code. The code is only ever used on the Screen time
  and Recovery code Change screens.
- **Tor/Orbot and torrents are on by default and cannot be cancelled.** The toggles still render on
  the Config tab for visibility, but both return `true` from a no-setter getter and `enforce()`
  puts them back whenever they are out of line.
- **Any source that could fetch pornography is blocked by default and not cancellable.** That covers
  the built-in adult-site browser list, forced SafeSearch, the three filtering DNS hosts, Tor and
  torrents, the developer/unsafe-source routes, and — on the adult side only — the commit lock
  itself once it is running.

The recovery code is still the only ordinary way to *weaken* an adult-protection layer later, which
is exactly what the code was added to guard. Everything else is now a settings change that the user
can make directly.

### What “default and not cancellable” means in the code

`security/MandatoryProtection.enforce()` is the single place that re-asserts all of it. It is
idempotent in both directions — it writes only what is out of line and returns whether it had to
write anything — so it runs on every app launch (`SarimApplication`), on every accessibility
service start, on `CommitmentLock.enforce`, and from the preference listener in the service.

There are no "off" switches for the layers below. Two of them still have preferences, kept so the
service reads a truthful value; `enforce()` puts them back:

```kotlin
fun isBlockTorEnabled(): Boolean = true        // no setter
fun isBlockTorrentsEnabled(): Boolean = true   // no setter
```

---

## 1. Setup, in order

The app is unusable until three things exist, in this order:

1. **A recovery code** — generated in-app, written on paper, handed to someone else if possible.
2. **The commitment** — how long protection stays locked on.
3. **Device owner and advanced blocking** — the two Android powers the enforcement relies on,
   granted through [Shizuku](https://shizuku.rikka.app/) and re-checked on every launch.

### 1.1 Device owner

`DeviceOwnerRepository` runs `dpm set-device-owner com.sarim.app/.SarimDeviceAdminReceiver`
through Shizuku and then waits for the authoritative `DEVICE_POLICY_CHANGED` broadcast
(`SarimPolicyUpdateReceiver` → `DeviceAdmin.persistResult`) instead of trusting the shell exit code.

Device owner is what unlocks, at the OS level:

- the `UserManager` restrictions in §2.7,
- a private DNS host (§2.4),
- `setPackagesSuspended` — suspending Tor, torrents, unsupported browsers and 18+ apps (§2.5, §2.6),
- `setApplicationHidden` / `setUninstallBlocked` on the Apps screen,
- `setApplicationRestrictions` — the `URLBlocklist` and `ForceGoogleSafeSearch` policies (§2.3).

Without it the app still runs, but only the accessibility layer works, and the Protection tab
shows an error card saying the protection is incomplete.

### 1.2 The commitment

`security/CommitmentLock.kt`.

| | |
|---|---|
| Presets | 90, 180, 365, 730 days, or **permanent** |
| Accepted range | 90 – 3650 days (values below the floor are raised to 90) |
| Stored as | `security_prefs` → `commitment_start_rtc`, `commitment_start_elapsed`, `commitment_duration_ms` (`-1` = permanent), `commitment_last_seen_rtc` |
| Chosen at | setup, once, before the app opens for the first time |
| Release | the period running out; or the 48-hour forced removal — **not offered for a permanent commitment** |

`start()` is the only entry point and `hasStarted()` guards it: a running commitment is never
restarted, shortened or replaced. There is no `stop()` anywhere in the app.

**What it freezes.** While `isLocked()`:

- every action that goes through `RecoveryGate` is refused, *including* one a recovery session
  would normally authorise — the recovery code does not override the commitment. This covers
  blocked-word edits, browser lists, DNS, monitoring changes, app unblocking/unsuspending, the
  accessibility switch being turned *off*, and changes to the recovery code itself;
- the only bypass is `strengtheningAction = true`, used by the few controls that can only add
  protection: enabling advanced blocking, blocking an app, suspending an app.

**Telling time.** The deadline is recomputed, never stored as a countdown:

- `elapsedRealtime` is authoritative while the device stays up — changing the date does nothing;
- after a reboot (`elapsedRealtime` going backwards) the wall clock takes over;
- a watermark (`commitment_last_seen_rtc`) records the furthest date ever seen, so setting the date
  *backwards* after a reboot cannot buy back time;
- the wall-clock reading is capped at the deadline, so an absurd jump forward cannot make the
  remaining time negative either.

A permanent commitment reports `Long.MAX_VALUE` remaining, `0` as an end time, and
`SecurityManager.canStartForcedRemoval()` returns false.

### 1.3 Recovery code and sessions

`security/SecurityManager.kt`, `screens/common/RecoveryCodeWizard.kt`.

- 16 random characters from `A–Z`, generated with `SecureRandom`.
- The wizard: generate → write it down → type it back from the paper letter by letter → choose
  where to keep it, with a one-tap option to send it to an accountability partner. Two dialogs
  confirm and regenerate it later (`RecoveryGenerateDialog`, `RecoveryConfirmDialog`).
- Entering it opens a **10-minute session** (`SecurityManager.sessionEndTime`, renewable, shown as
  a countdown pill in the top bar). Every gated action calls `RecoveryGate.run`, which asks for the
  code only when no session is active.
- The session never weakens the protection layers themselves: it only allows editing settings that
  the layers leave adjustable, and only when the commitment is not running.

---

## 2. The protection stack

`security/MandatoryProtection.enforce()` is the single place that re-asserts all of it. It is
idempotent in both directions — it writes only what is out of line and returns whether it had to
write anything — so it runs on every app launch (`SarimApplication`), on every accessibility
service start, on `CommitmentLock.enforce`, and from the preference listener in the service.

There are no "off" switches for the layers below. Two of them still have preferences, kept so the
service reads a truthful value; `enforce()` puts them back:

```kotlin
fun isBlockTorEnabled(): Boolean = true        // no setter
fun isBlockTorrentsEnabled(): Boolean = true   // no setter
```

### 2.1 Visual blocking

`SarimAccessibilityService` + `utils/NsfwContentDetector.kt` (LiteRT / MobileNetV2).

| | |
|---|---|
| Model | `nsfw_model.tflite`, bundled, 224×224 input, on-device only |
| Labels | `drawings`, `hentai`, `neutral`, `porn`, `sexy` |
| Categories classified | **all four adult ones**, always (`VisualBlockingSettings.DEFAULT_CATEGORIES`); `neutral` is never in the list |
| Monitored apps | `com.reddit.frontpage` by default and **not removable**; any other app can be added |
| Sensitivity | 0.5 default; adjustable 0.3 – 0.7 while a session is active |
| Scan throttle | at most one scan per 2 s, never overlapping |

How a scan happens:

1. On a content-changed event in a monitored app, the accessibility tree is walked for likely media
   views — view class names (`Image`, `Photo`, `Video`, `Media`, `Player`, `Gif`, `TextureView`,
   `SurfaceView`, `WebView`), accessibility vocabulary (`foto`, `photo`, `imagem`, `video`, …),
   Reddit's post-card description pattern, and finally a geometric fallback (unlabelled leaf nodes
   with a plausible photo/video aspect ratio, capped at 5 candidates).
2. Only those regions are screenshotted and cropped — not the whole screen — so the classifier sees
   the media rather than a page of text and buttons.
3. Each crop is classified; the highest score across the adult categories is compared with the
   threshold. Above it, the app is closed.

**Escalation is always on.** Detections are counted per package in a sliding window
(`VisualBlockingSuspensionTracker`, persisted). When an app is blocked 5 times within 15 minutes it
is **suspended for 30 minutes** instead of just closed; the release timer is re-armed after a
service restart from the persisted deadline. Below the threshold the app is suspended and
immediately un-suspended, which force-closes it without leaving it blocked.

Windows that are closed by the flanking screens: the blocked-content screen
(`NsfwContentBlockedActivity`) shows how long a suspension lasts.

### 2.2 Forbidden words

`security/MandatoryProtection` seeds `data/ProtectionDefaults.FORBIDDEN_WORDS` — English, Arabic and
Portuguese adult vocabulary, matched case-insensitively on word boundaries (`(?<![\p{L}\p{N}_])`).
The list is unioned back in on every launch and re-union'd on every save
(`BlockedWordsViewModel.updateWords`), so nothing in the UI can remove it; the user can add more.

Two modes, per app:

- **Active** — after the user stops typing in an editable field of a selected app, the text is
  checked and the app is sent `GLOBAL_ACTION_BACK` with the word shown on
  `BlockedWordActivity`. Every installed browser is always in this list (re-added by
  `enforce()`), so an address bar or a search query is covered.
- **Passive** — per app, an optional extra list checked against the text *and* content descriptions
  currently on screen; a match closes the app the same way.

Matching is conservative on purpose: the screen text is only checked for words that are actually
configured, and the built-in list avoids words that appear in ordinary health or news content.

### 2.3 Adult sites in browsers

`data/BrowserRestrictionsManager.kt` + `data/ProtectionDefaults.ADULT_SITES`.

- Every installed app that resolves `CATEGORY_APP_BROWSER`, `http://` or `https://` is treated as a
  browser.
- For browsers that declare `URLBlocklist` and `ForceGoogleSafeSearch`
  (`RestrictionsManager.getManifestRestrictions`), a managed configuration is pushed with the
  built-in list (~80 adult domains, matched with their subdomains) plus every user list, and
  `ForceGoogleSafeSearch = true`.
- The built-in list lives under the reserved id `sarim-mandatory-adult-sites`: it is re-written on
  every launch, it cannot be edited or deleted in the UI, and `applyRestrictions()` unions the
  domains in regardless of what the preference file says.
- A browser that does not support managed restrictions is **suspended** instead of being left
  unrestricted — both when it is first seen and when it is installed while the service is running
  (the package-added receiver).
- Every newly installed browser gets the policy immediately, before it can be opened.

### 2.4 Filtering private DNS

`MandatoryProtection.ensureFilteringDns`, `DeviceOwnerRepository.setPrivateDNS`.

Only three hosts are accepted:

| Host | Label |
|---|---|
| `family.cloudflare-dns.com` | Cloudflare Family |
| `family.adguard-dns.com` | AdGuard Family |
| `adult-filter-dns.cleanbrowsing.org` | CleanBrowsing Adult |

There is no "automatic", no "off" and no free-text host, and the OS setting itself is closed by
`DISALLOW_CONFIG_PRIVATE_DNS`. On every launch, if the current mode is not "hostname" or the host
is not one of the three, it is replaced with `family.cloudflare-dns.com`; the picker
(`DnsSelectionDialog`) only offers the three.

### 2.5 Tor, Orbot and torrents

Detection (`BrowserRestrictionsManager`):

- **Tor** — three signals, in order: the official/legacy package ids
  (`org.torproject.torbrowser`, `…android`, `…torservices`, `info.guardianproject.orbot`, …); a
  normalized identity match on package name *and* visible label (`torbrowser`, `torproject`,
  `orbot`, `orfox`, `onionbrowser`), which catches rebuilds and rebrands; and a cached sweep of all
  installed apps.
- **Torrents** — every app that handles a `magnet:` link or an `application/x-bittorrent` payload,
  probed through five intent shapes so clients that only register one of them are still found.

Enforcement: anything detected is suspended via `setPackagesSuspended`, recorded in
`security_prefs` (`tor_suspended_packages`, `torrent_suspended_packages`), and swept again on every
service start — so a client that was already installed before setup is caught too. Installation of
a new Tor/torrent client produces the package-added broadcast, which suspends it on the spot.

### 2.6 Apps the store rates 18+

`data/PlayStoreRatingFetcher.kt`, checked from the accessibility service:

- when a package is installed (package-added receiver), and
- the first time an app is opened, if it has no cached rating.

If the store reports explicit content **and** `Rated 18+`, the app is suspended. The rating is
cached in `app_ratings` so it is fetched once; a 404 is cached as "not explicit". Offline (or a
store page that cannot be fetched) means no action — the visual, word and DNS layers still apply.

### 2.7 System restrictions

`MandatoryProtection.RESTRICTIONS`, applied whenever the admin is active and never removable:

| Restriction | What it closes |
|---|---|
| `DISALLOW_CONFIG_VPN` | pointing the device at a VPN that skips the filtering DNS |
| `DISALLOW_CONFIG_PRIVATE_DNS` | switching the resolver back |
| `DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY` | sideloading a browser or an app the store would not hand out |
| `DISALLOW_UNINSTALL_APPS` | uninstalling the blocker |
| `DISALLOW_APPS_CONTROL` | force-stopping or clearing it |
| `DISALLOW_SAFE_BOOT` | booting into safe mode, where third-party apps are off |
| `DISALLOW_DEBUGGING_FEATURES` | attaching ADB or a debugger |
| `DISALLOW_CONFIG_DATE_TIME` | moving the clock a commitment is measured against |

Everything else Android exposes as a `UserManager` policy is listed on the **System restrictions**
screen, searchable, and can be added on top (factory reset, accounts, Bluetooth, and so on). The
eight above render as *always on* checkboxes that cannot be unticked.

### 2.8 Advanced blocking (accessibility)

The switch lives in Settings and is applied through Shizuku
(`DeviceOwnerRepository.changeAccessibilityPermission`), i.e. it can be granted without touching
Android's accessibility settings. It is what runs visual blocking, word blocking, blocked-app
enforcement against typed text, and the media scan.

When the service goes down without being asked to (`onUnbind` with `disablingService == false`),
it suspends the apps that were being watched for words and applies `DISALLOW_INSTALL_APPS` until it
is back, then opens `AccessibilityRequestActivity` to ask for it again. Turning it back on is
allowed even during a commitment, since it only adds protection.

---

## 3. Getting in

`activities/LockScreen.kt`.

1. The lock screen opens showing the commitment (days left, or "Permanent"). Nothing else is behind
   it yet.
2. **Access app** → biometric prompt, falling back to the device credential, falling back to no
   check at all if the device has neither configured.
3. Then the access challenges (`SecurityManager.getAccessChallenges`), run in sequence. The default
   is **Words**: a random string that has to be typed back exactly, once. Math, Tetris and timed
   reflective reading can be configured instead.
4. Every challenge screen has *give up*, which returns to the lock screen without opening the app.

APK updates of already-installed apps are offered here without authentication (only when unknown
sources are restricted, so the APK path exists at all). The Play Store build disables that path
(`BuildConfig.PLAY_STORE_BUILD`).

---

## 4. Apps screen

Search, plus filter chips for *system apps*, *blocked* and *explicit content*. Each row has three
toggles:

| Toggle | Calls | Effect |
|---|---|---|
| Block | `AppRepository.setAppHidden` | hides the app from the launcher (`setApplicationHidden`); re-applied on reinstall |
| Suspend | `AppRepository.setAppSuspended` | suspends it outright; the icon stays but tapping it does nothing |
| Uninstall lock | `AppRepository.setUninstallBlocked` | the app cannot be removed |

Apps are flagged `18+` when the store rating check found explicit content. Blocking and suspending
are allowed during a commitment; unblocking and un-suspending are not.

---

## 5. Settings screen

| Row | What it does |
|---|---|
| Commitment | days left or *Permanent*; read-only |
| Device owner | state badge; opens the Shizuku setup screen |
| Advanced blocking | accessibility switch (gated; enabling is allowed during a commitment) |
| Visual blocking | monitored apps, categories (fixed), sensitivity, suspension thresholds |
| Blocked words | active list, per-app passive lists |
| Browser restrictions | installed browsers, per-browser suspension, user URL lists, the built-in list |
| Private DNS | the three filtering resolvers |
| System restrictions | the mandatory eight plus every other `UserManager` policy |
| Tor / torrents | always on, shown as disabled switches |
| How it works | the 5-slide onboarding carousel again |
| Language | English, Portuguese, Arabic (Arabic is the default) |
| Keyboard | normal or shuffled keypad for the code and the word challenge |
| Change recovery code | regenerate, gated |
| Forced removal | 48-hour timer, or "disabled" for a permanent commitment |

---

## 6. Forced removal

`SecurityManager.startForcedRemoval` / `getForcedRemovalRemainingTime` — accumulated with
`elapsedRealtime`, so a reboot does not pause it, capped at 48 hours. When it reaches zero, device
owner can be removed without the recovery code, and the app can be uninstalled.

It is cancellable before it completes, and it is **not offered at all for a permanent commitment**
(`canStartForcedRemoval` returns false, and the Settings row renders as disabled). That is the only
difference between "permanent" and a very long period, and the setup screen says so before the
choice is made.

---

## 7. What each layer does not gate

The only screen that asks for the recovery code is **Screen time** (the challenge that opens before
settings) and **Recovery code** (changing the code itself). Every other screen is a normal settings
change the user can make directly — in particular:

- the visual-blocking monitored apps, categories and sensitivity;
- the forbidden-words active list and per-app passive lists;
- the browser list, per-browser suspension and extra URL lists;
- the private-DNS choice among the three filtering resolvers;
- the system restrictions list;
- app blocking, suspending and the uninstall lock;
- the accessibility goal and the DNS choice;
- language and keyboard layout;
- the forced-removal timer (when available).

The code only protects the adult layers — which is why it exists.

---

## 8. What is no longer in the app

Removed, along with their screens, models, services, receivers and strings:

| Removed | Why |
|---|---|
| Focus mode (Pomodoro), focus schedules, focus reminders, focus notifications + foreground service | productivity, not adult-content blocking |
| Per-app daily/weekday limits, time windows, reopening cooldowns, usage tracking | screen-time management |
| Usage-warning notifications and stages | belongs to the time-limit feature |
| App groups | only existed to share limits and windows |
| Colour filters (grayscale, night light, extra dim, inversion) | comfort/sleep feature |
| Impulse lock and the panic button | replaced by the commitment; its 15–360-minute timer was a time-management tool |
| Activity blocker (blocking individual screens by class name) | short-video/social-media blocking |
| Strictness lock | superseded by the commitment: 90 days minimum, permanent option, no optional start |

Also removed with them: `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_SPECIAL_USE` and `POST_NOTIFICATIONS` from the manifest, and the
`notification_focus` / `notification_usage_warning` layouts with their icons.

---

## 9. Fixed, adjustable, and gated

**Fixed — no UI can change it:**

- visual blocking on, all four adult categories, suspension escalation on;
- the built-in forbidden words and the built-in adult-site browser list;
- SafeSearch forced on every supporting browser;
- Tor/Orbot and torrent blocking;
- the eight system restrictions;
- the commitment itself, once started.

**Adjustable — by an authenticated session, and only when no commitment is running:**

- which extra apps are monitored visually (the default one cannot be removed);
- visual sensitivity within 0.3 – 0.7, suspension counts/windows/durations;
- additional blocked words, per-app passive lists;
- extra URL lists, suspending a particular browser;
- the DNS choice among the three filtering resolvers;
- extra `UserManager` restrictions;
- blocking/suspending any app (blocking stays available even during a commitment);
- the recovery code itself;
- language and keyboard layout (not gated);
- the 48-hour forced removal timer (not gated; unavailable when permanent).

---

---

## 10. Where the state lives

| Preference file | Contents |
|---|---|
| `security_prefs` | recovery code + session, commitment, forced removal, access challenges, Tor/torrent suspension sets, admin result, shuffle keyboard |
| `visual_blocking_prefs` | enabled (always true), monitored packages, categories, threshold, suspension settings |
| `blocked_words_prefs` | `blocked_words` (built-in ∪ user), `target_packages`, `passive_words_<package>` |
| `browser_prefs` | `blocked_lists_json` (built-in list + user lists) |
| `hidden_apps_prefs` | packages to re-hide when they are reinstalled |
| `app_ratings` | package → has explicit content |
| `visual_blocking_suspensions` | suspension deadlines per package |
| `onboarding_prefs` | whether the one-time carousel has been shown |

---

## 11. Requirements and limits

- **Android 11+.** The screen capture used by visual blocking needs the accessibility
  `takeScreenshot` API (30+), and `setPackagesSuspended`/private DNS need a modern `DevicePolicyManager`.
- **Device owner is effectively mandatory.** Without it the app can still read words and scan
  images, but cannot suspend apps, filter DNS, or hold the system restrictions — the Protection tab
  says so until it is granted.
- **Advanced blocking is required** for every content layer. With it off, the `onUnbind` path
  suspends the word-watched apps and blocks app installs until it is back on.
- **A rooted device, a custom ROM, or a factory reset are outside any app's reach.** Nothing on the
  phone can stop someone who can reflash it. Within Android's own options, the restrictions above
  close the usual routes: VPN, resolver, sideload, uninstall, force-stop, safe mode, ADB and the
  clock.
- **The classifier can miss.** It sees the regions the accessibility tree exposes; an app that
  renders media in a way the heuristics cannot identify, or content that scores below the
  threshold, will not be caught by this layer. The DNS, URL, word and 18+ layers are independent of
  it.
- **The Play Store build** has no self-update path (`PLAY_STORE_BUILD`).
- **Waydroid** (used for testing in `docs/BUILD_AND_RUN.md`) has no real Play Store, so the 18+
  rating check finds nothing there, and screenshot-based scanning depends on the host GPU.
