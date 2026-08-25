package com.hr

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

/**
 * Enforces the module boundaries described in docs/03-architecture.md §2.
 *
 * Spring Modulith treats each direct sub-package of `com.hr` as a module. A module's root package
 * is its published API; anything under `internal` is private to it. This test fails the build if
 * one module reaches into another's internals, or if a dependency cycle appears between modules.
 *
 * This is the mechanism that keeps a modular monolith modular. Without it, "we'll be disciplined
 * about boundaries" lasts about six weeks — and by the time anyone notices, untangling it is a
 * quarter of work. Catching it at the pull request that introduces it costs five minutes.
 */
class ModuleStructureTest {
    private val modules = ApplicationModules.of(HrBackendApplication::class.java)

    @Test
    fun `module boundaries are respected`() {
        modules.verify()
    }

    @Test
    fun `module structure is printed for review`() {
        modules.forEach(::println)
    }

    /**
     * Regenerates the module documentation under `build/spring-modulith-docs`.
     *
     * Not an assertion — it produces the C4-style component diagrams and the module canvas that
     * go into the architecture docs, so they cannot drift from the code.
     */
    @Test
    fun `write module documentation`() {
        Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeModuleCanvases()
    }
}
