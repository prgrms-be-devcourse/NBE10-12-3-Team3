package com.scommit.domain.user.user.dto

import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Int,
    val user: LoginProfile,
) {
    data class LoginProfile(
        val id: Long,
        val email: String,
        val nickname: String,
        val role: UserRole,
    ) {
        constructor(user: User) : this(checkNotNull(user.id), user.email, user.nickname, user.role)
    }

    constructor(accessToken: String, refreshToken: String?, expiresIn: Int, user: User) :
        this(accessToken, refreshToken, expiresIn, LoginProfile(user))
}
