import Foundation

public struct ReelEstimate: Equatable, Sendable {
    public let count: Int
    public let low: Int
    public let high: Int

    public init(count: Int, low: Int, high: Int) {
        self.count = count
        self.low = low
        self.high = high
    }
}

public enum Estimator {
    /**
     reels ≈ app time × short-form share ÷ seconds per reel. The range combines the low share with the
     long duration and vice versa, and always contains the point estimate. Same formula as Android's
     `Estimator.estimate`, so both apps report the same number for the same app time.
     */
    public static func estimate(appTimeMs: Int64, profile: EstimationProfile) -> ReelEstimate {
        let seconds = Double(max(appTimeMs, 0)) / 1_000
        let count = Int((seconds * profile.shortFormShare / profile.secondsPerReel).rounded())
        let low = Int((seconds * profile.shareLow / profile.secondsHigh).rounded(.down))
        let high = Int((seconds * profile.shareHigh / profile.secondsLow).rounded(.up))
        return ReelEstimate(count: count, low: min(low, count), high: max(high, count))
    }
}
