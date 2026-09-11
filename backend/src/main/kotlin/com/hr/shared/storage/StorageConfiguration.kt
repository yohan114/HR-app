package com.hr.shared.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class StorageConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @Primary
    @ConditionalOnProperty(name = ["hr.storage.provider"], havingValue = "s3")
    fun s3ObjectStorageService(
        @Value("\${hr.storage.s3.region:us-east-1}") region: String,
        @Value("\${hr.storage.s3.buckets.documents:hr-documents}") documentsBucket: String,
        @Value("\${hr.storage.s3.buckets.payslips:hr-payslips}") payslipsBucket: String,
        @Value("\${hr.storage.s3.buckets.attachments:hr-attachments}") attachmentsBucket: String,
        @Value("\${hr.storage.s3.buckets.payroll-snapshots:hr-payroll-snapshots}") snapshotsBucket: String,
        @Value("\${AWS_ACCESS_KEY_ID:#{null}}") accessKey: String?,
        @Value("\${AWS_SECRET_ACCESS_KEY:#{null}}") secretKey: String?,
        @Value("\${hr.storage.s3.kms-key-arn:#{null}}") kmsKeyArn: String?,
    ): ObjectStorageService {
        log.info("Configuring S3ObjectStorageService in AWS region: {}", region)
        val bucketMap = mapOf(
            StorageBucket.DOCUMENTS to documentsBucket,
            StorageBucket.PAYSLIPS to payslipsBucket,
            StorageBucket.ATTACHMENTS to attachmentsBucket,
            StorageBucket.PAYROLL_SNAPSHOTS to snapshotsBucket,
        )
        return S3ObjectStorageService(
            awsRegion = region,
            bucketNames = bucketMap,
            accessKeyId = accessKey,
            secretAccessKey = secretKey,
            kmsKeyArn = kmsKeyArn,
        )
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = ["hr.storage.provider"], havingValue = "local", matchIfMissing = true)
    fun localFileSystemStorageService(
        @Value("\${hr.storage.local-path:.localdev/storage}") localPath: String,
        @Value("\${hr.storage.public-base-url:http://localhost:8080/v1/storage}") publicBaseUrl: String,
    ): ObjectStorageService {
        log.info("Configuring LocalFileSystemStorageService with storage path: {}", localPath)
        return LocalFileSystemStorageService(
            baseDirectoryPath = localPath,
            publicBaseUrl = publicBaseUrl,
        )
    }
}
