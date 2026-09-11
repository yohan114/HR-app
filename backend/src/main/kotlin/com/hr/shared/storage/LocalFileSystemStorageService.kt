package com.hr.shared.storage

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Instant

/**
 * Local file-system implementation of [ObjectStorageService].
 *
 * Used for local development and offline test environments without needing AWS S3 credentials.
 * Stores objects in an isolated directory structure matching bucket logical names:
 * `{basePath}/{bucket.logicalName}/{path}`.
 */
class LocalFileSystemStorageService(
    baseDirectoryPath: String = ".localdev/storage",
    private val publicBaseUrl: String = "http://localhost:8080/v1/storage",
) : ObjectStorageService {

    private val log = LoggerFactory.getLogger(javaClass)
    private val rootPath: Path = Paths.get(baseDirectoryPath).toAbsolutePath().normalize()

    init {
        // Ensure root and sub-bucket directories exist
        for (bucket in StorageBucket.entries) {
            val bucketDir = rootPath.resolve(bucket.logicalName)
            Files.createDirectories(bucketDir)
        }
        log.info("Initialized LocalFileSystemStorageService at root: {}", rootPath)
    }

    override fun store(
        bucket: StorageBucket,
        path: String,
        content: ByteArray,
        mimeType: String,
    ): StoredObject {
        val targetFile = resolveTargetFile(bucket, path)
        Files.createDirectories(targetFile.parent)
        Files.write(targetFile, content)

        val checksum = computeSha256(content)
        val url = "$publicBaseUrl/${bucket.logicalName}/$path"

        log.debug("Stored {} bytes to bucket {} at path {}", content.size, bucket, path)
        return StoredObject(
            bucket = bucket,
            path = path,
            fileSizeBytes = content.size.toLong(),
            mimeType = mimeType,
            checksumSha256 = checksum,
            url = url,
            lastModified = Instant.now(),
        )
    }

    override fun read(bucket: StorageBucket, path: String): ByteArray? {
        val targetFile = resolveTargetFile(bucket, path)
        if (!Files.exists(targetFile) || !Files.isRegularFile(targetFile)) {
            return null
        }
        return Files.readAllBytes(targetFile)
    }

    override fun delete(bucket: StorageBucket, path: String): Boolean {
        val targetFile = resolveTargetFile(bucket, path)
        if (!Files.exists(targetFile)) {
            return false
        }
        return Files.deleteIfExists(targetFile)
    }

    override fun getPresignedUploadUrl(
        bucket: StorageBucket,
        path: String,
        mimeType: String,
        ttlSeconds: Long,
    ): String {
        // For local development, pre-signed upload URL routes to the local upload endpoint
        val expiresAt = Instant.now().epochSecond + ttlSeconds
        return "$publicBaseUrl/${bucket.logicalName}/$path?action=upload&expires=$expiresAt"
    }

    override fun getPresignedDownloadUrl(
        bucket: StorageBucket,
        path: String,
        ttlSeconds: Long,
    ): String {
        val expiresAt = Instant.now().epochSecond + ttlSeconds
        return "$publicBaseUrl/${bucket.logicalName}/$path?expires=$expiresAt"
    }

    private fun resolveTargetFile(bucket: StorageBucket, path: String): Path {
        val sanitized = path.replace("\\", "/").trimStart('/')
        val target = rootPath.resolve(bucket.logicalName).resolve(sanitized).normalize()
        if (!target.startsWith(rootPath)) {
            throw IllegalArgumentException("Path traversal attempt detected: $path")
        }
        return target
    }

    private fun computeSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
