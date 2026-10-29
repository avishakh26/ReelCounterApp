# CLAUDE.md: context for AI assistants

Read this first. It records what was set up, decided and built across earlier sessions, and
what is still in progress, so a new session can continue without re-discovering it.
Engineering docs for each phase are in `docs/` (start with `docs/README.md`).

## 1. The project

ReelCounter: an Android app (Kotlin, Jetpack Compose, Material 3, Hilt, Room, WorkManager) that
shows how much short-form video someone watches in TikTok, Instagram, YouTube and Facebook, and
lets friends compare. Backend: Supabase (Postgres + RLS, Auth, Realtime).

- **Counting is time-based, not per video.** Android's Usage Access (`UsageStatsManager`) gives
  foreground app time; reels = app time × short-form share ÷ seconds per reel
  (`core/domain/.../tracking/Estimator.kt`). There is no AccessibilityService and no overlay.
  Every figure is labeled Verified / Estimated / App time only / Not tracked.
- **Honesty rules (keep them):** estimates always show "≈" and a likely range; days with no
  data are null and shown as "No data", never as 0, and are left out of averages; app time is
  never called "reels time".
- Modules: `app`, `core/{common,data,database,datastore,designsystem,domain,model,network,preview,testing,tracking,ui,work}`,
  `feature/{auth,friends,insights,privacy,profile,settings,today,tracking}`. Features never depend on each other;
  navigation between them is wired in `app/.../navigation/ReelCounterNavHost.kt`.
- Package `com.reelcounter.app`; debug builds add `.debug`.

## 2. The user

- Builds this app and tests it themselves; not deeply familiar with Android tooling, so give
  step-by-step instructions (Windows 11, PowerShell/Git Bash).
- Wants to use the app on their own phone (realme 9 5G Speed Edition, Android 11+) via an APK.
  USB debugging never worked on their cable/port ("Device Descriptor Request Failed").
- Earlier asked for a QA checklist with the rule "don't modify code"; that rule applied to that
  task only. Later explicitly asked for a full UI redesign and new features.

## 3. Local environment (this PC)

- Project: `C:\Users\USER\Desktop\Reel-Counter-main`, a git repo pushed to
  https://github.com/avishakh26/ReelCounterApp (public, branch `main`). `.gitignore` keeps out
  `secrets.properties`, `local.properties`, `*.apk`, `bin/` and `*.local.txt` (demo password).
- JDK 19 on PATH (Gradle needs 17+). JDK tools: `C:\Program Files\Java\jdk-19\bin`.
- Android SDK installed by CLI at `%LOCALAPPDATA%\Android\Sdk` (platform-tools, emulator,
  `platforms;android-37.0`, `build-tools;37.0.0`, `system-images;android-36;google_apis_playstore;x86_64`).
  `local.properties` points at it. Android Studio is NOT installed.
- Emulator AVD: `ReelCounter_API36` (Pixel 7). Start:
  `"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd ReelCounter_API36 -gpu auto -no-boot-anim` (run in background).
- **`COMPILE_SDK` must be 37** in `build-logic/convention/src/main/kotlin/com/reelcounter/buildlogic/KotlinAndroid.kt`.
  Another tool on this PC once changed it to 36 and broke the build (AndroidX deps require 37).
  The comment above it ("Highest platform available…") was added by that tool.
- Git Bash quirks: set `MSYS_NO_PATHCONV=1` for `adb shell` paths like `/sdcard/...`; Windows
  Python needs `cygpath -w` paths; complex Python inside bash heredocs can break: write the
  script to a file and run it.

## 4. Backends and `secrets.properties`

`secrets.properties` (git-ignored, repo root) sets `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`.
The build refuses secret/service_role keys and allows plain http only for localhost,
127.0.0.1 and 10.0.2.2.

