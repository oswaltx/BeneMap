package com.example.VoloMap.server

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GeocodingService {
    private val logger = LoggerFactory.getLogger(GeocodingService::class.java)

    fun geocode(address: String): Pair<Double, Double>? {
        return try {
            Thread.sleep(1100) // Nominatim rate limit: 1 req/s
            val encoded = java.net.URLEncoder.encode(address, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1"

            val response = Jsoup.connect(url)
                .userAgent("VoloMap-Scraper/1.0 (TH Köln; david_ari_ikerimma.oswalt@smail.th-koeln.de)")
                .ignoreContentType(true)
                .get()
                .body()
                .text()

            val json = org.json.JSONArray(response)
            if (json.length() == 0) return null

            val first = json.getJSONObject(0)
            Pair(first.getDouble("lat"), first.getDouble("lon"))
        } catch (e: Exception) {
            logger.warn("Geocoding failed for address '$address'", e)
            null
        }
    }
}
