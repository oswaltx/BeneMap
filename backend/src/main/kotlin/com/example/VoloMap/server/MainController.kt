package com.example.VoloMap.server

import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime

val sampleMarkers = listOf(
    Marker(1, 50.9375, 6.9603, "Hilfe für Obdachlose",
        LocalDateTime.of(2023, 10, 1, 12, 0),
        "Mühlheimerstraße 1, 50935 Köln",
        "Essen und Kleidung sammeln",
        "Engagierte Helfer verteilen warme Mahlzeiten und Kleidung."),
    Marker(2, 50.9381, 6.9645, "Tierheim Köln",
        LocalDateTime.of(2023, 10, 1, 12, 0),
        "Steinstraße 2, 50935 Köln",
        "Tierschutz",
        "Unterstützung beim Pflege der Tiere und bei Veranstaltungen."),
    Marker(3, 50.9390, 6.9631, "Umweltschutzgruppe",
        LocalDateTime.of(2026, 7, 20, 15, 30),
        "Hauptstraße 3, 50935 Köln",
        "Umweltpflege",
        "Gemeinsame Aktionen zur Säuberung von Parks und Grünflächen."),
    Marker(4, 50.9405, 6.9677, "Lesepatenschaften",
        LocalDateTime.of(2023, 9, 15, 9, 0),
        "Bahnhofstraße 4, 50935 Köln",
        "Leseförderung",
        "Freiwillige unterstützen Kinder beim Lesen."),
    Marker(5, 50.9358, 6.9612, "Seniorenhilfe",
        LocalDateTime.of(2023, 12, 25, 14, 0),
        "Bergstraße 5, 50935 Köln",
        "Begleitung und Unterstützung",
        "Freiwillige helfen Senioren im Alltag."),
    Marker(6, 50.9344, 6.9599, "Jugend- und Freizeitangebote",
        LocalDateTime.of(2024, 1, 10, 16, 45),
        "Dorfstraße 6, 50935 Köln",
        "Freizeitgestaltung",
        "Aktivitäten für Jugendliche organisieren und begleiten.")
)

@CrossOrigin(origins = ["http://localhost:5173"])
@RestController
class MainController {

    @GetMapping("/")
    fun index() = "Hello World!"

    @GetMapping("/markers")
    fun markers(@RequestParam dateFilter: String, @RequestParam category: String): List<Marker> {
        var filterDate = LocalDate.of(2023, 10, 1)
        try {
            filterDate = LocalDate.parse(dateFilter) // Assuming dateFilter is in the format YYYY-MM-DD
        }
        catch (e: Exception) {
            println(e)
        }
        //val today = LocalDate.now()

        return if (category =="Umweltpflege") {
            sampleMarkers.filter { it.dateTime.toLocalDate() == filterDate }
        } else {
            sampleMarkers
        }
    }
}
