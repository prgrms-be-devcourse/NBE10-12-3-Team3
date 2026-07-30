package com.scommit.domain.user.user.dto

import com.scommit.domain.user.user.entity.User
import java.time.LocalDateTime

data class UserMeResponse(
    val id: Long,
    val email: String,
    val profile: UserProfileDto,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?,
) {
    constructor(user: User, profileImageUrl: String?) : this(
        user.id,
        user.email,
        UserProfileDto(user, profileImageUrl),
        user.createdAt,
        user.updatedAt,
    )
}
