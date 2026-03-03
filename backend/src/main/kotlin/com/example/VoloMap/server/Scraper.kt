package com.example.VoloMap.server
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

open class Scraper {
    fun scrapeWebsite(url: String, pageString: String?){
        fun getDocument(url: String): Document {
            val document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .get()
            return document
        }
        println(getDocument(url).body())
        if (pageString == null)
            return
        //iterate through all pages
        var i = 0
        while(true){
            val newUrl = if (url.contains("$pageString$i")) {
                url.replace("$pageString$i", "$pageString${i+1}")
            } else if (i == 0){
                println("No first page")
            }
            else
                break
            i++
            print(getDocument(newUrl as String).body())
        }
    }
}