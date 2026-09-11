package com.hr.training.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TrainingProviderRepository : JpaRepository<TrainingProviderEntity, UUID>

@Repository
interface ResourcePersonRepository : JpaRepository<ResourcePersonEntity, UUID>

@Repository
interface TrainingCourseRepository : JpaRepository<TrainingCourseEntity, UUID> {
    fun findByIsActiveTrue(): List<TrainingCourseEntity>
    fun findByCategoryAndIsActiveTrue(category: String): List<TrainingCourseEntity>
    fun findByCompetencyIdAndIsActiveTrue(competencyId: UUID): List<TrainingCourseEntity>
}

@Repository
interface TrainingScheduleRepository : JpaRepository<TrainingScheduleEntity, UUID> {
    fun findByCourseId(courseId: UUID): List<TrainingScheduleEntity>
    fun findByStatus(status: String): List<TrainingScheduleEntity>
    fun findByCourseIdAndStatus(courseId: UUID, status: String): List<TrainingScheduleEntity>
}

@Repository
interface TrainingEnrollmentRepository : JpaRepository<TrainingEnrollmentEntity, UUID> {
    fun findByEmployeeId(employeeId: UUID): List<TrainingEnrollmentEntity>
    fun findByScheduleId(scheduleId: UUID): List<TrainingEnrollmentEntity>
    fun findByStatus(status: String): List<TrainingEnrollmentEntity>
    fun findByEmployeeIdAndStatus(employeeId: UUID, status: String): List<TrainingEnrollmentEntity>
    fun findByScheduleIdAndEmployeeId(scheduleId: UUID, employeeId: UUID): TrainingEnrollmentEntity?
}

@Repository
interface TrainingAttendanceRepository : JpaRepository<TrainingAttendanceEntity, UUID> {
    fun findByEnrollmentId(enrollmentId: UUID): List<TrainingAttendanceEntity>
}

@Repository
interface TraineeEvaluationRepository : JpaRepository<TraineeEvaluationEntity, UUID> {
    fun findByEnrollmentId(enrollmentId: UUID): TraineeEvaluationEntity?
}

@Repository
interface TrainingCertificateRepository : JpaRepository<TrainingCertificateEntity, UUID> {
    fun findByEnrollmentId(enrollmentId: UUID): TrainingCertificateEntity?
    fun findByEnrollmentIdIn(enrollmentIds: Collection<UUID>): List<TrainingCertificateEntity>
}
