import Foundation

/**
 How app time is measured on iPhone.

 iOS never tells an app how long other apps were used. With Screen Time (the DeviceActivity framework)
 an app can ask to be told when the apps a user picked have been used for a set number of minutes
 today. ReelCounter registers one event per step below; each one that fires proves "at least this
 many minutes", and the highest one reached is the day's app time.

 Steps are fine at first (most days are short) and coarser later, to keep the number of events per
 platform small.
 */
public enum UsageThresholds {
    public static let minutes: [Int] =
        Array(1...5)
        + Array(stride(from: 10, through: 60, by: 5))
        + Array(stride(from: 70, through: 180, by: 10))
        + Array(stride(from: 200, through: 360, by: 20))
        + Array(stride(from: 390, through: 600, by: 30))

    private static let prefix = "rc"

    /// The DeviceActivity event name for [platform] reaching [minutes].
    public static func eventName(platform: PlatformID, minutes: Int) -> String {
        "\(prefix).\(platform.rawValue).\(minutes)"
    }

    /// The DeviceActivity activity name that monitors [platform] for the whole day.
    public static func activityName(platform: PlatformID) -> String {
        "\(prefix).daily.\(platform.rawValue)"
    }

    /// The platform and minutes an event name stands for; nil for names ReelCounter didn't make.
    public static func parse(eventName: String) -> (platform: PlatformID, minutes: Int)? {
        let parts = eventName.split(separator: ".")
        guard parts.count == 3, parts[0] == prefix,
              let platform = PlatformID(rawValue: String(parts[1])),
              let minutes = Int(parts[2]), minutes > 0
        else { return nil }
        return (platform, minutes)
    }

    /// The step after [minutes], i.e. the most the real app time can be; nil past the last step.
    public static func nextStep(after minutes: Int) -> Int? {
        self.minutes.first { $0 > minutes }
    }
}
