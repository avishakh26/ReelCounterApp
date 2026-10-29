# 04: Technical Limitations & Mitigations

Phase 1 deliverable. These are the constraints that shape the tracking engine (Phase 4) and the release checklist (Phase 10). Some items are marked **unverified**: spike S1 must measure them on real devices before we rely on them.

## Summary table

| # | Limitation | Impact | Mitigation |
|---|---|---|---|
| L1 | UsageStats is **package-level**; it can't see in-app surfaces | Reels can't be told apart from feed or DMs | Short-form share factor, user calibration, "app time" labels, DURATION_ONLY defaults for YouTube and Facebook |
| L2 | **Raw events are kept only briefly.** The window varies by OEM and OS (**unverified**: design for ≤ 3 days). | A user who doesn't open the app for days loses data | Periodic worker, ingest on every app open, finalize days at D−2, coverage gaps shown as UNAVAILABLE, a gentle "open to keep tracking" nudge |
| L3 | Events between the last OS persist and a shutdown **aren't saved** (AOSP note) | Small undercount around shutdowns | Close sessions on `DEVICE_SHUTDOWN`/`STARTUP`; accept and document the small error |
| L4 | `queryEvents` returns nothing **before the first unlock** (Direct Boot) | A worker sees an empty window | Check `UserManager.isUserUnlocked`; never advance the watermark on an empty or locked read |
| L5 | **OEM background killing** and **App Standby buckets** | Workers are deferred for hours or days | Correctness doesn't depend on background runs (pull reconciliation); OEM guide on detected gaps; no FGS; no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| L6 | **Time zones:** travel, DST (23 h or 25 h days), manual clock changes | Wrong day attribution or apparent double counting | `java.time` with injected `Clock`/`ZoneId`; zone stored per session and per row; recompute non-final days on time changes |
| L7 | Multi-window, multi-resume (Android 10+), picture-in-picture | Overlapping foreground time | Per-app totals stay correct; never present the sum of apps as "screen time"; PiP counts as background |
| L8 | Work profile, Private Space (Android 15+), secondary users, web browser use | Usage is invisible | Shown as UNAVAILABLE with an explanation |
| L9 | **Permission lifecycle:** usage access can be revoked at any time | Silent data loss | Check AppOps on every run and on resume; re-grant banner; affected days become UNAVAILABLE |
| L10 | **Package visibility** (Android 11+) | Can't detect installed platforms | `<queries>` for known packages; never `QUERY_ALL_PACKAGES` |
| L11 | **Play policy:** user data and prominent disclosure | Rejection or removal | Disclosure before settings intent; affirmative consent; privacy policy; accurate Data Safety form |
| L12 | Client-reported data **can be faked** | Leaderboard cheating | Server-only VERIFIED; sanity constraints; "device-measured" labels |
| L13 | Data-portability APIs: region-locked, review-gated, asynchronous, no per-video duration | VERIFIED is limited and delayed | Post-v1 (Phase 11); token handling and archive polling in Edge Functions; "verified up to <date>" |

## Details

### L1: Package-level granularity

`UsageStatsManager` reports when an app's activities are resumed and paused. Instagram, TikTok, YouTube and Facebook each run most of their UI inside one or a few activities, so `UsageEvents.Event.getClassName()` doesn't reveal which tab is open. The only compliant ways to learn more would be the platform's own APIs, and none exist outside L13. Everything downstream (labels, estimation bands, default categories) follows from this one fact.

### L2: Event retention

The OS keeps the raw event stream for a limited window, while aggregated interval stats last longer but have imprecise day boundaries. We rely on raw events because aggregated `INTERVAL_DAILY` buckets don't align with local midnight.

