package com.scommit.domain.user.user.dto

import com.scommit.domain.user.user.entity.User
import java.time.LocalDateTime

data class UserUpdateResponse(
    val id: Long,
    val email: String,
    val profile: UserProfileDto,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?,
) {
    constructor(user: User, profileImage: String?) : this(
        user.id,
        user.email,
        UserProfileDto(user, profileImage),
        user.createdAt,
        user.updatedAt,
    )
}
