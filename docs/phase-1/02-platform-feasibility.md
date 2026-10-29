# 02: Platform Feasibility Matrix & Metric Classification

Phase 1 deliverable. Platform API facts were checked against public documentation in October 2026 (sources at the end). Platform APIs change often, so re-verify before Phase 11.

## 1. Bottom line

- **No platform offers a "reels watched" API to ordinary apps worldwide.**
  - Instagram and Facebook have no consumption-data API at all.
  - The Instagram Basic Display API was retired on **4 Dec 2024**.
  - The remaining Instagram APIs (Graph API and "Instagram API with Instagram Login") only expose a professional account's *own* media and insights.
- **VERIFIED is possible only for users in the EEA and UK**, and only through data-portability APIs that exist because of the EU Digital Markets Act:
  - **TikTok Data Portability API:** *Activity → Watch History* (date, video link, post title). It requires TikTok's app review plus a privacy and security review, and returns data only for EEA/UK users.
  - **Google Data Portability API, resource `myactivity.youtube`:** a **restricted** OAuth scope that needs Google app verification. It covers all YouTube activity, and Shorts may not be separable from long-form video, so this needs a spike (S3).
  - Both are **asynchronous archive exports**. You start a job, poll it, then download the files. Data arrives hours later, not in real time.
- **For everyone else, the most we can measure is per-app foreground time** (`UsageStatsManager`). Android reports time per *package*, not per screen, so the app **cannot tell Reels from the feed, Stories or DMs**. Every reel count outside the portability APIs is an estimate built on an assumed short-form share.
- **Decision (D3):** the MVP (Phases 2–10) ships `DURATION_ONLY` + `ESTIMATED` with honest labels. VERIFIED connectors are a proposed post-v1 **Phase 11**, once eligibility is confirmed. The schema reserves `VERIFIED` from day one, and only the server can write it.

## 2. Feasibility matrix

| Platform | Android package(s) | Official consumption data? | Compliant source we use | VERIFIED possible? | Default category |
|---|---|---|---|---|---|
| **TikTok** | `com.zhiliaoapp.musically`, `com.ss.android.ugc.trill`, Lite `com.zhiliaoapp.musically.go` | Only the **Data Portability API** (EEA and UK users; app and privacy review). Login Kit and Display API cover profile and own posts only. The Research API is academic-only. | UsageStats duration; post-v1, DP API Watch History | **Yes, EEA/UK only, delayed by hours** | **ESTIMATED**. The app is nearly all short-form, so this is our best estimate. |
| **YouTube (Shorts)** | `com.google.android.youtube` | Data API v3 has no watch history. Google **Data Portability API** `myactivity.youtube` is a restricted scope (OAuth verification; EEA, with the UK also offered). | UsageStats duration (whole app) | **Partial.** Verified *total* watches; separating Shorts is unproven (S3). | **DURATION_ONLY** until the user calibrates their Shorts share (D1) |
| **Instagram (Reels)** | `com.instagram.android`, `com.instagram.lite` | **None.** Basic Display retired on 4 Dec 2024. The current APIs cover a professional account's own media and insights only. | UsageStats duration | No | **ESTIMATED** (low–medium confidence; app time includes feed, Stories and DMs) |
| **Facebook (Reels)** | `com.facebook.katana`, `com.facebook.lite` | **None.** The Graph API exposes no viewing history, and Meta has no open portability API. | UsageStats duration | No | **DURATION_ONLY** until calibrated (D1). Reels are a small and variable share of Facebook time. |
| **Others** (e.g. Snapchat Spotlight) | added through the server `platforms` registry plus an app update for `<queries>` | None | UsageStats duration | No | DURATION_ONLY |
| Any platform used **on the web, another device, a work profile or Private Space** | n/a | n/a | n/a | n/a | **UNAVAILABLE** (not visible to UsageStats) |

### Per-platform notes

- **TikTok:**
  - Almost all of TikTok's foreground time is the short-form feed, so the *share* assumption contributes little error. The main uncertainty is seconds per video.
  - Watch History in the portability export has no per-video watch duration, so a VERIFIED count is "videos started", not "videos finished". The UI copy must say so.
- **YouTube:**
  - App time mixes long-form, Shorts, music and comments, and background play (Premium) isn't foreground at all.
  - Without a calibrated Shorts share, any count would mostly reflect our assumption rather than the user's behavior. That's why it defaults to DURATION_ONLY.
