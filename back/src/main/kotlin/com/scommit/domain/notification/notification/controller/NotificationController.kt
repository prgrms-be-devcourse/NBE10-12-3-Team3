package com.scommit.domain.notification.notification.controller

import com.scommit.domain.notification.notification.service.NotificationService
import com.scommit.domain.user.user.entity.User
import com.scommit.global.security.CurrentUser
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationService: NotificationService,
) {
    @GetMapping(value = ["/subscribe"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribe(
        @CurrentUser actor: User,
    ): SseEmitter {
        val userId = checkNotNull(actor.id)
        return notificationService.subscribe(userId)
    }
}
