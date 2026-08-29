package com.example.VoloMap.server

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface VolunteerActivityRepository : JpaRepository<VolunteerActivity, Long> {
    // Spring generates the SQL automatically from the method name -> no SQL cod required
    fun existsBySourceUrl(sourceUrl: String): Boolean

    fun findByCreatedBy(user: User): List<VolunteerActivity>

    // Pessimistic write lock so concurrent sign-up requests for the same activity are
    // serialized — prevents overbooking a maxParticipants-limited activity and prevents
    // a duplicate-signup race from hitting the DB unique constraint as an uncaught 500.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from VolunteerActivity a where a.id = :id")
    fun findByIdForUpdate(id: Long): VolunteerActivity?
}