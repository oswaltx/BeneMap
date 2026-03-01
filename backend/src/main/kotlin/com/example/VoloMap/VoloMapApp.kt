package com.example.VoloMap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VoloMapApp

fun main(args: Array<String>) {
    println("Hello World")
    runApplication<VoloMapApp>(*args) // Change to VoloMapApp instead of DemoApplication
}