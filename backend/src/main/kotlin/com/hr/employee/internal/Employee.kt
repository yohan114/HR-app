package com.hr.employee.internal

import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate
import java.util.UUID

/**
 * An employee.
 *
 * The centre of the product — every other module references this.
 *
 * ## What is deliberately absent
 *
 * **Salary.** It lives in `employee_salary` (Phase 3), effective-dated, so that
 * "what did they earn in March?" is answerable. A single mutable column here
 * cannot answer that, and would quietly make historical payroll runs
 * unreproducible — which is a compliance problem, not just an engineering one.
 *
 * **JPA associations.** Relationships are raw ids. `@ManyToOne` would make a
 * profile read a candidate for lazy-loading through department → company →
 * parent company, and the resulting N+1 is the most common cause of a slow page
 * in a JPA application. The API layer batches the handful of names it needs.
 */
@Entity
@Table(name = "employee")
class Employee(
    @Column(name = "employee_code", nullable = false, length = 64)
    var employeeCode: String,
    @Column(name = "company_id", nullable = false)
    var companyId: UUID,
    @Column(name = "first_name", nullable = false, length = 128)
    var firstName: String,
    @Column(name = "last_name", nullable = false, length = 128)
    var lastName: String,
    /**
     * Stored rather than derived from first and last name.
     *
     * Naming order differs by market, and several of our target countries use a
     * mononym or a patronymic that does not reconstruct from a first/last pair.
     * Deriving it would render some people's names wrong, which is not a
     * cosmetic defect.
     */
    @Column(name = "display_name", nullable = false)
    var displayName: String,
    @Column(name = "join_date", nullable = false)
    var joinDate: LocalDate,
) : TenantScopedEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: EmployeeStatus = EmployeeStatus.ACTIVE

    // --- Personal ----------------------------------------------------------

    @Column(name = "title_id")
    var titleId: UUID? = null

    @Column(name = "middle_name", length = 128)
    var middleName: String? = null

    @Column(name = "preferred_name", length = 128)
    var preferredName: String? = null

    @Column(name = "date_of_birth")
    var dateOfBirth: LocalDate? = null

    @Column(name = "gender_type_id")
    var genderTypeId: UUID? = null

    @Column(name = "marital_status_id")
    var maritalStatusId: UUID? = null

    @Column(name = "blood_group_id")
    var bloodGroupId: UUID? = null

    @Column(name = "nationality_id")
    var nationalityId: UUID? = null

    @Column(name = "religion_id")
    var religionId: UUID? = null

    @Column(name = "race_id")
    var raceId: UUID? = null

    /** Encrypted at the application layer. Never returned by the directory. */
    @Column(name = "national_id_enc")
    var nationalIdEnc: String? = null

    @Column(name = "photo_key", length = 512)
    var photoKey: String? = null

    // --- Employment --------------------------------------------------------

    @Column(name = "confirmation_date")
    var confirmationDate: LocalDate? = null

    @Column(name = "probation_end_date")
    var probationEndDate: LocalDate? = null

    @Column(name = "resign_date")
    var resignDate: LocalDate? = null

    @Column(name = "last_working_date")
    var lastWorkingDate: LocalDate? = null

    @Column(name = "employment_type_id")
    var employmentTypeId: UUID? = null

    @Column(name = "employee_category_id")
    var employeeCategoryId: UUID? = null

    @Column(name = "employee_group_id")
    var employeeGroupId: UUID? = null

    @Column(name = "statutory_classification_id")
    var statutoryClassificationId: UUID? = null

    // --- Workstation -------------------------------------------------------

    @Column(name = "department_id")
    var departmentId: UUID? = null

    @Column(name = "designation_id")
    var designationId: UUID? = null

    @Column(name = "salary_grade_id")
    var salaryGradeId: UUID? = null

    @Column(name = "corporate_title_id")
    var corporateTitleId: UUID? = null

    @Column(name = "location_id")
    var locationId: UUID? = null

    @Column(name = "cost_centre_id")
    var costCentreId: UUID? = null

    @Column(name = "function_id")
    var functionId: UUID? = null

    /**
     * The solid reporting line.
     *
     * Approval routing follows this and only this. An ambiguous approver is how
     * requests sit unactioned for a week while two managers each assume the
     * other has it.
     */
    @Column(name = "supervisor_id")
    var supervisorId: UUID? = null

    /** Matrix reporting. Informational — never used for approval routing. */
    @Column(name = "dotted_line_supervisor_id")
    var dottedLineSupervisorId: UUID? = null

    // --- Contact -----------------------------------------------------------

    @Column(name = "personal_email", length = 320)
    var personalEmail: String? = null

    @Column(name = "work_email", length = 320)
    var workEmail: String? = null

    @Column(name = "mobile", length = 32)
    var mobile: String? = null

    @Column(name = "work_phone", length = 32)
    var workPhone: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permanent_address", columnDefinition = "jsonb", nullable = false)
    var permanentAddress: MutableMap<String, Any?> = mutableMapOf()

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_address", columnDefinition = "jsonb", nullable = false)
    var currentAddress: MutableMap<String, Any?> = mutableMapOf()

    // --- Custom fields -----------------------------------------------------

    /**
     * Tenant-defined values, keyed by `field_definition.field_key`.
     *
     * Validated against the definitions on write. Reading an unknown key is
     * tolerated — a field removed from the definitions leaves its values
     * behind rather than destroying data on a configuration change.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb", nullable = false)
    var customFields: MutableMap<String, Any?> = mutableMapOf()

    // --- Derived -----------------------------------------------------------

    /** Whether the employee is currently employed, regardless of leave or suspension. */
    val isEmployed: Boolean
        get() = status != EmployeeStatus.EXITED && status != EmployeeStatus.PENDING_JOIN

    /**
     * Completed years of service as at [asOf].
     *
     * Uses `last_working_date` when set, so a leaver's tenure stops accruing.
     * Drives service-based leave entitlement slabs and gratuity calculations,
     * both of which are money — hence the explicit end date rather than "now".
     */
    fun yearsOfService(asOf: LocalDate = LocalDate.now()): Int {
        val end = lastWorkingDate?.takeIf { it.isBefore(asOf) } ?: asOf
        if (end.isBefore(joinDate)) return 0
        return java.time.Period.between(joinDate, end).years
    }
}

enum class EmployeeStatus {
    /** Not yet started. Visible to HR, absent from the directory. */
    PENDING_JOIN,
    PROBATION,
    ACTIVE,
    SUSPENDED,

    /** Resigned or terminated, still working out notice. */
    ON_NOTICE,
    EXITED,
}
