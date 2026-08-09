package com.example.VoloMap.server

import org.jsoup.Jsoup
import org.springframework.stereotype.Component

@Component
class GeocodingService {
    fun geocode(address: String): Pair<Double, Double>? {
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
        return Pair(first.getDouble("lat"), first.getDouble("lon"))
    }
}
