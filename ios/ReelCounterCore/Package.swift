// swift-tools-version:5.9
import PackageDescription

// Logic shared by the app and its Screen Time extension: platforms, estimates, day summaries and the
// App Group usage store. Foundation only, so `swift test` runs it on a Mac without a simulator.
let package = Package(
    name: "ReelCounterCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "ReelCounterCore", targets: ["ReelCounterCore"]),
    ],
    targets: [
        .target(name: "ReelCounterCore"),
        .testTarget(name: "ReelCounterCoreTests", dependencies: ["ReelCounterCore"]),
    ]
)
