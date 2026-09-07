package com.example.VoloMap.server

// Shared between MainController (activity photoUrls) and AuthController (provider
// profile photoUrl) so both normalize hand-typed URLs and uploaded-photo paths the
// same way — a value already pointing at our own /uploads/photos/ storage must be
// kept as-is, not have "https://" glued onto the front like a bare domain would.
const val MAX_PHOTO_URLS = 10
const val PHOTO_URL_PREFIX = "/uploads/photos/"

fun parsePhotoUrls(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { normalizePhotoUrlValue(it) }
        .take(MAX_PHOTO_URLS)
}

fun normalizePhotoUrls(raw: String?): String? {
    val parsed = parsePhotoUrls(raw)
    return if (parsed.isEmpty()) null else parsed.joinToString("\n")
}

fun normalizePhotoUrlValue(value: String): String =
    when {
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.startsWith(PHOTO_URL_PREFIX) -> value
        else -> "https://$value"
    }
