package com.hr.app.ui.attendance

/**
 * Simulated GPS location coordinates and presets for testing and demonstrating
 * geofencing (inside vs outside) and mock location security checks.
 */
data class GeoSimulationPreset(
    val id: String,
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val isMock: Boolean,
) {
    companion object {
        val HQ_INSIDE = GeoSimulationPreset(
            id = "HQ_INSIDE",
            name = "Inside Office (45m)",
            description = "Colombo HQ Lobby (Within 200m boundary)",
            latitude = 6.9274,
            longitude = 79.8614,
            accuracyMeters = 8.0,
            isMock = false,
        )

        val OUTSIDE_GEOFENCE = GeoSimulationPreset(
            id = "OUTSIDE_GEOFENCE",
            name = "Outside Office (1.8km)",
            description = "Colombo 07 Town Hall (Beyond 200m boundary)",
            latitude = 6.9147,
            longitude = 79.8653,
            accuracyMeters = 16.0,
            isMock = false,
        )

        val MOCK_SPOOFED = GeoSimulationPreset(
            id = "MOCK_SPOOFED",
            name = "Mock GPS Spoof",
            description = "HQ Coordinates via Fake GPS App (Flagged for Security Audit)",
            latitude = 6.9271,
            longitude = 79.8612,
            accuracyMeters = 3.0,
            isMock = true,
        )

        val ALL = listOf(HQ_INSIDE, OUTSIDE_GEOFENCE, MOCK_SPOOFED)
    }
}