Mitigations:
- **Ingest on every app open,** plus a periodic worker (~60 min; WorkManager's minimum is 15 min).
- **Finalize days at D−2,** so expired events never shrink a stored total.
- **Track coverage per day.** Uncovered time shows as UNAVAILABLE, never as 0.
- **S1 measures the real retention window** on Pixel, Samsung and Xiaomi devices.

### L4: Direct Boot / locked user

Before the user unlocks the device for the first time after boot, usage data isn't accessible. The worker checks `UserManager.isUserUnlocked()` and, if the device is locked, retries later without touching the watermark.

### L5: OEM background restrictions

Xiaomi/HyperOS, Huawei, Samsung ("sleeping apps"), Oppo/ColorOS, Vivo and OnePlus can delay or kill background work far beyond stock Android. App Standby buckets (`rare`, and `restricted` on Android 12+) also throttle jobs.

- **Background runs only affect freshness, never correctness.** The OS log is the source of truth, and any later run backfills what's still retained.
- **If gaps are detected, show an OEM-specific guide** that opens `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (the list screen).
- **We don't request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.** Play restricts that permission to specific app categories.
- **We don't run a foreground service.** That avoids FGS-type declarations and a persistent notification.

### L6: Time zones and clocks

Rules:
1. Every session stores the `ZoneId` in effect when it started. Day bucketing splits at **that zone's** local midnight. DST days are naturally 23 h or 25 h long, and the 25 h cap in the schema allows for that.
2. A day row stores its `tz_id`.
3. When the user travels, each session is attributed to the local date where it happened. Two rows can then share a calendar date label from different zones; we document this rather than "fix" it.
4. On `ACTION_TIME_CHANGED` or `ACTION_TIMEZONE_CHANGED`, all non-final days are recomputed.
5. Event timestamps are wall-clock, so a manual clock change can produce out-of-order events. The SessionBuilder sorts events, drops negative or over-long sessions, and the sanity cap applies.
6. Friends are compared by **each user's own local date** ("their Monday"), never by converting to one zone.
7. Week start follows the locale, with a user override in settings.

All time logic takes an injected `Clock` and `ZoneId`. Phase 9 has a boundary test matrix covering DST forward and back, crossing the date line, midnight-spanning sessions, and clock changes.

### L7: Multi-window and PiP

From Android 10, several activities can be resumed at the same time (split screen). Per-app totals stay correct, but the sum across apps can exceed wall-clock time, so the app never shows a "total screen time". Picture-in-picture YouTube is paused, not resumed, so it doesn't count as foreground time; this is documented in the in-app explainer.

### L9: Permission lifecycle

Usage access is a special app-op, not a runtime permission:
1. A prominent disclosure screen explains what's collected and why. Consent is an affirmative tap.
2. We launch `Settings.ACTION_USAGE_ACCESS_SETTINGS`.
3. On return, we verify with `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, …)`.

The same check runs on every ingest and on every app resume. If access was revoked, a banner offers to re-grant it, and the affected days become UNAVAILABLE ("permission off").

### L10: Package visibility

Android 11+ hides other installed apps unless declared. We declare a `<queries>` element listing the known platform packages from [03](03-database-schema.md). `QUERY_ALL_PACKAGES` is a restricted Play permission and isn't needed. A platform added server-side therefore also needs an app update before its "installed" state can be detected.

### L11: Google Play policy

- **Prominent disclosure:** in-app, shown during normal use, before the settings intent, describing the data (app usage times of the selected platforms) and how it's used (personal stats; sharing with friends only as configured).
- **Data Safety form:** declares app activity and app interactions, whether data is shared, encryption in transit, and the deletion mechanism (finalized in Phase 10).
- **Account deletion:** required both in-app and via a web link (Phase 8).
- **No AccessibilityService, VpnService, MediaProjection or foreground service.**
- **Re-verify current Play requirements at Phase 10.** Policies change, and this document checked them in October 2026.

### L12: Untrusted client data

Anything a client computes can be forged by a modified app or a rooted device. Mitigations:
- `VERIFIED` and `data_portability` rows can only be written by the server.
- DB check constraints cap values (≤ 25 h per day, non-negative counts, ordered bands).
- Leaderboards are labeled "device-measured".
- Anomaly flags can come later.

This can't be fully prevented, and the product doesn't pretend otherwise.

### L13: Data-portability APIs (post-v1)

- They are restricted to users in the EEA and UK.
- They require platform app review (TikTok) or OAuth verification of a restricted scope (Google).
- Exports are asynchronous: start, poll, download.
- They don't include per-video watch duration.
- The Phase 11 design runs token storage and archive polling in Edge Functions, writes `source = 'data_portability'` rows with `device_id = NULL`, and shows "verified up to <date>".
