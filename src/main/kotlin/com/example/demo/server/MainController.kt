import com.example.demo.server.VolunteerActivity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
@CrossOrigin(origins = ["http://localhost:5173"])
@RestController
class MainController {

    @GetMapping("/activities")
    fun activities(): List<VolunteerActivity> {
        return listOf(
            VolunteerActivity(
                id = 1,
                name = "Beach Cleanup",
                latitude = 52.5,
                longitude = 13.4
            ),VolunteerActivity(
                id = 2,
                name = "Tree Cutting",
                latitude = 52.51,
                longitude = 13.41

            )
        )
    }
}
@CrossOrigin(origins = ["http://localhost:5173"])
@Controller
class MapController {
    @GetMapping("/")
    fun map(): String {
        return "index"
    }
}