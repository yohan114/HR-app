package com.hr

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import java.io.File
import java.lang.reflect.Field

/**
 * Every JPA entity maps to a table and columns that the migrations actually create.
 *
 * ## Why this exists
 *
 * Hibernate validates the mapping against the live schema — at application
 * startup, which needs PostgreSQL, which needs Docker. In an environment
 * without it the first time anybody learns that `@Column(name = "employe_code")`
 * has a typo is on a deployment.
 *
 * This closes that specific gap and nothing wider. It reads the *real*
 * annotations by reflection rather than parsing Kotlin, so the entity half is
 * exact; only the SQL half is parsed, and only for the two statements that
 * introduce columns.
 *
 * ## What it deliberately does not check
 *
 * Types, nullability and lengths. Matching Kotlin types against SQL types means
 * encoding Hibernate's dialect mapping, which is a large table of rules that
 * would itself be wrong in places — and being wrong here means failing correct
 * code, which is how a check gets deleted. Names are where the realistic
 * mistakes live: a typo, a rename applied on one side only, a column that was
 * planned and never migrated.
 */
@DisplayName("Entity/schema agreement")
class EntitySchemaTest {
    private val schema = MigrationSchema.parse(MIGRATION_DIR)

    @Test
    fun `every entity maps to a table that a migration creates`() {
        val softly = SoftAssertions()

        for (entity in entities()) {
            val table = tableName(entity)
            softly
                .assertThat(schema.tables.keys)
                .describedAs("${entity.simpleName} maps to table '$table'")
                .contains(table)
        }

        softly.assertAll()
    }

    @Test
    fun `every mapped column exists in the migrations`() {
        val softly = SoftAssertions()

        for (entity in entities()) {
            val table = tableName(entity)
            val columns = schema.tables[table] ?: continue

            for ((field, column) in mappedColumns(entity)) {
                softly
                    .assertThat(columns)
                    .describedAs("${entity.simpleName}.${field.name} maps to $table.$column")
                    .contains(column)
            }
        }

        softly.assertAll()
    }

    /**
     * A column the database requires but no entity sets will fail on the first
     * insert with a not-null violation.
     *
     * Audit and identity columns are excluded: `tenant_id` is populated by
     * `TenantScopedEntity` on persist, the `created_*`/`updated_*` pairs by
     * Spring Data auditing, and `version` by Hibernate — none through a mapped
     * field the reflection below can see.
     */
    @Test
    fun `every NOT NULL column without a default is mapped by its entity`() {
        val softly = SoftAssertions()

        val byTable = entities().groupBy { tableName(it) }

        for ((table, required) in schema.requiredColumns) {
            val owners = byTable[table] ?: continue
            val mapped = owners.flatMap { mappedColumns(it).map { (_, column) -> column } }.toSet()

            for (column in required - MANAGED_COLUMNS) {
                softly
                    .assertThat(mapped)
                    .describedAs(
                        "$table.$column is NOT NULL with no default, but ${owners.joinToString { it.simpleName }} " +
                            "does not map it — the first insert would fail",
                    )
                    .contains(column)
            }
        }

        softly.assertAll()
    }

    /**
     * Raw `INSERT` statements name real tables and real columns.
     *
     * JPA entities are checked above by reflection; hand-written SQL is not
     * checked by anything. `LocalDemoSeeder` is nine inserts across seven
     * tables, none of which can run in this environment, and a wrong column
     * name there fails at application startup with a stack trace pointing at a
     * string literal.
     *
     * Only the column list is verified — the `VALUES` side would require
     * understanding types, which is the same trap the type checking above
     * avoids.
     */
    @Test
    fun `raw SQL inserts reference columns that exist`() {
        val softly = SoftAssertions()
        var statements = 0

        for (source in kotlinSources()) {
            val text = source.readText()
            for (match in INSERT_STATEMENT.findAll(text)) {
                val table = match.groupValues[1].lowercase()
                val columns =
                    match.groupValues[2]
                        .split(',')
                        .map { it.trim().lowercase() }
                        .filter { it.isNotEmpty() }
                statements++

                val known = schema.tables[table]
                if (known == null) {
                    softly
                        .assertThat(schema.tables.keys)
                        .describedAs("${source.name}: INSERT INTO $table")
                        .contains(table)
                    continue
                }
                for (column in columns) {
                    softly
                        .assertThat(known)
                        .describedAs("${source.name}: INSERT INTO $table references $table.$column")
                        .contains(column)
                }
            }
        }

        // Without this, deleting every insert would make the test pass loudly.
        assertThat(statements).describedAs("raw INSERT statements found in main sources").isGreaterThan(0)
        softly.assertAll()
    }

