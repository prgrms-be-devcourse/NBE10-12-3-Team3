package com.scommit.domain.notification.notification.dto

data class NotificationResponse(
    val type: NotificationType,
    val message: String,
    val targetId: Long?,
)
