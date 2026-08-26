package com.example.VoloMap

import com.example.VoloMap.server.Scraper
import com.example.VoloMap.server.VolunteerActivityRepository
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VoloMapApp

fun main(args: Array<String>) {
    val context = runApplication<VoloMapApp>(*args)

    val repository = context.getBean(VolunteerActivityRepository::class.java)
    if (repository.count() == 0L) {
        val scraper = context.getBean(Scraper::class.java)
        scraper.fakeScraper(30)
    }

    if (args.contains("--scrape")) {
        val scraper = context.getBean(Scraper::class.java)
        scraper.scrapeAllCategories(20)
    }
}
