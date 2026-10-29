# ReelCounter: Manual Testing Checklist

Version under test: debug build `com.reelcounter.app.debug` · Tester: ________ · Device(s): ________ · Android version: ________ · Date: ________

---

## 0. Read this first: what the code actually does

This checklist was written from a code review. Nothing has been run, and none of the predictions below should be taken as results. Before you start, note these facts, because several of your requirements depend on them:

| Area | What the code does | Where |
|---|---|---|
| Tracking permission | **Usage Access** (`PACKAGE_USAGE_STATS`) only. There is **no AccessibilityService** and no `SYSTEM_ALERT_WINDOW` / overlay code anywhere in the project. | `core/tracking/src/main/AndroidManifest.xml` |
| How reels are counted | **Estimated from app foreground time**: `reels = seconds in app × short-form share ÷ seconds per reel`. It does not detect individual videos, scrolls or UI nodes. | `core/domain/.../tracking/Estimator.kt:69` |
| Default estimation values | TikTok 95 % / 21 s · Instagram 55 % / 18 s · YouTube 30 % / 30 s · Facebook 20 % / 20 s. The code labels these "unvalidated placeholders". | `core/domain/.../tracking/PlatformCatalog.kt` |
| Platforms | TikTok (`com.zhiliaoapp.musically`, `com.ss.android.ugc.trill`, `…musically.go`), Instagram (+ Lite), YouTube, Facebook (+ Lite) | `PlatformCatalog.kt` |
| When data updates | When the Today screen resumes, on pull-to-refresh, and through an hourly WorkManager job (with a 15-minute flex window) | `TodayViewModel.kt:76`, `TrackingWork.kt:19` |
| Day boundary | Local midnight, split per zone, with DST handled. Days older than 2 days are frozen ("finalized"). | `DayBucketer.kt`, `UsageIngestor.kt:193` |
| Insights (weekly/monthly) | **Static placeholder screen.** It always shows "Not enough data yet" and has no ViewModel. A code comment says this arrives in Phase 7. | `feature/insights/.../InsightsScreen.kt` |
| History view | The Today screen shows **today only**. There is no screen for past days. | `TodayViewModel.kt` |
| Backend | Supabase. Without it the app shows only a "not configured" screen. Sign-in is required before tracking screens appear. | `ReelCounterRoot.kt:39` |

The cases where the code review predicts a Fail or Blocked result are marked **⚠ Code predicts**. Run them anyway and record what really happens. A prediction is not a result.

**Prerequisites**
- There is **no APK in `app/build/outputs/apk/debug/` yet.** You'll need to build one yourself, for example with `./gradlew assembleDebug`, or use one you already have.
- The local Supabase must be running (`npx supabase start`). `secrets.properties` points the debug build at it through `http://10.0.2.2:54321`, which works **only on the emulator**. For a physical device, change `SUPABASE_URL` to your PC's LAN IP (e.g. `http://192.168.x.x:54321`). Be aware that the build only allows plain HTTP for `localhost`, `127.0.0.1` and `10.0.2.2`, and the debug network config only allows cleartext for those hosts too. So a physical device needs a hosted (HTTPS) Supabase project or `adb reverse tcp:54321 tcp:54321` together with `http://127.0.0.1:54321`.
- Sign-up verification codes arrive in Mailpit: http://127.0.0.1:54324
- You can inspect backend rows in Supabase Studio: http://127.0.0.1:54323 → table `public.daily_activity_summaries`
- Ground truth for app time: `adb shell dumpsys usagestats` (shows what Android itself recorded)

**Environment legend**
- 💻 **Computer**: build/APK inspection, ADB commands, logs, Supabase Studio. An emulator is fine.
- 💻📱 **Either**: works on the emulator, but confirm on a physical device before release.
- 📱 **Physical device required**: real short-video apps, real scrolling, battery, reboot behaviour, OEM background limits.

Useful variables for the commands below (PowerShell):
```powershell
$PKG = "com.reelcounter.app.debug"
$ACT = "$PKG/com.reelcounter.app.MainActivity"
```

---

## 1. Installation & Startup (INST)

### INST-01 · Clean install
- **Feature:** First-time APK installation
- **Environment:** 💻📱 Either
- **Steps:**
  1. `adb uninstall $PKG` (ignore "not installed").
  2. `adb install -r app-debug.apk`
  3. Check the app drawer for the ReelCounter icon and label.
