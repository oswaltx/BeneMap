package com.example.VoloMap.server

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FavoriteRepository : JpaRepository<Favorite, Long> {
    fun findByUserAndActivity(user: User, activity: VolunteerActivity): Favorite?
    fun findByUser(user: User): List<Favorite>
    fun deleteByActivity(activity: VolunteerActivity)
}
