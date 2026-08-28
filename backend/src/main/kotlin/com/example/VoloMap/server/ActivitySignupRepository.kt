package com.example.VoloMap.server

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ActivitySignupRepository : JpaRepository<ActivitySignup, Long> {
    fun findByUserAndActivity(user: User, activity: VolunteerActivity): ActivitySignup?
    fun findByActivity(activity: VolunteerActivity): List<ActivitySignup>
    fun countByActivity(activity: VolunteerActivity): Long
}
