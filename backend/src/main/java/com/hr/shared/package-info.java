/**
 * Shared kernel.
 *
 * <p>Declared OPEN so that any module may depend on any of its sub-packages. This is the only
 * module allowed to be open — everything else exposes a narrow published API from its root
 * package and hides its implementation under {@code internal}.
 *
 * <p>Keep this module small. It holds cross-cutting primitives only: the API error envelope,
 * persistence base types, cursor pagination, and identifier generation. Business logic must
 * never live here.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Shared Kernel")
package com.hr.shared;
