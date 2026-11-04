# Build and run Sarim

Everything needed to go from a fresh checkout to a running app that is **Device Owner**,
either inside a [Waydroid](https://waydro.id/) container or on a USB phone. All of it is
wrapped in the [`Makefile`](../Makefile) at the repo root — run `make help` for the short
version of this page.

The feature-by-feature test checklist and the manual (no-Makefile) commands stay in
[FEATURES.md](FEATURES.md); this document is the build/run/deploy path.

---

## 1. Requirements

| | Needed for | Notes |
|---|---|---|
| **JDK 17–21** | Gradle | AGP 8.13 rejects 22+. A default `java` of 27 fails with the opaque `What went wrong: 27-ea`. |
| **Android SDK platform 36** + build-tools | Gradle | Path goes in `local.properties` as `sdk.dir=...`. |
| **Waydroid** | the container path | Running Wayland session; `waydroid` and `sudo` on the host. |
| **adb** | the phone path | `adb devices` must list the phone, USB debugging on. |

`gradle.properties` pins a JDK path, and the `Makefile` prefers the first `jdk-21*` it finds
under `~/.local/share/jdks` or `~/.jdks`. If your JDK lives elsewhere, pass it explicitly:

```bash
make build JAVA_HOME=/usr/lib/jvm/java-21-openjdk
```

## 2. Quick start (Waydroid)

```bash
make doctor          # JDK, SDK, Gradle, and whether the container answers
make waydroid-up     # starts the container + session (runs `sudo waydroid init` on first use)
make setup           # build → install → Device Owner → accessibility → launch
```

`make setup` is the whole first-run story in one command. When it finishes, the Android
window shows Sarim with the Config tab badge already green — *Granted* — and *Advanced
blocking* already on.

### What `setup` actually does

| Step | Command it runs |
|---|---|
| `build` | `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` |
| `install` | `waydroid app install app/build/outputs/apk/debug/app-debug.apk` |
| `owner` | eligibility checks, then `sudo waydroid shell dpm set-device-owner com.sarim.app/.SarimDeviceAdminReceiver` |
| `privileges` | `sudo waydroid shell`: `settings put secure enabled_accessibility_services com.sarim.app/.SarimAccessibilityService`, `settings put secure accessibility_enabled 1`, `pm grant … WRITE_SECURE_SETTINGS` |
| `launch` | `waydroid app launch com.sarim.app` |
| `status` | `dpm list-owners`, the accessibility setting, and the app's version/permissions |

`waydroid shell` needs root on the host, so the targets that talk to Android's package, device
policy and settings services call `sudo waydroid shell` — expect one password prompt per target.
Override the escalation with `make setup SUDO='doas'` (or `SUDO=` for a rootless setup).

Before running `dpm`, the `owner` target refuses to continue when the container is not
eligible — another owner is already set, more than one user exists, or an account is signed
in. Those are the same three conditions Android enforces; a GAPPS image with a signed-in
Google account can never accept a Device Owner (re-init with `sudo waydroid init -f -s VANILLA`,
or remove the account).

### Why there is no Shizuku here

On a phone, Sarim earns Device Owner by asking Shizuku to run the `dpm` command in a shell
with system privileges. `waydroid shell` already *is* such a shell, so the same commands run
from the host and Shizuku is never needed. The app does not care which route granted the
privilege — it detects the Device Owner state on its own.

## 3. Targets

| Target | What it does |
|---|---|
| `make help` | list every target |
| `make doctor` | check JDK 17–21, Android SDK, Gradle, device reachability |
| `make build` | debug APK |
| `make release` | minified release APK (signed only if the `dechainer.store*` keys exist in `local.properties`) |
| `make playstore` | debug APK with `-PplayStoreBuild=true`: no self-update / unknown-sources path, per Play policy |
| `make test` | JVM unit tests (no device); HTML report in `app/build/reports/tests/testDebugUnitTest/` |
| `make check` | `test` then `build` |
| `make install` | build, then install the APK |
| `make launch` | start the app |
| `make owner` | eligibility checks + `dpm set-device-owner` |
| `make privileges` | accessibility service + `WRITE_SECURE_SETTINGS` |
| `make status` | owner registration, accessibility, app version and grants |
| `make setup` | build, install, owner, privileges, launch, status |
| `make deploy-waydroid` / `make deploy-adb` | `setup` pinned to that device |
| `make logcat` | follow `logcat -s Sarim` |
| `make uninstall` | remove the app (release Device Owner first, see §6) |
| `make clean` | delete build outputs |
| `make waydroid-up` | start container + session; `sudo waydroid init` if never initialised |
| `make waydroid-restart` | reboot Android inside the container — use it to prove state survives a reboot |
| `make waydroid-down` | stop the session and the container |
| `make waydroid-reset` | wipe the container back to a fresh Android (`sudo waydroid init -f`) |

Every device target talks to Waydroid by default. Add `DEVICE=adb` for a USB phone:

```bash
make setup DEVICE=adb
```

## 4. After setup: first-run inside the app

The Makefile can grant the two system powers, not the parts that belong to you:

1. **Recovery code wizard** — 16 characters. Write them on paper, type them back from the
   paper, store the paper somewhere hard to reach. There is no digital copy, and this code
   is the only ordinary way to *weaken* the protection layers later: it opens a 10-minute
   session for the protection settings that are adjustable at all (monitored apps, word lists,
   URL lists, the DNS host among the three, optional system restrictions, app blocking) and for
   changing the code itself. Language, keyboard layout and the forced-removal timer never ask
   for it.
2. **Onboarding carousel** — the 5-slide tour appears once after the code is set.
3. Walk the layers in [FEATURES.md §2](FEATURES.md) to exercise each protection.

> [!WARNING]
> Once `make owner` succeeds, the restrictions you switch on in the app apply for real, and
> the app cannot be uninstalled from the launcher anymore. In the container that is cheap to
> undo (`make waydroid-reset`); on a phone it is not. Read
> [Recovery and Safety](../README.md#recovery-and-safety) first.

### What the Makefile cannot do for you

The Makefile grants the two Android powers (owner and accessibility), but not the choices that
belong to you:

- the recovery code — yours, on paper;
- the commitment — yours, chosen once at setup;
- the optional monitored apps, the DNS host among the three, and any extra words or URL lists —
  yours, in Settings.

Tor/Orbot and torrents are already blocked before you touch the app, and they cannot be cancelled.


## 5. The phone path

```bash
adb devices                      # the phone must be listed and authorised
make doctor DEVICE=adb
make setup DEVICE=adb
```

`dpm set-device-owner` from adb only works on a device with **no accounts, one user and no
other device admin** — in practice a freshly factory-reset phone, before the setup wizard
signs into Google. If the command fails, the app's own route still works: install Shizuku,
open Config → *Device owner state* → *Get owner privileges*.

## 6. Releasing Device Owner

A Device Owner cannot simply be uninstalled. In order of convenience:

- **In the app** — Config → *Forced removal* starts a 48-hour timer; the privileges can be
  dropped once it expires. This is the intended last resort if the recovery code is lost.
- **In the container** — `make waydroid-reset` wipes Android back to a fresh image, which
  drops the owner, the accounts and all app data in one go.
- **On a phone** — let the app's 48-hour forced removal complete, then uninstall normally.
  Device Owner is also released by a factory reset (if your active restrictions allow one).

## 7. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `What went wrong: 27-ea` | the build used JDK 22+. Run `make build JAVA_HOME=/path/to/jdk-21`. |
| `SDK location not found` | no `local.properties`: `echo "sdk.dir=$HOME/Android/Sdk" > local.properties`. |
| `dpm set-device-owner` → *already several users/accounts* | `make waydroid-reset`, or remove the account in Settings → Accounts. |
| `dpm` → *already set* | something is already owner: `make status`, then `make waydroid-reset`. |
| Accessibility shows *off* after `make privileges` | reopen the app; some images need `settings put secure accessibility_enabled 1` again after a reboot — `make privileges` is idempotent, just rerun it. |
| `waydroid app install` fails | the session is not running: `make waydroid-up`. |
| App installed but blank screen | check `make logcat` for a `ClassNotFoundException` on a native lib; the debug APK ships `x86_64` (for the container) and ARM libs, so reinstall with `make install`. |
| Visual blocking slow | LiteRT runs on the CPU here — the APK intentionally ships without the GPU accelerator. |

## 8. What Waydroid cannot verify

The container is a phone-shaped Android, not a phone: no SIM, no biometric sensor, no real
camera, no Bluetooth/NFC. Authentication falls through (no credential enrolled), telephony
restrictions are untestable, and battery/thermal/OEM behaviour needs the real device.
Everything else — owner restrictions, accessibility-driven blocking, visual and word blocking,
the commitment countdown, RTL Arabic rendering and reboot survival — runs exactly as it does on a
phone. See [docs/FEATURES.md](FEATURES.md) for what each layer does.

## 9. Repository layout

```
app/src/main/java/com/sarim/app/
  activities/     entry points: MainActivity, lock screen, block screens
  data/           policy and persistence (device admin, restrictions, browser policy, blocklists)
  models/         small data types shared by the screens
  notifications/  APK-update result receiver
  screens/        Compose UI: tabs, common components, challenges, setup
  security/       recovery code, sessions, commitment, mandatory protection
  utils/          accessibility helpers, ML detector, Shizuku bridge, locale
  viewmodels/     state holders for the tabs
app/src/main/res/ en/ar/pt strings, icons, service configs
app/src/test/     JVM unit tests (Robolectric where Android APIs are needed)
docs/             this document and the logo
```

Build outputs (`build/`, `app/build/`) and machine-local files (`local.properties`,
`.gradle`, `.kotlin`) are generated and ignored by git.
