package com.example.VoloMap.server

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@CrossOrigin(origins = ["http://localhost:5173"])
@RestController
class MainController {
    @GetMapping("/api")
    fun api() = mapOf("message" to "Hello from Spring Boot")
    @GetMapping("/")
    fun index() = "Hello World!"
}