package com.example.VoloMap.server

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Random
import javax.imageio.ImageIO

@SpringBootTest
class PhotoStorageServiceTest {

    @Autowired
    lateinit var uploadedPhotoRepository: UploadedPhotoRepository

    @Autowired
    lateinit var userRepository: UserRepository

    // @SpringBootTest classes share one database across the whole suite run (see the
    // comment in AuthControllerTest) — leftover UploadedPhoto rows referencing users
    // created here would block other classes' userRepository.deleteAll() with a FK
    // violation, so clean up everything this class touches after every test.
    @AfterEach
    fun cleanUp() {
        uploadedPhotoRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun newUser(email: String) = userRepository.save(
        User(email = email, passwordHash = "x", name = "Test", role = Role.ANBIETER)
    )

    // A smooth gradient with a bit of per-pixel jitter — realistic enough that JPEG
    // quality actually affects the encoded size (unlike a flat color, which would
    // compress to almost nothing at any quality and make size assertions meaningless),
    // while still being compressible enough to fit small test limits.
    private fun gradientJpegBytes(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val random = Random(42)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val r = (x * 255 / width + random.nextInt(16)).coerceIn(0, 255)
                val g = (y * 255 / height + random.nextInt(16)).coerceIn(0, 255)
                val b = ((x + y) * 255 / (width + height) + random.nextInt(16)).coerceIn(0, 255)
                image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", output)
        return output.toByteArray()
    }

    private fun serviceWithLimits(maxImageBytes: Long, maxAccountBytes: Long): PhotoStorageService {
        val tempDir = Files.createTempDirectory("photo-test")
        return PhotoStorageService(uploadedPhotoRepository, tempDir.toString(), maxImageBytes, maxAccountBytes)
    }

    @Test
    fun `compresses a large photo under the per-image limit`() {
        val service = serviceWithLimits(maxImageBytes = 60_000, maxAccountBytes = 10_000_000)
        val owner = newUser("photo-owner-1@example.com")
        val file = MockMultipartFile("file", "big.jpg", "image/jpeg", gradientJpegBytes(2400, 1600))

        val result = service.upload(file, owner)

        assertTrue(result is PhotoUploadResult.Success)
        result as PhotoUploadResult.Success
        assertTrue(result.sizeBytes <= 60_000, "expected compressed size <= 60000, was ${result.sizeBytes}")
        assertTrue(result.url.startsWith(PHOTO_URL_PREFIX))
        assertEquals(result.sizeBytes, service.usedBytes(owner))
    }

    @Test
    fun `rejects a photo that stays too large even at the lowest compression quality`() {
        val service = serviceWithLimits(maxImageBytes = 1_000, maxAccountBytes = 10_000_000)
        val owner = newUser("photo-owner-5@example.com")
        val file = MockMultipartFile("file", "big.jpg", "image/jpeg", gradientJpegBytes(2400, 1600))

        val result = service.upload(file, owner)

        assertTrue(result is PhotoUploadResult.Rejected)
        assertEquals(413, (result as PhotoUploadResult.Rejected).status)
        assertEquals(0L, service.usedBytes(owner))
    }

    @Test
    fun `rejects upload once it would exceed the per-account quota`() {
        val service = serviceWithLimits(maxImageBytes = 5_000_000, maxAccountBytes = 1_000)
        val owner = newUser("photo-owner-2@example.com")
        val file = MockMultipartFile("file", "small.jpg", "image/jpeg", gradientJpegBytes(200, 200))

        val result = service.upload(file, owner)

        assertTrue(result is PhotoUploadResult.Rejected)
        assertEquals(413, (result as PhotoUploadResult.Rejected).status)
        assertEquals(0L, service.usedBytes(owner))
    }

    @Test
    fun `rejects a file that is not a valid image`() {
        val service = serviceWithLimits(maxImageBytes = 5_000_000, maxAccountBytes = 10_000_000)
        val owner = newUser("photo-owner-3@example.com")
        val file = MockMultipartFile("file", "not-an-image.txt", "text/plain", "hello world".toByteArray())

        val result = service.upload(file, owner)

        assertTrue(result is PhotoUploadResult.Rejected)
        assertEquals(400, (result as PhotoUploadResult.Rejected).status)
    }

    @Test
    fun `deleteIfOwnedBy only removes photos belonging to the given owner`() {
        val service = serviceWithLimits(maxImageBytes = 5_000_000, maxAccountBytes = 10_000_000)
        val owner = newUser("photo-owner-4@example.com")
        val stranger = newUser("photo-stranger-4@example.com")
        val file = MockMultipartFile("file", "photo.jpg", "image/jpeg", gradientJpegBytes(200, 200))
        val uploaded = service.upload(file, owner) as PhotoUploadResult.Success

        service.deleteIfOwnedBy(uploaded.url, stranger)
        assertEquals(uploaded.sizeBytes, service.usedBytes(owner))

        service.deleteIfOwnedBy(uploaded.url, owner)
        assertEquals(0L, service.usedBytes(owner))
    }
}
