/**
 * Tenant-defined fields and the forms that render them — the config module's published API.
 *
 * <p>Spring Modulith publishes a module's <em>root</em> package only. {@code com.hr.config.forms}
 * is a sub-package, so without this declaration every type in it is private to {@code config} and
 * the employee module cannot validate a custom field value.
 *
 * <p>A {@code @NamedInterface} rather than flattening these types into {@code com.hr.config}: the
 * config module will grow to own approval flows, numbering schemes and notification templates as
 * well, and a single root package holding all four published surfaces tells a caller nothing about
 * which part of the module it has coupled itself to. Named interfaces make that visible in the
 * dependency graph.
 *
 * <p>What is published here is deliberately narrow — {@code CustomFields} and the form schema wire
 * types. The field definitions themselves, and the repository behind them, stay under
 * {@code internal} so that no other module can interpret a validation rule its own way.
 */
@org.springframework.modulith.NamedInterface("forms")
package com.hr.config.forms;
