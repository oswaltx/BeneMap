package com.example.VoloMap.server

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class CertificateService {

    fun generate(userName: String, totalHours: Double, activityCount: Int): ByteArray {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val width = page.mediaBox.width

            PDPageContentStream(document, page).use { content ->
                fun centeredText(text: String, font: PDType1Font, size: Float, y: Float) {
                    val textWidth = font.getStringWidth(text) / 1000 * size
                    content.beginText()
                    content.setFont(font, size)
                    content.newLineAtOffset((width - textWidth) / 2, y)
                    content.showText(text)
                    content.endText()
                }

                val activityWord = if (activityCount == 1) "Aktivität" else "Aktivitäten"
                val issuedOn = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

                centeredText("Ehrenamts-Zertifikat", PDType1Font.HELVETICA_BOLD, 24f, 720f)
                centeredText("BeneMap", PDType1Font.HELVETICA, 12f, 695f)
                centeredText(userName, PDType1Font.HELVETICA_BOLD, 18f, 620f)
                centeredText("hat sich mit insgesamt", PDType1Font.HELVETICA, 13f, 580f)
                centeredText("${formatHours(totalHours)} Stunden", PDType1Font.HELVETICA_BOLD, 20f, 550f)
                centeredText("ehrenamtlich engagiert", PDType1Font.HELVETICA, 13f, 520f)
                centeredText("($activityCount abgeschlossene $activityWord)", PDType1Font.HELVETICA, 11f, 495f)
                centeredText("Ausgestellt am $issuedOn", PDType1Font.HELVETICA, 10f, 100f)
            }

            val output = ByteArrayOutputStream()
            document.save(output)
            return output.toByteArray()
        }
    }

    // Ganze Stunden ohne Nachkommastelle anzeigen (z.B. "6" statt "6.0"), halbe
    // Stunden mit einer Nachkommastelle ("2.5") — sauberer für ein Zertifikat.
    private fun formatHours(hours: Double): String =
        if (hours == hours.toLong().toDouble()) hours.toLong().toString() else hours.toString()
}
