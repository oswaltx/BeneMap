package com.example.VoloMap

import com.example.VoloMap.server.Scraper
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
class VoloMapApp

fun main(args: Array<String>) {
    val context = runApplication<VoloMapApp>(*args)

    if (args.contains("--seed-fake")) {
        val scraper = context.getBean(Scraper::class.java)
        scraper.fakeScraper(30)
    }

    if (args.contains("--scrape")) {
        val scraper = context.getBean(Scraper::class.java)
        scraper.scrapeAllCategories(20)
    }
}
