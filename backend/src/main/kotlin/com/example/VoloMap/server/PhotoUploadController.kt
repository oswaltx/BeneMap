package com.example.VoloMap.server

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

data class PhotoUploadResponse(val url: String, val sizeBytes: Long, val usedBytes: Long, val maxBytes: Long)
data class PhotoQuotaResponse(val usedBytes: Long, val maxBytes: Long)

@RestController
@RequestMapping("/uploads")
class PhotoUploadController(
    private val photoStorageService: PhotoStorageService,
    private val userRepository: UserRepository,
) {

    @PostMapping("/photos")
    fun upload(
        @RequestParam("file") file: MultipartFile,
        authentication: Authentication,
    ): ResponseEntity<*> {
        val user = userRepository.findByEmail(authentication.name)!!
        return when (val result = photoStorageService.upload(file, user)) {
            is PhotoUploadResult.Success -> ResponseEntity.ok(
                PhotoUploadResponse(
                    url = result.url,
                    sizeBytes = result.sizeBytes,
                    usedBytes = photoStorageService.usedBytes(user),
                    maxBytes = photoStorageService.maxAccountBytes(),
                )
            )
            is PhotoUploadResult.Rejected -> ResponseEntity.status(result.status).body(ErrorResponse(result.message))
        }
    }

    @GetMapping("/quota")
    fun quota(authentication: Authentication): ResponseEntity<PhotoQuotaResponse> {
        val user = userRepository.findByEmail(authentication.name)!!
        return ResponseEntity.ok(
            PhotoQuotaResponse(photoStorageService.usedBytes(user), photoStorageService.maxAccountBytes())
        )
    }

    @GetMapping("/photos/{filename}")
    fun serve(@PathVariable filename: String): ResponseEntity<ByteArray> {
        val bytes = photoStorageService.readFile(filename) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
            .body(bytes)
    }
}
