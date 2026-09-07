package com.example.VoloMap.server

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

// One row per file actually stored under uploads/photos — lets us compute a user's
// total storage usage and delete the right file from disk when a photo is removed,
// which a bare URL string on VolunteerActivity/User can't do on its own.
@Entity
@Table(name = "uploaded_photos")
class UploadedPhoto(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,

    @Column(nullable = false, unique = true)
    var storedFilename: String,

    @Column(nullable = false)
    var sizeBytes: Long,

    var createdAt: Instant = Instant.now(),
)
