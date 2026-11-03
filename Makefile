# Sarim — build, test, install, become Device Owner.
#
# One target for the whole job:
#
#make setup           # build + install + Device Owner + accessibility + launch
#             then first-run in-app: recovery code, commitment, onboarding
#
# The device defaults to the Waydroid container; pass DEVICE=adb for a USB phone.
# Full walkthrough: docs/BUILD_AND_RUN.md

SHELL := /bin/bash
.DEFAULT_GOAL := help

APP_ID        := com.sarim.app
ADMIN         := $(APP_ID)/.SarimDeviceAdminReceiver
ACCESSIBILITY := $(APP_ID)/.SarimAccessibilityService
APK_DEBUG     := app/build/outputs/apk/debug/app-debug.apk
APK_RELEASE   := app/build/outputs/apk/release/app-release.apk
DEVICE        ?= waydroid
# Waydroid's shell needs root on the host; a phone's does not.
SUDO          ?= sudo

# AGP accepts JDK 17-21 only. Override with `make build JAVA_HOME=/path/to/jdk-21`
# if the guess below picks the wrong one; gradle.properties has the fallback.
JAVA_HOME ?= $(firstword $(wildcard $(HOME)/.local/share/jdks/jdk-21* $(HOME)/.jdks/*21*))
GRADLE    := ./gradlew $(if $(JAVA_HOME),-Dorg.gradle.java.home=$(JAVA_HOME))

ifeq ($(DEVICE),waydroid)
  DSH     := $(SUDO) waydroid shell
  DPROBE  := waydroid status
  DINSTALL:= waydroid app install
  DLAUNCH := waydroid app launch $(APP_ID)
  DREMOVE := waydroid app remove $(APP_ID)
else ifeq ($(DEVICE),adb)
  DSH     := adb shell
  DPROBE  := adb devices
  DINSTALL:= adb install -r
  DLAUNCH := adb shell am start -n $(APP_ID)/.activities.MainActivity
  DREMOVE := adb uninstall $(APP_ID)
else
  $(error DEVICE must be waydroid or adb, got "$(DEVICE)")
endif

# ---------------------------------------------------------------- build & test

build: ## Build the debug APK
	$(GRADLE) :app:assembleDebug
	@echo "APK: $(APK_DEBUG)"

release: ## Build the minified release APK (signed only if dechainer.store* keys exist)
	$(GRADLE) :app:assembleRelease
	@echo "APK: $(APK_RELEASE)"

playstore: ## Build the Play-policy debug APK (drops the self-update path)
	$(GRADLE) :app:assembleDebug -PplayStoreBuild=true

test: ## Run the JVM unit tests
	$(GRADLE) :app:testDebugUnitTest
	@echo "Report: app/build/reports/tests/testDebugUnitTest/index.html"

check: test build ## Tests, then the APK

clean: ## Delete build outputs
	$(GRADLE) clean
	rm -rf build app/build

# ------------------------------------------------------------------- the device

doctor: ## Check the toolchain and the device before anything else
	@echo "== device ($(DEVICE)) =="
	@$(DPROBE) 2>/dev/null | sed 's/^/  /' \
		|| echo "  not reachable — start it with 'make waydroid-up' (or plug the phone in)"
	@echo "== JDK =="
	@if [ -n "$(JAVA_HOME)" ]; then "$(JAVA_HOME)/bin/java" -version 2>&1 | sed 's/^/  /'; \
		else echo "  no JDK 21 found — set JAVA_HOME=/path/to/jdk-21"; fi
	@echo "== Android SDK =="
	@if [ -f local.properties ]; then sed 's/^/  /' local.properties; \
		else echo "  local.properties missing — echo \"sdk.dir=/path/to/Android/Sdk\" > local.properties"; fi
	@echo "== Gradle =="
	@$(GRADLE) --version -q 2>/dev/null | grep -E "^(Gradle|Launcher JVM)" | sed 's/^/  /'

install: build ## Install the debug APK on the device
	$(DINSTALL) $(APK_DEBUG)

launch: ## Start Sarim on the device
	$(DLAUNCH)

# One `$(DSH) sh -c` call per target: sudo then prompts for the password once, not per line.
owner: ## Register Sarim as Device Owner (needs one user, no account, no other owner)
	@echo "==> Checking eligibility, then dpm set-device-owner $(ADMIN)"
	@$(DSH) sh -c 'set -e; \
		owners=$$(dpm list-owners 2>/dev/null); \
		if [ -n "$$owners" ]; then \
			echo "!! a device owner is already registered:"; echo "$$owners"; \
			echo "   wipe the container with: make waydroid-reset"; exit 1; \
		fi; \
		users=$$(pm list users | grep -c UserInfo); \
		if [ "$$users" -ne 1 ]; then echo "!! more than one user:"; pm list users; exit 1; fi; \
		accounts=$$(dumpsys account 2>/dev/null | grep -c "Account {") || true; \
		if [ "$$accounts" -ne 0 ]; then \
			echo "!! an account is signed in; remove it first (a GAPPS image with a"; \
			echo "   Google account can never accept a device owner)"; exit 1; \
		fi; \
		dpm set-device-owner $(ADMIN); \
		dpm list-owners'

privileges: ## Enable the accessibility service and grant the system permissions
	@$(DSH) sh -c 'settings put secure enabled_accessibility_services $(ACCESSIBILITY); \
		settings put secure accessibility_enabled 1; \
		pm grant $(APP_ID) android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true; \
		pm grant $(APP_ID) android.permission.POST_NOTIFICATIONS 2>/dev/null || true; \
		echo "== accessibility service =="; settings get secure enabled_accessibility_services; \
		echo "== permissions granted =="; \
		dumpsys package $(APP_ID) | grep -E "android.permission.(WRITE_SECURE_SETTINGS|POST_NOTIFICATIONS): granted=true"'

status: ## Show owner registration, accessibility and app state
	@$(DSH) sh -c 'echo "== device owner =="; dpm list-owners 2>/dev/null; \
		echo "== accessibility service =="; settings get secure enabled_accessibility_services; \
		echo "== app =="; \
		dumpsys package $(APP_ID) 2>/dev/null \
			| grep -E "versionName|WRITE_SECURE_SETTINGS: granted|POST_NOTIFICATIONS: granted"'

logcat: ## Follow Sarim's logs (debug builds log through Timber)
	$(DSH) logcat -s Sarim

uninstall: ## Remove Sarim from the device (a Device Owner must be released first)
	-$(DSH) dpm remove-active-admin $(ADMIN) --user 0
	$(DREMOVE)

# ---------------------------------------------------------------------- setup

setup: ## First-time setup: build, install, Device Owner, accessibility, launch
	$(MAKE) build
	$(MAKE) install
	$(MAKE) owner
	$(MAKE) privileges
	$(MAKE) launch
	@echo
	@$(MAKE) status

deploy-waydroid: ## Same as setup, inside the Waydroid container
	$(MAKE) setup DEVICE=waydroid

deploy-adb: ## Same as setup, on the USB phone
	$(MAKE) setup DEVICE=adb

# ------------------------------------------------------------------- waydroid

waydroid-up: ## Start Waydroid (running `sudo waydroid init` first if never initialised)
	@if [ ! -f /var/lib/waydroid/waydroid.cfg ]; then \
		echo "==> first run: downloading the Android image"; sudo waydroid init; fi
	@sudo systemctl enable --now waydroid-container
	@waydroid session start >/dev/null 2>&1 || true
	@sleep 2
	@waydroid status

waydroid-restart: ## Reboot Android inside the container (checks that state survives)
	sudo waydroid container restart
	@sleep 3
	@waydroid status

waydroid-down: ## Stop the session and the container
	-waydroid session stop
	-sudo waydroid container stop

waydroid-reset: ## Wipe the container back to a fresh Android (drops owner, accounts, app data)
	-sudo waydroid container stop
	sudo waydroid init -f
	$(MAKE) waydroid-up

help: ## Show this help
	@echo "Sarim — build, run and Device Owner automation"
	@echo "device: $(DEVICE)   (override with DEVICE=waydroid|adb)"
	@echo
	@grep -hE '^[a-zA-Z0-9_-]+:.*?## ' $(MAKEFILE_LIST) \
		| sort \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

.PHONY: build release playstore test check clean doctor install launch owner \
	privileges status logcat uninstall setup deploy-waydroid deploy-adb \
	waydroid-up waydroid-restart waydroid-down waydroid-reset help
