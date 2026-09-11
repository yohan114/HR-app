package com.hr.shared.storage

import java.time.Instant

/**
 * Storage bucket categories matching the four isolated S3 buckets defined in Terraform.
 *
 * Distinct buckets rather than prefixes ensures hard IAM boundary separation, distinct
 * compliance retention policies, and Object Lock controls.
 */
enum class StorageBucket(val logicalName: String, val purpose: String) {
    DOCUMENTS("documents", "Employee documents, contracts and generated letters"),
    PAYSLIPS("payslips", "Generated payslip PDFs with statutory retention"),
    ATTACHMENTS("attachments", "Leave certificates, expense receipts, profile photos"),
    PAYROLL_SNAPSHOTS("payroll-snapshots", "Immutable inputs to each payroll run with Object Lock"),
}

/**
 * Metadata record describing a stored object in a storage bucket.
 */
data class StoredObject(
    val bucket: StorageBucket,
    val path: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val checksumSha256: String? = null,
    val url: String,
    val lastModified: Instant = Instant.now(),
)

/**
 * Common object storage service interface supporting both local filesystem development
 * and AWS S3 in production.
 */
interface ObjectStorageService {
    /** Stores object bytes and returns metadata with access URL. */
    fun store(bucket: StorageBucket, path: String, content: ByteArray, mimeType: String): StoredObject

    /** Reads object bytes, or returns null if not found. */
    fun read(bucket: StorageBucket, path: String): ByteArray?

    /** Deletes an object. Returns true if removed, false if not found. */
    fun delete(bucket: StorageBucket, path: String): Boolean

    /** Generates a pre-signed HTTP PUT URL for direct client-to-storage upload. */
    fun getPresignedUploadUrl(bucket: StorageBucket, path: String, mimeType: String, ttlSeconds: Long = 3600): String

    /** Generates a pre-signed HTTP GET URL for temporary read access. */
    fun getPresignedDownloadUrl(bucket: StorageBucket, path: String, ttlSeconds: Long = 3600): String
}
