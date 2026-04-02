package com.example.VoloMap.server

import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime

@CrossOrigin(origins = ["http://localhost:5173"])
@RestController
class MainController(
    private val repository: VolunteerActivityRepository
) {

    @GetMapping("/")
    fun index() = "Hello World!"


    @GetMapping("/markers")
    fun markers(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) date: String?, // format: YYYY-MM-DD
    ): List<Marker> {
        val filterDate = date?.let { LocalDate.parse(it) }

        return repository.findAll()
            .filter { category == null || it.category == category }
            .filter { it.latitude != null && it.longitude != null }
            .map { activity ->
                Marker(
                    id = activity.id,
                    lat = activity.latitude!!,
                    lng = activity.longitude!!,
                    name = activity.name,
                    address = activity.addressText ?: "",
                    category = activity.category ?: "",
                    description = activity.description ?: "",
                    dateTime = LocalDateTime.now()
                        .plusDays((0..30).random().toLong())
                        .plusHours((8..20).random().toLong())
                )
            }
            .filter { filterDate == null || it.dateTime?.toLocalDate() == filterDate }
    }
    @GetMapping("/categories")
    fun categories(): List<String> {
        return repository.findAll()
            .mapNotNull { it.category }
            .distinct()
            .sorted()
    }


}
