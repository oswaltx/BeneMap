package com.example.VoloMap.server

import org.mockito.Mockito
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.mail.javamail.JavaMailSender

@Configuration
class TestMailConfiguration {
    @Bean
    @Primary
    fun javaMailSender(): JavaMailSender = Mockito.mock(JavaMailSender::class.java)
}
