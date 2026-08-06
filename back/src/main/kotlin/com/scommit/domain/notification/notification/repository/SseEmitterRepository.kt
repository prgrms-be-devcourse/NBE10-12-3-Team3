package com.scommit.domain.notification.notification.repository

import com.scommit.domain.notification.notification.dto.NotificationResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@Component
class SseEmitterRepository {
    private val logger = LoggerFactory.getLogger(SseEmitterRepository::class.java)
    private val emitters = ConcurrentHashMap<Long, SseEmitter>()

    fun add(
        userId: Long,
        emitter: SseEmitter,
    ) {
        emitters.put(userId, emitter)?.complete()
    }

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
            logger.warn("SSE 전송 실패 (userId=$userId), 연결 제거", e)
            emitters.remove(userId)
        }
    }

    fun remove(
        userId: Long,
        emitter: SseEmitter,
    ) {
        emitters.remove(userId, emitter)
    }
}