    /** Guards the parser itself: a silent parse failure would make every assertion above vacuous. */
    @Test
    fun `the migration parser found a plausible schema`() {
        assertThat(schema.tables).describedAs("tables parsed from $MIGRATION_DIR").hasSizeGreaterThan(30)
        assertThat(schema.tables["employee"])
            .describedAs("employee columns")
            .contains("id", "tenant_id", "employee_code", "display_name", "join_date", "custom_fields")
        assertThat(entities()).describedAs("entities discovered on the classpath").hasSizeGreaterThan(3)
    }

    // ------------------------------------------------------------------------

    private fun kotlinSources(): List<File> =
        File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

    private fun entities(): List<Class<*>> {
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AnnotationTypeFilter(Entity::class.java))
        return scanner
            .findCandidateComponents("com.hr")
            .mapNotNull { it.beanClassName }
            .sorted()
            .map { Class.forName(it) }
    }

    private fun tableName(entity: Class<*>): String =
        entity.getAnnotation(Table::class.java)?.name?.takeIf { it.isNotBlank() }
            // JPA's default is the entity name; Spring Boot's default naming
            // strategy then converts it to snake_case.
            ?: entity.simpleName.replace(CAMEL_BOUNDARY, "_$1").lowercase()

    /**
     * Mapped persistent fields, including those inherited from
     * `BaseEntity`/`TenantScopedEntity`.
     */
    private fun mappedColumns(entity: Class<*>): List<Pair<Field, String>> {
        val fields = mutableListOf<Field>()
        var current: Class<*>? = entity
        while (current != null && current != Any::class.java) {
            fields += current.declaredFields
            current = current.superclass
        }

        return fields
            .asSequence()
            .filter { !it.isSynthetic }
            .filter { it.getAnnotation(Transient::class.java) == null }
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .mapNotNull { field ->
                val explicit =
                    field.getAnnotation(Column::class.java)?.name?.takeIf { it.isNotBlank() }
                        ?: field.getAnnotation(JoinColumn::class.java)?.name?.takeIf { it.isNotBlank() }
                // Only explicitly named columns are checked. An unannotated
                // Kotlin property may be a computed value, a delegate, or a
                // backing field for something not persisted at all, and guessing
                // which would produce failures on correct code.
                explicit?.let { field to it.lowercase() }
            }
            .toList()
    }

    private companion object {
        val MIGRATION_DIR = File("src/main/resources/db/migration")
        val CAMEL_BOUNDARY = Regex("(?<=[a-z0-9])([A-Z])")

        /**
         * `INSERT INTO table (a, b, c)` across newlines.
         *
         * Column lists in this codebase wrap, so `DOT_MATCHES_ALL` is required;
         * the `[^)]` class then stops it running past the closing bracket.
         */
        val INSERT_STATEMENT =
            Regex(
                "INSERT\\s+INTO\\s+([a-z_][a-z0-9_]*)\\s*\\(([^)]*)\\)",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )

        /**
         * Columns populated by the framework rather than by a mapped field.
         *
         * `tenant_id` by `TenantScopedEntity` on persist, the audit pairs by
         * Spring Data auditing, `version` by Hibernate's optimistic locking.
         */
        val MANAGED_COLUMNS =
            setOf("tenant_id", "created_at", "created_by", "updated_at", "updated_by", "version")
    }
}

/**
 * The table and column names the migrations create.
 *
 * Deliberately narrow: it reads `CREATE TABLE` bodies, `ALTER TABLE ... ADD
 * COLUMN`, and the reference-taxonomy generator's call sites. Anything it
 * cannot understand it ignores, because a parser that guesses produces failures
 * on correct code — and the response to that is always to delete the check.
 */
