package com.scommit.domain.user.user.dto

import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import java.time.LocalDateTime

data class UserMeResponse(
    val id: Long,
    val email: String,
    val role: UserRole,
    val profile: UserProfileDto,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?,
) {
    constructor(user: User, profileImageUrl: String?) : this(
        checkNotNull(user.id),
        user.email,
        user.role,
        UserProfileDto(user, profileImageUrl),
        checkNotNull(user.createdAt),
        user.updatedAt,
    )
}
