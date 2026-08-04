package com.scommit.domain.user.user.dto

import com.scommit.domain.user.user.entity.User
import java.time.LocalDateTime

data class SignupResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    val createdAt: LocalDateTime,
) {
    constructor(user: User) : this(checkNotNull(user.id), user.email, user.nickname, checkNotNull(user.createdAt))
}
