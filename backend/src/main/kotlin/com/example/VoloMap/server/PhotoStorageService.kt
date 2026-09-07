package com.example.VoloMap.server

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream

// Longest edge an uploaded photo is scaled down to before compression — activity
// photos are shown at most a few hundred px wide in the UI, so anything a phone
// camera produces (often 3000px+) is needlessly large before it's even compressed.
private const val MAX_DIMENSION = 1920
private const val MIN_JPEG_QUALITY = 0.3f
private const val JPEG_QUALITY_STEP = 0.1f

sealed class PhotoUploadResult {
    data class Success(val url: String, val sizeBytes: Long) : PhotoUploadResult()
    data class Rejected(val status: Int, val message: String) : PhotoUploadResult()
}

@Service
class PhotoStorageService(
    private val uploadedPhotoRepository: UploadedPhotoRepository,
    @Value("\${photo.upload-dir:uploads/photos}") uploadDir: String,
    @Value("\${photo.max-image-bytes:5242880}") private val maxImageBytes: Long,
    @Value("\${photo.max-account-bytes:104857600}") private val maxAccountBytesValue: Long,
) {
    private val storageDir: Path = Path.of(uploadDir).toAbsolutePath().normalize().also {
        Files.createDirectories(it)
    }

    fun maxAccountBytes(): Long = maxAccountBytesValue

    fun maxImageBytes(): Long = maxImageBytes

    fun usedBytes(owner: User): Long = uploadedPhotoRepository.findByOwner(owner).sumOf { it.sizeBytes }

    fun upload(file: MultipartFile, owner: User): PhotoUploadResult {
        if (file.isEmpty) {
            return PhotoUploadResult.Rejected(400, "Keine Datei erhalten.")
        }

        val original = try {
            ImageIO.read(file.inputStream)
        } catch (e: Exception) {
            null
        }
        if (original == null) {
            return PhotoUploadResult.Rejected(400, "Datei ist kein gültiges Bild.")
        }

        val resized = resizeIfNeeded(original, MAX_DIMENSION)
        val compressed = compressToJpeg(resized, maxImageBytes)
            ?: return PhotoUploadResult.Rejected(
                413,
                "Bild ist auch komprimiert noch größer als das Limit von ${maxImageBytes / (1024 * 1024)} MB pro Bild."
            )

        val alreadyUsed = usedBytes(owner)
        if (alreadyUsed + compressed.size > maxAccountBytesValue) {
            return PhotoUploadResult.Rejected(
                413,
                "Speicherlimit für dein Konto erreicht (max. ${maxAccountBytesValue / (1024 * 1024)} MB insgesamt für alle Fotos)."
            )
        }

        val filename = "${UUID.randomUUID()}.jpg"
        Files.write(storageDir.resolve(filename), compressed)
        uploadedPhotoRepository.save(
            UploadedPhoto(owner = owner, storedFilename = filename, sizeBytes = compressed.size.toLong())
        )
        return PhotoUploadResult.Success(url = PHOTO_URL_PREFIX + filename, sizeBytes = compressed.size.toLong())
    }

    fun readFile(filename: String): ByteArray? {
        // fileName strips any directory components an attacker could smuggle in via
        // "../" — the endpoint must never be able to read outside storageDir.
        val safeName = Path.of(filename).fileName.toString()
        val path = storageDir.resolve(safeName)
        if (!Files.isRegularFile(path)) return null
        return Files.readAllBytes(path)
    }

    /** Deletes the uploaded photo `url` points to, but only if it belongs to `owner`. No-op for external URLs or files owned by someone else. */
    fun deleteIfOwnedBy(url: String, owner: User) {
        val filename = filenameFromUrl(url) ?: return
        val photo = uploadedPhotoRepository.findByStoredFilename(filename) ?: return
        if (photo.owner.id != owner.id) return
        uploadedPhotoRepository.delete(photo)
        Files.deleteIfExists(storageDir.resolve(filename))
    }

    /** Deletes every uploaded photo owned by `owner` — used when the account itself is deleted. */
    fun deleteAllOwnedBy(owner: User) {
        for (photo in uploadedPhotoRepository.findByOwner(owner)) {
            uploadedPhotoRepository.delete(photo)
            Files.deleteIfExists(storageDir.resolve(photo.storedFilename))
        }
    }

    private fun filenameFromUrl(url: String): String? {
        if (!url.startsWith(PHOTO_URL_PREFIX)) return null
        val name = url.removePrefix(PHOTO_URL_PREFIX)
        if (name.isBlank() || name.contains("/") || name.contains("..")) return null
        return name
    }

    private fun resizeIfNeeded(image: BufferedImage, maxDimension: Int): BufferedImage {
        if (image.width <= maxDimension && image.height <= maxDimension) return image
        val scale = maxDimension.toDouble() / maxOf(image.width, image.height)
        val newWidth = (image.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (image.height * scale).toInt().coerceAtLeast(1)
        val scaled = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val g = scaled.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, newWidth, newHeight)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(image, 0, 0, newWidth, newHeight, null)
        g.dispose()
        return scaled
    }

    // JPEG has no alpha channel — flattening onto white first avoids transparent PNG
    // areas turning black, which is what an uncomposited draw onto a fresh RGB buffer
    // would otherwise produce.
    private fun toRgb(image: BufferedImage): BufferedImage {
        if (image.type == BufferedImage.TYPE_INT_RGB) return image
        val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val g = rgb.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, image.width, image.height)
        g.drawImage(image, 0, 0, null)
        g.dispose()
        return rgb
    }

    // Re-encodes at decreasing JPEG quality until the result fits maxBytes, or gives
    // up once quality would drop below a level that's no longer a usable photo.
    private fun compressToJpeg(image: BufferedImage, maxBytes: Long): ByteArray? {
        val rgbImage = toRgb(image)
        var quality = 0.85f
        var bytes = encodeJpeg(rgbImage, quality)
        while (bytes.size > maxBytes && quality > MIN_JPEG_QUALITY) {
            quality -= JPEG_QUALITY_STEP
            bytes = encodeJpeg(rgbImage, quality)
        }
        return if (bytes.size <= maxBytes) bytes else null
    }

    private fun encodeJpeg(image: BufferedImage, quality: Float): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val params = writer.defaultWriteParam
        params.compressionMode = ImageWriteParam.MODE_EXPLICIT
        params.compressionQuality = quality
        val output = ByteArrayOutputStream()
        MemoryCacheImageOutputStream(output).use { ios ->
            writer.output = ios
            writer.write(null, IIOImage(image, null, null), params)
        }
        writer.dispose()
        return output.toByteArray()
    }
}
