# ReelCounter: Engineering Docs

ReelCounter is an Android app for tracking short-form video habits across platforms (TikTok, Instagram Reels, YouTube Shorts, Facebook Reels), setting habits and comparing stats with friends. Transparency comes first: a number is never presented as more exact than its source allows.

## Phase status

| Phase | Status |
|---|---|
| 1 Feasibility & technical architecture | **Approved** (defaults D1–D7) |
| 2 Project setup & UI foundation | **Approved**: [phase-2/README.md](phase-2/README.md) |
| 3 Authentication & user profiles | **Approved**: [phase-3/README.md](phase-3/README.md) |
| 4 Tracking engine & local data | **Approved**: [phase-4/README.md](phase-4/README.md) |
| 5 Backend integration & activity data model | **Approved**: [phase-5/README.md](phase-5/README.md) |
| 6 Friends & social (with a minimal "who can see my activity" control) | **Delivered, awaiting review**: [phase-6/README.md](phase-6/README.md) |
| 7–10 | Not started |

## Phase 1 deliverables

1. [System architecture & data flow](phase-1/01-system-architecture.md)
2. [Platform feasibility matrix & metric classification](phase-1/02-platform-feasibility.md)
3. [Database schema proposal (Postgres + RLS)](phase-1/03-database-schema.md)
4. [Technical limitations & mitigations](phase-1/04-technical-limitations.md)
5. [Roadmap, risk register & open decisions](phase-1/05-roadmap-and-risks.md)

## Glossary: metric categories

Every reel-count figure the app shows belongs to exactly one category, resolved per (user, platform, local date):

| Category | Meaning | Who can write it |
|---|---|---|
| `VERIFIED` | A complete count for that date from an approved platform API (today, only the DMA data-portability APIs for users in the EEA and UK) | **Server only** (Edge Functions) |
| `ESTIMATED` | Measured app time × a versioned estimation model. This includes user-adjusted parameters and manual corrections (`user_adjusted = true`). | Client |
| `DURATION_ONLY` | Measured foreground app time, with no count shown | Client |
| `UNAVAILABLE` | No data: permission missing, tracking paused, app not installed, coverage gap, web or other-device use | Client |

Other terms:
- **App time:** foreground time of the whole platform app as reported by Android `UsageStatsManager`. It is *not* "reels time": Android can't see which screen inside an app is open.
- **Coverage gap:** part of a day that no ingest run ever read. It is reported as UNAVAILABLE, never as zero.
- **Finalized day:** a day older than D−2 that is frozen and never recomputed from OS events again.
