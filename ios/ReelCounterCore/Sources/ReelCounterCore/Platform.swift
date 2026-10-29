import Foundation

/**
 Default assumptions for one platform: how much of its app time is short-form video, and how long one
 video takes on average, each with a plausible range.

 Mirrors `PlatformCatalog` on Android and `public.estimation_profiles` version 1 on the server. The
 numbers are unvalidated placeholders until calibration publishes version 2.
 */
public struct EstimationProfile: Equatable, Sendable {
    public let version: Int
    public let shortFormShare: Double
    public let shareLow: Double
    public let shareHigh: Double
    public let secondsPerReel: Double
    public let secondsLow: Double
    public let secondsHigh: Double

    public init(version: Int, shortFormShare: Double, shareLow: Double, shareHigh: Double,
                secondsPerReel: Double, secondsLow: Double, secondsHigh: Double) {
        precondition(0 <= shareLow && shareLow <= shortFormShare && shortFormShare <= shareHigh && shareHigh <= 1)
        precondition(0 < secondsLow && secondsLow <= secondsPerReel && secondsPerReel <= secondsHigh)
        self.version = version
        self.shortFormShare = shortFormShare
        self.shareLow = shareLow
        self.shareHigh = shareHigh
        self.secondsPerReel = secondsPerReel
        self.secondsLow = secondsLow
        self.secondsHigh = secondsHigh
    }
}

/// The short-video platforms ReelCounter tracks. Raw values are the server's `platforms.id`.
public enum PlatformID: String, CaseIterable, Codable, Sendable, Identifiable {
    case tiktok
    case instagram
    case youtube
    case facebook

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .tiktok: return "TikTok"
        case .instagram: return "Instagram"
        case .youtube: return "YouTube"
        case .facebook: return "Facebook"
        }
    }

    public var profile: EstimationProfile {
        switch self {
        case .tiktok:
            return EstimationProfile(version: 1, shortFormShare: 0.95, shareLow: 0.85, shareHigh: 1.00,
                                     secondsPerReel: 21, secondsLow: 12, secondsHigh: 35)
        case .instagram:
            return EstimationProfile(version: 1, shortFormShare: 0.55, shareLow: 0.30, shareHigh: 0.80,
                                     secondsPerReel: 18, secondsLow: 10, secondsHigh: 30)
        case .youtube:
            return EstimationProfile(version: 1, shortFormShare: 0.30, shareLow: 0.10, shareHigh: 0.60,
                                     secondsPerReel: 30, secondsLow: 15, secondsHigh: 50)
        case .facebook:
            return EstimationProfile(version: 1, shortFormShare: 0.20, shareLow: 0.05, shareHigh: 0.50,
                                     secondsPerReel: 20, secondsLow: 10, secondsHigh: 35)
        }
    }

    /// Platforms in display order (the server's `sort_order`).
    public static var ordered: [PlatformID] { allCases }
}
