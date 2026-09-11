package com.hr.shared.storage

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@DisplayName("Object Storage Service (Local File System & S3 Presigning)")
class ObjectStorageServiceTest {

    @Test
    fun `stores, reads, generates presigned urls, and deletes objects in local storage`(@TempDir tempDir: Path) {
        val storage = LocalFileSystemStorageService(
            baseDirectoryPath = tempDir.toString(),
            publicBaseUrl = "http://localhost:8080/v1/storage",
        )

        val fileContent = "HR Compliance Document Content v1".toByteArray(Charsets.UTF_8)
        val path = "contracts/2026/employment-agreement.pdf"

        // 1. Store
        val stored = storage.store(
            bucket = StorageBucket.DOCUMENTS,
            path = path,
            content = fileContent,
            mimeType = "application/pdf",
        )

        assertThat(stored.bucket).isEqualTo(StorageBucket.DOCUMENTS)
        assertThat(stored.fileSizeBytes).isEqualTo(fileContent.size.toLong())
        assertThat(stored.mimeType).isEqualTo("application/pdf")
        assertThat(stored.checksumSha256).isNotNull()
        assertThat(stored.url).contains("http://localhost:8080/v1/storage/documents/contracts/2026/employment-agreement.pdf")

        // 2. Read
        val readBack = storage.read(StorageBucket.DOCUMENTS, path)
        assertThat(readBack).isNotNull()
        assertThat(String(readBack!!, Charsets.UTF_8)).isEqualTo("HR Compliance Document Content v1")

        // 3. Pre-signed upload & download URLs
        val uploadUrl = storage.getPresignedUploadUrl(StorageBucket.ATTACHMENTS, "receipts/rec-01.jpg", "image/jpeg", 1800)
        assertThat(uploadUrl).contains("action=upload")
        assertThat(uploadUrl).contains("expires=")

        val downloadUrl = storage.getPresignedDownloadUrl(StorageBucket.DOCUMENTS, path, 1800)
        assertThat(downloadUrl).contains(path)
        assertThat(downloadUrl).contains("expires=")

        // 4. Delete
        val deleted = storage.delete(StorageBucket.DOCUMENTS, path)
        assertThat(deleted).isTrue()

        val readAfterDelete = storage.read(StorageBucket.DOCUMENTS, path)
        assertThat(readAfterDelete).isNull()
    }

    @Test
    fun `generates valid SigV4 presigned urls for S3`() {
        val s3 = S3ObjectStorageService(
            awsRegion = "eu-central-1",
            bucketNames = mapOf(
                StorageBucket.DOCUMENTS to "production-hr-documents",
                StorageBucket.PAYSLIPS to "production-hr-payslips",
            ),
            accessKeyId = "AKIAIOSFODNN7EXAMPLE",
            secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
        )

        val uploadUrl = s3.getPresignedUploadUrl(
            bucket = StorageBucket.DOCUMENTS,
            path = "letters/offer-101.pdf",
            mimeType = "application/pdf",
        )

        assertThat(uploadUrl).startsWith("https://production-hr-documents.s3.eu-central-1.amazonaws.com/letters/offer-101.pdf?")
        assertThat(uploadUrl).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
        assertThat(uploadUrl).contains("X-Amz-Signature=")

        val downloadUrl = s3.getPresignedDownloadUrl(
            bucket = StorageBucket.PAYSLIPS,
            path = "2026-08/payslip-e001.pdf",
        )

        assertThat(downloadUrl).startsWith("https://production-hr-payslips.s3.eu-central-1.amazonaws.com/2026-08/payslip-e001.pdf?")
        assertThat(downloadUrl).contains("X-Amz-Signature=")
    }
}
