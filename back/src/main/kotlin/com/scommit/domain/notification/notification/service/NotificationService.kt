package com.scommit.domain.notification.notification.service

import com.scommit.domain.notification.notification.dto.NotificationResponse
import com.scommit.domain.notification.notification.dto.NotificationType
import com.scommit.domain.notification.notification.repository.SseEmitterRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException

@Service
class NotificationService(
    private val sseEmitterRepository: SseEmitterRepository,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(NotificationService::class.java)
        private const val SSE_TIMEOUT_MS = 30 * 60 * 1000L
    }

    @Suppress("TooGenericExceptionThrown")
    fun subscribe(userId: Long): SseEmitter {
        val emitter = SseEmitter(SSE_TIMEOUT_MS)

        sseEmitterRepository.add(userId, emitter)

        emitter.onTimeout { sseEmitterRepository.remove(userId, emitter) }
        emitter.onCompletion { sseEmitterRepository.remove(userId, emitter) }

        try {
            emitter.send(
                SseEmitter
                    .event()
                    .name("connect")
                    .data("connected!"),
            )
        } catch (e: IOException) {
            logger.warn("SSE 초기 연결 이벤트 전송 실패 userId={}", userId, e)
            sseEmitterRepository.remove(userId, emitter)
            throw RuntimeException(e)
        }

        return emitter
    }

    fun notifyComment(
        postOwnerId: Long,
        commenterNickname: String,
        postId: Long,
    ) = sseEmitterRepository.sendToUser(
        postOwnerId,
        NotificationResponse(
            type = NotificationType.COMMENT,
            message = "${commenterNickname}님이 댓글을 작성했습니다.",
            targetId = postId,
        ),
    )

    fun notifyFollow(
        creatorId: Long,
        followerNickname: String,
        followerId: Long,
    ) = sseEmitterRepository.sendToUser(
        creatorId,
        NotificationResponse(
            type = NotificationType.FOLLOW,
            message = "${followerNickname}님이 팔로우했습니다.",
            targetId = followerId,
        ),
    )

    fun notifyMembership(
        creatorId: Long,
        memberNickname: String,
        memberId: Long,
    ) = sseEmitterRepository.sendToUser(
        creatorId,
        NotificationResponse(
            type = NotificationType.MEMBERSHIP,
            message = "${memberNickname}님이 멤버십에 가입했습니다.",
            targetId = memberId,
        ),
    )

    fun notifyNewPost(
        subscriberIds: List<Long>,
        creatorNickname: String,
        postId: Long?,
    ) = subscriberIds.forEach { subscriberId ->
        sseEmitterRepository.sendToUser(
            subscriberId,
            NotificationResponse(
                type = NotificationType.NEW_POST,
                message = "${creatorNickname}님이 새 게시글을 작성했습니다.",
                targetId = postId,
            ),
        )
    }
}
