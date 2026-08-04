package com.scommit.domain.notification.notification.repository

import com.scommit.domain.notification.notification.dto.NotificationResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@Component
class SseEmitterRepository {
    private val emitters = ConcurrentHashMap<Long, SseEmitter>()

    fun add(
        userId: Long,
        emitter: SseEmitter,
    ) {
        emitters[userId] = emitter
    }

    @Suppress("SwallowedException")
    fun sendToUser(
        userId: Long,
        response: NotificationResponse,
    ) {
        val emitter = emitters[userId] ?: return

        try {
            emitter.send(
                SseEmitter
                    .event()
                    .name("notification")
                    .data(response),
            )
        } catch (e: IOException) {
            emitters.remove(userId)
        }
    }

    fun remove(userId: Long) {
        emitters.remove(userId)
    }
}
