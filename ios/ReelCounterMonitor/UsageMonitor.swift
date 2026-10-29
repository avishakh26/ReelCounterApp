import DeviceActivity
import Foundation
import ReelCounterCore

/**
 Screen Time runs this extension when one of the apps the user picked reaches a usage step today
 (see `UsageThresholds`). It only writes the minutes reached to the App Group; the app reads them,
 shows them and uploads the day's totals. It never sees which app was used beyond the platform the
 user assigned it to, and never what was watched.
 */
final class UsageMonitor: DeviceActivityMonitor {
    private let store = UsageStore.shared()

    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name, activity: DeviceActivityName) {
        super.eventDidReachThreshold(event, activity: activity)
        guard let reached = UsageThresholds.parse(eventName: event.rawValue) else { return }
        store.record(platform: reached.platform, minutes: reached.minutes)
    }
}
