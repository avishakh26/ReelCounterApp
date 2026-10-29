# 05: Roadmap, Risk Register & Open Decisions

Phase 1 deliverable. Protocol: one phase at a time, and each phase starts only after your explicit approval of the previous one.

## 1. Roadmap

| Phase | Scope | Exit criteria |
|---|---|---|
| **1** Feasibility & architecture | These docs | **Approved by you** |
| **2** Setup & UI foundation | Multi-module Gradle with convention plugins; M3 theme; type-safe Navigation Compose; core components | Build compiles; light, dark and dynamic color; Loading/Empty/Error/Offline wrappers and skeletons with previews (preview providers isolated from production code); CI build and lint |
| **3** Auth & profiles | Supabase Auth (sign up, log in, reset, persistent session); profile setup; secrets handling | Works against a local Supabase (`supabase start`); username unique at client and DB level; no secrets in VCS (Secrets Gradle plugin); no service-role key in the app; first migration created from [03](03-database-schema.md) §1–2 |
| **4** Tracking engine & local data | **Spikes S1 and S2 first.** Then disclosure and permission flow, app selection, SessionBuilder, DayBucketer, Estimator, Room, DataStore, Ingest/Sync/Finalize workers. | Unit tests for DST, travel, reboot, midnight split, gaps and duplicate runs; estimator defaults seeded from S2 |
| **5** Backend & sync | Activity tables, RLS, idempotent sync | Migrations from [03](03-database-schema.md) §3–6; revision-based conflict handling; retry with backoff; verified offline → online behavior |
| **6** Social | Search RPC, friend-request state machine (send, accept, reject, cancel, remove), blocks, private presence | Server-side authorization tests; no email exposure; rate limiting |
| **7** Dashboards | Today, weekly and monthly views (Vico), heatmap, manual correction, leaderboard | Display invariants from [02](02-platform-feasibility.md) §3 enforced and tested |
| **8** Privacy & data | Visibility controls, pause and stealth, JSON/CSV export, account deletion | Deletion Edge Function (storage purge, `auth.admin.deleteUser`, FK cascade) plus a web deletion link; local purge |
| **9** QA | Unit, Room and worker tests; pgTAP RLS suite; offline and battery-resilience matrix | Every RLS policy covered by allow and deny tests |
| **10** Release | R8 rules, manifest audit, Data Safety, deployment guide | Play policy re-verified; migrations reproducible from scratch |
| ***11* (proposed, post-v1)** | TikTok and Google Data Portability connectors for EEA/UK users | Eligibility and legal review done (S3); VERIFIED rows written server-side only |

### Spikes

| Spike | When | Question | Output |
|---|---|---|---|
| **S1** UsageStats fidelity | Phase 4 kickoff | On Pixel, Samsung and Xiaomi (Android 12–16): how long are raw events retained? Are RESUMED/PAUSED pairs reliable? What's lost at shutdown, during PiP and in split screen? | Measured numbers that confirm or adjust L2–L7 |
| **S2** Estimator calibration | Phase 4 | Compare manual reel counts with measured app time per platform | First `estimation_profiles` seed with honest bands |
| **S3** Portability eligibility | Before Phase 11 | Can we pass TikTok and Google review? Can YouTube Shorts be identified in `myactivity.youtube`? | Go / no-go for Phase 11 |

## 2. Risk register

| Risk | Likelihood / impact | Mitigation |
|---|---|---|
| **R1** Users read estimates as exact counts, so the product misleads them | High / High | Onboarding explainer; "≈" and ranges everywhere; category badges; DURATION_ONLY defaults where the model is weak |
| **R2** Play rejection (usage access, disclosure, Data Safety) | Medium / High | L11 measures; no accessibility, VPN or FGS; pre-launch policy review in Phase 10 |
| **R3** Data gaps from OEM killing or event expiry | High / Medium | Pull-based reconciliation, gap detection, OEM guides (L2, L5) |
| **R4** An RLS bug leaks private activity | Low / Critical | Deny-by-default; helpers in a non-exposed schema; column grants; `security_invoker` views; pgTAP tests for every policy |
| **R5** Fake or inflated stats on leaderboards | Medium / Medium | Server-only VERIFIED, sanity constraints, labels (L12) |
| **R6** Brand and ToS issues (platform names and logos) | Medium / Medium | Nominative use only; follow each brand's guidelines; no implied endorsement; legal check before release |
| **R7** Time-zone and DST bugs | Medium / Medium | Injected clock; exhaustive boundary tests (Phase 9) |
| **R8** Minors using a social-comparison app | Medium / High | Age gate per D4; stay out of the Families program |
| **R9** Portability APIs refused or changed | Medium / Low (post-v1 only) | The MVP doesn't depend on them |
| **R10** Supabase cost or lock-in (Realtime connections) | Low / Medium | Repository abstraction; plain Postgres; presence limited to friends |

## 3. Open decisions (defaults applied; change any at review)

| # | Decision | Default | Alternative |
|---|---|---|---|
| D1 | Default category for YouTube and Facebook | **ESTIMATED for all platforms** (revised 2026-10-29 at the product owner's request; was DURATION_ONLY for YouTube and Facebook). Users can still turn estimates off per app. | DURATION_ONLY for YouTube and Facebook until the user calibrates |
| D2 | How manual corrections are classified | **ESTIMATED + "adjusted by you"**; never VERIFIED | n/a (VERIFIED would break the transparency rule) |
| D3 | When VERIFIED connectors are built | **Post-v1 Phase 11**, EEA/UK users only | Inside Phase 4/5 (delays the MVP; depends on external review) |
| D4 | Minimum age | **16+** (simpler GDPR consent; no Families policy) | 13+ (more consent work; stricter review) |
| D5 | Default leaderboard metric | **App time**; reel-count ranking shown with badges | Reel count by default |
| D6 | Avatar privacy | **Public-read bucket**, unguessable paths | Private bucket with signed URLs (more private; more requests) |
| D7 | Tooling | **Hilt; multi-module with convention plugins; minSdk 26, targetSdk 36** | Koin; single module |
