package com.example.VoloMap.server

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ActivityRatingRepository : JpaRepository<ActivityRating, Long> {
    fun findByUserAndActivity(user: User, activity: VolunteerActivity): ActivityRating?
    fun findByActivity(activity: VolunteerActivity): List<ActivityRating>
    fun findByUser(user: User): List<ActivityRating>
}
