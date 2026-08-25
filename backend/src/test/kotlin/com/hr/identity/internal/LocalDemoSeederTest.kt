package com.hr.identity.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The demo workforce is internally consistent.
 *
 * Seed data is dev-only, so a bug here costs a developer an hour rather than a
 * customer anything — but it costs that hour at the worst moment, when someone
 * is trying to get the application running for the first time and has no basis
 * yet for telling a seed bug from a real one.
 *
 * These assertions are about the *shape* of the data, which is what breaks when
 * somebody adds a tenth person: a supervisor code that does not exist, a
 * duplicate employee code, a reporting cycle. None of those are visible by
 * reading the list, and all of them fail at startup with an error pointing
 * somewhere unhelpful.
 */
@DisplayName("Demo seed data")
class LocalDemoSeederTest {
    private val workforce = LocalDemoSeeder().workforce

    @Test
    fun `every supervisor code refers to somebody in the list`() {
        val codes = workforce.map { it.code }.toSet()

        assertThat(workforce.mapNotNull { it.supervisorCode })
            .describedAs("supervisor codes")
            .allSatisfy { assertThat(codes).contains(it) }
    }

    @Test
    fun `employee codes are unique`() {
        assertThat(workforce.map { it.code }).doesNotHaveDuplicates()
    }

    /**
     * A supervisor must appear before their reports, because
     * `seedEmployees` inserts in declaration order. The hierarchy trigger would
     * recover from an out-of-order insert by cascading, but relying on that
     * would make the seed depend on behaviour nothing else depends on.
     */
    @Test
    fun `a supervisor is always declared before their reports`() {
        val position = workforce.withIndex().associate { (i, p) -> p.code to i }

        workforce.forEach { person ->
            person.supervisorCode?.let { supervisor ->
                assertThat(position.getValue(supervisor))
                    .describedAs("${person.code} reports to $supervisor, which must be declared earlier")
                    .isLessThan(position.getValue(person.code))
            }
        }
    }

    /**
     * `V6` rejects a reporting cycle at write time with a `check_violation`,
     * because a cycle makes the hierarchy rebuild recurse forever. Seed data
     * that trips that check turns a first run into a debugging session.
     */
    @Test
    fun `the reporting structure has no cycles`() {
        val supervisorOf = workforce.associate { it.code to it.supervisorCode }

        workforce.forEach { person ->
            val seen = mutableSetOf(person.code)
            var current = supervisorOf[person.code]
            while (current != null) {
                assertThat(seen)
                    .describedAs("reporting chain from ${person.code} revisits $current")
                    .doesNotContain(current)
                seen += current
                current = supervisorOf[current]
            }
        }
    }

    @Test
    fun `exactly one person has no supervisor`() {
        assertThat(workforce.filter { it.supervisorCode == null })
            .describedAs("roots of the reporting tree")
            .hasSize(1)
    }

    /**
     * The reason the demo tree is three deep rather than two: "my team" means
     * the whole subtree, and a department head who cannot open the record of
     * someone two levels down is the bug the ltree hierarchy exists to prevent.
     * A two-level tree cannot demonstrate that either way.
     */
    @Test
    fun `the reporting tree is at least three levels deep`() {
        val supervisorOf = workforce.associate { it.code to it.supervisorCode }

        val deepest =
            workforce.maxOf { person ->
                var depth = 0
                var current = supervisorOf[person.code]
                while (current != null) {
                    depth++
                    current = supervisorOf[current]
                }
                depth
            }

        assertThat(deepest).isGreaterThanOrEqualTo(2)
    }

    /**
     * Something must land today, or the milestone cards demo empty on the day
     * somebody first runs this — which is the whole reason the dates are
     * relative rather than fixed.
     */
    @Test
    fun `a birthday and a work anniversary both fall today`() {
        assertThat(workforce.filter { it.birthdayIn == 0L })
            .describedAs("birthdays today")
            .isNotEmpty()

        assertThat(workforce.filter { it.joinedDaysOffset == 0L })
            .describedAs("work anniversaries today")
            .isNotEmpty()
    }

    @Test
    fun `more milestones fall within the coming week`() {
        assertThat(workforce.count { it.birthdayIn != null && it.birthdayIn in 1..7 })
            .describedAs("birthdays in the next 7 days")
            .isGreaterThan(0)
    }

    /**
     * Someone has to have no date of birth. It is the field most likely to be
     * absent in a real import, and the rendering path for a missing one is
     * otherwise never exercised locally.
     */
    @Test
    fun `at least one person has no date of birth`() {
        assertThat(workforce.filter { it.birthdayIn == null }).isNotEmpty()
    }
}
