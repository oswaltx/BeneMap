package com.example.VoloMap.server

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface VolunteerActivityRepository : JpaRepository<VolunteerActivity, Long> {
    // Spring generates the SQL automatically from the method name -> no SQL cod required
    fun existsBySourceUrl(sourceUrl: String): Boolean
}