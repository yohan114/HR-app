package com.hr.tenancy

import com.hr.shared.api.ApiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DisplayName("Tenant context")
class TenantContextTest {
    @AfterEach
    fun tearDown() = TenantContext.clear()

    @Test
    fun `require throws when no tenant is bound`() {
        assertThatThrownBy { TenantContext.require() }
            .isInstanceOf(ApiException::class.java)
            .hasMessageContaining("No tenant bound")
    }

    @Test
    fun `runAs binds for the duration and restores afterwards`() {
        val outer = handle("outer")
        val inner = handle("inner")

        TenantContext.set(outer)
        TenantContext.runAs(inner) {
            assertThat(TenantContext.currentId()).isEqualTo(inner.id)
        }
        assertThat(TenantContext.currentId()).isEqualTo(outer.id)
    }

    @Test
    fun `runAs restores the previous binding even when the block throws`() {
        val outer = handle("outer")
        TenantContext.set(outer)

        runCatching {
            TenantContext.runAs(handle("inner")) { error("boom") }
        }

        assertThat(TenantContext.currentId())
            .describedAs("A leaked binding would attribute the next operation to the wrong tenant")
            .isEqualTo(outer.id)
    }

    @Test
    fun `runWithoutTenant unbinds and restores`() {
        val bound = handle("bound")
        TenantContext.set(bound)

        TenantContext.runWithoutTenant {
            assertThat(TenantContext.currentIdOrNull()).isNull()
        }
        assertThat(TenantContext.currentId()).isEqualTo(bound.id)
    }

    @Test
    fun `runAs with no previous binding leaves nothing behind`() {
        TenantContext.runAs(handle("temp")) {
            assertThat(TenantContext.currentIdOrNull()).isNotNull()
        }
        assertThat(TenantContext.currentIdOrNull())
            .describedAs("Must clear rather than restore a null, or the ThreadLocal leaks into the next request on this thread")
            .isNull()
    }

    /**
     * Servlet containers pool threads. If the binding were shared rather than thread-local, one
     * request could observe another request's tenant — the exact failure this whole design exists
     * to prevent.
     */
    @Test
    fun `bindings are isolated between threads`() {
        val a = handle("a")
        val b = handle("b")
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)

        try {
            val futureA =
                pool.submit<UUID> {
                    TenantContext.set(a)
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    TenantContext.currentId()
                }
            val futureB =
                pool.submit<UUID> {
                    TenantContext.set(b)
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    TenantContext.currentId()
                }

            started.await(5, TimeUnit.SECONDS)
            release.countDown()

            assertThat(futureA.get()).isEqualTo(a.id)
            assertThat(futureB.get()).isEqualTo(b.id)
        } finally {
            pool.shutdown()
        }
    }

    private fun handle(code: String) =
        TenantHandle(
            id = UUID.randomUUID(),
            code = code,
            name = code,
            dataRegion = "default",
            defaultCurrency = "LKR",
            timezone = "Asia/Colombo",
            locale = "en",
            isolationTier = IsolationTier.SHARED,
            status = TenantStatus.ACTIVE,
        )
}