- **Hosted project (current setting, used by the phone APK):** `https://gsdzfjylnwbtxatmfglf.supabase.co`.
  The publishable key is in `secrets.properties`. Auth: Email provider ON, "Confirm email" OFF
  (found under Authentication → Sign In / Providers → **User Signups**, not inside the Email panel),
  so sign-up logs straight in. The first 3 migrations were applied by pasting `hosted-setup.sql`.
  The user pasted their **secret** key in chat once; they were told to delete/rotate it. Never use or store it.
- **Local Docker backend:** `npx supabase start` (from repo root). API `http://127.0.0.1:54321`,
  Studio `:54323`, Mailpit (catches all emails/codes) `:54324`. Local publishable key:
  `npx supabase status -o json`. For the emulator or a USB/Wi-Fi-debugging phone use
  `SUPABASE_URL=http://127.0.0.1:54321` plus `adb reverse tcp:54321 tcp:54321` (re-run after adb restarts).
  Apply new migrations locally with `npx supabase migration up`.
- Local test accounts exist (`qa.redesign@example.test` with username `qa_tester`, `demo@reelcounter.test`,
  `friend.one@example.test` = `maya.reels`, `stranger.two@example.test` = `leo_watch`). The demo login is in
  `demo-login.local.txt`. Local data: qa_tester and maya.reels are friends; maya has a week of
  estimated activity rows. Don't print passwords in chat.

## 5. Build, test, install

```bash
./gradlew assembleDebug testDebugUnitTest :core:model:test :app:lintDebug
```
Last full run: build OK, 105/105 unit tests pass, lint 0 issues (before the friends work below).
APK: `app/build/outputs/apk/debug/app-debug.apk`; the copy given to the user is `ReelCounter.apk`
(built against the hosted project).
**Build the user's APK with `./gradlew assembleDebug -PreelCounting=false`.** That merges
`app/src/withoutCounting/AndroidManifest.xml`, which removes the reel-counting accessibility service:
the user's phone (Play Protect) refused to install the APK that declares it. `ReelCountingStatus.isAvailable`
is then false and the app skips the CountReels setup step, the disclosure bullet and the Reel counting card,
falling back to estimates. Verify with `aapt2 dump xmltree <apk> --file AndroidManifest.xml | grep -ci accessibility` → 0. Install: `adb -s emulator-5554 install -r <apk>`.
Screenshots: `adb exec-out screencap -p > file.png`. Tap by text: `uiautomator dump` then parse bounds.

## 6. UI redesign (done)

Design system in `core/designsystem` (all screens use it; no hard-coded colors or sizes):
- Colors (`theme/Color.kt`): neutral background `#F6F6F8`, cards `surfaceContainerLowest` (white;
  raised in dark), hairline `outlineVariant` borders, one accent `#5B4CDB` (dark `#A99FFF`).
  Dynamic color is OFF by default. Metric category colors in `theme/MetricColors.kt`.
- Type (`theme/Type.kt`): **all letterSpacing in `em`.** Mixing `sp` and `em` crashes text-field
  label animation ("Cannot perform operation for Sp and Em").
- `Spacing` (4 dp grid, `screen` = 20 dp), `Shape` (buttons 12, cards 18, dialogs 24).
- Components: `RcButton/RcOutlinedButton/RcTonalButton/RcTextButton` (sizes Regular/Small),
  `RcCard`, `RcListCard`, `RcListRow`, `RcSwitchRow`, `RcRadioRow`, `RcDivider`, `RcSectionHeader`,
  `RcIconTile`, `RcBanner`, `RcStatusPill` (`RcTone`), `RcBarChart`/`RcBar`, `RcProgressBar`,
  `RcSegmentedControl`, `RcScreenHeader` (large title for top-level tabs), `RcTopAppBar` (nested screens),
  state views, skeletons. Icons only via `icon/RcIcons.kt`.
- `core/ui`: `DaySummary` + `summarize()` (null = no data), `PlatformActivityRow`/`Card`,
  `PlatformMonogram`, `formatAppTime`, `formatCompactAppTime`, `formatCount`.
