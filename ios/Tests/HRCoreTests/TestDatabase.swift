import Foundation
import GRDB
@testable import HRCore

/// An in-memory database with the sync tables.
///
/// Shared rather than duplicated per suite. Two copies of a schema drift, and the way they drift
/// is that a column is added to one — so a test suite keeps passing against a table shape the app
/// no longer has, which is worse than having no test.
///
/// This mirrors the tables `AppDatabase` creates. It is deliberately not `AppDatabase` itself:
/// tests want a fresh in-memory queue per case with no file, no encryption key and no migration
/// history, and threading all of that through the production type would complicate it for the
/// benefit of tests only.
enum TestDatabase {
    static func make() throws -> DatabaseQueue {
        let queue = try DatabaseQueue()
        try migrate(queue)
        return queue
    }

    static func migrate(_ queue: DatabaseQueue) throws {
        try queue.write { db in
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
            try db.create(table: "sync_cursor") { t in
                t.primaryKey("scope", .text)
                t.column("cursor", .text)
                t.column("lastSyncedAt", .datetime)
                t.column("lastAttemptAt", .datetime)
                t.column("lastError", .text)
            }
        }
    }
}
