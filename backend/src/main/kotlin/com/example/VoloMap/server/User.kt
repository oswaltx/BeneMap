package com.example.VoloMap.server

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class Role { ANBIETER, USER }

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(unique = true, nullable = false)
    var email: String,

    var passwordHash: String,

    var name: String,

    @Enumerated(EnumType.STRING)
    var role: Role,

    var createdAt: Instant = Instant.now(),
)
