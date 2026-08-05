package com.scommit.domain.notification.notification.controller

import com.scommit.domain.notification.notification.repository.SseEmitterRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.global.security.CurrentUser
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException

@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val sseEmitterRepository: SseEmitterRepository,
) {
    companion object {
        private const val SSE_TIMEOUT_MS = 30 * 60 * 1000L
    }

    @Suppress("TooGenericExceptionThrown")
    @GetMapping(value = ["/subscribe"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribe(
        @CurrentUser actor: User,
    ): SseEmitter {
        val userId = checkNotNull(actor.id)

        val emitter = SseEmitter(SSE_TIMEOUT_MS)

        sseEmitterRepository.add(userId, emitter)

        emitter.onTimeout { sseEmitterRepository.remove(userId) }
        emitter.onCompletion { sseEmitterRepository.remove(userId) }

        try {
            emitter.send(
                SseEmitter
                    .event()
                    .name("connect")
                    .data("connected!"),
            )
        } catch (e: IOException) {
            sseEmitterRepository.remove(userId)
            throw RuntimeException(e)
        }

        return emitter
    }
}
