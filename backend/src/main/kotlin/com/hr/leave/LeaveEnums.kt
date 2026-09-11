package com.hr.leave

/**
 * Method determining how leave is credited to an employee.
 */
enum class AccrualMethod {
    /**
     * Total annual entitlement credited upfront at the start of the leave year
     * (pro-rated if joining mid-year).
     */
    ANNUAL_UPFRONT,

    /**
     * Fixed rate credited periodically per month of service (e.g. 1.16 days/month).
     */
    MONTHLY,

    /**
     * Leave earned dynamically based on verified worked attendance days in the period.
     */
    PER_WORKED_DAY,

    /**
     * Entitlement tiered by employee completed years of service (e.g. 0-2 yrs: 14d, 3-5 yrs: 18d, 5+ yrs: 21d).
     */
    SERVICE_SLAB,

    /**
     * Earned in arrears based on prior period qualification.
     */
    EARNED,
}

enum class LeaveYearStatus {
    ACTIVE,
    CLOSED,
}

/**
 * Immutable transaction type recorded in the leave ledger.
 */
enum class LedgerEntryType {
    OPENING,
    ACCRUAL,
    TAKEN,
    CANCELLED,
    ADJUSTMENT,
    CARRY_FORWARD,
    ENCASHMENT,
    EXPIRY,
}

/**
 * Portion of the day requested for leave.
 */
enum class DayPortion {
    FULL_DAY,
    FIRST_HALF,
    SECOND_HALF,
}

/**
 * Lifecycle state machine of an employee leave application.
 */
enum class ApplicationStatus {
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED,
    WITHDRAWN,
}

