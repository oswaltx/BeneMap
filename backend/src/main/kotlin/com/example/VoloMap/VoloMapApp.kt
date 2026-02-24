package com.example.VoloMap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoApplication

fun main(args: Array<String>) {
    println("Hello World")
	runApplication<DemoApplication>(*args)
}
