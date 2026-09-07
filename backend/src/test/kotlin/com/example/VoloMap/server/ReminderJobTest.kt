package com.example.VoloMap.server

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDateTime

@SpringBootTest
class ReminderJobTest {

    @Autowired
    lateinit var reminderJob: ReminderJob

    @Autowired
    lateinit var activityRepository: VolunteerActivityRepository

    @Autowired
    lateinit var activitySignupRepository: ActivitySignupRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var emailVerificationTokenRepository: EmailVerificationTokenRepository

    @MockitoBean
    lateinit var mailSender: JavaMailSender

    @BeforeEach
    fun cleanUp() {
        whenever(mailSender.createMimeMessage()).thenReturn(MimeMessage(Session.getInstance(java.util.Properties())))
        activitySignupRepository.deleteAll()
        activityRepository.deleteAll()
        emailVerificationTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        activitySignupRepository.deleteAll()
        activityRepository.deleteAll()
        emailVerificationTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun newUser(email: String) = userRepository.save(
        User(email = email, passwordHash = "x", name = "Test", role = Role.USER)
    )

    @Test
    fun `sends a reminder for a signup starting in about 23point5 hours and marks it sent`() {
        val user = newUser("reminder1@example.com")
        val activity = activityRepository.save(
            VolunteerActivity(name = "Morgige Aktion", dateTime = LocalDateTime.now().plusHours(23).plusMinutes(30))
        )
        val signup = activitySignupRepository.save(ActivitySignup(user = user, activity = activity))

        reminderJob.sendUpcomingActivityReminders()

        verify(mailSender, timeout(2000)).send(any<MimeMessage>())
        val updated = activitySignupRepository.findById(signup.id).get()
        assertNotNull(updated.reminderSentAt)
    }

    @Test
    fun `does not send twice for the same signup`() {
        val user = newUser("reminder2@example.com")
        val activity = activityRepository.save(
            VolunteerActivity(name = "Morgige Aktion", dateTime = LocalDateTime.now().plusHours(23).plusMinutes(30))
        )
        activitySignupRepository.save(ActivitySignup(user = user, activity = activity))

        reminderJob.sendUpcomingActivityReminders()
        verify(mailSender, timeout(2000)).send(any<MimeMessage>())

        reminderJob.sendUpcomingActivityReminders()

        verify(mailSender, timeout(500)).send(any<MimeMessage>())
    }

    @Test
    fun `does not send for an activity outside the 23-24h window`() {
        val user = newUser("reminder3@example.com")
        val farActivity = activityRepository.save(
            VolunteerActivity(name = "Weit weg", dateTime = LocalDateTime.now().plusDays(5))
        )
        val signup = activitySignupRepository.save(ActivitySignup(user = user, activity = farActivity))

        reminderJob.sendUpcomingActivityReminders()

        verify(mailSender, never()).send(any<MimeMessage>())
        val unchanged = activitySignupRepository.findById(signup.id).get()
        assertNull(unchanged.reminderSentAt)
    }
}
