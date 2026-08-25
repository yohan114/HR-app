package com.hr

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic

/**
 * Application entry point.
 *
 * The system is a **modular monolith**: each direct sub-package of `com.hr` is a Spring Modulith
 * application module with an enforced boundary. Modules may only depend on another module's
 * root package (its published API) — never on its `internal` sub-packages. This is verified at
 * build time by `ModuleStructureTest`.
 *
 * See docs/adr/0001-modular-monolith.md
 */
@Modulithic(
    systemName = "HR Platform",
    sharedModules = ["shared"],
)
@SpringBootApplication
class HrBackendApplication

fun main(args: Array<String>) {
    runApplication<HrBackendApplication>(*args)
}