- **Instagram:**
  - Reels is a dominant surface, but feed, Stories and DMs are significant.
  - We estimate with a conservative default share and a wide band, and invite the user to calibrate.
- **Facebook:**
  - Reels is a minority surface for most users, so an estimate would be mostly assumption. It defaults to DURATION_ONLY.

### Rejected approaches (non-compliant; never to be used)

| Approach | Why rejected |
|---|---|
| AccessibilityService screen scraping | Violates Play's Accessibility API policy (non-accessibility use) and the project spec |
| MediaProjection screen recording or OCR | Spec forbids it; privacy-invasive; requires a persistent capture notification |
| Notification-listener inference | Not meaningful for viewing; sensitive permission |
| VpnService traffic inspection | Play VpnService policy; inspecting encrypted platform traffic breaks the platforms' ToS |
| Private or reverse-engineered APIs, web-session scraping | Violates platform ToS; brittle; account-ban risk for users |
| Collecting users' platform passwords | Prohibited by every platform; security liability |
| Manual "Download Your Information" file import as VERIFIED | It's an export file rather than an API, and its completeness is unproven. Revisit post-v1 (it could at most be ESTIMATED). |

## 3. Metric classification model

`MetricQuality` is resolved once per (user, platform, local date). The precedence order is `VERIFIED > ESTIMATED > DURATION_ONLY > UNAVAILABLE`, which is also the enum declaration order in Postgres.

| Category | When | What the UI shows |
|---|---|---|
| `VERIFIED` | A complete count for that date from an approved platform API. **Server-written only.** | "142 videos · Verified via TikTok (up to 18 Oct)" |
| `ESTIMATED` | Measured duration × an estimation model, with estimation turned on. **Manual corrections stay here** with `user_adjusted = true` (D2). | "≈ 85 reels (60–110) · Estimated", plus an "adjusted by you" badge where relevant |
| `DURATION_ONLY` | Duration is measured, but there's no trustworthy model (or the user turned estimation off) | "47 min app time" with no count |
| `UNAVAILABLE` | Permission missing or revoked, tracking paused, app not installed, a coverage gap, or web/other-device use | "Not tracked" with the reason |

### Display invariants (enforced in `:core:domain` formatters and tested)

1. The UI never sums across categories into one unlabeled number. A combined total reads "≈ 230 (142 verified + ≈ 88 estimated)".
2. Duration is labeled **"app time"**, never "reels time" or "watch time".
3. Estimates always carry "≈", a range, and the "Estimated" badge.
4. Leaderboards rank by **app time** by default (D5), the only metric every user has. Reel-count rankings show each entry's category badge.
5. Stealth mode is indistinguishable, to viewers, from activity simply not being shared.

### Estimator v1 (pure Kotlin, versioned, unit-tested)

```
reel_minutes    = foreground_minutes(sessions ≥ min_session_sec) × short_form_share
estimated_reels = round(reel_minutes × 60 / seconds_per_reel)
band_low        = same formula with (share_low,  seconds_high)
band_high       = same formula with (share_high, seconds_low)
```

- **Parameters** come from server-side `estimation_profiles` (versioned), so they can be tuned without shipping a release. The user can override them per platform in `user_platform_settings`.
- **Every stored estimate records `estimation_version`**, so a later model change never rewrites history silently.
- **The default parameter values are not research-backed.** Spike S2 in Phase 4 sets them through a calibration exercise that compares manual counts with measured app time, and the app presents them to users as assumptions they can change.

## Sources

- [TikTok Data Portability API](https://developers.tiktok.com/products/data-portability-api)
- [TikTok Data Portability: data types (Activity → Watch History)](https://developers.tiktok.com/docs/en/data-portability-data-types)
- [Google Data Portability API: introduction](https://developers.google.com/data-portability/user-guide/introduction)
- [Google Data Portability API: scopes (myactivity.youtube is Restricted)](https://developers.google.cn/data-portability/user-guide/scopes?hl=en)
- [UK CMA: data portability conduct requirement (EEA under DMA; UK offered voluntarily)](https://assets.publishing.service.gov.uk/media/6979ce981c24881f40a4d6dd/_Data_portability_conduct_requirement_v2.pdf)
- [Instagram API overview 2026 (Basic Display retired Dec 2024)](https://www.getphyllo.com/post/instagram-api-guide). This is a secondary source; confirm against Meta's own docs before Phase 11.
