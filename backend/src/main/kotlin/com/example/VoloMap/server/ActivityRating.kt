package com.example.VoloMap.server

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "activity_ratings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "activity_id"])]
)
class ActivityRating(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    var activity: VolunteerActivity,

    var stars: Int,

    @Column(columnDefinition = "TEXT")
    var comment: String? = null,

    var createdAt: Instant = Instant.now(),
)
