package com.hr.attendance.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface BiometricDeviceRepository : JpaRepository<BiometricDevice, UUID> {
    fun findBySerialNumber(serialNumber: String): Optional<BiometricDevice>
    fun existsBySerialNumber(serialNumber: String): Boolean
    fun findAllByStatus(status: BiometricDeviceStatus): List<BiometricDevice>
}

@Repository
interface BiometricDeviceCommandRepository : JpaRepository<BiometricDeviceCommand, UUID> {
    fun findAllByDeviceIdAndStatusOrderByCreatedAtAsc(deviceId: UUID, status: String): List<BiometricDeviceCommand>
}
