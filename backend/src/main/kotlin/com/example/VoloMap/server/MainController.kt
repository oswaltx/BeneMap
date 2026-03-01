package com.example.VoloMap.server

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@CrossOrigin(origins = ["http://localhost:5173"])
@RestController
class MainController {
    @GetMapping("/")
    fun index() = "Hello World!"
    @GetMapping("/markers")
    fun markers(): List<Marker> {
        return listOf(
            Marker(1, 50.9375, 6.9603, "Marker 1"),
            Marker(2, 50.9335, 6.9503, "Marker 2"),
            Marker(3, 50.9235, 6.9543, "Marker 3"),
            Marker(4, 50.9175, 6.9603, "Marker 4"),
        )
    }
}