internal class MigrationSchema(
    val tables: Map<String, Set<String>>,
    /** Columns declared NOT NULL with no DEFAULT — the ones an insert must supply. */
    val requiredColumns: Map<String, Set<String>>,
) {
    companion object {
        /**
         * Columns of a table made by `create_reference_table()` in V5.
         *
         * Mirrored from the generator body. `MigrationSchemaParserTest` asserts
         * this list still matches what the function declares, so a change to the
         * generator fails there rather than silently here.
         */
        val REFERENCE_TABLE_COLUMNS =
            setOf(
                "id", "tenant_id", "code", "name", "description", "sequence", "active",
                "created_at", "created_by", "updated_at", "updated_by", "version",
            )

        fun parse(directory: File): MigrationSchema {
            val tables = mutableMapOf<String, MutableSet<String>>()
            val required = mutableMapOf<String, MutableSet<String>>()

            val files =
                directory
                    .listFiles { f -> f.name.matches(Regex("^V\\d+__.+\\.sql$")) }
                    .orEmpty()
                    .sortedBy { it.name.drop(1).takeWhile(Char::isDigit).toInt() }

            for (file in files) {
                val raw = file.readText()
                val sql = strip(raw)

                for (match in CREATE_TABLE.findAll(sql)) {
                    val name = match.groupValues[1].lowercase()
                    val body = balancedFrom(sql, match.range.last) ?: continue
                    val columns = tables.getOrPut(name) { mutableSetOf() }
                    for (line in splitTopLevel(body)) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue
                        if (CONSTRAINT_START.containsMatchIn(trimmed)) continue
                        val column = trimmed.split(Regex("\\s+")).first().lowercase()
                        if (!column.matches(IDENTIFIER)) continue
                        columns += column
                        if (isRequired(trimmed)) required.getOrPut(name) { mutableSetOf() } += column
                    }
                }

                for (match in ADD_COLUMN.findAll(sql)) {
                    val name = match.groupValues[1].lowercase()
                    val column = match.groupValues[2].lowercase()
                    tables.getOrPut(name) { mutableSetOf() } += column
                    // The rest of the statement carries NOT NULL / DEFAULT just
                    // as a CREATE TABLE line does. Missing this was a real hole:
                    // "someone adds a required column in a later migration and
                    // forgets the entity" is the most likely version of this
                    // fault, and it is the one an ALTER produces.
                    if (isRequired(match.groupValues[3])) {
                        required.getOrPut(name) { mutableSetOf() } += column
                    }
                }

                // Tables produced by the V5 generator, which no regex over the
                // call site could describe on its own.
                for (match in REFERENCE_TABLE.findAll(raw.replace(LINE_COMMENT, " "))) {
                    tables.getOrPut(match.groupValues[1].lowercase()) { mutableSetOf() } += REFERENCE_TABLE_COLUMNS
                }
            }

            return MigrationSchema(tables, required)
        }

        private fun isRequired(columnDefinition: String): Boolean =
            NOT_NULL.containsMatchIn(columnDefinition) && !DEFAULT.containsMatchIn(columnDefinition)

        /**
         * Removes comments, string literals and function bodies.
         *
         * Without this a `tenant_id` mentioned in a `COMMENT ON` reads as a
         * column definition.
         */
        private fun strip(sql: String): String =
            sql
                .replace(Regex("\\$\\$[\\s\\S]*?\\$\\$"), " ")
                .replace(LINE_COMMENT, " ")
                .replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")
                .replace(Regex("'(?:[^']|'')*'"), "''")

        /** Splits a table body on commas that are not inside parentheses. */
        private fun splitTopLevel(body: String): List<String> {
            val parts = mutableListOf<String>()
            var depth = 0
            var start = 0
            body.forEachIndexed { i, c ->
                when (c) {
                    '(' -> depth++
                    ')' -> depth--
                    ',' -> if (depth == 0) {
                        parts += body.substring(start, i)
                        start = i + 1
                    }
                }
            }
            parts += body.substring(start)
            return parts
        }

        private fun balancedFrom(text: String, open: Int): String? {
            var depth = 0
            for (i in open until text.length) {
                when (text[i]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) return text.substring(open + 1, i)
                    }
                }
            }
            return null
        }

        private val CREATE_TABLE =
            Regex("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-z_][a-z0-9_]*)\\s*\\(", RegexOption.IGNORE_CASE)
        private val ADD_COLUMN =
            Regex(
                "ALTER\\s+TABLE\\s+([a-z_][a-z0-9_]*)\\s+ADD\\s+COLUMN\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?" +
                    "([a-z_][a-z0-9_]*)([^;]*)",
                RegexOption.IGNORE_CASE,
            )
        private val REFERENCE_TABLE =
            Regex("create_reference_table\\s*\\(\\s*'([a-z_][a-z0-9_]*)'", RegexOption.IGNORE_CASE)
        private val CONSTRAINT_START =
            Regex("^(PRIMARY|FOREIGN|UNIQUE|CHECK|CONSTRAINT|EXCLUDE|LIKE)\\b", RegexOption.IGNORE_CASE)
        private val NOT_NULL = Regex("\\bNOT\\s+NULL\\b", RegexOption.IGNORE_CASE)
        private val DEFAULT = Regex("\\bDEFAULT\\b", RegexOption.IGNORE_CASE)
        private val LINE_COMMENT = Regex("--[^\\n]*")
        private val IDENTIFIER = Regex("[a-z_][a-z0-9_]*")
    }
}
