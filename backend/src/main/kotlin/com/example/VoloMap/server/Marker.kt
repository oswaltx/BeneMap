package com.example.VoloMap.server

import java.time.LocalDateTime

data class Marker(
    val id: Long,
    val lat: Double,
    val lng: Double,
    val name: String,
    val address: String,
    val category: String,
    val description: String,
    val photoUrls: List<String>,
    val dateTime: LocalDateTime?,
    val activityRating: Double?,
    val activityRatingCount: Int,
    val providerId: Long?,
    val providerName: String?,
    val providerRating: Double?,
    val providerRatingCount: Int,
)
