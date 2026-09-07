package com.example.VoloMap.server

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FavoriteController(
    private val activityRepository: VolunteerActivityRepository,
    private val userRepository: UserRepository,
    private val favoriteRepository: FavoriteRepository,
) {

    @PostMapping("/activities/{id}/favorite")
    fun addFavorite(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<*> {
        val activity = activityRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
        val user = userRepository.findByEmail(authentication.name)!!

        if (favoriteRepository.findByUserAndActivity(user, activity) == null) {
            favoriteRepository.save(Favorite(user = user, activity = activity))
        }
        return ResponseEntity.ok().build<Any>()
    }

    @DeleteMapping("/activities/{id}/favorite")
    fun removeFavorite(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val activity = activityRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val user = userRepository.findByEmail(authentication.name)!!

        favoriteRepository.findByUserAndActivity(user, activity)?.let {
            favoriteRepository.delete(it)
        }
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/favorites/ids")
    fun favoriteIds(authentication: Authentication): ResponseEntity<List<Long>> {
        val user = userRepository.findByEmail(authentication.name)!!
        return ResponseEntity.ok(favoriteRepository.findByUser(user).map { it.activity.id })
    }
}
