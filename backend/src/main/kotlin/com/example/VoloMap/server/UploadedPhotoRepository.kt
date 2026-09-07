package com.example.VoloMap.server

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UploadedPhotoRepository : JpaRepository<UploadedPhoto, Long> {
    fun findByOwner(owner: User): List<UploadedPhoto>
    fun findByStoredFilename(storedFilename: String): UploadedPhoto?
}
