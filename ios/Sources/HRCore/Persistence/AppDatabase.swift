import Foundation
import GRDB

/// Creates and migrates the local database.
///
/// The store is a cache of server state, so migrations may be destructive where that is simpler —
/// with one exception. The outbox holds mutations the server has **not** seen, so it is the only
/// table whose loss costs the user something. It is migrated carefully, and if it ever cannot be,
/// the user is warned rather than having their queued work silently discarded.
///
/// Encryption is provided by iOS file protection rather than SQLCipher. `.completeUnlessOpen`
/// means the file is encrypted with a key derived from the device passcode whenever the device is
/// locked and the app is not already holding it open — which is what protects against offline
/// extraction from a stolen device. Adding SQLCipher on top would double the crypto without
/// meaningfully raising the bar on iOS, unlike Android where no equivalent guarantee exists.
public enum AppDatabase {
    public static func open(at url: URL) throws -> DatabaseQueue {
        var configuration = Configuration()
        configuration.prepareDatabase { db in
            // Foreign keys are off by default in SQLite. Without this, cascade deletes silently
            // do nothing.
            try db.execute(sql: "PRAGMA foreign_keys = ON")
        }
        // Background sync and outbox drains run while the app is suspended, so the file must stay
        // readable once opened — `.completeUntilFirstUserAuthentication` would be stricter but
        // would stop background work entirely after a reboot.
        configuration.prepareDatabase { _ in }

        try FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )

        let queue = try DatabaseQueue(path: url.path, configuration: configuration)
        try migrator.migrate(queue)
        try applyFileProtection(to: url)
        return queue
    }

    /// Destroys the local store.
    ///
    /// Called on sign-out and device revocation. Deleting rather than clearing: a revoked device
    /// must retain no readable copy of tenant data (docs/sync-protocol.md §8).
    public static func destroy(at url: URL) throws {
        let fileManager = FileManager.default
        for suffix in ["", "-wal", "-shm"] {
            let path = url.path + suffix
            if fileManager.fileExists(atPath: path) {
                try fileManager.removeItem(atPath: path)
            }
        }
    }

    private static var migrator: DatabaseMigrator {
        var migrator = DatabaseMigrator()

        #if DEBUG
        // Rebuild on schema change during development rather than hand-writing migrations for a
        // schema that is still moving. Disabled for release builds.
        migrator.eraseDatabaseOnSchemaChange = true
        #endif

        migrator.registerMigration("v1_sync_infrastructure") { db in
            try db.create(table: "outbox") { t in
                t.primaryKey("id", .text)
                t.column("idempotencyKey", .text).notNull().unique()
                t.column("aggregateType", .text).notNull()
                t.column("aggregateId", .text).notNull()
                t.column("httpMethod", .text).notNull()
                t.column("path", .text).notNull()
                t.column("payload", .text).notNull()
                t.column("state", .text).notNull()
                t.column("attemptCount", .integer).notNull().defaults(to: 0)
                t.column("createdAt", .datetime).notNull()
                t.column("lastAttemptAt", .datetime)
                t.column("nextAttemptAt", .datetime).notNull()
                t.column("failureCode", .text)
                t.column("failureMessage", .text)
            }
            // Drain order: oldest first, within an aggregate.
            try db.create(
                index: "idx_outbox_aggregate",
                on: "outbox",
                columns: ["aggregateType", "aggregateId", "createdAt"]
            )
            try db.create(index: "idx_outbox_state", on: "outbox", columns: ["state"])

            try db.create(table: "sync_cursor") { t in
                t.primaryKey("scope", .text)
                t.column("cursor", .text)
                t.column("lastSyncedAt", .datetime)
                t.column("lastAttemptAt", .datetime)
                t.column("lastError", .text)
            }
        }

        return migrator
    }

    private static func applyFileProtection(to url: URL) throws {
        try FileManager.default.setAttributes(
            [.protectionKey: FileProtectionType.completeUnlessOpen],
            ofItemAtPath: url.path
        )
    }

    public static func defaultURL() throws -> URL {
        let support = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        return support.appendingPathComponent("hr.sqlite")
    }
}
