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
    val dateTime: LocalDateTime?
)