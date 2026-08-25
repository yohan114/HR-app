// swift-tools-version:5.9

import PackageDescription

/// The iOS client's non-UI layers.
///
/// Packaged as a library rather than living inside the Xcode app target so that the sync engine,
/// outbox and secure storage are testable without launching an app, and so CI can build and test
/// them with `swift test` alone.
///
/// The SwiftUI app target lives in `ios/App/` and depends on this package. See ios/README.md.
let package = Package(
    name: "HRCore",
    platforms: [
        // iOS 16 for Swift Charts, modern navigation and the Observation-era APIs.
        // See docs/03-architecture.md §1.
        .iOS(.v16),
        .macOS(.v13),
    ],
    products: [
        .library(name: "HRCore", targets: ["HRCore"]),
    ],
    dependencies: [
        // GRDB rather than SwiftData or Core Data.
        //
        // The sync engine needs SQL semantics that match Room's closely enough to implement one
        // written protocol on both platforms: explicit transactions, predictable migrations, and
        // observation of arbitrary queries. SwiftData's model is too opinionated to build a sync
        // engine we control on top of, and Core Data's migration behaviour is hard to reason
        // about when the store is a disposable cache.
        .package(url: "https://github.com/groue/GRDB.swift.git", from: "7.0.0"),

        // Generated from spec/openapi.yaml — see clients/README.md.
        .package(path: "../clients/swift"),
    ],
    targets: [
        .target(
            name: "HRCore",
            dependencies: [
                .product(name: "GRDB", package: "GRDB.swift"),
                .product(name: "HRClient", package: "swift"),
            ]
        ),
        .testTarget(
            name: "HRCoreTests",
            dependencies: ["HRCore"]
        ),
    ]
)