- Today: greeting + avatar (opens Settings tab), hero (≈ reels, range hidden when low == high,
  app time, yesterday's total), 7-day chart, by-app list. `TodayViewModel` takes `ProfileRepository`.
- Insights: real data from the last 14 days via `TrackingRepository.observeDay` (`InsightsViewModel`, tests added).
- Backup of all sources before the redesign:
  `C:\Users\USER\AppData\Local\Temp\claude\...\scratchpad\backup-before-redesign.tar` (session temp; may be gone).

## 7. Friends feature (DONE, 2026-10-29)

- Migration `supabase/migrations/20261029120000_friend_activity_and_suggestions.sql` (applied locally; the
  user was asked to paste it into the hosted SQL Editor once): statement-level triggers send event
  `activity` `{"user_id"}` on each friend's `friendships:<id>` channel; `suggest_friends()`;
  `friends_sharing_presence()`.
- App: `FriendActivityRemoteDataSource` (reads view `daily_activity_totals`), `FriendActivityRepository`
  (`DefaultFriendActivityRepository`, fake in core:testing), `RealtimeRemoteDataSource.friendsChannel`
  (one channel, two events), `RealtimeCoordinator` passes activity events on.
- **Presence bug fixed:** joining a friend's presence channel without permission made supabase-kt retry
  forever and drop the whole websocket every ~37 s (killing live updates). The coordinator now joins only
  ids from `friends_sharing_presence()`.
- UI: Friends tab = search + segmented Friends | Requests | Suggestions; friend rows show today's ≈ reels;
  tap → `FriendDetailScreen` (hero, 7-day chart, by app, live). Friends is a nested graph `FriendsGraph`.
- YouTube/Facebook now estimate by default (decision D1 revised; `PlatformCatalog.estimateByDefault = true`).
- Testing note: `uiautomator dump` on the emulator can send a stray Back and jump to the Today tab; don't
  mistake that for an app bug. Reinstalling over a running app restores old nav state (can look broken).

## 8. Real per-video reel counting (BUILT 2026-10-29; not yet verified on real apps)

- `ReelAccessibilityService` (core:tracking/counting) + config `res/xml/core_tracking_reel_counter_accessibility.xml`
  (only the 8 platform packages; reads view ids, bounds, list positions, scroll direction; never text).
  Feeds: YouTube `reel_recycler` (+1 s polling), Instagram `clips_viewer_view_pager`, TikTok `viewpager`,
  Facebook = generic full-screen vertical pager. Logic in pure `ReelSwipeDetector` (core:domain, tested).
- `ReelCountingRecorder` (domain) → `DefaultReelCountingRecorder` (core:data, also an AppInitializer):
  stores counts in Room `reel_counts` (DB v3), keeps `countingSinceMs`/`countingStoppedAtMs` in
  TrackingPreferences. A rebind gap with no tracked-app use keeps the run. `ReelCountingStatus` reads
  Settings.Secure enabled services.
- `UsageIngestor`: platform-day is COUNTED only if counting ran since before that day's first use
  (`activeCountingSinceMs`), else the estimate. New `MetricQuality.COUNTED` / `PlatformMetric.Counted`
  (plain number, no badge). Server migration `20261029130000_counted_reels.sql` (enum value + view column
  `counted_reels`) applied locally, NOT on hosted yet.
- UI: setup step `CountReels` (restricted-settings help for Android 13+ sideloads), Tracking & apps
  "Reel counting" card, disclosure v3 (bullet about counting), platform logos via `PlatformLogo`: bundled vector tiles `core/ui/res/drawable/core_ui_logo_*.xml` (Simple Icons glyphs, CC0), always shown, even if the app isn't installed.
- Emulator limits: its YouTube (19.17) is outdated and can't load Shorts; other apps not installed.
  `uiautomator dump` unbinds accessibility services: never use it while testing counting.

## 9. Other files made for the user

- `MANUAL_TEST_CHECKLIST.md`: 67 manual QA cases (written before the redesign; screens changed since).
- `hosted-setup.sql`: the first 3 migrations combined for the hosted SQL Editor.
- `demo-login.local.txt`: local-only demo login.
