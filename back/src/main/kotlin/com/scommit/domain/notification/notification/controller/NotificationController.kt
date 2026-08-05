package com.scommit.domain.notification.notification.controller

import com.scommit.domain.notification.notification.service.NotificationService
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import com.scommit.global.security.SecurityHelper
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationService: NotificationService,
    private val securityHelper: SecurityHelper,
) {
    @GetMapping(value = ["/subscribe"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribe(): SseEmitter {
        val actor = securityHelper.actor ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        val userId = checkNotNull(actor.id)
        return notificationService.subscribe(userId)
    }
}
