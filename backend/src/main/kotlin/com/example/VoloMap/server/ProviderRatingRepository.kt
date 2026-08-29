package com.example.VoloMap.server

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProviderRatingRepository : JpaRepository<ProviderRating, Long> {
    fun findByUserAndProvider(user: User, provider: User): ProviderRating?
    fun findByProvider(provider: User): List<ProviderRating>
    fun findByUser(user: User): List<ProviderRating>
}
