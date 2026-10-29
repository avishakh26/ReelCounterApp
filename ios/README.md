# ReelCounter for iPhone

A native SwiftUI app (iOS 16+) that uses the same Supabase backend as the Android app, so one
account works on both and iPhone and Android users can be friends.

## What's different from Android

| | Android | iPhone |
|---|---|---|
| App time | Usage Access (exact) | Apple Screen Time, in steps (1, 2, 3… minutes, then 5/10/20/30-minute steps) |
| Reel count | Estimated, or counted one by one (accessibility service) | Estimated only: iOS has no way to count videos |
| Picking apps | Found by package name | The user picks each app in Apple's picker (iOS hides app identities) |
| Uploads | Background worker | When the app opens, and iOS background refresh |

Estimates use the same formula and profiles as Android (`ReelCounterCore/Estimator.swift`).

## Layout

- `ReelCounterCore/`: Swift package with the shared logic (platforms, estimates, day summaries,
  Screen Time thresholds, App Group usage store, server row formats) and its tests (`swift test`).
- `ReelCounter/`: the app: `App/`, `Design/` (design system matching Android), `Data/`
  (Supabase: session, friends, uploads), `Tracking/` (Screen Time), `Features/` (screens).
- `ReelCounterMonitor/`: the Screen Time (DeviceActivity monitor) extension. It only records the
  minutes reached per platform into the App Group `group.com.reelcounter.app`.
- `project.yml`: XcodeGen definition; the `.xcodeproj` is generated, not committed.
- `Config/`: `App.xcconfig` (placeholders) and `Secrets.xcconfig` (git-ignored real values).

## Build

GitHub Actions builds and tests it on a cloud Mac (`.github/workflows/ios.yml`). On a Mac:

```bash
brew install xcodegen
cd ios
cp Config/Secrets.example.xcconfig Config/Secrets.xcconfig   # then fill in the values
xcodegen generate
open ReelCounter.xcodeproj
```

## Before it can run on iPhones

1. **Apple Developer Program** ($99/year), needed for TestFlight and the App Store.
2. **Family Controls entitlement**: request "Family Controls (Distribution)" for the app and its
   extension at developer.apple.com/contact/request/family-controls-distribution. Development
   builds work on your own iPhone without it; TestFlight and App Store builds need it.
3. Register the bundle ids `com.reelcounter.app` and `com.reelcounter.app.monitor` and the App Group
   `group.com.reelcounter.app` in the developer account, then set the team in Xcode.
4. Run `supabase/migrations/20261029140000_delete_account.sql` on the hosted project: Apple
   requires in-app account deletion.

Screen Time doesn't work in the simulator; test tracking on a real iPhone.
