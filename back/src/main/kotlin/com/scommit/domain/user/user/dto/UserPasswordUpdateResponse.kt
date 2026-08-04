package com.scommit.domain.user.user.dto

data class UserPasswordUpdateResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
)
