package com.hr.notification.internal.push

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class NotificationPushConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnProperty(name = ["hr.notification.fcm.project-id"])
    fun fcmPushGateway(
        @Value("\${hr.notification.fcm.project-id}") projectId: String,
        @Value("\${hr.notification.fcm.token:}") staticToken: String,
        objectMapper: ObjectMapper,
    ): PushGateway {
        log.info("Configuring FcmPushGateway for project {}", projectId)
        val tokenProvider = { staticToken.ifBlank { null } }
        return FcmPushGateway(
            projectId = projectId,
            accessTokenProvider = tokenProvider,
            objectMapper = objectMapper,
        )
    }

    @Bean
    @ConditionalOnMissingBean(PushGateway::class)
    fun mockPushGateway(): PushGateway {
        log.info("Using in-memory MockPushGateway for push notifications")
        return MockPushGateway()
    }
}