- **Expected:** `Success` is printed. The icon and label appear. No errors in `adb logcat -b crash`.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### INST-02 · Permissions declared by the APK
- **Feature:** Declared permission surface
- **Environment:** 💻 Computer
- **Steps:**
  1. `adb shell dumpsys package $PKG | Select-String "permission"`
  2. Compare against the list in Expected.
- **Expected:** Only `INTERNET`, `ACCESS_NETWORK_STATE`, `PACKAGE_USAGE_STATS`, plus permissions added by libraries (e.g. WorkManager's `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`). **No** `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW` or `BIND_ACCESSIBILITY_SERVICE`. Record every extra permission you find.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### INST-03 · Cold start time
- **Feature:** Launch performance
- **Environment:** 💻📱 Either (record the device numbers as the final figures)
- **Steps:**
  1. `adb shell am force-stop $PKG`
  2. `adb shell am start -W -n $ACT`
  3. Repeat 5 times and record each `TotalTime`.
- **Expected:** The median `TotalTime` is below 1500 ms on a mid-range device (record your own target). The splash screen shows no blank white frame and no ANR.
- **Actual result:** Run 1 ___ · 2 ___ · 3 ___ · 4 ___ · 5 ___ ms
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### INST-04 · Warm start time
- **Feature:** Resume performance
- **Environment:** 💻📱 Either
- **Steps:**
  1. Open the app, then press Home.
  2. `adb shell am start -W -n $ACT`
  3. Repeat 5 times.
- **Expected:** `TotalTime` is below 500 ms. The app returns to the same screen and tab.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### INST-05 · Backend not reachable on first launch
- **Feature:** Startup with no backend
- **Environment:** 💻 Computer
- **Steps:**
  1. Run `npx supabase stop`.
  2. Clear app data with `adb shell pm clear $PKG`, then launch.
- **Expected:** The app shows a clear error or a retry option. It must not crash or show an endless spinner. After you restart Supabase and tap retry (or reopen the app), it recovers.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### INST-06 · Sign-up and sign-in gate before tracking
- **Feature:** Account required to reach the dashboard
- **Environment:** 💻📱 Either
- **Steps:**
  1. Fresh install, then sign up with a new email and a password of at least 8 characters.
  2. Get the 6-digit code from Mailpit and enter it.
  3. Kill the app and reopen it.
- **Expected:** After verification you land on Today. Reopening keeps you signed in, with no second sign-in prompt.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### INST-07 · Upgrade install keeps data
- **Feature:** In-place update
- **Environment:** 💻📱 Either
- **Steps:**
  1. With tracked data visible on Today, note the numbers.
  2. `adb install -r app-debug.apk` (same or newer build).
  3. Open the app.
- **Expected:** You're still signed in, and today's numbers are the same or higher (never lower). The tracking setup isn't asked for again.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### INST-08 · Uninstall and reinstall
- **Feature:** Data after reinstall
- **Environment:** 💻📱 Either
- **Steps:**
  1. Note today's totals.
  2. `adb uninstall $PKG`, reinstall, sign in to the same account.
  3. Set up tracking again.
- **Expected:** Record exactly what comes back. The disclosure says daily totals are "saved to your ReelCounter account", and Android's own usage log covers roughly the last few days, so today's totals should be rebuilt. Check whether the numbers match what you noted.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

---

## 2. Permissions & Negative Testing (PERM)

### PERM-01 · Prominent disclosure shown before the permission
- **Feature:** Usage-access disclosure (Play policy)
- **Environment:** 💻📱 Either
- **Steps:**
  1. Fresh account, then tap **Set up tracking** on Today.
  2. Read the "How tracking works" screen.
- **Expected:** The disclosure appears **before** any system settings page opens. It states what is collected, what isn't, that counts are estimates, and how to pause. "Agree and continue" is required to proceed, and the system permission is never requested at install time.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERM-02 · Decline the disclosure
- **Feature:** Negative path, user refuses
- **Environment:** 💻📱 Either
- **Steps:**
  1. On the disclosure, press **Not now** or system Back.
  2. Go back to Today.
- **Expected:** No crash. Today shows a "not set up" state with a way to start setup again. Usage access isn't granted: `adb shell appops get $PKG GET_USAGE_STATS` reports `default` or `ignore`.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERM-03 · Grant usage access via system settings
- **Feature:** Grant flow and auto-continue
- **Environment:** 💻📱 Either
- **Steps:**
  1. Agree, choose apps, then tap **Open settings**.
  2. Find ReelCounter, turn on **Permit usage access**, and press Back.
- **Expected:** The app moves on to the next step automatically ("We'll continue automatically when you return"). `appops get` reports `allow`.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERM-04 · Return from settings without granting
- **Feature:** Negative path, user backs out
- **Environment:** 💻📱 Either
- **Steps:**
  1. Tap **Open settings**, don't toggle anything, then press Back.
- **Expected:** The app stays on the access step with clear instructions. It doesn't advance, crash, or claim tracking is on.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERM-05 · Revoke usage access while the app is in the background
- **Feature:** Revocation handling
- **Environment:** 💻📱 Either
- **Steps:**
  1. With tracking working, press Home.
  2. `adb shell appops set $PKG GET_USAGE_STATS ignore` (or toggle it off in Settings).
  3. Reopen the app, then go to Settings → Tracking & apps.
- **Expected:** No crash. A clear notice says "Usage access is off, so nothing new is being recorded" (or similar). Numbers already recorded stay visible. Missing time is shown as unavailable, **not as 0 reels**.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERM-06 · Revoke usage access while the app is open
- **Feature:** Live revocation
- **Environment:** 💻 Computer
- **Steps:**
  1. Keep Today open in the foreground.
  2. Run `adb shell appops set $PKG GET_USAGE_STATS ignore`.
  3. Pull to refresh.
- **Expected:** No crash. The UI switches to the no-access state, either immediately or on refresh.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERM-07 · Re-grant after revocation
- **Feature:** Recovery
- **Environment:** 💻📱 Either
- **Steps:**
  1. After PERM-05, run `adb shell appops set $PKG GET_USAGE_STATS allow`.
  2. Reopen the app and pull to refresh.
- **Expected:** Tracking resumes without reinstalling. Usage that happened while access was off, and that Android still holds, gets back-filled. Compare against `dumpsys usagestats`.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERM-08 · Accessibility service
- **Feature:** Accessibility-based tracking
- **Environment:** 📱 Physical device
- **Steps:**
  1. Open Settings → Accessibility → Installed apps / Downloaded services.
  2. Look for ReelCounter.
  3. Run `adb shell settings get secure enabled_accessibility_services`
- **Expected (your requirement):** If your product spec needs accessibility-based detection, ReelCounter appears there, can be enabled and disabled, and the app handles both states without crashing.
- **⚠ Code predicts:** ReelCounter **won't be listed**, because the project contains no AccessibilityService. Mark as **Fail** if your spec requires it, or **Blocked / N/A** if it doesn't.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERM-09 · Display over other apps (overlay) permission
- **Feature:** Floating overlay permission
- **Environment:** 📱 Physical device
- **Steps:**
  1. Open Settings → Apps → ReelCounter → **Display over other apps** (on some OEMs: Special app access → Display over other apps).
  2. Run `adb shell appops get $PKG SYSTEM_ALERT_WINDOW`
- **Expected (your requirement):** If an overlay is in scope, the permission can be granted. Denying it must not crash the app, and the app explains what's lost.
- **⚠ Code predicts:** The app doesn't declare this permission and has no overlay, so the toggle is missing or has no effect. Mark accordingly.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERM-10 · Permission state after "clear data"
- **Feature:** State reset
- **Environment:** 💻 Computer
- **Steps:**
  1. `adb shell pm clear $PKG`
  2. Launch the app and sign in.
- **Expected:** The app asks for setup again, including the disclosure. It doesn't wrongly report "Tracking is on" from stale state. Note: Android keeps the usage-access app-op through a clear, so the access step may be skipped. Record what happens.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

---

## 3. Core Reel Counting Logic (COUNT)

> For every COUNT test: before starting, pull-to-refresh Today and note each platform's **app time** and **reel count**. After the test, return to ReelCounter, pull-to-refresh, and record the change. Cross-check app time with `adb shell dumpsys usagestats | Select-String "<package>"`.

### COUNT-01 · Video switches vs. elapsed time: TikTok ⭐ key test
- **Feature:** Counts real video switches, not time
- **Environment:** 📱 Physical device
- **Steps:**
  1. **Run A (pause):** open TikTok For You, stay on **one** video for exactly 60 s (let it loop), then leave TikTok.
  2. Refresh ReelCounter and note the change in reels (ΔA).
  3. **Run B (flick):** open TikTok and swipe through **20 different videos** in 60 s, then leave.
  4. Refresh ReelCounter and note the change (ΔB).
- **Expected (your requirement):** ΔA ≈ **1** and ΔB ≈ **20**. The count follows video switches, so ΔB is clearly larger than ΔA.
- **⚠ Code predicts:** ΔA ≈ ΔB ≈ **3** (60 s × 0.95 ÷ 21 s), shown as an estimate with a range of about 1–5. Equal deltas prove the count is **time-based**, which fails this requirement.
- **Actual result:** ΔA = ___ · ΔB = ___ · app time A = ___ · app time B = ___
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### COUNT-02 · Video switches vs. elapsed time: Instagram Reels
- **Feature:** Per-video counting on Instagram
- **Environment:** 📱 Physical device
- **Steps:** Same as COUNT-01 in the Instagram **Reels** tab (Run A: 1 reel for 60 s; Run B: 20 reels in 60 s).
- **Expected (your requirement):** ΔA ≈ 1, ΔB ≈ 20.
- **⚠ Code predicts:** both ≈ **2** (60 × 0.55 ÷ 18).
- **Actual result:** ΔA = ___ · ΔB = ___
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### COUNT-03 · Video switches vs. elapsed time: YouTube Shorts
- **Feature:** Per-video counting on YouTube Shorts
- **Environment:** 📱 Physical device
- **Steps:** Same as COUNT-01 in the YouTube **Shorts** tab.
- **Expected (your requirement):** ΔA ≈ 1, ΔB ≈ 20.
- **⚠ Code predicts:** both ≈ **1** (60 × 0.30 ÷ 30).
- **Actual result:** ΔA = ___ · ΔB = ___
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### COUNT-04 · Facebook Reels
- **Feature:** Per-video counting on Facebook
- **Environment:** 📱 Physical device
- **Steps:** Same as COUNT-01 in Facebook → Reels/Video.
- **Expected (your requirement):** ΔA ≈ 1, ΔB ≈ 20.
- **⚠ Code predicts:** both ≈ **1** (60 × 0.20 ÷ 20).
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### COUNT-05 · Non-reel usage inside a supported app
- **Feature:** Only short-form video is counted
- **Environment:** 📱 Physical device
- **Steps:**
  1. Open Instagram and spend 2 minutes **only in Direct Messages**. Don't open Reels.
  2. Then spend 2 minutes on YouTube watching a **long-form** video (not Shorts).
  3. Refresh ReelCounter.
- **Expected (your requirement):** Reels are unchanged (0 added) for both apps.
- **⚠ Code predicts:** Instagram goes up ≈ 4 and YouTube ≈ 1, because the whole app's foreground time is multiplied by the short-form share. The disclosure says Android "can't see which screen inside an app is open".
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### COUNT-06 · App time accuracy against Android's ground truth
- **Feature:** Foreground time measurement
- **Environment:** 💻📱 Either (📱 for a realistic result)
- **Steps:**
  1. Use TikTok for a timed 5 minutes (use a stopwatch).
  2. Refresh ReelCounter and note the TikTok app time.
  3. Compare with the stopwatch and with `dumpsys usagestats`.
- **Expected:** ReelCounter's app time is within ±5 s of the stopwatch and matches `usagestats`. Record your tolerance.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### COUNT-07 · Very short sessions
- **Feature:** Minimum session threshold (code: 3 s)
- **Environment:** 📱 Physical device
- **Steps:**
  1. Open TikTok for about 2 s and leave. Repeat 5 times.
  2. Refresh ReelCounter.
  3. Then open it once for about 10 s.
- **Expected:** Record whether the 2 s opens add any time or reels. Accidental glances should not inflate counts, and the 10 s session should be counted.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### COUNT-08 · Screen off while a reel plays
- **Feature:** Locked screen isn't counted
- **Environment:** 📱 Physical device
- **Steps:**
  1. Open TikTok and lock the phone with the power button for 2 minutes.
  2. Unlock, leave TikTok, and refresh ReelCounter.
- **Expected:** The 2 minutes with the screen off are not added to app time or reels.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### COUNT-09 · Split screen and picture-in-picture
- **Feature:** Multi-window edge cases
- **Environment:** 📱 Physical device
- **Steps:**
  1. Put YouTube in split screen with another app for 2 minutes.
  2. Put a YouTube video in PiP for 2 minutes while using another app.
  3. Refresh ReelCounter.
- **Expected:** Record what's counted. Whatever the result, it should be consistent and documented in "How we count".
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### COUNT-10 · Untracked app is ignored
- **Feature:** Per-app toggle
- **Environment:** 📱 Physical device
- **Steps:**
  1. In Settings → Tracking & apps, turn **Instagram** off.
  2. Use Instagram for 2 minutes.
  3. Refresh ReelCounter.
- **Expected:** No Instagram time or reels are added for that period. Other apps keep tracking.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### COUNT-11 · Pause tracking
- **Feature:** Global pause
- **Environment:** 📱 Physical device
- **Steps:**
  1. Settings → Tracking & apps → **Pause tracking**.
  2. Use TikTok for 2 minutes.
  3. Resume tracking and refresh.
- **Expected:** Time while paused is **never** recorded ("Time spent while paused is never recorded"). Usage after resuming is counted.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### COUNT-12 · Estimation turned off
- **Feature:** "App time only" mode
- **Environment:** 💻📱 Either
- **Steps:**
  1. In Tracking & apps, turn **Estimate reel counts** off for TikTok.
  2. Look at Today.
- **Expected:** TikTok shows app time only with **no reel number**, labelled "App time only".
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### COUNT-13 · Changing the estimation sliders
- **Feature:** User overrides recompute counts
- **Environment:** 💻📱 Either
- **Steps:**
  1. Note TikTok's app time T and count today.
  2. Set "Average seconds per video" to 10 s and "Time spent on short videos" to 100 %.
  3. Return to Today.
  4. Tap **Reset to defaults**.
- **Expected:** The count becomes ≈ T(s) × 1.0 ÷ 10, and the figure is marked as user-adjusted. Reset restores the default figure. Per the in-app note, the change applies to today and the last two days only.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### COUNT-14 · Platform not installed
- **Feature:** Missing apps
- **Environment:** 💻📱 Either
- **Steps:**
  1. On a device without Facebook installed, open tracking setup and Today.
- **Expected:** Facebook is shown as "Not installed on this device" and is never shown as 0 reels.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### COUNT-15 · TikTok variants and Lite apps
- **Feature:** Alternative package names
- **Environment:** 📱 Physical device
- **Steps:**
  1. If available in your region, install TikTok Lite or Instagram Lite / Facebook Lite.
  2. Use it for 2 minutes and refresh.
- **Expected:** Usage is attributed to the right platform (TikTok, Instagram or Facebook).
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### COUNT-16 · Transparency labels
- **Feature:** Verified / Estimated / App time only / Not tracked labels
- **Environment:** 💻📱 Either
- **Steps:**
  1. Create one platform in each state: estimated, estimation off, untracked, not installed, and no access.
  2. Read each label on Today and open **How we count**.
- **Expected:** Every number carries exactly one label. No estimate is shown as an exact count, and a range is shown for estimates.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

---

## 4. Dashboard & Metrics (DASH)

### DASH-01 · Today totals add up
- **Feature:** Today dashboard
- **Environment:** 💻📱 Either
- **Steps:**
  1. Use two platforms for known amounts of time.
  2. Read each platform's row and any total on Today.
- **Expected:** Each platform row matches COUNT-06-level accuracy, and the total equals the sum of the rows. "Last updated" shows a recent time.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### DASH-02 · Refresh triggers
- **Feature:** Data freshness
- **Environment:** 📱 Physical device
- **Steps:**
  1. Use TikTok for 2 minutes, then switch back to ReelCounter (screen resume).
  2. Use it again and pull to refresh.
  3. Use it again, don't open ReelCounter for more than 75 minutes, then check `daily_activity_summaries` in Supabase Studio.
- **Expected:** Steps 1 and 2 update immediately. In step 3 the hourly background job updates the data without opening the app.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### DASH-03 · Daily reset at real midnight
- **Feature:** Midnight rollover
- **Environment:** 📱 Physical device
- **Steps:**
  1. At about 23:55, open TikTok and keep using it until about 00:05.
  2. Open ReelCounter on Today and leave it open across midnight if you can.
- **Expected:** At 00:00 Today switches to the new date without a restart. About 5 minutes count toward yesterday and about 5 toward today (the session is split at midnight). Today starts from about 0 plus the post-midnight part.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### DASH-04 · Manual date and time change
- **Feature:** `TIME_SET` handling
- **Environment:** 💻📱 Either
- **Steps:**
  1. Turn off automatic date & time.
  2. Set the clock to tomorrow 10:00 and open ReelCounter.
  3. Set it back to automatic and reopen the app.
- **Expected:** No crash and no duplicated or negative values. The day shown follows the clock. Afterwards, the data for the real date is intact.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### DASH-05 · Time zone change
- **Feature:** Zone handling
- **Environment:** 💻📱 Either
- **Steps:**
  1. Use TikTok for 3 minutes.
  2. Switch the device time zone 8+ hours away (manual zone).
  3. Use it for 3 more minutes, then refresh.
- **Expected:** Sessions are dated in the zone they happened in. No time is lost or double counted. Record the per-day split.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### DASH-06 · Historical stats (previous days)
- **Feature:** View past days
- **Environment:** 💻📱 Either
- **Steps:**
  1. After 2+ days of tracking, look for a way to see yesterday or earlier.
- **Expected (your requirement):** Past days' counts can be browsed and match what Today showed on those days.
- **⚠ Code predicts:** No UI exists for past days, since Today shows today only. History exists only in the local DB and in `daily_activity_summaries` (Supabase Studio). Mark **Fail/Blocked** per your spec.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### DASH-07 · Weekly summary
- **Feature:** Weekly trends (Insights tab)
- **Environment:** 💻📱 Either
- **Steps:**
  1. After 7+ days of tracking (or 1+ full day at minimum), open **Insights**.
- **Expected (your requirement):** Weekly totals, a per-day breakdown, and a comparison with the previous week.
- **⚠ Code predicts:** The screen always shows "Not enough data yet" no matter how much data exists. It is a static placeholder, so its message about appearing "after your first full day" is misleading.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### DASH-08 · Finalized days don't change
- **Feature:** Days older than 2 days are frozen
- **Environment:** 💻 Computer (Supabase Studio)
- **Steps:**
  1. After 3+ days of tracking, note a row for D−3 in `daily_activity_summaries`.
  2. Change the TikTok estimation sliders.
  3. Refresh and check the D−3 row again.
- **Expected:** The D−3 row is unchanged. Rows for today, D−1 and D−2 are recalculated.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### DASH-09 · Empty state on a brand-new account
- **Feature:** Zero-data dashboard
- **Environment:** 💻📱 Either
- **Steps:**
  1. New account with tracking set up but no supported-app use today.
- **Expected:** The dashboard shows a friendly empty or zero state with no errors. The difference between "0 used" and "not tracked" is clear.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

---

## 5. Data Persistence & State Recovery (PERSIST)

### PERSIST-01 · Force stop
- **Feature:** Data survives force stop
- **Environment:** 💻📱 Either
- **Steps:**
  1. Note Today's numbers.
  2. `adb shell am force-stop $PKG` (or Settings → Force stop).
  3. Reopen the app.
- **Expected:** The numbers are the same or higher, you're still signed in, and tracking is still set up. Note: Android cancels scheduled jobs on force stop until the app is opened again. Verify the hourly job comes back afterwards with `adb shell dumpsys jobscheduler | Select-String $PKG`.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERSIST-02 · Swipe away from Recents
- **Feature:** Data survives task removal
- **Environment:** 📱 Physical device (OEM behaviour varies)
- **Steps:**
  1. Swipe ReelCounter away from Recents.
  2. Use TikTok for 3 minutes **without** opening ReelCounter.
  3. Wait more than 75 minutes, then check Supabase Studio before opening the app.
- **Expected:** Background ingest still ran (the row was updated). Opening the app shows the 3 minutes. On aggressive OEMs (Xiaomi, Oppo, Samsung "sleeping apps") record the outcome.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERSIST-03 · Full phone reboot
- **Feature:** Recovery after reboot
- **Environment:** 📱 Physical device
- **Steps:**
  1. Note Today's numbers.
  2. Reboot the phone and **don't** open ReelCounter.
  3. Use TikTok for 3 minutes.
  4. After more than 75 minutes, check `dumpsys jobscheduler` and Supabase Studio, then open the app.
- **Expected:** No data loss. The periodic job is rescheduled after boot without opening the app, and pre- and post-reboot usage are both counted.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERSIST-04 · Process death while on a sub-screen
- **Feature:** Saved UI state
- **Environment:** 💻 Computer
- **Steps:**
  1. Open Settings → Tracking & apps and move a slider without leaving the screen.
  2. Press Home, then run `adb shell am kill $PKG`.
  3. Reopen the app from Recents.
- **Expected:** The app restores to the same screen, or at least doesn't crash. The slider value either persists or is cleanly reverted, never corrupted.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERSIST-05 · Sign out and back in
- **Feature:** Account data restore
- **Environment:** 💻📱 Either
- **Steps:**
  1. Note today's totals, then sign out from Settings.
  2. Sign back in to the same account.
- **Expected:** Totals come back, no other account's data appears, and tracking settings are kept or re-asked consistently.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERSIST-06 · Local DB inspection
- **Feature:** Local storage integrity
- **Environment:** 💻 Computer
- **Steps:**
  1. `adb shell run-as $PKG ls databases` (look for `reelcounter.db`).
  2. Optionally pull it and open it in a SQLite viewer: tables `daily_usage`, `day_coverage`, `ingest_state`, `platform_settings`, `zone_changes`.
- **Expected:** One row per (platform, date) with no duplicates. Values match Today.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

---

## 6. Connectivity & Edge Cases (EDGE)

### EDGE-01 · Tracking while offline
- **Feature:** Offline counting
- **Environment:** 📱 Physical device (💻 emulator works too)
- **Steps:**
  1. Turn on airplane mode (Wi-Fi and data off).
  2. Use TikTok for 3 minutes. (TikTok may not load videos offline. Use cached content or run this with only ReelCounter's backend blocked by stopping Supabase.)
  3. Open ReelCounter and pull to refresh.
- **Expected:** Today updates from local data while offline. There's no crash and no blocking error, and at most a gentle "offline" notice.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### EDGE-02 · Sync after reconnect
- **Feature:** Deferred upload
- **Environment:** 💻📱 Either
- **Steps:**
  1. After EDGE-01, check `daily_activity_summaries` in Studio, where the offline minutes shouldn't be there yet.
  2. Turn the network back on and wait a few minutes or reopen the app.
  3. Check Studio again.
- **Expected:** The row updates automatically once connected (the sync job requires a network and retries with backoff). No duplicate rows appear.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### EDGE-03 · Backend down while online
- **Feature:** Server unavailable
- **Environment:** 💻 Computer
- **Steps:**
  1. `npx supabase stop` while the app is open.
  2. Pull to refresh on Today, then open Friends and Settings.
  3. `npx supabase start`
- **Expected:** Today still shows local data. Network screens show a recoverable error. Once the backend is back, everything recovers without reinstalling.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### EDGE-04 · Flaky network
- **Feature:** Intermittent connectivity
- **Environment:** 📱 Physical device
- **Steps:**
  1. Toggle airplane mode on and off every 10 s for 2 minutes while using the app and refreshing.
- **Expected:** No crash, no duplicated totals, and no stuck spinner.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### EDGE-05 · Invalid sign-up and sign-in inputs
- **Feature:** Input validation
- **Environment:** 💻📱 Either
- **Steps:** Try each of these and record the message shown:
  1. Empty email
  2. `not-an-email`
  3. Password of 7 characters
  4. Password over 72 bytes (e.g. 40 emoji)
  5. Wrong password for an existing account
  6. Wrong 6-digit code, then an expired code
  7. Paste an email with leading or trailing spaces
- **Expected:** A clear inline error for each, without crashing and without hitting the server for cases the client can validate (1–4).
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### EDGE-06 · Invalid profile inputs
- **Feature:** Username validation (code: 3–20 characters)
- **Environment:** 💻📱 Either
- **Steps:** In Edit profile, try a username of 2 characters, 21 characters, spaces, emoji, `../`, `<script>`, a reserved name such as `admin`, and one already taken by another account.
- **Expected:** Each is rejected with a specific message. Valid names save, and the saved value appears after reopening the app.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### EDGE-07 · Estimation slider extremes
- **Feature:** Input bounds
- **Environment:** 💻📱 Either
- **Steps:**
  1. Drag each slider to its minimum and maximum.
  2. Rapidly drag back and forth for 10 s.
- **Expected:** No crash and no division by zero. Counts at the extremes are finite and sensible (code clamps share to 1–100 % and seconds to at least 1).
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### EDGE-08 · Overlay draggability and button blocking
- **Feature:** Floating counter overlay
- **Environment:** 📱 Physical device
- **Steps:**
  1. With the overlay permission granted (PERM-09), open TikTok.
  2. Drag the overlay to each corner.
  3. Check that you can still tap TikTok's like, comment and share buttons and the navigation bar.
- **Expected (your requirement):** The overlay can be dragged smoothly, stays where it's dropped, never covers a tap target permanently, and survives rotation.
- **⚠ Code predicts:** **Blocked**. The app has no overlay.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### EDGE-09 · Rotation, dark mode and font scale
- **Feature:** UI robustness
- **Environment:** 💻📱 Either
- **Steps:**
  1. Rotate on every main screen.
  2. Switch the system dark/light theme while the app is open.
  3. Set font size to the largest and display size to the largest.
- **Expected:** No crash and no lost state. Text isn't clipped or overlapping, and numbers stay readable.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### EDGE-10 · Doze and App Standby
- **Feature:** Background work under power saving
- **Environment:** 💻 Computer (then confirm 📱)
- **Steps:**
  1. `adb shell dumpsys deviceidle force-idle`
  2. `adb shell am set-standby-bucket $PKG rare`
  3. Use TikTok, wait, then `adb shell dumpsys deviceidle unforce` and open the app.
- **Expected:** No data loss. When the app opens, usage from the idle period is back-filled from Android's log.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### EDGE-11 · Multiple user profiles or work profile
- **Feature:** Device profile isolation
- **Environment:** 📱 Physical device
- **Steps:**
  1. Install TikTok in a work profile or secondary user and use it there.
  2. Check ReelCounter in the main profile.
- **Expected:** Record the behaviour. Usage from another profile isn't visible to the main-profile app, and should be labelled as not tracked rather than silently missing.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

---

## 7. Performance & Stability (PERF)

### PERF-01 · Memory footprint
- **Feature:** RAM usage
- **Environment:** 💻📱 Either
- **Steps:**
  1. Open the app, visit every tab, and return to Today.
  2. `adb shell dumpsys meminfo $PKG` and record `TOTAL PSS`.
  3. Repeat after 15 minutes of navigating.
- **Expected:** PSS stays roughly flat (no steady growth). Record your target (e.g. < 150 MB).
- **Actual result:** Start ___ MB · after 15 min ___ MB
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERF-02 · Battery drain over a day
- **Feature:** Background battery use
- **Environment:** 📱 Physical device
- **Steps:**
  1. Charge fully, then run `adb shell dumpsys batterystats --reset`.
  2. Use the phone normally for 24 h, including the tracked apps.
  3. `adb shell dumpsys batterystats $PKG` and check Settings → Battery → ReelCounter.
- **Expected:** ReelCounter uses under 1–2 % of battery per day. Wakeups stay around 24 per day (hourly job) and no wakelocks are held for long.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERF-03 · Background job cadence
- **Feature:** WorkManager scheduling
- **Environment:** 💻 Computer
- **Steps:**
  1. `adb shell dumpsys jobscheduler | Select-String -Context 0,15 $PKG`
  2. Note the periodic job's interval and its last and next run times.
- **Expected:** One periodic ingest job about every hour (15-minute flex), not duplicated after repeated launches, upgrades or reboots.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERF-04 · Monkey stress test
- **Feature:** Crash resistance
- **Environment:** 💻 Computer
- **Steps:**
  1. Sign in and set up tracking first.
  2. `adb shell monkey -p $PKG --throttle 150 --pct-syskeys 0 -v 5000`
  3. Run `adb logcat -b crash -d` afterwards.
- **Expected:** `Monkey finished` with no `CRASH` or `ANR` lines. (Monkey may sign you out or tap destructive buttons, so use a throwaway account.)
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERF-05 · Extended usage session
- **Feature:** Long-run stability
- **Environment:** 📱 Physical device
- **Steps:**
  1. Over 2+ hours, alternate between 10-minute sessions on TikTok, Instagram and YouTube, opening ReelCounter and refreshing between each.
  2. Watch for jank and check `adb logcat -b crash`.
- **Expected:** No crash or ANR. Totals only go up, and scrolling the dashboard stays smooth (check with the on-screen GPU profiling bars in Developer options).
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERF-06 · Many days of data
- **Feature:** Scalability of ingest
- **Environment:** 📱 Physical device
- **Steps:**
  1. After 7+ days of tracking, time a pull-to-refresh (stopwatch) and run `adb shell am start -W -n $ACT` cold.
- **Expected:** Refresh completes in under 2 s and cold start stays within the INST-03 target.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

### PERF-07 · Logcat during normal use
- **Feature:** Log hygiene
- **Environment:** 💻 Computer
- **Steps:**
  1. Find the app's process ID with `adb shell pidof $PKG`, then run `adb logcat --pid=<that PID>` while running INST-06 through DASH-02.
- **Expected:** No uncaught exceptions and no repeated error loops. No email addresses, passwords, tokens or keys are printed in the log.
- **Actual result:**
  > &nbsp;
- **Status:** - [ ] Not Tested - [ ] Pass - [ ] Fail - [ ] Blocked

---

## Summary

| Section | Total | Pass | Fail | Blocked | Not tested |
|---|---|---|---|---|---|
| INST | 8 | | | | |
| PERM | 10 | | | | |
| COUNT | 16 | | | | |
| DASH | 9 | | | | |
| PERSIST | 6 | | | | |
| EDGE | 11 | | | | |
| PERF | 7 | | | | |
| **Total** | **67** | | | | |

Release blockers found: ________________________________________________
