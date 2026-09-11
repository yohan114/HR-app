package com.hr.attendance

/**
 * Classification of shift operational models.
 */
enum class ShiftType {
    FIXED,
    ROTATING,
    SPLIT,
    NIGHT,
    FLEXIBLE,
    OPEN,
}

/**
 * Daily attendance outcome for an employee on a specific calendar work date.
 */
enum class DayStatus {
    PRESENT,
    HALF_DAY,
    ABSENT,
    ON_LEAVE,
    REST_DAY,
    HOLIDAY,
    NO_SHOW,
}

/**
 * Directional intent of a clock event.
 */
enum class PunchType {
    IN,
    OUT,
    BREAK_IN,
    BREAK_OUT,
    AUTO,
}

/**
 * Originating channel or hardware source of a clock event.
 */
enum class PunchSource {
    BIOMETRIC_DEVICE,
    MOBILE_APP,
    WEB_PORTAL,
    KIOSK,
    MANUAL_IMPORT,
}

/**
 * Spatial verification status against assigned job location or branch geofence.
 */
enum class GeofenceStatus {
    INSIDE,
    OUTSIDE,
    UNKNOWN,
    NOT_APPLICABLE,
}

/**
 * Location enforcement policy on mobile punch acquisition.
 */
enum class LocationCapturePolicy {
    OFF,
    OPTIONAL,
    REQUIRED,
}

/**
 * Action taken when mobile punch occurs outside the designated geofence.
 */
enum class GeofenceEnforcementPolicy {
    OFF,
    WARN,
    BLOCK,
}

/**
 * Action taken when mock-location / spoofed GPS provider is detected.
 */
enum class MockLocationAction {
    IGNORE,
    FLAG,
    BLOCK,
}

/**
 * Strategy for computing salary deductions on late arrivals.
 */
enum class LatenessPenaltyTier {
    PER_MINUTE,
    TIERED_BRACKETS,
    FLAT_DEDUCTION,
}

/**
 * Originating source of an employee shift schedule row.
 */
enum class ScheduleSource {
    DEFAULT_SHIFT,
    ROSTER,
    MANUAL,
    SWAP,
}
