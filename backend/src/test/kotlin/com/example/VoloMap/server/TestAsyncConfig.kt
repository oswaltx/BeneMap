package com.example.VoloMap.server

import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.scheduling.annotation.AsyncConfigurer
import java.util.concurrent.Executor

/**
 * Runs @Async methods (the mailers) synchronously in tests. Without this, a mailer's
 * background thread can still be calling the mocked JavaMailSender while the next
 * test's @BeforeEach sets up a new stub on the same mock — Mockito isn't thread-safe
 * for concurrent stubbing/invocation, which surfaces as a misleading
 * "CannotStubVoidMethodWithReturnValue" error unrelated to the actual stub.
 */
@Configuration
class TestAsyncConfig : AsyncConfigurer {
    override fun getAsyncExecutor(): Executor = SyncTaskExecutor()
}
