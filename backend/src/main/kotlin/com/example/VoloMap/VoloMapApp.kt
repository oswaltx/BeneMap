package com.example.VoloMap

import com.example.VoloMap.server.Scraper
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VoloMapApp

fun main(args: Array<String>) {
    //val scraper = Scraper()
    //scraper.scrapeWebsite("https://oswalt.eu")
    //scraper.scrapeWebsite("https://engagementdatenbank.stadt-koeln.de/ergebnisse?fulltext=&id=&area_of_activity=All&target_group=All&postal_code=")
    runApplication<VoloMapApp>(*args) // Change to VoloMapApp instead of DemoApplication
}