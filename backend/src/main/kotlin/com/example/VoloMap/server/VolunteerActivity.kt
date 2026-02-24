package com.example.demo.server

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id


@Entity // This tells Hibernate to make a table out of this class
data class VolunteerActivity (
    // data class = custom datatype for volunteer activities
    // comes with some nice functions like copy, equals, hashCode etc.
    @Id // = primary key; every entity needs a primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // generates a unique id for the entity
    val id: Long = 0,
    val name: String = "",
    val latitude: Double,
    val longitude: Double,
)