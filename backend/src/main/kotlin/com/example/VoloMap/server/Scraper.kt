package com.example.VoloMap.server
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

open class Scraper {
    fun scrapeWebsite(url: String, pageString: String?){
        fun getDocument(url: String): Document {
            val document = Jsoup.connect(url)
                .userAgent("VoloMap-Scraper/1.0 (TH Köln; david_ari_ikerimma.oswalt@smail.th-koeln.de)")
                .get()
            return document
        }

        // Scrape first page
        val firstDocument = getDocument(url)
        scrapeEhrenamtLinks(firstDocument)

        if (pageString == null)
            return

        // Iterate through all pages
        var page = 2
        while(true){
            val newUrl = url.replace("page=1", "page=$page")
            try {
                println("Scraping page $page")
                val document = getDocument(newUrl)
                scrapeEhrenamtLinks(document)
                page++
            } catch (e: Exception) {
                println("No more pages")
                break
            }
        }
    }

    fun scrapeEhrenamtLinks(document: Document) {
        val links = document.select("a.btn.btn-primary")

        links.forEach { link ->
            val href = link.attr("href")
            val fullUrl = "https://engagementdatenbank.stadt-koeln.de$href"

            println("Scraping: $fullUrl")
            scrapeEhrenamtDetails(fullUrl)
        }
    }

    fun scrapeEhrenamtDetails(url: String) {
        val document = Jsoup.connect(url)
            .userAgent("Mozilla/5.0")
            .get()

        // Alle Field-Divs finden
        val fields = document.select("div.field")

        val ehrenamtData = mutableMapOf<String, String>()

        fields.forEach { field ->
            // Label extrahieren
            val label = field.select("div.field__label").text()

            // Items extrahieren (kann mehrere geben)
            val items = field.select("div.field__item")
            val itemsText = items.joinToString(", ") { item ->
                // Wenn es ein Link ist, nimm den href, sonst den Text
                val link = item.select("a").attr("href")
                link.ifEmpty { item.text() }
            }

            if (label.isNotEmpty() && itemsText.isNotEmpty()) {
                ehrenamtData[label] = itemsText
                println("$label: $itemsText")
            }
        }

        // Hier könntest du die Daten speichern (z.B. in DB oder Liste)
        println("---")
    }
}
