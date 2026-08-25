package com.hr.identity.internal

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID

/**
 * Supplies the RSA keypair used to sign access tokens, and publishes the public half as a JWK set.
 *
 * ## Key material
 *
 * In deployed environments the keypair is injected as base64 PKCS#8 / X.509 from the secret store.
 * If no key is configured the provider generates an ephemeral pair at startup and logs a loud
 * warning — that is fine for a developer laptop and a test run, and catastrophic in production,
 * because every restart would invalidate every outstanding token and a multi-instance deployment
 * would have instances unable to verify each other's tokens.
 *
 * ## Why RSA rather than HMAC
 *
 * A shared secret would mean every service that needs to *verify* a token could also *mint* one.
 * With asymmetric signing the private key stays in the auth path and everything else — future
 * workers, the public API gateway, third-party integrations — verifies against a published JWK set
 * it can fetch without holding a credential.
 */
@Configuration
class JwtKeyProvider(
    @Value("\${hr.auth.jwt.private-key:}") private val privateKeyBase64: String,
    @Value("\${hr.auth.jwt.public-key:}") private val publicKeyBase64: String,
    @Value("\${hr.auth.jwt.key-id:}") private val configuredKeyId: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val keyId: String = configuredKeyId.ifBlank { UUID.randomUUID().toString() }

    private val keyPair: KeyPair = loadOrGenerate()

    @Bean
    fun rsaKey(): RSAKey =
        RSAKey.Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private as RSAPrivateKey)
            .keyID(keyId)
            .build()

    /**
     * The JWK set exposed at `/v1/auth/.well-known/jwks.json`.
     *
     * Returns a list so that key rotation can publish the old and new keys simultaneously:
     * during a rotation window, tokens signed with either key must still verify.
     */
    @Bean
    fun jwkSource(rsaKey: RSAKey): JWKSource<SecurityContext> = ImmutableJWKSet(JWKSet(rsaKey))

    @Bean
    fun jwtEncoder(jwkSource: JWKSource<SecurityContext>): JwtEncoder = NimbusJwtEncoder(jwkSource)

    /**
     * Decodes tokens using the in-process public key.
     *
     * Explicitly *not* configured via `jwk-set-uri`, which would make the application issue an
     * HTTP request to itself on the first token validation after startup — a needless dependency
     * on its own liveness, and a startup ordering hazard.
     */
    @Bean
    fun jwtDecoder(rsaKey: RSAKey): JwtDecoder =
        NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build()

    private fun loadOrGenerate(): KeyPair {
        if (privateKeyBase64.isNotBlank() && publicKeyBase64.isNotBlank()) {
            val factory = KeyFactory.getInstance("RSA")
            val private = factory.generatePrivate(PKCS8EncodedKeySpec(decode(privateKeyBase64)))
            val public = factory.generatePublic(X509EncodedKeySpec(decode(publicKeyBase64)))
            log.info("Loaded configured JWT signing key (kid={})", keyId)
            return KeyPair(public, private)
        }

        log.warn(
            "No JWT signing key configured — generating an ephemeral keypair (kid={}). " +
                "Every restart invalidates all outstanding tokens, and multiple instances will " +
                "not be able to verify each other's tokens. Acceptable locally; NEVER in a " +
                "deployed environment. Set hr.auth.jwt.private-key and hr.auth.jwt.public-key.",
            keyId,
        )
        return KeyPairGenerator.getInstance("RSA").apply { initialize(RSA_KEY_SIZE) }.generateKeyPair()
    }

    private fun decode(value: String): ByteArray =
        Base64.getDecoder().decode(
            value.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace(Regex("\\s"), ""),
        )

    private companion object {
        const val RSA_KEY_SIZE = 2048
    }
}
